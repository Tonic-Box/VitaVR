package com.tonic.vitavr.world;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.ViewMode;
import com.tonic.vitavr.render.scene.SceneRenderer;
import net.runelite.api.Client;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;

/**
 * Keeps roofs drawn indoors in first person and draws dimmed ceilings for the levels above the player.
 */
public final class Roofs
{
    /** The highest level the client draws. */
    public static final int TOP_LEVEL = 3;
    private static final int NO_CEILINGS = -1;
    private static final int CEILING_ZONES = 3;

    private final Client client;
    private final VitaVRConfig config;
    private final SceneRenderer renderer;
    private int ceilingsFrom = NO_CEILINGS;

    /**
     * Creates the roof handler.
     * @param client The client.
     * @param config The plugin config.
     * @param renderer The renderer the ceilings are drawn with.
     */
    public Roofs(Client client, VitaVRConfig config, SceneRenderer renderer)
    {
        this.client = client;
        this.config = config;
        this.renderer = renderer;
    }

    /**
     * Decides whether a scene keeps its roofs and notes which levels get ceilings this frame.
     *
     * @param scene The scene about to be drawn.
     * @param level The player's level.
     * @return Whether every level up to the top is drawn with no roofs hidden.
     */
    public boolean keeps(Scene scene, int level)
    {
        if (scene.getWorldViewId() != WorldView.TOPLEVEL)
        {
            return false;
        }
        boolean keep = config.viewMode() == ViewMode.FIRST_PERSON && config.keepRoofsIndoors() && client.getVarbitValue(VarbitID.OPTION_HIDE_ROOFTOPS) == 0;
        ceilingsFrom = keep && level < TOP_LEVEL ? level + 1 : NO_CEILINGS;
        return keep;
    }

    /**
     * Gets whether a zone gets ceilings this frame.
     *
     * @param scene The scene the zone is in.
     * @param zoneX The zone x.
     * @param zoneZ The zone z.
     * @param playerX The player position, across.
     * @param playerZ The player position, along.
     * @return Whether its ceilings are drawn.
     */
    public boolean hasCeilings(Scene scene, int zoneX, int zoneZ, double playerX, double playerZ)
    {
        return ceilingsFrom != NO_CEILINGS && scene.getWorldViewId() == WorldView.TOPLEVEL && Math.abs(zoneX - Zones.of(playerX)) <= CEILING_ZONES
                && Math.abs(zoneZ - Zones.of(playerZ)) <= CEILING_ZONES && renderer.hasFacesFrom(scene, zoneX, zoneZ, ceilingsFrom);
    }

    /**
     * Draws a zone's ceilings by drawing the levels above the player again with front faces culled and colour dimmed.
     *
     * @param projection The zone's projection.
     * @param scene The scene the zone is in.
     * @param zoneX The zone x.
     * @param zoneZ The zone z.
     */
    public void ceilings(Projection projection, Scene scene, int zoneX, int zoneZ)
    {
        renderer.ceilings(projection, scene, zoneX, zoneZ, ceilingsFrom, TOP_LEVEL, config.ceilingLightPercent() / 100f);
    }
}
