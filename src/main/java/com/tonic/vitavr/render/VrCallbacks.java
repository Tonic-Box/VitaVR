package com.tonic.vitavr.render;

import com.tonic.Logger;
import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.body.OwnBody;
import com.tonic.vitavr.body.TrackedHand;
import com.tonic.vitavr.config.ViewMode;
import com.tonic.vitavr.hud.CanvasKeys;
import com.tonic.vitavr.feel.Haptics;
import com.tonic.vitavr.overlay.ActorOverlays;
import com.tonic.vitavr.space.ComfortMotion;
import com.tonic.vitavr.space.EyeClearance;
import com.tonic.vitavr.timing.FrameTimes;
import com.tonic.vitavr.wrist.WristDisplay;
import com.tonic.vitavr.wrist.WristPicture;
import com.tonic.vitavr.pointer.CameraDriver;
import com.tonic.vitavr.pointer.Pointer;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.Placement;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.xr.Controllers;
import com.tonic.vitavr.xr.EyeLayer;
import com.tonic.vitavr.xr.Headset;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Actor;
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
import net.runelite.client.plugins.gpu.GpuPluginConfig;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
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
 * Draws the game into the headset by wrapping the GPU plugin's draw callbacks and replaying each draw for both eyes.
 */
public final class VrCallbacks implements DrawCallbacks, AutoCloseable
{
    private static final int EYES = 2;
    /** How many tiles the table's edge fades over. */
    private static final int TABLE_FOG_TILES = 2;
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
    /** How near a zone centre must be to the pointer for the probe to draw it, in game units. */
    private static final double PROBE_ZONE_REACH = 1024;

    @Getter
    private final DrawCallbacks gpu;
    private final Client client;
    private final VitaVRConfig config;
    private final GpuPluginConfig gpuConfig;
    private final ConfigManager configManager;
    private final CanvasKeys keys;
    private final Consumer<String> onEnded;

    private final Placement placement = new Placement();
    private final OwnBody ownBody = new OwnBody();
    private final CameraDriver cameraDriver = new CameraDriver();
    private final GlState state = new GlState();
    private final float[][] eyeMatrices = new float[EYES][];
    private final float[] gpuWorldProj = new float[16];
    private EyeProjection headProjection;
    private final int[] sceneViewport = new int[4];
    private Headset headset;
    private Controllers controllers;
    private GpuInternals internals;
    private EyesTarget eyes;
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
    private final ZoneDraw zoneDraw = new ZoneDraw();
    private final AlphaDraw alphaDraw = new AlphaDraw();
    private final double[] aimForward = new double[3];
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
    private int sceneFramebuffer;
    private int worldProjProgram;
    private int worldProj = -1;
    private int useFogUniform = -1;
    private int fogDepthUniform = -1;
    private int drawDistanceUniform = -1;
    private boolean tabletop;
    private double tableReach;
    private long lastFrameNanos;
    private boolean frameBegun;
    private boolean frameDrawn;
    private boolean placed;
    private boolean drawingEyes;
    private boolean eyesDrawn;
    private volatile boolean flagsStale;
    private boolean ended;

    /**
     * Creates the VR callbacks around the GPU plugin.
     * @param gpu The GPU plugin's draw callbacks.
     * @param client The client.
     * @param config The plugin config.
     * @param gpuConfig The GPU plugin config.
     * @param configManager The config manager the settings panel writes to.
     * @param onEnded Receives the reason, on the client thread, when the session ends or fails.
     */
    public VrCallbacks(DrawCallbacks gpu, Client client, VitaVRConfig config, GpuPluginConfig gpuConfig, ConfigManager configManager, Consumer<String> onEnded)
    {
        this.configManager = configManager;
        this.keys = new CanvasKeys(client);
        this.gpu = gpu;
        this.client = client;
        this.config = config;
        this.gpuConfig = gpuConfig;
        this.onEnded = onEnded;
        this.overlays = new ActorOverlays(client);
        this.clearance = new EyeClearance(client);
        this.haptics = new Haptics(client, config);
    }

    /**
     * Sets the GPU flags for VR, drawing every zone since the head can face any way. Runs on the client thread.
     */
    public void applyFlags()
    {
        client.setGpuFlags(GpuInternals.flags(gpuConfig, true));
        flagsStale = false;
    }

    /**
     * Restores the GPU plugin's own GPU flags. Runs on the client thread.
     */
    public void restoreFlags()
    {
        client.setGpuFlags(GpuInternals.flags(gpuConfig, false));
    }

    /**
     * Reapplies the GPU flags before the next frame after a GPU plugin setting change. Safe on any thread.
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
        frameDrawn = headset.beginFrame();
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
        placement.turn(controllers, config, seconds);
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
            tableReach = placement.tableReach();
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
        ownBody.frame(centre.yaw(), head, trackedHand(Controllers.RIGHT, head, bodyScale), trackedHand(Controllers.LEFT, head, bodyScale), seconds);
        frameBodyScale = bodyScale;
        cameraDriver.face(client, placement.getCentre().yaw(), System.currentTimeMillis());
        pointerMet = probe.hit(pointerHit);
        wrist.place(controllers.aim(Controllers.LEFT), rawHead(), seconds);
        rideWrist();
        pointer.update(controllers, placement.getRoom(), placement.getLift(), pointerMet ? pointerHit : null, layers.getPanel(), layers.getPicture(), layers.getOwnPanels(), wrist);
        probe.aim(pointer.view());
        placed = true;
    }

    /**
     * Marks the settings panel for repaint. Safe on any thread.
     */
    public void settingsChanged()
    {
        Layers shown = layers;
        if (shown != null)
        {
            shown.settingsChanged();
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
     * Prepares the eyes for the scene, saving the GPU plugin's framebuffer and matrix and clearing to the sky colour.
     */
    private void startEyes()
    {
        sceneFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, sceneViewport);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program != worldProjProgram)
        {
            worldProjProgram = program;
            worldProj = GL20.glGetUniformLocation(program, "worldProj");
            useFogUniform = GL20.glGetUniformLocation(program, "useFog");
            fogDepthUniform = GL20.glGetUniformLocation(program, "fogDepth");
            drawDistanceUniform = GL20.glGetUniformLocation(program, "drawDistance");
        }
        if (worldProj < 0)
        {
            return;
        }
        GL20.glGetUniformfv(program, worldProj, gpuWorldProj);
        centreFog();
        if (probe.aimed())
        {
            probe.clear();
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
     * Centres the GPU plugin's fog on the head in first person, or on the player on the tabletop.
     */
    private void centreFog()
    {
        View centre = placement.getCentre();
        double fogX = tabletop ? placement.anchorX() : centre.eyeX();
        double fogZ = tabletop ? placement.anchorZ() : centre.eyeZ();

        // Uniform block starts cameraYaw, cameraPitch, cameraX, cameraY, cameraZ as std140 floats
        int block = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, 0);
        if (block != 0)
        {
            int bound = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, block);
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 8, new float[]{(float) fogX});
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 16, new float[]{(float) fogZ});
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, bound);
        }
        if (tabletop && drawDistanceUniform >= 0)
        {
            GL20.glUniform1i(useFogUniform, 1);
            GL20.glUniform1i(fogDepthUniform, TABLE_FOG_TILES);
            GL20.glUniform1i(drawDistanceUniform, (int) Math.round(tableReach));
        }
    }

    /**
     * Gets whether a top level zone lies past the table's edge.
     */
    private boolean offTable(Scene scene, int zoneX, int zoneZ)
    {
        if (!tabletop || scene.getWorldViewId() != WorldView.TOPLEVEL)
        {
            return false;
        }
        double left = GpuInternals.zoneEdge(zoneX);
        double bottom = GpuInternals.zoneEdge(zoneZ);
        double dx = Math.max(0, Math.max(left - placement.anchorX(), placement.anchorX() - (left + GpuInternals.ZONE_UNITS)));
        double dz = Math.max(0, Math.max(bottom - placement.anchorZ(), placement.anchorZ() - (bottom + GpuInternals.ZONE_UNITS)));
        return dx * dx + dz * dz > tableReach * tableReach;
    }

    /**
     * A reusable opaque zone draw, run once per view.
     */
    private final class ZoneDraw implements Runnable
    {
        private final Runnable once = this::once;
        private Projection projection;
        private Scene scene;
        private int zoneX;
        private int zoneZ;
        private boolean nearPointer;

        private void set(Projection projection, Scene scene, int zoneX, int zoneZ, boolean nearPointer)
        {
            this.projection = projection;
            this.scene = scene;
            this.zoneX = zoneX;
            this.zoneZ = zoneZ;
            this.nearPointer = nearPointer;
        }

        @Override
        public void run()
        {
            forEachView(once, nearPointer);
        }

        private void once()
        {
            gpu.drawZoneOpaque(projection, scene, zoneX, zoneZ);
        }
    }

    /**
     * A reusable alpha zone draw for the right eye and steadied camera, after the GPU plugin drew the left.
     */
    private final class AlphaDraw implements Runnable
    {
        private Scene scene;
        private int level;
        private int zoneX;
        private int zoneZ;

        private void set(Scene scene, int level, int zoneX, int zoneZ)
        {
            this.scene = scene;
            this.level = level;
            this.zoneX = zoneX;
            this.zoneZ = zoneZ;
        }

        @Override
        public void run()
        {
            useEye(1);
            internals.drawZoneAlphaAgain(scene, level, zoneX, zoneZ);
            if (recording.isSpectating())
            {
                useSpectator();
                internals.drawZoneAlphaAgain(scene, level, zoneX, zoneZ);
            }
        }
    }

    /**
     * Runs a GPU plugin draw once for each eye.
     */
    private void forEachEye(Runnable draw)
    {
        for (int eye = 0; eye < EYES; eye++)
        {
            useEye(eye);
            draw.run();
        }
        if (recording.isSpectating())
        {
            useSpectator();
            draw.run();
        }
    }

    private void useSpectator()
    {
        recording.useSpectator();
        GL20.glUniformMatrix4fv(worldProj, false, recording.getSpectatorMatrix());
    }

    /**
     * Runs a GPU plugin draw for each eye, and for the pointer probe when aimed and requested.
     */
    private void forEachView(Runnable draw, boolean forProbe)
    {
        forEachEye(draw);
        if (forProbe && probe.aimed())
        {
            probe.use();
            GL20.glUniformMatrix4fv(worldProj, false, probe.getWorldToClip());
            draw.run();
        }
    }

    /**
     * Gets whether a zone is near enough the pointer for the probe.
     */
    private boolean nearPointer(Scene scene, int zoneX, int zoneZ)
    {
        View aimed = pointer.view();
        if (aimed == null)
        {
            return false;
        }
        if (scene.getWorldViewId() != WorldView.TOPLEVEL)
        {
            return true;
        }
        double[] forward = aimForward;
        aimed.forward(forward);
        double level = Math.hypot(forward[0], forward[2]);
        double dx = GpuInternals.zoneEdge(zoneX) + GpuInternals.ZONE_UNITS / 2.0 - aimed.eyeX();
        double dz = GpuInternals.zoneEdge(zoneZ) + GpuInternals.ZONE_UNITS / 2.0 - aimed.eyeZ();
        if (level < 1e-6)
        {
            return dx * dx + dz * dz < PROBE_ZONE_REACH * PROBE_ZONE_REACH;
        }
        double along = Math.max(0, (dx * forward[0] + dz * forward[2]) / level);
        double acrossX = dx - forward[0] / level * along;
        double acrossZ = dz - forward[2] / level * along;
        return acrossX * acrossX + acrossZ * acrossZ < PROBE_ZONE_REACH * PROBE_ZONE_REACH;
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
            Tile hovered = client.getSelectedSceneTile();
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
        if (centre == null || !centre.isInScene())
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

    private void useEye(int eye)
    {
        eyes.use(eye);
        GL20.glUniformMatrix4fv(worldProj, false, eyeMatrices[eye]);
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
     * Submits both eyes to the headset and restores the GPU plugin's framebuffer, mirroring the left eye if enabled.
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
        probe.read();
        prepareOverlays();
        prepareTiles();
        rideWrist();
        for (int eye = 0; eye < EYES; eye++)
        {
            eyes.use(eye);
            drawOver(placement.eye(eye), eyeMatrices[eye]);
        }
        if (recording.isSpectating())
        {
            recording.useSpectator();
            drawOver(recording.getSpectatorView(), recording.getSpectatorMatrix());
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

        if (config.mirror())
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFramebuffer);
            GL11.glViewport(sceneViewport[0], sceneViewport[1], sceneViewport[2], sceneViewport[3]);
            mirror();
        }
        state.restore();
    }

    /**
     * Restores the GPU plugin's scene framebuffer, viewport and matrix.
     */
    private void leaveEyes()
    {
        if (!drawingEyes)
        {
            return;
        }
        drawingEyes = false;
        GL20.glUniformMatrix4fv(worldProj, false, gpuWorldProj);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFramebuffer);
        GL11.glViewport(sceneViewport[0], sceneViewport[1], sceneViewport[2], sceneViewport[3]);
    }

    /**
     * Draws the left eye over the game's viewport, cropped to its shape.
     */
    private void mirror()
    {
        double viewportAspect = (double) sceneViewport[2] / Math.max(1, sceneViewport[3]);
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
     * Opens the headset on first use, in the GPU plugin's context on the client thread.
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
            internals = new GpuInternals(gpu);
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
            probe = new PointerProbe();
            beams = new Beams();
            markers = new Markers();
            layers = new Layers(client, config, configManager, keys, headset, pointer);
            recording = new Recording(config, layers, placement, times);
            roofs = new Roofs(client, config, internals);
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
            Logger.error(e, "[VitaVR] Headset frame failed");
            leaveEyes();
            end("Headset error: " + e.getMessage());
        }
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
        boolean keep = roofs != null && roofs.keeps(scene, minLevel, level);
        gpu.preSceneDraw(scene, entityProjection, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, keep ? Roofs.TOP_LEVEL : maxLevel,
                keep ? Collections.emptySet() : hideRoofIds);
        if (internals != null)
        {
            safely(() -> internals.prepare(scene));
        }
        if (toplevel && placed)
        {
            safely(() ->
            {
                View centre = placement.getCentre();
                internals.sortFrom(scene, centre.eyeX(), centre.eyeY(), centre.eyeZ(), centre.yaw(), centre.pitch());
                recording.pace(spectatorHead());
                startEyes();
            });
        }
    }

    @Override
    public void drawZoneOpaque(Projection projection, Scene scene, int zoneX, int zoneZ)
    {
        if (drawingEyes)
        {
            if (!offTable(scene, zoneX, zoneZ))
            {
                zoneDraw.set(projection, scene, zoneX, zoneZ, nearPointer(scene, zoneX, zoneZ));
                zoneDraw.run();
                if (roofs.hasCeilings(scene, zoneX, zoneZ, placement.anchorX(), placement.anchorZ()))
                {
                    safely(() -> roofs.ceilings(scene, zoneDraw));
                }
            }
            return;
        }
        gpu.drawZoneOpaque(projection, scene, zoneX, zoneZ);
    }

    @Override
    public void drawZoneAlpha(Projection projection, Scene scene, int level, int zoneX, int zoneZ)
    {
        if (drawingEyes && offTable(scene, zoneX, zoneZ))
        {
            return;
        }
        if (drawingEyes)
        {
            // Plugin call for the left eye, since its zone sorting must run only once
            useEye(0);
            gpu.drawZoneAlpha(projection, scene, level, zoneX, zoneZ);
            alphaDraw.set(scene, level, zoneX, zoneZ);
            safely(alphaDraw);
            return;
        }
        gpu.drawZoneAlpha(projection, scene, level, zoneX, zoneZ);
    }

    @Override
    public void drawPass(Projection projection, Scene scene, int pass)
    {
        if (drawingEyes && pass == DrawCallbacks.PASS_OPAQUE && scene.getWorldViewId() == WorldView.TOPLEVEL)
        {
            safely(() ->
            {
                internals.takeOpaque();
                forEachView(internals::drawOpaque, true);
                internals.resetOpaque();
            });
            return;
        }
        gpu.drawPass(projection, scene, pass);
    }

    @Override
    public void postSceneDraw(Scene scene)
    {
        if (scene.getWorldViewId() == WorldView.TOPLEVEL && drawingEyes)
        {
            safely(this::finishEyes);
        }
        gpu.postSceneDraw(scene);
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
        gpu.draw(overlayColor);
    }

    @Override
    public boolean zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY)
    {
        return true;
    }

    @Override
    public void draw(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash)
    {
        gpu.draw(projection, scene, renderable, orientation, x, y, z, hash);
    }

    @Override
    public void drawScenePaint(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY)
    {
        gpu.drawScenePaint(scene, paint, plane, tileX, tileY);
    }

    @Override
    public void drawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY)
    {
        gpu.drawSceneTileModel(scene, model, tileX, tileY);
    }

    @Override
    public void drawScene(double cameraX, double cameraY, double cameraZ, double cameraPitch, double cameraYaw, int plane)
    {
        gpu.drawScene(cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, plane);
    }

    @Override
    public void postDrawScene()
    {
        gpu.postDrawScene();
    }

    @Override
    public void animate(Texture texture, int diff)
    {
        gpu.animate(texture, diff);
    }

    @Override
    public void loadScene(Scene scene)
    {
        gpu.loadScene(scene);
    }

    @Override
    public void swapScene(Scene scene)
    {
        gpu.swapScene(scene);
    }

    @Override
    public boolean tileInFrustum(Scene scene, float pitchSin, float pitchCos, float yawSin, float yawCos, int cameraX, int cameraY, int cameraZ, int plane, int msx, int msy)
    {
        return gpu.tileInFrustum(scene, pitchSin, pitchCos, yawSin, yawCos, cameraX, cameraY, cameraZ, plane, msx, msy);
    }

    @Override
    public void loadScene(WorldView worldView, Scene scene)
    {
        gpu.loadScene(worldView, scene);
    }

    @Override
    public void despawnWorldView(WorldView worldView)
    {
        gpu.despawnWorldView(worldView);
    }

    @Override
    public void preSceneDraw(Scene scene, float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw, int minLevel, int level, int maxLevel,
                             Set<Integer> hideRoofIds)
    {
        gpu.preSceneDraw(scene, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, maxLevel, hideRoofIds);
    }

    @Override
    public void drawDynamic(Projection projection, Scene scene, TileObject tileObject, Renderable renderable, Model model, int orientation, int x, int y, int z)
    {
        gpu.drawDynamic(seenFrom(projection, scene), scene, tileObject, renderable, model, orientation, x, y, z);
    }

    @Override
    public void drawDynamic(int renderThreadId, Projection projection, Scene scene, TileObject tileObject, Renderable renderable, Model model, int orientation,
                            int x, int y, int z)
    {
        gpu.drawDynamic(renderThreadId, seenFrom(projection, scene), scene, tileObject, renderable, model, orientation, x, y, z);
    }

    @Override
    public void drawTemp(Projection projection, Scene scene, GameObject gameObject, Model model, int orientation, int x, int y, int z)
    {
        Player player = client.getLocalPlayer();
        if (!firstPersonEyes() || scene.getWorldViewId() != WorldView.TOPLEVEL || player == null || gameObject.getRenderable() != player)
        {
            gpu.drawTemp(seenFrom(projection, scene), scene, gameObject, model, orientation, x, y, z);
            return;
        }

        // Stand the body at the view anchor so they move together
        int facing = ownBody.facing(player, orientation);
        int bodyX = (int) Math.round(placement.anchorX());
        int bodyZ = (int) Math.round(placement.anchorZ());
        ownBody.shape(player, model, facing, bodyX, y, bodyZ);
        try
        {
            gpu.drawTemp(seenFrom(projection, scene), scene, gameObject, model, facing, bodyX, y, bodyZ);
        }
        finally
        {
            ownBody.restore(model);
        }
    }

    /**
     * Gets the projection a top level model is culled and sorted with, the head's while the eyes are drawn.
     */
    private Projection seenFrom(Projection projection, Scene scene)
    {
        return drawingEyes && scene.getWorldViewId() == WorldView.TOPLEVEL ? headProjection : projection;
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
        gpu.invalidateZone(scene, zoneX, zoneZ);
    }

    /**
     * Releases the headset and GL objects. Call on the client thread. Safe to call more than once.
     */
    @Override
    public void close()
    {
        leaveEyes();
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
