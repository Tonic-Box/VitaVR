package com.tonic.vitavr.world;

import net.runelite.api.Constants;

/**
 * The client's 8 by 8 tile zones, indexed in the extended scene as the draw callbacks give them.
 */
public final class Zones
{
    /** The width of a zone, in game units. */
    public static final int UNITS = 1024;
    /** The offset of the scene's own zones into the extended scene, in zones. */
    public static final int OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2 >> 3;

    private Zones()
    {
    }

    /**
     * Gets the west or south edge of a top level zone.
     *
     * @param zone The zone index in the extended scene.
     * @return The edge, in game units.
     */
    public static double edge(int zone)
    {
        return (zone - OFFSET) * (double) UNITS;
    }

    /**
     * Gets the top level zone a scene coordinate lies in.
     *
     * @param at The coordinate, in game units.
     * @return The zone index in the extended scene.
     */
    public static int of(double at)
    {
        return (int) Math.floor(at / UNITS) + OFFSET;
    }
}
