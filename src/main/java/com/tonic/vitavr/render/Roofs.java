package com.tonic.vitavr.render;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.ViewMode;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Keeps roofs drawn indoors in first person and draws dimmed ceilings for the levels above the player.
 */
final class Roofs
{
    /** The highest level the client draws. */
    static final int TOP_LEVEL = 3;
    private static final int NO_CEILINGS = -1;
    private static final int CEILING_ZONES = 3;

    private final Client client;
    private final VitaVRConfig config;
    private final GpuInternals internals;
    private int ceilingsFrom = NO_CEILINGS;
    private int lowestLevel;

    /**
     * Creates the roof handler.
     * @param client The client.
     * @param config The plugin config.
     * @param internals The GPU plugin internals the ceilings are drawn with.
     */
    Roofs(Client client, VitaVRConfig config, GpuInternals internals)
    {
        this.client = client;
        this.config = config;
        this.internals = internals;
    }

    /**
     * Decides whether a scene keeps its roofs and notes which levels get ceilings this frame.
     *
     * @param scene The scene about to be drawn.
     * @param minLevel The lowest level the client draws.
     * @param level The player's level.
     * @return Whether every level up to the top is drawn with no roofs hidden.
     */
    boolean keeps(Scene scene, int minLevel, int level)
    {
        if (scene.getWorldViewId() != WorldView.TOPLEVEL)
        {
            return false;
        }
        boolean keep = config.viewMode() == ViewMode.FIRST_PERSON && config.keepRoofsIndoors() && client.getVarbitValue(VarbitID.OPTION_HIDE_ROOFTOPS) == 0;
        ceilingsFrom = keep && level < TOP_LEVEL ? level + 1 : NO_CEILINGS;
        lowestLevel = minLevel;
        return keep;
    }

    /**
     * Draws a zone's ceilings when it is a top level zone near the player.
     *
     * @param scene    The scene the zone is in.
     * @param zoneX    The zone's x.
     * @param zoneZ    The zone's z.
     * @param playerX  The player's x.
     * @param playerZ  The player's z.
     * @param drawZone Draws the zone into every view.
     */
    void ceilings(Scene scene, int zoneX, int zoneZ, double playerX, double playerZ, Runnable drawZone)
    {
        if (ceilingsFrom == NO_CEILINGS || scene.getWorldViewId() != WorldView.TOPLEVEL
                || Math.abs(zoneX - GpuInternals.zoneOf(playerX)) > CEILING_ZONES || Math.abs(zoneZ - GpuInternals.zoneOf(playerZ)) > CEILING_ZONES)
        {
            return;
        }
        float light = config.ceilingLightPercent() / 100f;
        internals.levels(scene, ceilingsFrom, TOP_LEVEL);
        GL11.glCullFace(GL11.GL_FRONT);
        GL14.glBlendColor(light, light, light, 1f);
        GL14.glBlendFuncSeparate(GL14.GL_CONSTANT_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE);
        try
        {
            drawZone.run();
        }
        finally
        {
            GL11.glCullFace(GL11.GL_BACK);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
            internals.levels(scene, lowestLevel, TOP_LEVEL);
        }
    }
}
