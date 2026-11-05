package com.tonic.vitavr.render.scene;

import com.tonic.vitavr.space.View;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.hooks.DrawCallbacks;

import java.util.Set;

/**
 * What VR needs from the game's renderer to get the scene into every view each frame.
 */
public interface SceneRenderer
{
    /**
     * @return The renderer's own draw callbacks, which get every call VR does not change.
     */
    DrawCallbacks plugin();

    /**
     * Sets the renderer up for VR before the next frame. Runs on the client thread.
     */
    void applyFlags();

    /**
     * Puts the renderer back as the desktop had it. Runs on the client thread.
     */
    void restoreFlags();

    /**
     * Sets a scene up for drawing, as the draw callback of the same name.
     *
     * @param head The head's view to sort and cull from, or null when no views are drawn this frame.
     */
    void preSceneDraw(Scene scene, Projection projection, float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw, int minLevel, int level,
                      int maxLevel, Set<Integer> hideRoofIds, View head);

    /**
     * Starts this frame's views after the top level scene is set up.
     *
     * @param views The views drawn this frame.
     * @param fogX Where the fog centres, across.
     * @param fogZ Where the fog centres, along.
     * @param tableReach How far the table reaches from the fog centre, or 0 when not on the tabletop.
     * @return Whether the views can be drawn this frame.
     */
    boolean startViews(SceneViews views, double fogX, double fogZ, double tableReach);

    /**
     * @return Whether the zone is drawn, with every direction in view.
     */
    boolean zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY);

    /**
     * Draws a zone's opaque models into the views.
     */
    void drawZoneOpaque(Projection projection, Scene scene, int zoneX, int zoneZ);

    /**
     * Draws a zone's transparent models into the views.
     */
    void drawZoneAlpha(Projection projection, Scene scene, int level, int zoneX, int zoneZ);

    /**
     * Ends a draw pass, as the draw callback of the same name, into the views while they are drawn.
     */
    void drawPass(Projection projection, Scene scene, int pass);

    /**
     * Gets whether a zone has any faces on a level or above.
     *
     * @param scene The scene the zone is in.
     * @param zoneX The zone x.
     * @param zoneZ The zone z.
     * @param level The lowest level checked.
     * @return Whether there are faces on that level or above.
     */
    boolean hasFacesFrom(Scene scene, int zoneX, int zoneZ, int level);

    /**
     * Draws a zone's levels from one up again into the views with front faces culled and colour dimmed, seen from below as ceilings.
     *
     * @param projection The zone's projection.
     * @param scene The scene the zone is in.
     * @param zoneX The zone x.
     * @param zoneZ The zone z.
     * @param fromLevel The lowest level drawn.
     * @param toLevel The highest level drawn.
     * @param light How much of the colour is kept, 0 to 1.
     */
    void ceilings(Projection projection, Scene scene, int zoneX, int zoneZ, int fromLevel, int toLevel, float light);

    /**
     * Finishes a scene, as the draw callback of the same name.
     *
     * @param scene The scene.
     * @param finishViews Draws over and submits the views once they hold the scene, or null when no views are drawn.
     */
    void postSceneDraw(Scene scene, Runnable finishViews);

    /**
     * Stops drawing into the views and gives the renderer back its own targets. Safe to call when not drawing them.
     */
    void leaveViews();

    /**
     * Binds what the desktop shows of the scene for the mirrored eye.
     *
     * @param viewport Receives the bound viewport as x, y, width and height.
     * @return Whether anything was bound.
     */
    boolean bindMirror(int[] viewport);
}
