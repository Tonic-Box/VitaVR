package com.tonic.vitavr.ui.filechooser;

import javax.swing.Icon;
import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The file table's rows: icon, name, size, modified date and type, folders always sorted first. */
public final class FileListModel extends AbstractTableModel
{
    public static final int COL_ICON = 0;
    public static final int COL_NAME = 1;
    public static final int COL_SIZE = 2;
    public static final int COL_DATE = 3;
    public static final int COL_TYPE = 4;
    private static final String[] COLUMN_NAMES = {"", "Name", "Size", "Date Modified", "Type"};
    private static final Class<?>[] COLUMN_CLASSES = {Icon.class, String.class, Long.class, Date.class, String.class};

    private final List<FileEntry> entries = new ArrayList<>();
    private File currentDirectory;
    private int sortColumn = COL_NAME;
    private boolean sortAscending = true;

    @Override
    public int getRowCount()
    {
        return entries.size();
    }

    @Override
    public int getColumnCount()
    {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column)
    {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int column)
    {
        return COLUMN_CLASSES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        FileEntry entry = entryAt(rowIndex);
        if (entry == null)
        {
            return null;
        }
        switch (columnIndex)
        {
            case COL_ICON:
                return entry.icon();
            case COL_NAME:
                return entry.name();
            case COL_SIZE:
                return entry.isDirectory() ? -1L : entry.size();
            case COL_DATE:
                return entry.lastModified();
            case COL_TYPE:
                return entry.type();
            default:
                return null;
        }
    }

    public FileEntry entryAt(int row)
    {
        return row < 0 || row >= entries.size() ? null : entries.get(row);
    }

    public File fileAt(int row)
    {
        FileEntry entry = entryAt(row);
        return entry == null ? null : entry.file();
    }

    public List<File> filesAt(int[] rows)
    {
        List<File> result = new ArrayList<>();
        for (int row : rows)
        {
            File file = fileAt(row);
            if (file != null)
            {
                result.add(file);
            }
        }
        return result;
    }

    public void files(List<File> files)
    {
        entries.clear();
        if (files != null)
        {
            for (File file : files)
            {
                entries.add(new FileEntry(file));
            }
        }
        sortEntries();
        fireTableDataChanged();
    }

    public File currentDirectory()
    {
        return currentDirectory;
    }

    public void currentDirectory(File directory)
    {
        this.currentDirectory = directory;
    }

    public int sortColumn()
    {
        return sortColumn;
    }

    public boolean sortAscending()
    {
        return sortAscending;
    }

    /** Sorts by a column; sorting by the current column again flips the direction. */
    public void sortBy(int column)
    {
        if (column == sortColumn)
        {
            sortAscending = !sortAscending;
        }
        else
        {
            sortColumn = column;
            sortAscending = true;
        }
        sortEntries();
        fireTableDataChanged();
    }

    /** Folders first, then the sort column in its direction, ties broken by name. */
    private void sortEntries()
    {
        Comparator<FileEntry> comparator;
        switch (sortColumn)
        {
            case COL_SIZE:
                comparator = Comparator.comparingLong(FileEntry::size);
                break;
            case COL_DATE:
                comparator = Comparator.comparingLong(e -> e.lastModified().getTime());
                break;
            case COL_TYPE:
                comparator = Comparator.comparing(FileEntry::type, String.CASE_INSENSITIVE_ORDER);
                break;
            case COL_NAME:
            default:
                comparator = Comparator.comparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER);
                break;
        }
        entries.sort(Comparator.comparing((FileEntry e) -> !e.isDirectory())
                .thenComparing(sortAscending ? comparator : comparator.reversed())
                .thenComparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER));
    }

    /** @return the first row whose name starts with the prefix, ignoring case, or -1 */
    public int findByPrefix(String prefix)
    {
        if (prefix == null || prefix.isEmpty())
        {
            return -1;
        }
        String lower = prefix.toLowerCase();
        for (int i = 0; i < entries.size(); i++)
        {
            if (entries.get(i).name().toLowerCase().startsWith(lower))
            {
                return i;
            }
        }
        return -1;
    }

    /** One row: a file with its display properties read once. */
    public static final class FileEntry
    {
        private final File file;
        private final String name;
        private final boolean directory;
        private final long size;
        private final Date lastModified;
        private final String type;
        private Icon icon;
        private boolean iconLoaded;

        public FileEntry(File file)
        {
            this.file = file;
            this.name = file.getName();
            this.directory = file.isDirectory();
            this.size = directory ? 0 : file.length();
            this.lastModified = new Date(file.lastModified());
            this.type = typeOf(name, directory);
        }

        /** The kind shown in the Type column, by extension. */
        static String typeOf(String name, boolean directory)
        {
            if (directory)
            {
                return "Folder";
            }
            String ext = ExtensionFileFilter.extensionOf(name);
            if (ext == null)
            {
                return "File";
            }
            switch (ext)
            {
                case "json":
                    return "JSON File";
                case "java":
                    return "Java Source";
                case "txt":
                    return "Text File";
                case "md":
                    return "Markdown";
                case "xml":
                    return "XML File";
                case "png":
                    return "PNG Image";
                case "jar":
                    return "JAR Archive";
                case "class":
                    return "Class File";
                case "properties":
                    return "Properties";
                default:
                    return ext.toUpperCase(Locale.ROOT) + " File";
            }
        }

        public File file()
        {
            return file;
        }

        public String name()
        {
            return name;
        }

        public boolean isDirectory()
        {
            return directory;
        }

        public long size()
        {
            return size;
        }

        public Date lastModified()
        {
            return lastModified;
        }

        public String type()
        {
            return type;
        }

        /** The system icon, fetched on first use so a listing does not pay for icons it never shows. */
        public Icon icon()
        {
            if (!iconLoaded)
            {
                icon = FileSystemWorker.systemIcon(file);
                iconLoaded = true;
            }
            return icon;
        }

        public String formattedSize()
        {
            return directory ? "--" : formatSize(size);
        }

        static String formatSize(long size)
        {
            if (size < 1024)
            {
                return size + " B";
            }
            if (size < 1024L * 1024)
            {
                return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
            }
            if (size < 1024L * 1024 * 1024)
            {
                return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024));
            }
            return String.format(Locale.ROOT, "%.1f GB", size / (1024.0 * 1024 * 1024));
        }

        public String formattedDate()
        {
            return new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.ROOT).format(lastModified);
        }
    }
}
