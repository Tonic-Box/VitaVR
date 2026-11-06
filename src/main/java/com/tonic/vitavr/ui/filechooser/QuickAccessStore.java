package com.tonic.vitavr.ui.filechooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * The chooser's remembered places, kept in the user's preferences: pinned folders in the user's order, the
 * ten most recent folders visited, and the directory the last pick was made in.
 */
public final class QuickAccessStore
{
    private static final String PINNED_PREFIX = "pinnedDir_";
    private static final String PINNED_COUNT = "pinnedDirCount";
    private static final String RECENT_PREFIX = "recentDir_";
    private static final String RECENT_COUNT = "recentDirCount";
    private static final String LAST_DIRECTORY = "lastDirectory";
    static final int MAX_RECENT = 10;

    private static QuickAccessStore shared;

    private final Preferences prefs;
    private final List<File> pinned = new ArrayList<>();
    private final List<File> recent = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener
    {
        void pinnedChanged(List<File> pinned);

        void recentChanged(List<File> recent);
    }

    private static final String PREFERENCES_NODE = "/com/tonic/vitavr/ui/filechooser";

    public QuickAccessStore(Preferences prefs)
    {
        this.prefs = prefs;
        load();
    }

    /**
     * The store every chooser in every OSH window shares, backed by the user's preferences under the node the designer
     * first kept it in, so the places pinned there carry over.
     */
    public static synchronized QuickAccessStore shared()
    {
        if (shared == null)
        {
            shared = new QuickAccessStore(Preferences.userRoot().node(PREFERENCES_NODE));
        }
        return shared;
    }

    public void pin(File dir)
    {
        if (dir == null || !dir.isDirectory() || isPinned(dir))
        {
            return;
        }
        pinned.add(dir);
        recent.removeIf(f -> samePath(f, dir));
        savePinned();
        saveRecent();
        notifyPinned();
        notifyRecent();
    }

    public void unpin(File dir)
    {
        if (dir != null && pinned.removeIf(f -> samePath(f, dir)))
        {
            savePinned();
            notifyPinned();
        }
    }

    public void movePinnedUp(File dir)
    {
        int index = indexOfPinned(dir);
        if (index > 0)
        {
            reorderPinned(index, index - 1);
        }
    }

    public void movePinnedDown(File dir)
    {
        int index = indexOfPinned(dir);
        if (index >= 0 && index < pinned.size() - 1)
        {
            reorderPinned(index, index + 1);
        }
    }

    private void reorderPinned(int from, int to)
    {
        pinned.add(to, pinned.remove(from));
        savePinned();
        notifyPinned();
    }

    private int indexOfPinned(File dir)
    {
        if (dir == null)
        {
            return -1;
        }
        for (int i = 0; i < pinned.size(); i++)
        {
            if (samePath(pinned.get(i), dir))
            {
                return i;
            }
        }
        return -1;
    }

    public boolean isPinned(File dir)
    {
        return indexOfPinned(dir) >= 0;
    }

    /** @return the pinned folders that still exist, in the user's order */
    public List<File> pinned()
    {
        return existing(pinned);
    }

    /** Puts a visited folder at the front of the recent list, unless it is pinned. */
    public void addRecent(File dir)
    {
        if (dir == null || !dir.isDirectory() || isPinned(dir))
        {
            return;
        }
        recent.removeIf(f -> samePath(f, dir));
        recent.add(0, dir);
        while (recent.size() > MAX_RECENT)
        {
            recent.remove(recent.size() - 1);
        }
        saveRecent();
        notifyRecent();
    }

    public void removeRecent(File dir)
    {
        if (dir != null && recent.removeIf(f -> samePath(f, dir)))
        {
            saveRecent();
            notifyRecent();
        }
    }

    public void clearRecent()
    {
        recent.clear();
        saveRecent();
        notifyRecent();
    }

    /** @return the recent folders that still exist, newest first */
    public List<File> recent()
    {
        return existing(recent);
    }

    /** @return the directory of the last approved pick, or null when none is remembered or it is gone */
    public File lastDirectory()
    {
        String path = prefs.get(LAST_DIRECTORY, null);
        if (path == null)
        {
            return null;
        }
        File dir = new File(path);
        return dir.isDirectory() ? dir : null;
    }

    public void lastDirectory(File dir)
    {
        if (dir != null && dir.isDirectory())
        {
            prefs.put(LAST_DIRECTORY, dir.getAbsolutePath());
        }
    }

    public void addListener(Listener listener)
    {
        if (!listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener)
    {
        listeners.remove(listener);
    }

    private static boolean samePath(File a, File b)
    {
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    private static List<File> existing(List<File> dirs)
    {
        List<File> result = new ArrayList<>();
        for (File dir : dirs)
        {
            if (dir.isDirectory())
            {
                result.add(dir);
            }
        }
        return result;
    }

    private void load()
    {
        pinned.clear();
        int pinnedCount = prefs.getInt(PINNED_COUNT, 0);
        for (int i = 0; i < pinnedCount; i++)
        {
            addExisting(pinned, prefs.get(PINNED_PREFIX + i, null));
        }
        recent.clear();
        int recentCount = prefs.getInt(RECENT_COUNT, 0);
        for (int i = 0; i < recentCount && i < MAX_RECENT; i++)
        {
            addExisting(recent, prefs.get(RECENT_PREFIX + i, null));
        }
    }

    private static void addExisting(List<File> into, String path)
    {
        if (path != null)
        {
            File dir = new File(path);
            if (dir.isDirectory())
            {
                into.add(dir);
            }
        }
    }

    private void savePinned()
    {
        save(PINNED_PREFIX, PINNED_COUNT, pinned);
    }

    private void saveRecent()
    {
        save(RECENT_PREFIX, RECENT_COUNT, recent);
    }

    private void save(String prefix, String countKey, List<File> dirs)
    {
        int oldCount = prefs.getInt(countKey, 0);
        for (int i = 0; i < oldCount; i++)
        {
            prefs.remove(prefix + i);
        }
        prefs.putInt(countKey, dirs.size());
        for (int i = 0; i < dirs.size(); i++)
        {
            prefs.put(prefix + i, dirs.get(i).getAbsolutePath());
        }
    }

    private void notifyPinned()
    {
        List<File> snapshot = pinned();
        for (Listener listener : new ArrayList<>(listeners))
        {
            listener.pinnedChanged(snapshot);
        }
    }

    private void notifyRecent()
    {
        List<File> snapshot = recent();
        for (Listener listener : new ArrayList<>(listeners))
        {
            listener.recentChanged(snapshot);
        }
    }
}
