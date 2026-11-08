package com.tonic.vitavr.ui.gestures;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every item in the cache by name, tradeable or not, read once on the client thread a few hundred items a frame so the
 * game never stalls; notes and placeholders are left out.
 */
public final class ItemIndex
{
    private static final int PER_FRAME = 500;
    private static final int MOST_RESULTS = 200;

    /**
     * One item.
     */
    @Getter
    @AllArgsConstructor
    public static final class Entry
    {
        private final int id;
        private final String name;
        private final List<String> actions;

        @Override
        public String toString()
        {
            return name + " (" + id + ")";
        }
    }

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean ready;
    private boolean started;

    /**
     * Starts reading the items, once.
     *
     * @param client       The client.
     * @param clientThread The client thread.
     * @param done         Run on the client thread once every item is read.
     */
    public synchronized void build(Client client, ClientThread clientThread, Runnable done)
    {
        if (started)
        {
            return;
        }
        started = true;
        int[] next = {0};
        clientThread.invoke(() ->
        {
            int count = client.getItemCount();
            int end = Math.min(count, next[0] + PER_FRAME);
            for (int id = next[0]; id < end; id++)
            {
                ItemComposition item = client.getItemDefinition(id);
                if (item == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1)
                {
                    continue;
                }
                String name = item.getName();
                if (name == null || name.isEmpty() || name.equalsIgnoreCase("null"))
                {
                    continue;
                }
                String[] actions = item.getInventoryActions();
                entries.add(new Entry(id, name, actions == null ? Collections.emptyList() : Arrays.asList(actions.clone())));
            }
            next[0] = end;
            if (end >= count)
            {
                ready = true;
                done.run();
                return true;
            }
            return false;
        });
    }

    /**
     * @return Whether every item has been read.
     */
    public boolean isReady()
    {
        return ready;
    }

    /**
     * @param text Part of a name.
     * @return The items whose name holds it, names starting with it first, up to a few hundred.
     */
    public List<Entry> search(String text)
    {
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        List<Entry> starts = new ArrayList<>();
        List<Entry> holds = new ArrayList<>();
        if (wanted.isEmpty())
        {
            return starts;
        }
        synchronized (entries)
        {
            for (Entry entry : entries)
            {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.startsWith(wanted))
                {
                    starts.add(entry);
                }
                else if (name.contains(wanted))
                {
                    holds.add(entry);
                }
                if (starts.size() >= MOST_RESULTS)
                {
                    break;
                }
            }
        }
        starts.addAll(holds);
        return starts.size() > MOST_RESULTS ? new ArrayList<>(starts.subList(0, MOST_RESULTS)) : starts;
    }

    /**
     * @param id An item id.
     * @return The item, or null.
     */
    public Entry entry(int id)
    {
        synchronized (entries)
        {
            for (Entry entry : entries)
            {
                if (entry.getId() == id)
                {
                    return entry;
                }
            }
        }
        return null;
    }
}
