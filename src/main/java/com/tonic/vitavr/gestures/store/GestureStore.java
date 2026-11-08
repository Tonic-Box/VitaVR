package com.tonic.vitavr.gestures.store;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.Profile;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Holds the gesture library and profiles, saved as JSON under one folder: library.json and profiles/&lt;name&gt;.json.
 * Readers on any thread take the published snapshot and must not change it; every change goes through update, which
 * publishes a fresh copy, saves it and tells the listeners.
 */
@Slf4j
public final class GestureStore
{
    private static final String LIBRARY = "library.json";
    private static final String PROFILES = "profiles";
    private static final String JSON = ".json";
    private static final String BAD = ".bad";

    private final Path folder;
    private final Consumer<String> activeSaved;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Gestures current = new Gestures();
    private volatile int version;
    private Gestures previous;
    @Getter
    private volatile String problem = "";

    /**
     * @param folder      The folder the files are kept in.
     * @param active      The profile last in use, or empty.
     * @param activeSaved Told the profile in use whenever it changes, to keep it in the settings.
     */
    public GestureStore(Path folder, String active, Consumer<String> activeSaved)
    {
        this.folder = folder;
        this.activeSaved = activeSaved;
        Gestures loaded = new Gestures();
        if (active != null && !active.isEmpty())
        {
            loaded.setActive(active);
        }
        current = loaded;
    }

    /**
     * Reads the files; one that cannot be read is put aside as .bad and the store goes on without it.
     */
    public synchronized void load()
    {
        List<String> bad = new ArrayList<>();
        Gestures loaded = new Gestures();
        loaded.setActive(current.getActive());
        Path library = folder.resolve(LIBRARY);
        if (Files.exists(library))
        {
            Gestures read = read(library, Gestures.class, bad);
            if (read != null && read.getLibrary() != null)
            {
                loaded.getLibrary().addAll(read.getLibrary());
            }
        }
        Path profiles = folder.resolve(PROFILES);
        if (Files.isDirectory(profiles))
        {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(profiles, "*" + JSON))
            {
                for (Path file : files)
                {
                    Profile profile = read(file, Profile.class, bad);
                    if (profile != null && profile.getName() != null && loaded.profile(profile.getName()) == null)
                    {
                        loaded.getProfiles().add(profile);
                    }
                }
            }
            catch (IOException e)
            {
                bad.add(PROFILES);
            }
        }
        loaded.getLibrary().removeIf(gesture -> gesture == null || gesture.getId() == null);
        loaded.tidy();
        problem = bad.isEmpty() ? "" : "Could not read " + String.join(", ", bad) + "; kept aside as .bad";
        publish(loaded);
        previous = null;
    }

    private <T> T read(Path file, Class<T> type, List<String> bad)
    {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            T value = GestureJson.GSON.fromJson(reader, type);
            if (value != null)
            {
                return value;
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Could not read {}", file, e);
        }
        bad.add(file.getFileName().toString());
        try
        {
            Files.move(file, file.resolveSibling(file.getFileName() + BAD), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            log.warn("Could not put aside {}", file, e);
        }
        return null;
    }

    /**
     * @return The published snapshot; never change it.
     */
    public Gestures snapshot()
    {
        return current;
    }

    /**
     * @return A number that goes up with every change, for readers that poll.
     */
    public int version()
    {
        return version;
    }

    /**
     * Changes a copy of the snapshot, then publishes, saves and announces it.
     *
     * @param change Changes the copy it is given; it must not keep the copy.
     */
    public synchronized void update(Consumer<Gestures> change)
    {
        Gestures copy = GestureJson.copy(current, Gestures.class);
        change.accept(copy);
        copy.tidy();
        previous = current;
        publish(GestureJson.copy(copy, Gestures.class));
        save();
    }

    /**
     * Puts back the snapshot from before the last change.
     *
     * @return Whether there was one to put back.
     */
    public synchronized boolean undo()
    {
        if (previous == null)
        {
            return false;
        }
        Gestures back = previous;
        previous = null;
        publish(back);
        save();
        return true;
    }

    private void publish(Gestures next)
    {
        String before = current.getActive();
        current = next;
        version++;
        if (!next.getActive().equals(before))
        {
            activeSaved.accept(next.getActive());
        }
        for (Runnable listener : listeners)
        {
            listener.run();
        }
    }

    private void save()
    {
        Gestures saving = current;
        try
        {
            Path profiles = Files.createDirectories(folder.resolve(PROFILES));
            Gestures library = new Gestures();
            library.setLibrary(saving.getLibrary());
            library.setProfiles(null);
            library.setActive(null);
            write(folder.resolve(LIBRARY), library);
            Set<Path> kept = new HashSet<>();
            for (Profile profile : saving.getProfiles())
            {
                Path file = profiles.resolve(fileName(profile.getName(), kept));
                kept.add(file);
                write(file, profile);
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(profiles, "*" + JSON))
            {
                for (Path file : files)
                {
                    if (!kept.contains(file))
                    {
                        Files.delete(file);
                    }
                }
            }
            problem = "";
        }
        catch (IOException e)
        {
            problem = "Could not save the gestures: " + e.getMessage();
            log.warn(problem, e);
        }
    }

    private static void write(Path file, Object value) throws IOException
    {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
        {
            writer.write(GestureJson.GSON.toJson(value).replace("\r\n", "\n"));
            writer.write('\n');
        }
        try
        {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e)
        {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * @param name A profile's name.
     * @param taken Files already used by other profiles.
     * @return A file name safe on any system, numbered when two names come to the same one.
     */
    static String fileName(String name, Set<Path> taken)
    {
        String safe = name.replaceAll("[^A-Za-z0-9 _-]", "_").trim();
        if (safe.isEmpty())
        {
            safe = "profile";
        }
        String chosen = safe + JSON;
        for (int number = 2; containsName(taken, chosen); number++)
        {
            chosen = safe + " " + number + JSON;
        }
        return chosen;
    }

    private static boolean containsName(Set<Path> taken, String name)
    {
        for (Path path : taken)
        {
            if (path.getFileName().toString().equalsIgnoreCase(name))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @param listener Run after every change, on the thread that made it.
     */
    public void addListener(Runnable listener)
    {
        listeners.add(listener);
    }

    /**
     * @param listener A listener added before.
     */
    public void removeListener(Runnable listener)
    {
        listeners.remove(listener);
    }

    /**
     * @param id A gesture's id.
     * @return The gesture in the snapshot, or null.
     */
    public Gesture gesture(String id)
    {
        return current.gesture(id);
    }
}
