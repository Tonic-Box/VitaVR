package com.tonic.vitavr.ui.filechooser;

import com.tonic.vitavr.ui.Style;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

/**
 * The file table: sortable columns, double-click or Enter to open, Backspace up, type-ahead selection,
 * and a context menu for pinning folders and, when saving, making or deleting entries.
 */
final class FileListPanel extends JPanel
{
    private static final long TYPE_AHEAD_TIMEOUT_MS = 1000;

    interface FileListListener
    {
        void onFileDoubleClicked(File file);

        void onSelectionChanged(List<File> selectedFiles);

        void onDirectoryEntered(File directory);
    }

    private final QuickAccessStore store;
    private final FileListModel model = new FileListModel();
    private final JTable table = new JTable(model);
    private FileListListener listener;
    private FileChooserMode mode = FileChooserMode.OPEN_FILE;
    private File currentDirectory;
    private final StringBuilder typeAhead = new StringBuilder();
    private long lastKeyTime;

    FileListPanel(QuickAccessStore store)
    {
        super(new BorderLayout());
        this.store = store;
        setBackground(Style.BG_TERTIARY);
        setupTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Style.BG_TERTIARY);
        add(scroll, BorderLayout.CENTER);
    }

    private void setupTable()
    {
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(Style.ROW_HEIGHT);
        table.setBackground(Style.BG_TERTIARY);
        table.setForeground(Style.TEXT);
        table.setSelectionBackground(Style.SELECTION);
        table.setSelectionForeground(Style.TEXT);
        table.setFont(Style.font(Style.FONT_SIZE));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(false);

        FileListRenderer renderer = new FileListRenderer(model);
        for (int i = 0; i < table.getColumnCount(); i++)
        {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        column(FileListModel.COL_ICON, Style.px(28)).setMinWidth(Style.px(28));
        column(FileListModel.COL_ICON, Style.px(28)).setMaxWidth(Style.px(28));
        column(FileListModel.COL_NAME, Style.px(200));
        column(FileListModel.COL_SIZE, Style.px(80));
        column(FileListModel.COL_DATE, Style.px(130));
        column(FileListModel.COL_TYPE, Style.px(100));

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                int column = header.columnAtPoint(e.getPoint());
                if (column >= 0 && column != FileListModel.COL_ICON)
                {
                    model.sortBy(column);
                    header.repaint();
                }
            }
        });

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e))
                {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0)
                    {
                        open(row);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                maybeShowMenu(e);
            }
        });
        table.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                handleKeyPress(e);
            }

            @Override
            public void keyTyped(KeyEvent e)
            {
                handleKeyTyped(e);
            }
        });
        table.getSelectionModel().addListSelectionListener(e ->
        {
            if (!e.getValueIsAdjusting() && listener != null)
            {
                listener.onSelectionChanged(selectedFiles());
            }
        });
    }

    private TableColumn column(int index, int width)
    {
        TableColumn column = table.getColumnModel().getColumn(index);
        column.setPreferredWidth(width);
        return column;
    }

    private void maybeShowMenu(MouseEvent e)
    {
        if (!e.isPopupTrigger())
        {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && !table.isRowSelected(row))
        {
            table.setRowSelectionInterval(row, row);
        }
        File selected = selectedFile();
        JPopupMenu menu = Style.menu();
        if (selected != null && selected.isDirectory())
        {
            if (store.isPinned(selected))
            {
                Style.menuItem(menu, "Unpin from Quick Access", () -> store.unpin(selected));
            }
            else
            {
                Style.menuItem(menu, "Pin to Quick Access", () -> store.pin(selected));
            }
        }
        if (mode == FileChooserMode.SAVE_FILE)
        {
            if (menu.getComponentCount() > 0)
            {
                menu.addSeparator();
            }
            Style.menuItem(menu, "New Folder", this::createNewFolder);
            if (selected != null)
            {
                Style.menuItem(menu, "Delete", this::deleteSelectedFiles);
            }
        }
        if (menu.getComponentCount() > 0)
        {
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    private void open(int row)
    {
        File file = model.fileAt(row);
        if (file == null || listener == null)
        {
            return;
        }
        if (file.isDirectory())
        {
            listener.onDirectoryEntered(file);
        }
        else
        {
            listener.onFileDoubleClicked(file);
        }
    }

    private void handleKeyPress(KeyEvent e)
    {
        switch (e.getKeyCode())
        {
            case KeyEvent.VK_ENTER:
                int[] rows = table.getSelectedRows();
                if (rows.length == 1)
                {
                    open(rows[0]);
                }
                e.consume();
                break;
            case KeyEvent.VK_BACK_SPACE:
                if (currentDirectory != null && currentDirectory.getParentFile() != null && listener != null)
                {
                    listener.onDirectoryEntered(currentDirectory.getParentFile());
                }
                e.consume();
                break;
            case KeyEvent.VK_HOME:
                selectRow(0);
                e.consume();
                break;
            case KeyEvent.VK_END:
                selectRow(table.getRowCount() - 1);
                e.consume();
                break;
            case KeyEvent.VK_DELETE:
                if (mode == FileChooserMode.SAVE_FILE)
                {
                    deleteSelectedFiles();
                }
                e.consume();
                break;
            default:
                break;
        }
    }

    private void handleKeyTyped(KeyEvent e)
    {
        char c = e.getKeyChar();
        if (Character.isISOControl(c) || e.isControlDown() || e.isAltDown())
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKeyTime > TYPE_AHEAD_TIMEOUT_MS)
        {
            typeAhead.setLength(0);
        }
        lastKeyTime = now;
        typeAhead.append(c);
        selectRow(model.findByPrefix(typeAhead.toString()));
    }

    private void selectRow(int row)
    {
        if (row >= 0 && row < table.getRowCount())
        {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
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
        if (folder.mkdir())
        {
            refresh();
        }
        else
        {
            JOptionPane.showMessageDialog(this, "Failed to create folder.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedFiles()
    {
        List<File> selected = selectedFiles();
        if (selected.isEmpty())
        {
            return;
        }
        String message = selected.size() == 1 ? "Delete '" + selected.get(0).getName() + "'?" : "Delete " + selected.size() + " items?";
        int result = JOptionPane.showConfirmDialog(this, message, "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION)
        {
            return;
        }
        for (File file : selected)
        {
            if (!file.delete())
            {
                JOptionPane.showMessageDialog(this, "Failed to delete: " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        refresh();
    }

    private void refresh()
    {
        FileSystemWorker.invalidate(currentDirectory);
        if (listener != null && currentDirectory != null)
        {
            listener.onDirectoryEntered(currentDirectory);
        }
    }

    void fileListListener(FileListListener listener)
    {
        this.listener = listener;
    }

    /** Only an open picks several files; saving and directory choice pick one. */
    void mode(FileChooserMode mode)
    {
        this.mode = mode;
        table.setSelectionMode(mode == FileChooserMode.OPEN_FILE
                ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                : ListSelectionModel.SINGLE_SELECTION);
    }

    void files(List<File> files, File directory)
    {
        this.currentDirectory = directory;
        model.currentDirectory(directory);
        model.files(files);
        table.clearSelection();
        if (table.getRowCount() > 0)
        {
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }

    File selectedFile()
    {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.fileAt(row);
    }

    List<File> selectedFiles()
    {
        return model.filesAt(table.getSelectedRows());
    }

    void selectFile(String name)
    {
        for (int i = 0; i < model.getRowCount(); i++)
        {
            FileListModel.FileEntry entry = model.entryAt(i);
            if (entry != null && entry.name().equalsIgnoreCase(name))
            {
                selectRow(i);
                return;
            }
        }
    }

    void focusTable()
    {
        table.requestFocusInWindow();
    }

    /** Header cells in the panel colours, the sorted column carrying a direction mark. */
    private final class HeaderRenderer implements TableCellRenderer
    {
        private final JLabel label = new JLabel();

        HeaderRenderer()
        {
            label.setOpaque(true);
            label.setBackground(Style.BG_SECONDARY);
            label.setForeground(Style.TEXT);
            label.setFont(Style.font(11));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Style.BORDER),
                    BorderFactory.createEmptyBorder(Style.px(4), Style.px(4), Style.px(4), Style.px(4))));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column)
        {
            String text = value == null ? "" : value.toString();
            int modelColumn = table.convertColumnIndexToModel(column);
            if (modelColumn == model.sortColumn() && !text.isEmpty())
            {
                text += model.sortAscending() ? " ▴" : " ▾";
            }
            label.setText(text);
            label.setHorizontalAlignment(modelColumn == FileListModel.COL_SIZE ? JLabel.RIGHT : JLabel.LEFT);
            return label;
        }
    }
}
