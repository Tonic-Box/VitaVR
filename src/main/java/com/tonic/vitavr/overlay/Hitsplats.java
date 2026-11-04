package com.tonic.vitavr.overlay;

import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks each actor's active hitsplats in the order they were applied.
 */
final class Hitsplats
{
    /** A hitsplat's type, amount and the game cycle it disappears on. */
    static final class Splat
    {
        final int type;
        final int amount;
        final int goneOnCycle;

        Splat(int type, int amount, int goneOnCycle)
        {
            this.type = type;
            this.amount = amount;
            this.goneOnCycle = goneOnCycle;
        }
    }

    private final Map<Actor, List<Splat>> byActor = new WeakHashMap<>();

    /**
     * Notes a hitsplat the game applied.
     * @param actor The actor it landed on.
     * @param hitsplat The hitsplat.
     */
    void applied(Actor actor, Hitsplat hitsplat)
    {
        byActor.computeIfAbsent(actor, key -> new ArrayList<>()).add(new Splat(hitsplat.getHitsplatType(), hitsplat.getAmount(), hitsplat.getDisappearsOnGameCycle()));
    }

    /**
     * Gets an actor's active hitsplats, oldest first, dropping expired ones.
     * @param actor The actor.
     * @param cycle The current game cycle.
     * @return The hitsplats, possibly empty.
     */
    List<Splat> of(Actor actor, int cycle)
    {
        List<Splat> splats = byActor.get(actor);
        if (splats == null)
        {
            return Collections.emptyList();
        }
        for (Iterator<Splat> it = splats.iterator(); it.hasNext(); )
        {
            if (it.next().goneOnCycle <= cycle)
            {
                it.remove();
            }
        }
        if (splats.isEmpty())
        {
            byActor.remove(actor);
        }
        return splats;
    }

    /**
     * Clears every hitsplat.
     */
    void clear()
    {
        byActor.clear();
    }
}
