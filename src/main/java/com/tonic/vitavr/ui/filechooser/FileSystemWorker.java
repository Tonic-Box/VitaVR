package com.tonic.vitavr.ui.filechooser;

import javax.swing.Icon;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Directory listings off the event thread, with a short-lived cache, plus the system's names and icons. */
final class FileSystemWorker
{
    private static final FileSystemView VIEW = FileSystemView.getFileSystemView();
    private static final Map<String, CachedListing> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000;

    interface DirectoryListingListener
    {
        void onListingComplete(File directory, List<File> files);

        void onListingError(File directory, Exception error);
    }

    private FileSystemWorker()
    {
    }

    /** Lists a directory, folders first, through the filter; the listener is called on the event thread. */
    static void listDirectory(File directory, ExtensionFileFilter filter, DirectoryListingListener listener)
    {
        String key = directory.getAbsolutePath();
        CachedListing cached = CACHE.get(key);
        if (cached != null && !cached.expired())
        {
            listener.onListingComplete(directory, filterFiles(cached.files, filter));
            return;
        }
        new SwingWorker<List<File>, Void>()
        {
            @Override
            protected List<File> doInBackground()
            {
                return listFilesSync(directory);
            }

            @Override
            protected void done()
            {
                try
                {
                    List<File> all = get();
                    CACHE.put(key, new CachedListing(all));
                    listener.onListingComplete(directory, filterFiles(all, filter));
                }
                catch (Exception e)
                {
                    listener.onListingError(directory, e);
                }
            }
        }.execute();
    }

    private static List<File> listFilesSync(File directory)
    {
        List<File> result = new ArrayList<>();
        if (directory == null || !directory.isDirectory())
        {
            return result;
        }
        File[] files = directory.listFiles();
        if (files != null)
        {
            result.addAll(Arrays.asList(files));
            result.sort(Comparator.comparing((File f) -> !f.isDirectory()).thenComparing(f -> f.getName().toLowerCase()));
        }
        return result;
    }

    static List<File> filterFiles(List<File> files, ExtensionFileFilter filter)
    {
        if (filter == null || filter.isAllFiles())
        {
            return new ArrayList<>(files);
        }
        List<File> result = new ArrayList<>();
        for (File file : files)
        {
            if (filter.accept(file))
            {
                result.add(file);
            }
        }
        return result;
    }

    static File[] roots()
    {
        return File.listRoots();
    }

    /** Home, Desktop, Documents and Downloads, those that exist, in that order. */
    static Map<String, File> specialFolders()
    {
        Map<String, File> folders = new LinkedHashMap<>();
        String userHome = System.getProperty("user.home");
        if (userHome == null)
        {
            return folders;
        }
        File home = new File(userHome);
        folders.put("Home", home);
        for (String name : new String[] {"Desktop", "Documents", "Downloads"})
        {
            File folder = new File(home, name);
            if (folder.exists())
            {
                folders.put(name, folder);
            }
        }
        return folders;
    }

    static String displayName(File file)
    {
        if (file == null)
        {
            return "";
        }
        String name = VIEW.getSystemDisplayName(file);
        return name != null && !name.isEmpty() ? name : file.getName();
    }

    static Icon systemIcon(File file)
    {
        return VIEW.getSystemIcon(file);
    }

    static void invalidate(File directory)
    {
        if (directory != null)
        {
            CACHE.remove(directory.getAbsolutePath());
        }
    }

    private static final class CachedListing
    {
        final List<File> files;
        final long timestamp = System.currentTimeMillis();

        CachedListing(List<File> files)
        {
            this.files = files;
        }

        boolean expired()
        {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}
