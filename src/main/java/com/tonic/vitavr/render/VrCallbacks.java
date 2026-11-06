package com.tonic.vitavr.render;

import com.tonic.Logger;
import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.ViewMode;
import com.tonic.vitavr.headset.Headset;
import com.tonic.vitavr.headset.input.Controllers;
import com.tonic.vitavr.headset.input.Haptics;
import com.tonic.vitavr.headset.layers.EyeLayer;
import com.tonic.vitavr.interfaces.hud.Layers;
import com.tonic.vitavr.interfaces.panels.CanvasKeys;
import com.tonic.vitavr.interfaces.pointer.CameraDriver;
import com.tonic.vitavr.interfaces.pointer.Pointer;
import com.tonic.vitavr.interfaces.pointer.PointerProbe;
import com.tonic.vitavr.interfaces.wrist.WristDisplay;
import com.tonic.vitavr.interfaces.wrist.WristPicture;
import com.tonic.vitavr.render.comfort.ComfortMotion;
import com.tonic.vitavr.render.comfort.ComfortShade;
import com.tonic.vitavr.render.comfort.EyeClearance;
import com.tonic.vitavr.render.draw.AtlasQuad;
import com.tonic.vitavr.render.draw.Beams;
import com.tonic.vitavr.render.draw.Markers;
import com.tonic.vitavr.render.draw.PictureQuad;
import com.tonic.vitavr.render.recording.Recording;
import com.tonic.vitavr.render.scene.SceneRenderer;
import com.tonic.vitavr.render.scene.SceneViews;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.space.placement.Placement;
import com.tonic.vitavr.world.Roofs;
import com.tonic.vitavr.world.body.OwnBody;
import com.tonic.vitavr.world.body.TrackedHand;
import com.tonic.vitavr.world.overlay.ActorOverlays;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Texture;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.config.ConfigManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.WGL;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import static com.tonic.vitavr.space.Vectors.square;
import static com.tonic.vitavr.space.Vectors.unit;

/**
 * Draws the game into the headset by wrapping the renderer's draw callbacks, with a scene renderer getting the scene into both eyes.
 */
public final class VrCallbacks implements DrawCallbacks, AutoCloseable
{
    private static final int EYES = 2;
    private static final int ACTIVE_BEAM = 0x40E0FFE0;
    private static final int RESTING_BEAM = 0x80A0B060;
    private static final double DOT_RADIANS = Math.toRadians(0.45);
    private static final int GHOST_HAND = 0x59FFFFFF;
    private static final double HAND_METRES = 0.06;
    private static final double DOT_EDGE_RADIANS = Math.toRadians(0.65);
    private static final double CROSS_RADIANS = Math.toRadians(1.3);
    private static final int DOT = 0xFFFFFFFF;
    private static final int DOT_EDGE = 0xC0000000;
    private static final int HOVERED_TILE = 0xFFFFFFFF;
    private static final int OWN_TILE = 0xFF00FFFF;
    private static final int DESTINATION_TILE = 0xFF00FF00;
    /** The tile outline's height above the ground and the pieces per edge. */
    private static final double OUTLINE_LIFT = 2;
    private static final int OUTLINE_STEPS = 4;
    /** How long frame times are gathered before reporting, in nanoseconds. */
    private static final long TIMES_STRETCH_NANOS = 10_000_000_000L;
    /** How quickly the view darkens inside scenery and clears after, in seconds. */
    private static final double WALL_DARKEN_SECONDS = 0.04;
    private static final double WALL_CLEAR_SECONDS = 0.15;
    /** How far ahead of and below the eyes the own head stack hangs in first person, in metres. */
    private static final double OWN_AHEAD_METRES = 0.7;
    private static final double OWN_BELOW_METRES = 0.3;
    /** How far below the own head stack the own hitsplats hang, in metres. */
    private static final double OWN_SPLATS_BELOW_METRES = 0.12;

    @Getter
    private final SceneRenderer renderer;
    private final DrawCallbacks plugin;
    private final Client client;
    private final VitaVRConfig config;
    private final ConfigManager configManager;
    private final CanvasKeys keys;
    private final Consumer<String> onEnded;

    private final Placement placement = new Placement();
    private final OwnBody ownBody = new OwnBody();
    private final CameraDriver cameraDriver = new CameraDriver();
    private final GlState state = new GlState();
    private final float[][] eyeMatrices = new float[EYES][];
    private EyeProjection headProjection;
    private final int[] mirrorViewport = new int[4];
    private Headset headset;
    private Controllers controllers;
    private EyesTarget eyes;
    private SceneViews views;
    private EyeLayer eyeLayer;
    private PictureQuad quad;
    private Pointer pointer;
    private PointerProbe probe;
    private Beams beams;
    private Markers markers;
    private Layers layers;
    private Recording recording;
    private Roofs roofs;
    private double frameBodyScale = 1;
    private final ActorOverlays overlays;
    private final ComfortMotion comfort = new ComfortMotion();
    private final EyeClearance clearance;
    private final Haptics haptics;
    private ComfortShade shade;
    private final WristDisplay wrist = new WristDisplay();
    private double wallDark;
    private final FrameTimes times = new FrameTimes(TIMES_STRETCH_NANOS);
    private long sceneFrom;
    private AtlasQuad atlas;
    private final double[] pointerHit = new double[3];
    private final Runnable finishViews = () -> safely(this::finishEyes);
    private final double[] beamFrom = new double[3];
    private final double[] beamTo = new double[3];
    private final double[] crossAt = new double[3];
    private final double[] handPoint = new double[3];
    private final double[] viewRight = new double[3];
    private final double[] viewEye = new double[3];
    private final double[][] cellCorners = new double[4][3];
    private final double[][] wristCorners = new double[4][3];
    private final double[] outlineFrom = new double[3];
    private final double[] outlineTo = new double[3];
    private double[] outlines = new double[0];
    private int[] outlineColours = new int[0];
    private int outlineCount;
    private boolean pointerMet;
    private boolean tabletop;
    private long lastFrameNanos;
    private boolean frameBegun;
    private boolean placed;
    private boolean drawingEyes;
    private boolean eyesDrawn;
    private volatile boolean flagsStale;
    private boolean ended;

    /**
     * Creates the VR callbacks around a renderer.
     * @param renderer The renderer the scene is drawn through.
     * @param client The client.
     * @param config The plugin config.
     * @param configManager The config manager the settings panel writes to.
     * @param onEnded Receives the reason, on the client thread, when the session ends or fails.
     */
    public VrCallbacks(SceneRenderer renderer, Client client, VitaVRConfig config, ConfigManager configManager, Consumer<String> onEnded)
    {
        this.configManager = configManager;
        this.keys = new CanvasKeys(client);
        this.renderer = renderer;
        this.plugin = renderer.plugin();
        this.client = client;
        this.config = config;
        this.onEnded = onEnded;
        this.overlays = new ActorOverlays(client);
        this.clearance = new EyeClearance(client);
        this.haptics = new Haptics(client, config);
    }

    /**
     * Sets the renderer up for VR, drawing every zone since the head can face any way. Runs on the client thread.
     */
    public void applyFlags()
    {
        renderer.applyFlags();
        flagsStale = false;
    }

    /**
     * Puts the renderer back as the desktop had it. Runs on the client thread.
     */
    public void restoreFlags()
    {
        renderer.restoreFlags();
    }

    /**
     * Sets the GPU plugin up for VR again before the next frame after one of its settings changed. Safe on any thread.
     */
    public void gpuSettingsChanged()
    {
        flagsStale = true;
    }

    /**
     * Begins the headset frame if not begun yet and places the eyes.
     */
    private void beginFrame()
    {
        if (frameBegun || !ready())
        {
            return;
        }
        headset.pollEvents();
        if (headset.isOver())
        {
            end("The headset ended the session");
            return;
        }
        if (!headset.isRunning())
        {
            return;
        }
        long waitFrom = System.nanoTime();
        boolean frameDrawn = headset.beginFrame();
        long waited = System.nanoTime();
        times.begin(waited);
        times.add(FrameTimes.Part.WAIT, waited - waitFrom);
        frameBegun = true;
        eyesDrawn = false;
        placed = false;
        if (recording != null)
        {
            recording.frameBegun();
        }
        if (!frameDrawn)
        {
            return;
        }

        long now = System.nanoTime();
        double seconds = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1e9;
        lastFrameNanos = now;
        controllers.sync(headset.getRoom(), headset.getDisplayTime());
        haptics.update(controllers);
        if (controllers.viewPressed())
        {
            switchView();
        }
        tabletop = config.viewMode() == ViewMode.TABLETOP;
        layers.frame(controllers, rawHead());
        // The right stick pushes, pulls and resizes a held HUD piece instead of turning
        if (!pointer.isMoving())
        {
            placement.turn(controllers, config, seconds);
        }
        if (tabletop && !pointer.isOnPanel() && placement.tiltTable(controllers, seconds))
        {
            config.tableTiltDegrees(placement.tableTiltDegrees());
        }

        LocalPoint at = playerOnMainWorld();
        if (at == null)
        {
            return;
        }
        // Ground under the player's model, else the tile height
        double ground = ownBody.getGround();
        if (Double.isNaN(ground) || at.getWorldView() != WorldView.TOPLEVEL)
        {
            ground = Perspective.getTileHeight(client, at, client.getTopLevelWorldView().getPlane());
        }
        if (headset.takeRecentred())
        {
            placement.recalibrate();
        }
        double unitsPerMetre;
        if (tabletop)
        {
            boolean jumped = placement.placeTable(headset, config, at.getX(), ground, at.getY(), seconds);
            comfort.step(0, 0, jumped, seconds);
            unitsPerMetre = placement.getRoom().getUnitsPerMetre();
        }
        else
        {
            double[] shift = ownBody.viewShift(config.headFollow());
            boolean jumped = placement.place(headset, config, at.getX(), ground, at.getY(), ownBody.restEyeHeight(), ownBody.unitsPerMetre(), shift, seconds);
            unitsPerMetre = placement.getRoom().getUnitsPerMetre();
            comfort.step(placement.getTurnRate(), placement.groundSpeed() / unitsPerMetre, jumped, seconds);
        }
        for (int eye = 0; eye < EYES; eye++)
        {
            eyeMatrices[eye] = placement.eye(eye).worldToClip(headset.eyeFov(eye));
        }
        View centre = placement.getCentre();
        double wanted = tabletop || !config.wallFade() ? 0 : clearance.darkness(new double[]{centre.eyeX(), centre.eyeY(), centre.eyeZ()}, unitsPerMetre, View.nearPlane());
        wallDark += (wanted - wallDark) * (1 - Math.exp(-Math.max(0, seconds) / (wanted > wallDark ? WALL_DARKEN_SECONDS : WALL_CLEAR_SECONDS)));
        double[] right = new double[3];
        double[] down = new double[3];
        double[] forward = new double[3];
        centre.right(right);
        centre.down(down);
        centre.forward(forward);
        double[] head = {centre.eyeX(), centre.eyeY(), centre.eyeZ()};
        headProjection = new EyeProjection(head, right, down, forward);
        double bodyScale = ownBody.unitsPerMetre() / unitsPerMetre;
        ownBody.look(config.heldItems(), config.heldItemPercent(), config.fadeNearEyes(), config.hideOwnHead());
        // Place the wrist plate from the last drawn body before the body updates
        wrist.place(controllers.aim(Controllers.LEFT), rawHead(), seconds);
        rideWrist();
        ownBody.frame(centre.yaw(), head, trackedHand(Controllers.RIGHT, head, bodyScale), trackedHand(Controllers.LEFT, head, bodyScale), seconds);
        frameBodyScale = bodyScale;
        cameraDriver.face(client, placement.getCentre().yaw(), System.currentTimeMillis());
        pointerMet = probe.hit(pointerHit);
        pointer.update(controllers, placement.getRoom(), placement.getLift(), pointerMet ? pointerHit : null, layers.getPanel(), layers.getPicture(), layers.getOwnPanels(), wrist);
        probe.aim(pointer.view());
        placed = true;
    }

    /**
     * Refreshes the settings panel and interfaces after a settings change. Safe on any thread.
     *
     * @param key The changed setting's key.
     */
    public void settingsChanged(String key)
    {
        Layers shown = layers;
        if (shown != null)
        {
            shown.settingsChanged(key);
        }
    }

    /**
     * Gets the session's finished recording.
     * @return The recording, or null if none was made.
     */
    public Path getRecorded()
    {
        return recording == null ? null : recording.getRecorded();
    }

    /**
     * Gets why the session was not recorded.
     * @return The reason, or null if nothing went wrong.
     */
    public String getRecordingProblem()
    {
        return recording == null ? null : recording.getProblem();
    }

    /**
     * Toggles between first person and tabletop, saving it to the config.
     */
    private void switchView()
    {
        config.viewMode(config.viewMode() == ViewMode.TABLETOP ? ViewMode.FIRST_PERSON : ViewMode.TABLETOP);
        placement.resetTable();
        ownBody.reset();
    }

    /**
     * Gets the local player's position on the main world, mapped from a boat if aboard one.
     */
    private LocalPoint playerOnMainWorld()
    {
        Player player = client.getLocalPlayer();
        if (player == null || player.getLocalLocation() == null)
        {
            return null;
        }
        LocalPoint at = player.getLocalLocation();
        if (at.getWorldView() != WorldView.TOPLEVEL)
        {
            WorldEntity boat = client.getTopLevelWorldView().worldEntities().byIndex(at.getWorldView());
            if (boat == null)
            {
                return null;
            }
            at = boat.transformToMainWorld(at);
        }
        return at;
    }

    /**
     * Ends the headset frame, submitting the eyes if they were drawn.
     */
    private void endFrame()
    {
        if (!frameBegun)
        {
            return;
        }
        frameBegun = false;
        long submitFrom = System.nanoTime();
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer frameLayers = null;
            if (eyesDrawn)
            {
                // World layer first, then the overlay layers
                frameLayers = stack.mallocPointer(1 + layers.count());
                frameLayers.put(eyeLayer.address());
                layers.put(frameLayers);
                frameLayers.flip();
            }
            headset.endFrame(frameLayers);
        }
        long done = System.nanoTime();
        times.add(FrameTimes.Part.SUBMIT, done - submitFrom);
        times.end(done, headset.getDisplayPeriod());
        String report = times.report(done);
        if (report != null && headset.getDisplayPeriod() > 0)
        {
            Logger.info(String.format("[VitaVR] %s, headset %.0f Hz", report, 1e9 / headset.getDisplayPeriod()));
        }
    }

    /**
     * Starts the views for the scene, clearing them to the sky colour; fog centres on the head, or on the player on the tabletop.
     */
    private void startEyes()
    {
        View centre = placement.getCentre();
        double fogX = tabletop ? placement.anchorX() : centre.eyeX();
        double fogZ = tabletop ? placement.anchorZ() : centre.eyeZ();
        if (!renderer.startViews(views, fogX, fogZ))
        {
            return;
        }
        eyes.clear(client.getSkyboxColor());
        if (recording.isSpectating())
        {
            recording.clearSpectator(client.getSkyboxColor());
        }
        drawingEyes = true;
        sceneFrom = System.nanoTime();
    }

    /**
     * Draws both pointers, the hit dot and the click cross into one eye.
     */
    private void drawPointers(View view, float[] matrix)
    {
        double[] from = beamFrom;
        double[] to = beamTo;
        for (int hand = Controllers.LEFT; hand <= Controllers.RIGHT; hand++)
        {
            if (!pointer.beam(controllers, hand, placement.getRoom(), placement.getLift(), from, to))
            {
                continue;
            }
            boolean active = hand == pointer.getActiveHand();
            beams.draw(matrix, from, active && pointerMet && pointer.isOnWorld() ? pointerHit : to, active ? ACTIVE_BEAM : RESTING_BEAM);
        }
        drawHands(view, matrix);
        if (pointerMet && pointer.isOnWorld())
        {
            markers.draw(view, matrix, pointerHit, DOT_EDGE_RADIANS, Markers.DOT, DOT_EDGE);
            markers.draw(view, matrix, pointerHit, DOT_RADIANS, Markers.DOT, DOT);
        }
        int cross = pointer.cross(crossAt);
        double scale = pointer.crossScale();
        if (cross != 0 && scale > 0)
        {
            markers.draw(view, matrix, crossAt, CROSS_RADIANS * scale, Markers.CROSS, cross);
        }
    }

    /**
     * Updates the interface layers from what the game just drew.
     */
    private void showInterfaces()
    {
        long from = System.nanoTime();
        layers.show(placement, wrist, pointerHit, pointerMet, rawHead());
        recording.start();
        times.add(FrameTimes.Part.INTERFACES, System.nanoTime() - from);
    }

    /**
     * Gets the head pose the steadied recording camera follows, offset as the eyes are, or null if not steadied.
     */
    private Pose spectatorHead()
    {
        if (!recording.isSteadied())
        {
            return null;
        }
        Pose head = rawHead().lifted(placement.getLift());
        return tabletop ? head : head.ahead(config.headClearanceCm() / 100.0 / frameBodyScale);
    }

    /**
     * Draws the frame's depth-tested tile outlines into the bound view.
     */
    private void drawTiles(float[] matrix)
    {
        for (int segment = 0; segment < outlineCount; segment++)
        {
            int at = segment * 6;
            outlineFrom[0] = outlines[at];
            outlineFrom[1] = outlines[at + 1];
            outlineFrom[2] = outlines[at + 2];
            outlineTo[0] = outlines[at + 3];
            outlineTo[1] = outlines[at + 4];
            outlineTo[2] = outlines[at + 5];
            beams.draw(matrix, outlineFrom, outlineTo, outlineColours[segment]);
        }
    }

    /**
     * Builds the frame's tile outlines for the hovered, own and destination tiles.
     */
    private void prepareTiles()
    {
        outlineCount = 0;
        int plane = client.getTopLevelWorldView().getPlane();
        if (config.outlineHoveredTile() && pointer.isOnWorld())
        {
            Tile hovered = client.getTopLevelWorldView().getSelectedSceneTile();
            if (hovered != null)
            {
                outline(hovered.getLocalLocation(), plane, HOVERED_TILE);
            }
        }
        Player player = client.getLocalPlayer();
        if (config.outlineLocalTile() && player != null)
        {
            LocalPoint at = player.getLocalLocation();
            if (at != null && at.getWorldView() == WorldView.TOPLEVEL)
            {
                outline(new LocalPoint(at.getX() & ~127 | 64, at.getY() & ~127 | 64, client.getTopLevelWorldView()), plane, OWN_TILE);
            }
        }
        if (config.outlineDestinationTile())
        {
            outline(client.getLocalDestinationLocation(), plane, DESTINATION_TILE);
        }
    }

    /**
     * Adds a tile outline, each edge split so it follows the slope.
     */
    private void outline(LocalPoint centre, int plane, int rgba)
    {
        if (centre == null || !client.getTopLevelWorldView().contains(centre))
        {
            return;
        }
        int left = centre.getX() & ~127;
        int bottom = centre.getY() & ~127;
        int[][] corners = {{left, bottom}, {left + 128, bottom}, {left + 128, bottom + 128}, {left, bottom + 128}};
        int colour = (rgba >> 16 & 0xFF) << 24 | (rgba >> 8 & 0xFF) << 16 | (rgba & 0xFF) << 8 | rgba >>> 24;
        int wanted = (outlineCount + 4 * OUTLINE_STEPS) * 6;
        if (outlines.length < wanted)
        {
            outlines = Arrays.copyOf(outlines, wanted * 2);
            outlineColours = Arrays.copyOf(outlineColours, wanted * 2 / 6);
        }
        for (int edge = 0; edge < 4; edge++)
        {
            int[] a = corners[edge];
            int[] b = corners[(edge + 1) % 4];
            for (int step = 0; step < OUTLINE_STEPS; step++)
            {
                ground(a[0] + (b[0] - a[0]) * step / OUTLINE_STEPS, a[1] + (b[1] - a[1]) * step / OUTLINE_STEPS, plane, outlineFrom);
                ground(a[0] + (b[0] - a[0]) * (step + 1) / OUTLINE_STEPS, a[1] + (b[1] - a[1]) * (step + 1) / OUTLINE_STEPS, plane, outlineTo);
                int at = outlineCount * 6;
                System.arraycopy(outlineFrom, 0, outlines, at, 3);
                System.arraycopy(outlineTo, 0, outlines, at + 3, 3);
                outlineColours[outlineCount++] = colour;
            }
        }
    }

    private void ground(int x, int z, int plane, double[] out)
    {
        int clampedX = Math.min(x, (client.getTopLevelWorldView().getSizeX() << 7) - 1);
        int clampedZ = Math.min(z, (client.getTopLevelWorldView().getSizeY() << 7) - 1);
        out[0] = x;
        out[1] = Perspective.getTileHeight(client, new LocalPoint(clampedX, clampedZ, client.getTopLevelWorldView()), plane) - OUTLINE_LIFT;
        out[2] = z;
    }

    /**
     * Draws ghost hands at the controllers in first person while the game poses the arms.
     */
    private void drawHands(View view, float[] matrix)
    {
        if (tabletop || !config.ghostHands() || ownBody.isArmsFollowing())
        {
            return;
        }
        for (int hand = Controllers.LEFT; hand <= Controllers.RIGHT; hand++)
        {
            double[] at = handPoint;
            if (!handAt(hand, at))
            {
                continue;
            }
            double away = Math.sqrt(square(at[0] - view.eyeX()) + square(at[1] - view.eyeY()) + square(at[2] - view.eyeZ()));
            markers.draw(view, matrix, at, Math.atan2(HAND_METRES * placement.getRoom().getUnitsPerMetre() / 2, Math.max(1, away)), Markers.DOT, GHOST_HAND);
        }
    }

    /**
     * Gets a controller's world position.
     *
     * @return Whether it is tracked; out is set only if so.
     */
    private boolean handAt(int hand, double[] out)
    {
        Pose held = controllers.aim(hand);
        if (held == null)
        {
            return false;
        }
        placement.toWorld(held.x(), held.y(), held.z(), out);
        return true;
    }

    /**
     * Gets a controller's pose for the body's hand, offset from the head at body scale, or null if untracked.
     */
    private TrackedHand trackedHand(int hand, double[] head, double bodyScale)
    {
        Pose held = controllers.aim(hand);
        double[] at = new double[3];
        if (held == null || !handAt(hand, at))
        {
            return null;
        }
        for (int axis = 0; axis < 3; axis++)
        {
            at[axis] = head[axis] + (at[axis] - head[axis]) * bodyScale;
        }
        return new TrackedHand(at, worldDirection(held, 0, 1, 0), worldDirection(held, 0, 0, -1), worldDirection(held, 1, 0, 0));
    }

    /**
     * Gets a room pose direction in world space, kept unit length.
     */
    private double[] worldDirection(Pose pose, double x, double y, double z)
    {
        double[] inRoom = new double[3];
        pose.rotate(x, y, z, inRoom);
        double[] inWorld = new double[3];
        placement.getRoom().direction(inRoom[0], inRoom[1], inRoom[2], inWorld);
        return inWorld;
    }

    /**
     * Gets the head pose in room space, between the eyes.
     */
    private Pose rawHead()
    {
        return Pose.between(headset.eyePose(0), headset.eyePose(1));
    }

    /**
     * Builds the actor overlays for this frame; in first person the own stack hangs ahead of and below the eyes.
     */
    private void prepareOverlays()
    {
        View centre = placement.getCentre();
        double unitsPerMetre = placement.getRoom().getUnitsPerMetre();
        double[] own = null;
        double[] forward = new double[3];
        centre.forward(forward);
        double level = Math.hypot(forward[0], forward[2]);
        if (!tabletop && level > 1e-6)
        {
            double ahead = OWN_AHEAD_METRES * unitsPerMetre;
            own = new double[]{centre.eyeX() + forward[0] / level * ahead, centre.eyeY() + OWN_BELOW_METRES * unitsPerMetre, centre.eyeZ() + forward[2] / level * ahead};
        }
        overlays.prepare(new double[]{centre.eyeX(), centre.eyeY(), centre.eyeZ()}, own, OWN_SPLATS_BELOW_METRES * unitsPerMetre);
        if (overlays.isRepainted())
        {
            atlas.upload(overlays.atlasPixels());
        }
    }

    /**
     * Draws the actor overlays into one eye, farthest first, facing the eye.
     */
    private void drawOverlays(View view, float[] matrix)
    {
        double[] right = viewRight;
        view.right(right);
        double level = Math.hypot(right[0], right[2]);
        if (level < 1e-6)
        {
            return;
        }
        viewEye[0] = view.eyeX();
        viewEye[1] = view.eyeY();
        viewEye[2] = view.eyeZ();
        for (ActorOverlays.Cell cell : overlays.getCells())
        {
            atlas.draw(matrix, ActorOverlays.corners(cell, viewEye, right[0] / level, right[2] / level, cellCorners), cell.getLeft(), cell.getTop(), cell.getWidth(),
                    cell.getHeight(), 1);
        }
    }

    /**
     * Draws the left wrist plate into one eye, faded by how far the wrist faces the eyes.
     */
    private void drawWrist(float[] matrix)
    {
        if (!wrist.corners(placement.getRoom(), placement.getLift(), wristCorners))
        {
            return;
        }
        layers.getWristPicture().draw(matrix, wristCorners, 0, 0, WristPicture.WIDTH, WristPicture.HEIGHT, (float) wrist.getShown());
    }

    /**
     * Moves the wrist plate onto the drawn body's left wrist while the arms follow the controllers.
     */
    private void rideWrist()
    {
        double[][] wristAt = tabletop ? null : ownBody.getLeftWrist();
        if (wristAt == null)
        {
            return;
        }
        RoomToWorld room = placement.getRoom();
        double[] at = new double[3];
        double[] along = new double[3];
        double[] out = new double[3];
        placement.toRoom(wristAt[0][0], wristAt[0][1], wristAt[0][2], at);
        room.roomDirection(wristAt[1][0], wristAt[1][1], wristAt[1][2], along);
        room.roomDirection(wristAt[2][0], wristAt[2][1], wristAt[2][2], out);
        wrist.placeOn(at, unit(along), unit(out));
    }

    /**
     * Records an applied hitsplat for the actor overlays. Runs on the client thread.
     *
     * @param actor The actor hit.
     * @param hitsplat The hitsplat.
     */
    public void applied(Actor actor, Hitsplat hitsplat)
    {
        overlays.applied(actor, hitsplat);
        Actor self = client.getLocalPlayer();
        haptics.hitsplat(actor == self, actor != self && hitsplat.isMine(), hitsplat.getAmount(), overlays.showsIn(hitsplat));
    }

    /**
     * Records a performed menu action for the click cross. Runs on the client thread.
     *
     * @param action The menu action.
     */
    public void performed(MenuAction action)
    {
        if (pointer != null)
        {
            pointer.performed(action);
        }
    }

    /**
     * Draws tile outlines, pointers, actor overlays and the wrist plate into the bound view.
     */
    private void drawOver(View view, float[] matrix)
    {
        drawTiles(matrix);
        drawPointers(view, matrix);
        drawOverlays(view, matrix);
        drawWrist(matrix);
    }

    /**
     * Draws over the views, submits both eyes to the headset and mirrors the left eye if enabled.
     */
    private void finishEyes()
    {
        long finishFrom = System.nanoTime();
        times.add(FrameTimes.Part.SCENE, finishFrom - sceneFrom);
        finishEyesTimed();
        times.add(FrameTimes.Part.FINISH, System.nanoTime() - finishFrom);
    }

    private void finishEyesTimed()
    {
        leaveEyes();
        state.save();
        probe.read(eyes, eyeMatrices[0]);
        prepareOverlays();
        prepareTiles();
        rideWrist();
        for (int view = 0; view < views.count(); view++)
        {
            views.bind(view);
            drawOver(views.view(view), views.worldToClip(view));
        }
        eyes.resolve();
        for (int eye = 0; eye < EYES; eye++)
        {
            int image = eyeLayer.acquire(eye);
            eyes.copyEye(eye, image);
            shade.draw(image, eyes.getEyeWidth(), eyes.getEyeHeight(), headset.eyeFov(eye), comfort.vignette() * config.vignette(), Math.max(comfort.black(), wallDark));
            eyeLayer.release(eye, headset.view(eye));
        }
        eyesDrawn = true;
        recording.record(eyes, eyeMatrices);

        if (config.mirror() && renderer.bindMirror(mirrorViewport))
        {
            mirror();
        }
        state.restore();
    }

    /**
     * Stops drawing into the views and gives the renderer back its own targets.
     */
    private void leaveEyes()
    {
        if (!drawingEyes)
        {
            return;
        }
        drawingEyes = false;
        renderer.leaveViews();
    }

    /**
     * Draws the left eye over the bound desktop picture, cropped to its shape.
     */
    private void mirror()
    {
        double viewportAspect = (double) mirrorViewport[2] / Math.max(1, mirrorViewport[3]);
        double eyeAspect = (double) eyes.getEyeWidth() / eyes.getEyeHeight();
        if (viewportAspect > eyeAspect)
        {
            float height = (float) (eyeAspect / viewportAspect);
            quad.draw(eyes.getTexture(), 0, (1 - height) / 2, 0.5f, height);
        }
        else
        {
            float width = (float) (viewportAspect / eyeAspect) * 0.5f;
            quad.draw(eyes.getTexture(), (0.5f - width) / 2, 0, width, 1);
        }
    }

    /**
     * Opens the headset on first use, in the renderer's context on the client thread.
     *
     * @return Whether the headset is open.
     */
    private boolean ready()
    {
        if (headset != null)
        {
            return true;
        }
        if (ended)
        {
            return false;
        }
        try
        {
            headset = Headset.open(WGL.wglGetCurrentDC(), WGL.wglGetCurrentContext());
            controllers = new Controllers(headset.getSession());
            int width = 1;
            int height = 1;
            for (int eye = 0; eye < EYES; eye++)
            {
                width = Math.max(width, (int) Math.round(headset.eyeWidth(eye) * config.renderScale()));
                height = Math.max(height, (int) Math.round(headset.eyeHeight(eye) * config.renderScale()));
            }
            eyes = new EyesTarget(width, height, config.samples(), EYES);
            eyeLayer = new EyeLayer(headset, new int[]{width, width}, new int[]{height, height});
            quad = new PictureQuad();
            pointer = new Pointer(client);
            probe = new PointerProbe(width, height);
            beams = new Beams();
            markers = new Markers();
            layers = new Layers(client, config, configManager, keys, headset, pointer);
            recording = new Recording(config, layers, placement, times);
            views = new SceneViews(eyes, eyeMatrices, placement, recording);
            roofs = new Roofs(client, config, renderer);
            atlas = new AtlasQuad(ActorOverlays.ATLAS_WIDTH, ActorOverlays.ATLAS_HEIGHT);
            shade = new ComfortShade();
            return true;
        }
        catch (RuntimeException | UnsatisfiedLinkError e)
        {
            Logger.error(e, "[VitaVR] Could not open the headset");
            end(e.getMessage());
            return false;
        }
    }

    /**
     * Ends the VR session once, reporting the reason.
     */
    private void end(String reason)
    {
        if (ended)
        {
            return;
        }
        ended = true;
        close();
        onEnded.accept(reason);
    }

    /**
     * Runs a headset step so a failure ends VR without breaking desktop drawing.
     */
    private void safely(Runnable step)
    {
        if (ended)
        {
            return;
        }
        try
        {
            step.run();
        }
        catch (RuntimeException e)
        {
            failed(e);
        }
    }

    /**
     * Ends VR after a failed headset step, giving the renderer back its own targets first.
     */
    private void failed(RuntimeException e)
    {
        Logger.error(e, "[VitaVR] Headset frame failed");
        leaveEyes();
        end("Headset error: " + e.getMessage());
    }

    @Override
    public void preSceneDraw(Scene scene, Projection entityProjection, float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw,
                             int minLevel, int level, int maxLevel, Set<Integer> hideRoofIds)
    {
        boolean toplevel = scene.getWorldViewId() == WorldView.TOPLEVEL;
        if (toplevel)
        {
            if (flagsStale)
            {
                applyFlags();
            }
            safely(this::beginFrame);
        }
        // Kept roofs draw every level up to the top
        boolean keep = roofs != null && roofs.keeps(scene, level);
        View head = toplevel && placed && !ended ? placement.getCentre() : null;
        try
        {
            renderer.preSceneDraw(scene, entityProjection, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, keep ? Roofs.TOP_LEVEL : maxLevel,
                    keep ? Collections.emptySet() : hideRoofIds, head);
        }
        catch (RuntimeException e)
        {
            failed(e);
        }
        if (head != null)
        {
            safely(() ->
            {
                recording.pace(spectatorHead());
                startEyes();
            });
        }
    }

    @Override
    public void drawZoneOpaque(Projection projection, Scene scene, int zoneX, int zoneZ)
    {
        if (!drawingEyes)
        {
            plugin.drawZoneOpaque(projection, scene, zoneX, zoneZ);
            return;
        }
        try
        {
            renderer.drawZoneOpaque(projection, scene, zoneX, zoneZ);
            if (roofs.hasCeilings(scene, zoneX, zoneZ, placement.anchorX(), placement.anchorZ()))
            {
                roofs.ceilings(projection, scene, zoneX, zoneZ);
            }
        }
        catch (RuntimeException e)
        {
            failed(e);
        }
    }

    @Override
    public void drawZoneAlpha(Projection projection, Scene scene, int level, int zoneX, int zoneZ)
    {
        if (!drawingEyes)
        {
            plugin.drawZoneAlpha(projection, scene, level, zoneX, zoneZ);
            return;
        }
        try
        {
            renderer.drawZoneAlpha(projection, scene, level, zoneX, zoneZ);
        }
        catch (RuntimeException e)
        {
            failed(e);
        }
    }

    @Override
    public void drawPass(Projection projection, Scene scene, int pass)
    {
        if (!drawingEyes)
        {
            plugin.drawPass(projection, scene, pass);
            return;
        }
        try
        {
            renderer.drawPass(projection, scene, pass);
        }
        catch (RuntimeException e)
        {
            failed(e);
        }
    }

    @Override
    public void postSceneDraw(Scene scene)
    {
        boolean views = scene.getWorldViewId() == WorldView.TOPLEVEL && drawingEyes;
        try
        {
            renderer.postSceneDraw(scene, views ? finishViews : null);
        }
        catch (RuntimeException e)
        {
            failed(e);
        }
    }

    @Override
    public void draw(int overlayColor)
    {
        safely(() ->
        {
            // Keep the headset going on frames with no scene, like login or loading
            beginFrame();
            if (eyesDrawn)
            {
                showInterfaces();
            }
            endFrame();
        });
        renderer.draw(overlayColor);
    }

    @Override
    public boolean zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY)
    {
        return renderer.zoneInFrustum(zoneX, zoneZ, maxY, minY);
    }

    @Override
    public void draw(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash)
    {
        plugin.draw(projection, scene, renderable, orientation, x, y, z, hash);
    }

    @Override
    public void drawScenePaint(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY)
    {
        plugin.drawScenePaint(scene, paint, plane, tileX, tileY);
    }

    @Override
    public void drawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY)
    {
        plugin.drawSceneTileModel(scene, model, tileX, tileY);
    }

    @Override
    public void drawScene(double cameraX, double cameraY, double cameraZ, double cameraPitch, double cameraYaw, int plane)
    {
        plugin.drawScene(cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, plane);
    }

    @Override
    public void postDrawScene()
    {
        plugin.postDrawScene();
    }

    @Override
    public void animate(Texture texture, int diff)
    {
        plugin.animate(texture, diff);
    }

    @Override
    public void loadScene(Scene scene)
    {
        plugin.loadScene(scene);
    }

    @Override
    public void swapScene(Scene scene)
    {
        plugin.swapScene(scene);
    }

    @Override
    public boolean tileInFrustum(Scene scene, float pitchSin, float pitchCos, float yawSin, float yawCos, int cameraX, int cameraY, int cameraZ, int plane, int msx, int msy)
    {
        return plugin.tileInFrustum(scene, pitchSin, pitchCos, yawSin, yawCos, cameraX, cameraY, cameraZ, plane, msx, msy);
    }

    @Override
    public void loadScene(WorldView worldView, Scene scene)
    {
        plugin.loadScene(worldView, scene);
    }

    @Override
    public void despawnWorldView(WorldView worldView)
    {
        plugin.despawnWorldView(worldView);
    }

    @Override
    public void drawDynamic(Projection projection, Scene scene, TileObject tileObject, Renderable renderable, Model model, int orientation, int x, int y, int z)
    {
        plugin.drawDynamic(seenFrom(projection, scene, x, y, z), scene, tileObject, renderable, model, orientation, x, y, z);
    }

    @Override
    public void drawDynamic(int renderThreadId, Projection projection, Scene scene, TileObject tileObject, Renderable renderable, Model model, int orientation,
                            int x, int y, int z)
    {
        plugin.drawDynamic(renderThreadId, seenFrom(projection, scene, x, y, z), scene, tileObject, renderable, model, orientation, x, y, z);
    }

    @Override
    public void drawTemp(Projection projection, Scene scene, GameObject gameObject, Model model, int orientation, int x, int y, int z)
    {
        Player player = client.getLocalPlayer();
        if (!firstPersonEyes() || scene.getWorldViewId() != WorldView.TOPLEVEL || player == null || gameObject.getRenderable() != player)
        {
            plugin.drawTemp(seenFrom(projection, scene, x, y, z), scene, gameObject, model, orientation, x, y, z);
            return;
        }

        // Stand the body at the view anchor so they move together
        int facing = ownBody.facing(player, orientation);
        int bodyX = (int) Math.round(placement.anchorX());
        int bodyZ = (int) Math.round(placement.anchorZ());
        ownBody.shape(player, model, facing, bodyX, y, bodyZ);
        try
        {
            plugin.drawTemp(seenFrom(projection, scene, bodyX, y, bodyZ), scene, gameObject, model, facing, bodyX, y, bodyZ);
        }
        finally
        {
            ownBody.restore(model);
        }
    }

    /**
     * Gets the projection a top level model is culled and sorted with: while the eyes are drawn, the head's, turned to face the model.
     */
    private Projection seenFrom(Projection projection, Scene scene, int x, int y, int z)
    {
        return drawingEyes && scene.getWorldViewId() == WorldView.TOPLEVEL ? headProjection.facing(x, y, z) : projection;
    }

    /**
     * Gets whether the eyes are being drawn in first person this frame.
     */
    private boolean firstPersonEyes()
    {
        return drawingEyes && config.viewMode() == ViewMode.FIRST_PERSON;
    }

    @Override
    public void invalidateZone(Scene scene, int zoneX, int zoneZ)
    {
        plugin.invalidateZone(scene, zoneX, zoneZ);
    }

    /**
     * Releases the headset and GL objects. Call on the client thread. Safe to call more than once.
     */
    @Override
    public void close()
    {
        leaveEyes();
        renderer.close();
        if (recording != null)
        {
            recording.close();
        }
        try
        {
            if (frameBegun)
            {
                frameBegun = false;
                headset.endFrame(null);
            }
        }
        catch (RuntimeException e)
        {
            Logger.error(e, "[VitaVR] Ending the last headset frame failed");
        }
        keys.releaseAll();
        if (layers != null)
        {
            layers.close();
            layers = null;
        }
        if (atlas != null)
        {
            atlas.close();
            atlas = null;
        }
        if (shade != null)
        {
            shade.close();
            shade = null;
        }
        overlays.clear();
        if (pointer != null)
        {
            pointer.close();
            pointer = null;
        }
        if (probe != null)
        {
            probe.close();
            probe = null;
        }
        if (beams != null)
        {
            beams.close();
            beams = null;
        }
        if (markers != null)
        {
            markers.close();
            markers = null;
        }
        if (quad != null)
        {
            quad.close();
            quad = null;
        }
        if (eyeLayer != null)
        {
            eyeLayer.close();
            eyeLayer = null;
        }
        if (eyes != null)
        {
            eyes.close();
            eyes = null;
        }
        if (controllers != null)
        {
            controllers.close();
            controllers = null;
        }
        if (headset != null)
        {
            headset.close();
            headset = null;
        }
    }
}
