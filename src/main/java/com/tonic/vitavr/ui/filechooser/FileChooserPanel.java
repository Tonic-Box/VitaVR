package com.tonic.vitavr.ui.filechooser;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The chooser's content: the path bar above a split of quick-access places and the file table, with the
 * file name, type filter and action buttons below. Ctrl+L edits the path, Escape cancels, Ctrl+N makes a
 * folder when saving.
 */
final class FileChooserPanel extends JPanel
{
    interface FileChooserListener
    {
        void onFilesSelected(List<File> files);

        void onCancelled();
    }

    private final QuickAccessStore store;
    private final PathBar pathBar;
    private final QuickAccessPanel quickAccessPanel;
    private final FileListPanel fileListPanel;
    private final FileFilterComboBox filterComboBox;
    private final JTextField fileNameField;
    private final ChooserButton actionButton;
    private final ChooserButton cancelButton;

    private FileChooserMode mode = FileChooserMode.OPEN_FILE;
    private FileChooserListener listener;
    private File currentDirectory;
    private ExtensionFileFilter currentFilter;
    private int listingSerial;
    private boolean focusNameAfterListing;

    FileChooserPanel(QuickAccessStore store)
    {
        super(new BorderLayout());
        this.store = store;
        setBackground(ChooserStyle.BG_PRIMARY);
        setPreferredSize(new Dimension(ChooserStyle.px(800), ChooserStyle.px(500)));

        pathBar = new PathBar(this::navigateTo);
        quickAccessPanel = new QuickAccessPanel(store, this::navigateTo);
        fileListPanel = new FileListPanel(store);
        filterComboBox = new FileFilterComboBox();
        fileNameField = new JTextField();
        actionButton = ChooserButton.primary("Open");
        cancelButton = ChooserButton.plain("Cancel");

        fileListPanel.fileListListener(new FileListPanel.FileListListener()
        {
            @Override
            public void onFileDoubleClicked(File file)
            {
                fileNameField.setText(file.getName());
                handleAction();
            }

            @Override
            public void onSelectionChanged(List<File> selectedFiles)
            {
                handleSelectionChanged(selectedFiles);
            }

            @Override
            public void onDirectoryEntered(File directory)
            {
                navigateTo(directory);
            }
        });
        filterComboBox.filterChangeListener(filter ->
        {
            currentFilter = filter;
            refresh();
        });

        add(pathBar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, quickAccessPanel, fileListPanel);
        split.setDividerLocation(ChooserStyle.px(180));
        split.setDividerSize(ChooserStyle.px(4));
        split.setBorder(null);
        split.setBackground(ChooserStyle.BORDER);
        add(split, BorderLayout.CENTER);
        add(bottomPanel(), BorderLayout.SOUTH);
        installShortcuts();
    }

    private JPanel bottomPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(ChooserStyle.BG_SECONDARY);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ChooserStyle.BORDER),
                BorderFactory.createEmptyBorder(ChooserStyle.px(12), ChooserStyle.px(12), ChooserStyle.px(12), ChooserStyle.px(12))));

        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setOpaque(false);
        JLabel nameLabel = new JLabel("File name:");
        nameLabel.setForeground(ChooserStyle.TEXT);
        nameLabel.setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
        nameLabel.setPreferredSize(new Dimension(ChooserStyle.px(70), ChooserStyle.px(24)));
        fileNameField.setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
        fileNameField.setBackground(ChooserStyle.BG_TERTIARY);
        fileNameField.setForeground(ChooserStyle.TEXT);
        fileNameField.setCaretColor(ChooserStyle.TEXT);
        fileNameField.setBorder(ChooserStyle.fieldBorder(ChooserStyle.BORDER));
        fileNameField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                validateFileName();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                validateFileName();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                validateFileName();
            }
        });
        fileNameField.addActionListener(e -> handleAction());
        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(fileNameField, BorderLayout.CENTER);
        nameRow.add(filterComboBox, BorderLayout.EAST);
        panel.add(nameRow, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        actionButton.addActionListener(e -> handleAction());
        cancelButton.addActionListener(e -> cancel());
        buttons.add(actionButton);
        buttons.add(cancelButton);
        panel.add(buttons, BorderLayout.SOUTH);
        validateFileName();
        return panel;
    }

    private void installShortcuts()
    {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "focusPathBar", pathBar::enterEditMode);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "newFolder", () ->
        {
            if (mode == FileChooserMode.SAVE_FILE)
            {
                createNewFolder();
            }
        });
    }

    private void bind(KeyStroke stroke, String key, Runnable action)
    {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, key);
        getActionMap().put(key, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                action.run();
            }
        });
    }

    /**
     * Lists a directory into the table, as an absolute path so the crumb trail runs from the drive; a
     * listing still in flight for an earlier request is discarded.
     */
    void navigateTo(File requested)
    {
        if (requested == null || !requested.isDirectory())
        {
            return;
        }
        File directory = requested.getAbsoluteFile();
        int serial = ++listingSerial;
        currentDirectory = directory;
        pathBar.currentDirectory(directory);
        FileSystemWorker.listDirectory(directory, currentFilter, new FileSystemWorker.DirectoryListingListener()
        {
            @Override
            public void onListingComplete(File dir, List<File> files)
            {
                if (serial != listingSerial)
                {
                    return;
                }
                fileListPanel.files(mode == FileChooserMode.SELECT_DIRECTORY ? directoriesOnly(files) : files, directory);
                store.addRecent(directory);
                if (focusNameAfterListing)
                {
                    focusNameAfterListing = false;
                    fileNameField.requestFocusInWindow();
                    fileNameField.selectAll();
                }
                else
                {
                    fileListPanel.focusTable();
                }
            }

            @Override
            public void onListingError(File dir, Exception e)
            {
                if (serial == listingSerial)
                {
                    fileListPanel.files(new ArrayList<>(), directory);
                }
            }
        });
    }

    private static List<File> directoriesOnly(List<File> files)
    {
        List<File> result = new ArrayList<>();
        for (File file : files)
        {
            if (file.isDirectory())
            {
                result.add(file);
            }
        }
        return result;
    }

    void refresh()
    {
        if (currentDirectory != null)
        {
            FileSystemWorker.invalidate(currentDirectory);
            navigateTo(currentDirectory);
        }
    }

    private void handleSelectionChanged(List<File> selected)
    {
        if (selected.isEmpty())
        {
            return;
        }
        if (mode == FileChooserMode.SELECT_DIRECTORY)
        {
            for (File file : selected)
            {
                if (file.isDirectory())
                {
                    fileNameField.setText(file.getName());
                    return;
                }
            }
            return;
        }
        if (selected.size() == 1)
        {
            if (!selected.get(0).isDirectory())
            {
                fileNameField.setText(selected.get(0).getName());
            }
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (File file : selected)
        {
            if (!file.isDirectory())
            {
                if (sb.length() > 0)
                {
                    sb.append("; ");
                }
                sb.append('"').append(file.getName()).append('"');
            }
        }
        if (sb.length() > 0)
        {
            fileNameField.setText(sb.toString());
        }
    }

    private void validateFileName()
    {
        actionButton.setEnabled(mode == FileChooserMode.SELECT_DIRECTORY || !fileNameField.getText().trim().isEmpty());
    }

    /** Resolves the name field the way the mode wants it: a path to walk into, files to open, or a file to write. */
    private void handleAction()
    {
        String text = fileNameField.getText().trim();
        if (text.isEmpty() && mode != FileChooserMode.SELECT_DIRECTORY)
        {
            return;
        }
        File typed = text.isEmpty() ? null : new File(text);
        if (typed != null && typed.isAbsolute())
        {
            if (typed.isDirectory())
            {
                if (mode == FileChooserMode.SELECT_DIRECTORY)
                {
                    approve(List.of(typed));
                }
                else
                {
                    navigateTo(typed);
                    fileNameField.setText("");
                }
                return;
            }
            if (typed.isFile() || (mode == FileChooserMode.SAVE_FILE && typed.getParentFile() != null && typed.getParentFile().isDirectory()))
            {
                approveFile(typed);
                return;
            }
        }
        if (currentDirectory == null)
        {
            return;
        }
        if (mode == FileChooserMode.SELECT_DIRECTORY)
        {
            File chosen = text.isEmpty() ? currentDirectory : new File(currentDirectory, text);
            approve(List.of(chosen.isDirectory() ? chosen : currentDirectory));
            return;
        }
        if (text.contains(";"))
        {
            List<File> files = new ArrayList<>();
            for (String part : text.split(";"))
            {
                String name = unquote(part.trim());
                File file = name.isEmpty() ? null : new File(currentDirectory, name);
                if (file != null && file.isFile())
                {
                    files.add(file);
                }
            }
            if (!files.isEmpty())
            {
                approve(files);
            }
            return;
        }
        File file = new File(currentDirectory, unquote(text));
        if (file.isDirectory())
        {
            navigateTo(file);
            fileNameField.setText("");
            return;
        }
        if (mode == FileChooserMode.SAVE_FILE || file.isFile())
        {
            approveFile(file);
        }
    }

    private static String unquote(String name)
    {
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\""))
        {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    private void approveFile(File file)
    {
        if (mode == FileChooserMode.SAVE_FILE && file.exists())
        {
            int answer = JOptionPane.showConfirmDialog(this, file.getName() + " already exists.\nReplace it?", "Confirm Save",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION)
            {
                return;
            }
        }
        approve(List.of(file));
    }

    private void approve(List<File> files)
    {
        if (listener != null)
        {
            listener.onFilesSelected(files);
        }
    }

    private void cancel()
    {
        if (listener != null)
        {
            listener.onCancelled();
        }
    }

    private void createNewFolder()
    {
        if (currentDirectory == null)
        {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty())
        {
            return;
        }
        File folder = new File(currentDirectory, name.trim());
        if (folder.exists())
        {
            JOptionPane.showMessageDialog(this, "A folder with this name already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!folder.mkdir())
        {
            JOptionPane.showMessageDialog(this, "Failed to create folder.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        refresh();
        fileListPanel.selectFile(folder.getName());
    }

    void mode(FileChooserMode mode)
    {
        this.mode = mode;
        fileListPanel.mode(mode);
        switch (mode)
        {
            case SAVE_FILE:
                actionButton.setText("Save");
                break;
            case SELECT_DIRECTORY:
                actionButton.setText("Select");
                break;
            case OPEN_FILE:
            default:
                actionButton.setText("Open");
                break;
        }
        validateFileName();
        refresh();
    }

    void fileChooserListener(FileChooserListener listener)
    {
        this.listener = listener;
    }

    void currentDirectory(File directory)
    {
        navigateTo(directory);
    }

    File currentDirectory()
    {
        return currentDirectory;
    }

    void selectedFileName(String name)
    {
        fileNameField.setText(name);
    }

    void fileFilters(ExtensionFileFilter... filters)
    {
        filterComboBox.filters(filters);
        currentFilter = filterComboBox.selectedFilter();
    }

    /** Puts the caret in the name field once the first listing lands, so a suggested name is ready to overtype. */
    void focusFileNameField()
    {
        focusNameAfterListing = true;
        fileNameField.requestFocusInWindow();
        fileNameField.selectAll();
    }

    /** Releases the store listener when the dialog goes away. */
    void dispose()
    {
        quickAccessPanel.detach();
    }
}
