package com.tonic.vitavr.ui.filechooser;

import com.tonic.vitavr.ui.Style;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The sidebar of places: pinned folders, the user's special folders, recent folders and the drives. A
 * click navigates; the context menu pins, unpins, reorders and clears.
 */
final class QuickAccessPanel extends JPanel implements QuickAccessStore.Listener
{
    private final Consumer<File> navigator;
    private final QuickAccessStore store;
    private final DefaultListModel<Item> pinnedModel = new DefaultListModel<>();
    private final DefaultListModel<Item> quickAccessModel = new DefaultListModel<>();
    private final DefaultListModel<Item> recentModel = new DefaultListModel<>();
    private final DefaultListModel<Item> drivesModel = new DefaultListModel<>();
    private final JList<Item> pinnedList;
    private final JList<Item> quickAccessList;
    private final JList<Item> recentList;
    private final JList<Item> drivesList;
    private final JLabel pinnedHeader;
    private final JLabel recentHeader;

    QuickAccessPanel(QuickAccessStore store, Consumer<File> navigator)
    {
        super(new BorderLayout());
        this.navigator = navigator;
        this.store = store;
        setBackground(Style.BG_SECONDARY);
        setPreferredSize(new Dimension(Style.px(170), 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Style.BORDER));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Style.BG_SECONDARY);

        pinnedHeader = header("Pinned");
        content.add(pinnedHeader);
        pinnedList = list(pinnedModel);
        content.add(pinnedList);
        content.add(Box.createVerticalStrut(Style.px(12)));

        content.add(header("Quick Access"));
        quickAccessList = list(quickAccessModel);
        content.add(quickAccessList);
        content.add(Box.createVerticalStrut(Style.px(12)));

        recentHeader = header("Recent");
        content.add(recentHeader);
        recentList = list(recentModel);
        content.add(recentList);
        content.add(Box.createVerticalStrut(Style.px(12)));

        content.add(header("This PC"));
        drivesList = list(drivesModel);
        content.add(drivesList);
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Style.BG_SECONDARY);
        add(scroll, BorderLayout.CENTER);

        populatePinned();
        populateQuickAccess();
        populateRecent();
        populateDrives();
        updateSectionVisibility();
        store.addListener(this);
    }

    /** Stops listening to the store; called when the dialog closes. */
    void detach()
    {
        store.removeListener(this);
    }

    @Override
    public void pinnedChanged(List<File> pinned)
    {
        SwingUtilities.invokeLater(() ->
        {
            populatePinned();
            updateSectionVisibility();
        });
    }

    @Override
    public void recentChanged(List<File> recent)
    {
        SwingUtilities.invokeLater(() ->
        {
            populateRecent();
            updateSectionVisibility();
        });
    }

    private void updateSectionVisibility()
    {
        boolean hasPinned = pinnedModel.getSize() > 0;
        pinnedHeader.setVisible(hasPinned);
        pinnedList.setVisible(hasPinned);
        boolean hasRecent = recentModel.getSize() > 0;
        recentHeader.setVisible(hasRecent);
        recentList.setVisible(hasRecent);
        revalidate();
        repaint();
    }

    private static JLabel header(String title)
    {
        JLabel label = new JLabel(title);
        label.setForeground(Style.TEXT_MUTED);
        label.setFont(Style.boldFont(11));
        label.setBorder(BorderFactory.createEmptyBorder(Style.px(8), Style.px(12), Style.px(4), Style.px(8)));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JList<Item> list(DefaultListModel<Item> model)
    {
        JList<Item> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Style.BG_SECONDARY);
        list.setForeground(Style.TEXT);
        list.setFont(Style.font(Style.FONT_SIZE));
        list.setFixedCellHeight(Style.ROW_HEIGHT + Style.px(2));
        list.setCellRenderer(new ItemRenderer());
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e))
                {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint()))
                    {
                        navigator.accept(model.getElementAt(index).file);
                        clearSelection();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                maybeShowMenu(e, list, model);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                maybeShowMenu(e, list, model);
            }
        });
        return list;
    }

    private void maybeShowMenu(MouseEvent e, JList<Item> list, DefaultListModel<Item> model)
    {
        if (!e.isPopupTrigger())
        {
            return;
        }
        int index = list.locationToIndex(e.getPoint());
        if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint()))
        {
            return;
        }
        list.setSelectedIndex(index);
        Item item = model.getElementAt(index);
        JPopupMenu menu = Style.menu();
        if (item.kind == Kind.PINNED)
        {
            Style.menuItem(menu, "Unpin", () -> store.unpin(item.file));
            menu.addSeparator();
            Style.menuItem(menu, "Move Up", () -> store.movePinnedUp(item.file));
            Style.menuItem(menu, "Move Down", () -> store.movePinnedDown(item.file));
        }
        else if (item.kind == Kind.RECENT)
        {
            Style.menuItem(menu, "Pin to Quick Access", () -> store.pin(item.file));
            Style.menuItem(menu, "Remove", () -> store.removeRecent(item.file));
            menu.addSeparator();
            Style.menuItem(menu, "Clear All Recent", store::clearRecent);
        }
        else if (store.isPinned(item.file))
        {
            Style.menuItem(menu, "Unpin", () -> store.unpin(item.file));
        }
        else
        {
            Style.menuItem(menu, "Pin to Quick Access", () -> store.pin(item.file));
        }
        menu.show(list, e.getX(), e.getY());
    }

    private void populatePinned()
    {
        pinnedModel.clear();
        for (File dir : store.pinned())
        {
            pinnedModel.addElement(new Item(nameOf(dir), dir, Kind.PINNED));
        }
    }

    private void populateQuickAccess()
    {
        quickAccessModel.clear();
        Map<String, File> folders = FileSystemWorker.specialFolders();
        for (String key : new String[] {"Desktop", "Documents", "Downloads", "Home"})
        {
            File folder = folders.get(key);
            if (folder != null)
            {
                quickAccessModel.addElement(new Item(key, folder, Kind.FOLDER));
            }
        }
    }

    private void populateRecent()
    {
        recentModel.clear();
        for (File dir : store.recent())
        {
            recentModel.addElement(new Item(nameOf(dir), dir, Kind.RECENT));
        }
    }

    private void populateDrives()
    {
        drivesModel.clear();
        for (File root : FileSystemWorker.roots())
        {
            String name = FileSystemWorker.displayName(root);
            drivesModel.addElement(new Item(name.isEmpty() ? root.getAbsolutePath() : name, root, Kind.DRIVE));
        }
    }

    private static String nameOf(File dir)
    {
        String name = dir.getName();
        return name.isEmpty() ? dir.getAbsolutePath() : name;
    }

    void clearSelection()
    {
        pinnedList.clearSelection();
        quickAccessList.clearSelection();
        recentList.clearSelection();
        drivesList.clearSelection();
    }

    private enum Kind
    {
        PINNED, FOLDER, RECENT, DRIVE
    }

    private static final class Item
    {
        final String name;
        final File file;
        final Kind kind;

        Item(String name, File file, Kind kind)
        {
            this.name = name;
            this.file = file;
            this.kind = kind;
        }
    }

    private static final class ItemRenderer implements ListCellRenderer<Item>
    {
        private final JPanel panel = new JPanel(new BorderLayout(6, 0));
        private final JLabel icon = new JLabel();
        private final JLabel text = new JLabel();
        private final JLabel pin = new JLabel();

        ItemRenderer()
        {
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(Style.px(3), Style.px(12), Style.px(3), Style.px(8)));
            text.setFont(Style.font(Style.FONT_SIZE));
            pin.setFont(Style.font(10));
            JPanel left = new JPanel(new BorderLayout(4, 0));
            left.setOpaque(false);
            left.add(icon, BorderLayout.WEST);
            left.add(text, BorderLayout.CENTER);
            panel.add(left, BorderLayout.CENTER);
            panel.add(pin, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Item> list, Item value, int index,
                                                      boolean isSelected, boolean cellHasFocus)
        {
            panel.setBackground(isSelected ? Style.SELECTION : Style.BG_SECONDARY);
            text.setForeground(Style.TEXT);
            pin.setForeground(Style.TEXT_MUTED);
            text.setText(value.name);
            icon.setIcon(FileSystemWorker.systemIcon(value.file));
            pin.setText(value.kind == Kind.PINNED ? "⌂" : "");
            return panel;
        }
    }
}
