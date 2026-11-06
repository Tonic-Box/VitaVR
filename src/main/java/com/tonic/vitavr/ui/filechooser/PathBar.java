package com.tonic.vitavr.ui.filechooser;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Back, forward and up buttons beside a breadcrumb trail of the current directory. Clicking a crumb
 * navigates to it; clicking the empty trail turns it into a text field for typing or pasting a path. A
 * trail too long for the bar keeps its tail and folds the leading segments behind an ellipsis.
 */
final class PathBar extends JPanel
{
    private static final String CARD_CRUMBS = "crumbs";
    private static final String CARD_EDIT = "edit";

    private final Consumer<File> navigator;
    private final List<File> history = new ArrayList<>();
    private int historyIndex = -1;

    private final ChooserButton backButton;
    private final ChooserButton forwardButton;
    private final ChooserButton upButton;
    private final JPanel pathContainer;
    private final CardLayout pathCards;
    private final BreadcrumbTrail breadcrumbPanel;
    private final JTextField pathField;
    private File currentDirectory;
    private boolean editing;

    PathBar(Consumer<File> navigator)
    {
        this.navigator = navigator;
        setLayout(new BorderLayout(4, 0));
        setBackground(ChooserStyle.BG_SECONDARY);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ChooserStyle.BORDER),
                BorderFactory.createEmptyBorder(ChooserStyle.px(4), ChooserStyle.px(8), ChooserStyle.px(4), ChooserStyle.px(8))));
        setPreferredSize(new Dimension(0, ChooserStyle.px(36)));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navButtons.setOpaque(false);
        backButton = ChooserButton.nav("←", "Go back");
        forwardButton = ChooserButton.nav("→", "Go forward");
        upButton = ChooserButton.nav("↑", "Go up");
        backButton.addActionListener(e -> goBack());
        forwardButton.addActionListener(e -> goForward());
        upButton.addActionListener(e -> goUp());
        navButtons.add(backButton);
        navButtons.add(forwardButton);
        navButtons.add(upButton);
        add(navButtons, BorderLayout.WEST);

        pathCards = new CardLayout();
        pathContainer = new JPanel(pathCards);
        pathContainer.setOpaque(false);

        breadcrumbPanel = new BreadcrumbTrail();
        breadcrumbPanel.setOpaque(false);
        breadcrumbPanel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        breadcrumbPanel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                enterEditMode();
            }
        });

        pathField = new JTextField();
        pathField.setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
        pathField.setBackground(ChooserStyle.BG_TERTIARY);
        pathField.setForeground(ChooserStyle.TEXT);
        pathField.setCaretColor(ChooserStyle.TEXT);
        pathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ChooserStyle.ACCENT),
                BorderFactory.createEmptyBorder(ChooserStyle.px(2), ChooserStyle.px(6), ChooserStyle.px(2), ChooserStyle.px(6))));
        pathField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    applyEditedPath();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    exitEditMode();
                    e.consume();
                }
            }
        });
        pathField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                exitEditMode();
            }
        });

        pathContainer.add(breadcrumbPanel, CARD_CRUMBS);
        pathContainer.add(pathField, CARD_EDIT);
        add(pathContainer, BorderLayout.CENTER);
        updateNavigationButtons();
    }

    File currentDirectory()
    {
        return currentDirectory;
    }

    /** Shows a directory as the trail and records it in the history, dropping any forward entries. */
    void currentDirectory(File directory)
    {
        if (directory == null || !directory.exists())
        {
            return;
        }
        this.currentDirectory = directory;
        if (historyIndex < 0 || !directory.equals(history.get(historyIndex)))
        {
            while (history.size() > historyIndex + 1)
            {
                history.remove(history.size() - 1);
            }
            history.add(directory);
            historyIndex = history.size() - 1;
        }
        updateBreadcrumbs();
        updateNavigationButtons();
    }

    private void updateBreadcrumbs()
    {
        List<File> segments = new ArrayList<>();
        for (File current = currentDirectory; current != null; current = current.getParentFile())
        {
            segments.add(0, current);
        }
        breadcrumbPanel.segments(segments);
    }

    private static String nameOf(File segment)
    {
        String name = FileSystemWorker.displayName(segment);
        return name.isEmpty() ? segment.getAbsolutePath() : name;
    }

    private JLabel crumb(File segment)
    {
        JLabel label = new JLabel(nameOf(segment));
        label.setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
        label.setForeground(ChooserStyle.TEXT);
        label.setBorder(BorderFactory.createEmptyBorder(ChooserStyle.px(2), ChooserStyle.px(6), ChooserStyle.px(2), ChooserStyle.px(6)));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                label.setForeground(ChooserStyle.ACCENT_BRIGHT);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                label.setForeground(ChooserStyle.TEXT);
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                navigator.accept(segment);
            }
        });
        return label;
    }

    private static JLabel separator()
    {
        JLabel sep = new JLabel(">");
        sep.setFont(ChooserStyle.font(10));
        sep.setForeground(ChooserStyle.TEXT_MUTED);
        sep.setBorder(BorderFactory.createEmptyBorder(ChooserStyle.px(2), ChooserStyle.px(2), ChooserStyle.px(2), ChooserStyle.px(2)));
        return sep;
    }

    /**
     * One row of crumbs and separators. When they outgrow the width the leading ones are folded away
     * behind an ellipsis whose click lists them, so the current folder is always the visible tail.
     */
    private final class BreadcrumbTrail extends JPanel
    {
        private final JLabel ellipsis = new JLabel("\u2026");
        private final List<Component> trail = new ArrayList<>();
        private final List<File> segments = new ArrayList<>();
        private int hiddenSegments;

        BreadcrumbTrail()
        {
            super(null);
            ellipsis.setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
            ellipsis.setForeground(ChooserStyle.TEXT);
            ellipsis.setBorder(BorderFactory.createEmptyBorder(ChooserStyle.px(2), ChooserStyle.px(6), ChooserStyle.px(2), ChooserStyle.px(6)));
            ellipsis.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ellipsis.setToolTipText("Folders above");
            ellipsis.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    showHidden(e);
                }
            });
            add(ellipsis);
        }

        void segments(List<File> newSegments)
        {
            for (Component c : trail)
            {
                remove(c);
            }
            trail.clear();
            segments.clear();
            segments.addAll(newSegments);
            for (int i = 0; i < segments.size(); i++)
            {
                if (i > 0)
                {
                    trail.add(separator());
                }
                trail.add(crumb(segments.get(i)));
            }
            for (Component c : trail)
            {
                add(c);
            }
            revalidate();
            repaint();
        }

        private void showHidden(MouseEvent e)
        {
            JPopupMenu menu = ChooserStyle.menu();
            for (int i = 0; i < hiddenSegments; i++)
            {
                File segment = segments.get(i);
                ChooserStyle.menuItem(menu, nameOf(segment), () -> navigator.accept(segment));
            }
            if (menu.getComponentCount() > 0)
            {
                menu.show(ellipsis, e.getX(), e.getY());
            }
        }

        @Override
        public Dimension getPreferredSize()
        {
            int width = 0;
            int height = 0;
            for (Component c : trail)
            {
                Dimension d = c.getPreferredSize();
                width += d.width;
                height = Math.max(height, d.height);
            }
            return new Dimension(width, Math.max(height, ellipsis.getPreferredSize().height));
        }

        @Override
        public void doLayout()
        {
            int width = getWidth();
            int height = getHeight();
            int total = getPreferredSize().width;
            int first = 0;
            if (total > width && trail.size() > 1)
            {
                int available = width - ellipsis.getPreferredSize().width;
                while (first + 2 < trail.size() && total > available)
                {
                    total -= trail.get(first).getPreferredSize().width + trail.get(first + 1).getPreferredSize().width;
                    first += 2;
                }
            }
            hiddenSegments = first / 2;
            int x = 0;
            ellipsis.setVisible(first > 0);
            if (first > 0)
            {
                x = place(ellipsis, x, height);
            }
            for (int i = 0; i < trail.size(); i++)
            {
                Component c = trail.get(i);
                if (i < first)
                {
                    c.setBounds(0, 0, 0, 0);
                }
                else
                {
                    x = place(c, x, height);
                }
            }
        }

        private int place(Component c, int x, int height)
        {
            Dimension d = c.getPreferredSize();
            c.setBounds(x, (height - d.height) / 2, d.width, d.height);
            return x + d.width;
        }
    }

    /** Swaps the trail for a text field holding the current path, selected for overtyping. */
    void enterEditMode()
    {
        if (editing)
        {
            return;
        }
        editing = true;
        pathField.setText(currentDirectory == null ? "" : currentDirectory.getAbsolutePath());
        pathCards.show(pathContainer, CARD_EDIT);
        pathField.requestFocusInWindow();
        pathField.selectAll();
    }

    private void exitEditMode()
    {
        if (!editing)
        {
            return;
        }
        editing = false;
        pathCards.show(pathContainer, CARD_CRUMBS);
    }

    private void applyEditedPath()
    {
        String path = pathField.getText().trim();
        exitEditMode();
        if (path.isEmpty())
        {
            return;
        }
        File target = new File(path);
        if (target.isDirectory())
        {
            navigator.accept(target);
        }
        else if (target.isFile() && target.getParentFile() != null)
        {
            navigator.accept(target.getParentFile());
        }
        else
        {
            flashError();
            enterEditMode();
        }
    }

    private void flashError()
    {
        Color original = ChooserStyle.BG_TERTIARY;
        Color error = ChooserStyle.ERROR;
        pathField.setBackground(new Color(error.getRed() / 3, error.getGreen() / 6, error.getBlue() / 6));
        Timer timer = new Timer(500, e -> pathField.setBackground(original));
        timer.setRepeats(false);
        timer.start();
    }

    private void updateNavigationButtons()
    {
        backButton.setEnabled(historyIndex > 0);
        forwardButton.setEnabled(historyIndex < history.size() - 1);
        upButton.setEnabled(currentDirectory != null && currentDirectory.getParentFile() != null);
    }

    void goBack()
    {
        if (historyIndex > 0)
        {
            historyIndex--;
            showHistoryEntry();
        }
    }

    void goForward()
    {
        if (historyIndex < history.size() - 1)
        {
            historyIndex++;
            showHistoryEntry();
        }
    }

    private void showHistoryEntry()
    {
        currentDirectory = history.get(historyIndex);
        updateBreadcrumbs();
        updateNavigationButtons();
        navigator.accept(currentDirectory);
    }

    void goUp()
    {
        if (currentDirectory != null && currentDirectory.getParentFile() != null)
        {
            navigator.accept(currentDirectory.getParentFile());
        }
    }
}
