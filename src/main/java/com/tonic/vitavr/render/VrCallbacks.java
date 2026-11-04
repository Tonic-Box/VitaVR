package com.tonic.vitavr.render;

import com.tonic.Logger;
import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.body.OwnBody;
import com.tonic.vitavr.body.TrackedHand;
import com.tonic.vitavr.config.ViewMode;
import com.tonic.vitavr.hud.CursorDot;
import com.tonic.vitavr.hud.CanvasKeys;
import com.tonic.vitavr.hud.HudPicture;
import com.tonic.vitavr.panels.AbstractPanel;
import com.tonic.vitavr.panels.KeyboardPanel;
import com.tonic.vitavr.panels.SettingsPanel;
import com.tonic.vitavr.hud.PointerLabel;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.feel.Haptics;
import com.tonic.vitavr.overlay.ActorOverlays;
import com.tonic.vitavr.space.ComfortMotion;
import com.tonic.vitavr.space.EyeClearance;
import com.tonic.vitavr.timing.FrameTimes;
import com.tonic.vitavr.wrist.WristDisplay;
import com.tonic.vitavr.wrist.WristPicture;
import com.tonic.vitavr.pointer.CameraDriver;
import com.tonic.vitavr.pointer.Pointer;
import com.tonic.vitavr.pointer.WorldMenu;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.Placement;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.xr.Controllers;
import com.tonic.vitavr.xr.EyeLayer;
import com.tonic.vitavr.xr.FloatingLayer;
import com.tonic.vitavr.xr.HudPanel;
import com.tonic.vitavr.xr.RoomPanel;
import com.tonic.vitavr.xr.Headset;
import lombok.Getter;
import net.runelite.client.RuneLite;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.Constants;
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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.gpu.GpuPluginConfig;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.WGL;
import org.lwjgl.system.MemoryStack;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Draws the game into the headset by wrapping the GPU plugin's draw callbacks and replaying each draw for both eyes.
 */
public final class VrCallbacks implements DrawCallbacks, AutoCloseable
{
    private static final int EYES = 2;
    private static final int ZONE_OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2 >> 3;
    private static final int ZONE_UNITS = 1024;
    /** How many tiles the table's edge fades over. */
    private static final int TABLE_FOG_TILES = 2;
    private static final int ACTIVE_BEAM = 0x40E0FFE0;
    private static final int RESTING_BEAM = 0x80A0B060;
    private static final double DOT_RADIANS = Math.toRadians(0.45);
    private static final int GHOST_HAND = 0x59FFFFFF;
    private static final double HAND_METRES = 0.06;
    private static final double STANDING_EYE_METRES = 1.65;
    private static final double USUAL_EYE_HEIGHT = 182;
    private static final double DOT_EDGE_RADIANS = Math.toRadians(0.65);
    private static final double CROSS_RADIANS = Math.toRadians(1.3);
    private static final int DOT = 0xFFFFFFFF;
    private static final int DOT_EDGE = 0xC0000000;
    /** The canvas width the chat and inventory side widths are measured against. */
    private static final int HUD_CANVAS_WIDTH = 1760;
    private static final int CHAT_SIDE_PIXELS = 520;
    private static final int INVENTORY_SIDE_PIXELS = 260;
    /** The angle one interface pixel covers at 100% size, in radians. */
    private static final double HUD_PIXEL_RADIANS = 0.00065;
    private static final int LABEL_WIDTH = 512;
    private static final int LABEL_HEIGHT = 96;
    /** The furthest the label stands from the head, in metres. */
    private static final double LABEL_METRES = 1.0;
    private static final double NEARER_THAN_WORLD = 0.95;
    /** The label's offset from the pointer end, in label pixels. */
    private static final double LABEL_RIGHT = -14;
    private static final double LABEL_BELOW = -16;
    private static final double LABEL_ENLARGED = 1.8;
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
    private static final double SETTINGS_METRES = 0.85;
    private static final double SETTINGS_BELOW_EYES = 0.12;
    private static final double KEYBOARD_METRES = 0.6;
    private static final double KEYBOARD_BELOW_EYES = 0.4;
    /** How often typing input is checked for, in milliseconds. */
    private static final long TYPING_CHECK_MILLIS = 200;
    /** How far ahead of and below the eyes the own head stack hangs in first person, in metres. */
    private static final double OWN_AHEAD_METRES = 0.7;
    private static final double OWN_BELOW_METRES = 0.3;
    /** How far below the own head stack the own hitsplats hang, in metres. */
    private static final double OWN_SPLATS_BELOW_METRES = 0.12;
    /** How near a zone centre must be to the pointer for the probe to draw it, in game units. */
    private static final double PROBE_ZONE_REACH = 1024;
    private static final int TOP_LEVEL = 3;
    private static final int NO_CEILINGS = -1;
    private static final int CEILING_ZONES = 3;
    private static final DateTimeFormatter RECORDING_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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
    private FloatingLayer menuLayer;
    private FloatingLayer labelLayer;
    private RoomPanel settingsPanel;
    private RoomPanel keyboardPanel;
    private List<RoomPanel> ownPanels = Collections.emptyList();
    private long nextTypingCheck;
    private boolean typingWanted;
    private boolean keyboardOpenedForTyping;
    private final PointerLabel label = new PointerLabel(LABEL_WIDTH, LABEL_HEIGHT);
    private final Billboard labelBoard = new Billboard();
    private Recording recording;
    private boolean recordingTried;
    private int ceilingsFrom = NO_CEILINGS;
    private int ceilingsMinLevel;
    private boolean recordingDue;
    private long lastPacedNanos;
    private double frameBodyScale = 1;
    private boolean spectating;
    private float[] spectatorMatrix;
    /** The finished recording, or null if none was made. */
    @Getter
    private Path recorded;
    /** Why the session was not recorded, or null if nothing went wrong. */
    @Getter
    private String recordingProblem;
    private final ActorOverlays overlays;
    private final ComfortMotion comfort = new ComfortMotion();
    private final EyeClearance clearance;
    private final Haptics haptics;
    private ComfortShade shade;
    private final WristDisplay wrist = new WristDisplay();
    private AtlasQuad wristPicture;
    private double wallDark;
    private final FrameTimes times = new FrameTimes(TIMES_STRETCH_NANOS);
    private long sceneFrom;
    private AtlasQuad atlas;
    private HudPanel panel;
    private final HudPicture picture = new HudPicture();
    private final CursorDot cursor = new CursorDot();
    private String hudShape;
    private final double[] pointerHit = new double[3];
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
        recordingDue = false;
        spectating = false;
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
        if (panel != null)
        {
            if (controllers.hudPressed())
            {
                panel.show(!panel.shown());
            }
            panel.standBefore(rawHead());
        }
        openPanels();
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
            boolean jumped = placement.place(headset, config, at.getX(), ground, at.getY(), ownBody.getEyeHeight(), shift, seconds);
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
        double bodyEyeHeight = Double.isNaN(ownBody.getEyeHeight()) ? USUAL_EYE_HEIGHT : ownBody.getEyeHeight();
        double bodyScale = bodyEyeHeight / STANDING_EYE_METRES / unitsPerMetre;
        ownBody.look(config.heldItems(), config.heldItemPercent(), config.fadeNearEyes(), config.hideOwnHead());
        ownBody.frame(centre.yaw(), head, trackedHand(Controllers.RIGHT, head, bodyScale), trackedHand(Controllers.LEFT, head, bodyScale), seconds);
        frameBodyScale = bodyScale;
        cameraDriver.face(client, placement.getCentre().yaw(), System.currentTimeMillis());
        pointerMet = probe.hit(pointerHit);
        wrist.place(controllers.aim(Controllers.LEFT), rawHead(), seconds);
        rideWrist();
        pointer.update(controllers, placement.getRoom(), placement.getLift(), pointerMet ? pointerHit : null, panel, picture, ownPanels, wrist);
        probe.aim(pointer.view());
        placed = true;
    }

    /**
     * Opens and closes the settings and keyboard panels; the keyboard also opens while the game wants typing.
     */
    private void openPanels()
    {
        double pixelAtOneMetre = HUD_PIXEL_RADIANS * config.hudSizePercent() / 100.0;
        if (controllers.settingsPressed() || ((SettingsPanel) settingsPanel.getPanel()).takeCloseAsked())
        {
            toggle(settingsPanel, pixelAtOneMetre);
        }
        boolean wasWanted = typingWanted;
        long now = System.currentTimeMillis();
        if (now >= nextTypingCheck)
        {
            nextTypingCheck = now + TYPING_CHECK_MILLIS;
            Widget input = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
            typingWanted = client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0 && input != null && !input.isHidden();
        }
        if (controllers.keyboardPressed() || ((KeyboardPanel) keyboardPanel.getPanel()).takeHideAsked())
        {
            keyboardOpenedForTyping = false;
            toggle(keyboardPanel, pixelAtOneMetre);
        }
        else if (typingWanted && !wasWanted && !keyboardPanel.isShown())
        {
            keyboardOpenedForTyping = true;
            keyboardPanel.open(rawHead(), pixelAtOneMetre);
        }
        else if (!typingWanted && wasWanted && keyboardOpenedForTyping)
        {
            keyboardOpenedForTyping = false;
            keyboardPanel.hide();
        }
    }

    private void toggle(RoomPanel own, double pixelAtOneMetre)
    {
        if (own.isShown())
        {
            own.hide();
        }
        else
        {
            own.open(rawHead(), pixelAtOneMetre);
        }
    }

    /**
     * Marks the settings panel for repaint. Safe on any thread.
     */
    public void settingsChanged()
    {
        RoomPanel shown = settingsPanel;
        if (shown != null)
        {
            ((SettingsPanel) shown.getPanel()).settingsChanged();
        }
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
            PointerBuffer layers = null;
            if (eyesDrawn)
            {
                // The world first, then the interfaces and the floating layers
                long[] hud = panel == null ? new long[0] : panel.layers();
                layers = stack.mallocPointer(5 + hud.length);
                layers.put(eyeLayer.address());
                for (long column : hud)
                {
                    layers.put(column);
                }
                layers.put(menuLayer.address());
                layers.put(labelLayer.address());
                layers.put(settingsPanel.address());
                layers.put(keyboardPanel.address());
                layers.flip();
            }
            headset.endFrame(layers);
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
        if (spectating)
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
        double left = (zoneX - ZONE_OFFSET) * ZONE_UNITS;
        double bottom = (zoneZ - ZONE_OFFSET) * ZONE_UNITS;
        double dx = Math.max(0, Math.max(left - placement.anchorX(), placement.anchorX() - (left + ZONE_UNITS)));
        double dz = Math.max(0, Math.max(bottom - placement.anchorZ(), placement.anchorZ() - (bottom + ZONE_UNITS)));
        return dx * dx + dz * dz > tableReach * tableReach;
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
        if (spectating)
        {
            useSpectator();
            draw.run();
        }
    }

    private void useSpectator()
    {
        recording.useSpectator();
        GL20.glUniformMatrix4fv(worldProj, false, spectatorMatrix);
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
        double[] forward = new double[3];
        aimed.forward(forward);
        double level = Math.hypot(forward[0], forward[2]);
        double dx = (zoneX - ZONE_OFFSET + 0.5) * ZONE_UNITS - aimed.eyeX();
        double dz = (zoneZ - ZONE_OFFSET + 0.5) * ZONE_UNITS - aimed.eyeZ();
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
        double[] from = new double[3];
        double[] to = new double[3];
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
        double[] crossAt = new double[3];
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
        showInterfacesTimed();
        times.add(FrameTimes.Part.INTERFACES, System.nanoTime() - from);
    }

    private void showInterfacesTimed()
    {
        BufferProvider buffer = client.getBufferProvider();
        picture.copy(buffer);
        readyPanel(picture.getWidth(), picture.getHeight());
        startRecording();

        WorldMenu menu = pointer.getMenu();
        if (client.isMenuOpen() && menu.lift(picture.getPixels(), picture.getWidth(), picture.getHeight(), client.getMenuX(), client.getMenuY(),
                client.getMenuWidth(), client.getMenuHeight(), rawHead()))
        {
            picture.clear(client.getMenuX(), client.getMenuY(), client.getMenuWidth(), client.getMenuHeight());
            int[] onMenu = pointer.getMenuCursor();
            if (onMenu != null)
            {
                cursor.paint(menu.getCrop().getPixels(), WorldMenu.WIDTH, WorldMenu.HEIGHT, onMenu[0], onMenu[1]);
            }
            menuLayer.upload(menu.getCrop().getPixels());
            menuLayer.place(menu.getBoard());
        }
        else
        {
            menuLayer.clear();
        }

        showLabel();
        paintWrist();
        settingsPanel.refresh();
        keyboardPanel.refresh();

        // Paint the dot for the upload only, so pointer hit tests never see it
        int[] onPanel = pointer.getPanelCursor();
        if (onPanel != null)
        {
            cursor.paint(picture.getPixels(), picture.getWidth(), picture.getHeight(), onPanel[0], onPanel[1]);
        }
        panel.upload(picture.getPixels());
        if (onPanel != null)
        {
            cursor.restore(picture.getPixels());
        }
    }

    /**
     * Places the action label beside the pointer end while it is on the world with no menu open.
     */
    private void showLabel()
    {
        boolean changed = label.update(client.getMenu().getMenuEntries());
        if (!config.pointerLabel() || !pointer.isOnWorld() || !pointerMet || client.isMenuOpen() || label.isEmpty())
        {
            labelLayer.clear();
            return;
        }
        double[] at = new double[3];
        placement.getRoom().room(pointerHit[0], pointerHit[1], pointerHit[2], at);
        at[1] -= placement.getLift();
        Pose head = rawHead();
        double dx = at[0] - head.x();
        double dy = at[1] - head.y();
        double dz = at[2] - head.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6)
        {
            labelLayer.clear();
            return;
        }
        double out = Math.min(LABEL_METRES, distance * NEARER_THAN_WORLD);
        double scale = out / distance;
        double metresPerPixel = HUD_PIXEL_RADIANS * config.hudSizePercent() / 100.0 * LABEL_ENLARGED * out;
        labelBoard.stand(head.x() + dx * scale, head.y() + dy * scale, head.z() + dz * scale, head, LABEL_WIDTH, LABEL_HEIGHT, metresPerPixel, LABEL_RIGHT,
                LABEL_BELOW);
        labelLayer.place(labelBoard);
        if (changed || labelLayer.isCleared())
        {
            labelLayer.upload(label.getPixels());
        }
    }

    /**
     * Draws the frame's depth-tested tile outlines into the bound view.
     */
    private void drawTiles(float[] matrix)
    {
        int plane = client.getTopLevelWorldView().getPlane();
        if (config.outlineHoveredTile() && pointer.isOnWorld())
        {
            Tile hovered = client.getSelectedSceneTile();
            if (hovered != null)
            {
                outline(matrix, hovered.getLocalLocation(), plane, HOVERED_TILE);
            }
        }
        Player player = client.getLocalPlayer();
        if (config.outlineLocalTile() && player != null)
        {
            LocalPoint at = player.getLocalLocation();
            if (at != null && at.getWorldView() == WorldView.TOPLEVEL)
            {
                outline(matrix, new LocalPoint(at.getX() & ~127 | 64, at.getY() & ~127 | 64, client.getTopLevelWorldView()), plane, OWN_TILE);
            }
        }
        if (config.outlineDestinationTile())
        {
            outline(matrix, client.getLocalDestinationLocation(), plane, DESTINATION_TILE);
        }
    }

    /**
     * Draws a tile's outline along the ground.
     */
    private void outline(float[] matrix, LocalPoint centre, int plane, int rgba)
    {
        if (centre == null || !centre.isInScene())
        {
            return;
        }
        int left = centre.getX() & ~127;
        int bottom = centre.getY() & ~127;
        int[][] corners = {{left, bottom}, {left + 128, bottom}, {left + 128, bottom + 128}, {left, bottom + 128}};
        int colour = (rgba >> 16 & 0xFF) << 24 | (rgba >> 8 & 0xFF) << 16 | (rgba & 0xFF) << 8 | rgba >>> 24;
        double[] from = new double[3];
        double[] to = new double[3];
        for (int edge = 0; edge < 4; edge++)
        {
            int[] a = corners[edge];
            int[] b = corners[(edge + 1) % 4];
            for (int step = 0; step < OUTLINE_STEPS; step++)
            {
                ground(a[0] + (b[0] - a[0]) * step / OUTLINE_STEPS, a[1] + (b[1] - a[1]) * step / OUTLINE_STEPS, plane, from);
                ground(a[0] + (b[0] - a[0]) * (step + 1) / OUTLINE_STEPS, a[1] + (b[1] - a[1]) * (step + 1) / OUTLINE_STEPS, plane, to);
                beams.draw(matrix, from, to, colour);
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
     * Creates the interface panel on first use or canvas resize, and reshapes it when settings change.
     */
    private void readyPanel(int width, int height)
    {
        if (panel != null && (panel.getPixelWidth() != width || panel.getPixelHeight() != height))
        {
            panel.close();
            panel = null;
        }
        if (panel == null)
        {
            int chat = CHAT_SIDE_PIXELS * Math.min(width, HUD_CANVAS_WIDTH) / HUD_CANVAS_WIDTH;
            int inventory = INVENTORY_SIDE_PIXELS * Math.min(width, HUD_CANVAS_WIDTH) / HUD_CANVAS_WIDTH;
            panel = new HudPanel(headset.getSession(), headset.getRoom(), width, height, chat, inventory, headset.isCylinders());
            hudShape = null;
        }
        String shape = config.hudDistance() + "/" + config.hudSizePercent() + "/" + config.chatSidePercent() + "/" + config.inventorySidePercent() + "/"
                + config.sidesApartDegrees();
        if (!shape.equals(hudShape))
        {
            hudShape = shape;
            panel.shape(config.hudDistance(), HUD_PIXEL_RADIANS * config.hudSizePercent() / 100.0, config.chatSidePercent() / 100.0,
                    config.inventorySidePercent() / 100.0, Math.toRadians(config.sidesApartDegrees()));
        }
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
            double[] at = handAt(hand);
            if (at == null)
            {
                continue;
            }
            double away = Math.sqrt(square(at[0] - view.eyeX()) + square(at[1] - view.eyeY()) + square(at[2] - view.eyeZ()));
            markers.draw(view, matrix, at, Math.atan2(HAND_METRES * placement.getRoom().getUnitsPerMetre() / 2, Math.max(1, away)), Markers.DOT, GHOST_HAND);
        }
    }

    /**
     * Gets where a controller is in the world, or null while it is not tracked.
     */
    private double[] handAt(int hand)
    {
        Pose held = controllers.aim(hand);
        if (held == null)
        {
            return null;
        }
        double[] at = new double[3];
        placement.getRoom().point(held.x(), held.y() + placement.getLift(), held.z(), at);
        return at;
    }

    /**
     * Gets a controller's pose for the body's hand, offset from the head at body scale, or null if untracked.
     */
    private TrackedHand trackedHand(int hand, double[] head, double bodyScale)
    {
        Pose held = controllers.aim(hand);
        double[] at = handAt(hand);
        if (held == null || at == null)
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
        double[] direction = new double[3];
        pose.rotate(x, y, z, direction);
        double[] from = new double[3];
        double[] to = new double[3];
        placement.getRoom().point(pose.x(), pose.y(), pose.z(), from);
        placement.getRoom().point(pose.x() + direction[0], pose.y() + direction[1], pose.z() + direction[2], to);
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        double length = Math.max(1e-9, Math.sqrt(dx * dx + dy * dy + dz * dz));
        return new double[]{dx / length, dy / length, dz / length};
    }

    private static double square(double value)
    {
        return value * value;
    }

    /**
     * Gets the head pose in room space, between the eyes.
     */
    private Pose rawHead()
    {
        Pose left = headset.eyePose(0);
        Pose right = headset.eyePose(1);
        return new Pose((left.x() + right.x()) / 2, (left.y() + right.y()) / 2, (left.z() + right.z()) / 2, left.qx(), left.qy(), left.qz(), left.qw());
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
        double[] right = new double[3];
        view.right(right);
        double level = Math.hypot(right[0], right[2]);
        if (level < 1e-6)
        {
            return;
        }
        double[] at = {view.eyeX(), view.eyeY(), view.eyeZ()};
        for (ActorOverlays.Cell cell : overlays.getCells())
        {
            atlas.draw(matrix, ActorOverlays.corners(cell, at, right[0] / level, right[2] / level), cell.getLeft(), cell.getTop(), cell.getWidth(),
                    cell.getHeight(), 1);
        }
    }

    /**
     * Draws the left wrist plate into one eye, faded by how far the wrist faces the eyes.
     */
    private void drawWrist(float[] matrix)
    {
        double[][] corners = new double[4][3];
        if (!wrist.corners(placement.getRoom(), placement.getLift(), corners))
        {
            return;
        }
        wristPicture.draw(matrix, corners, 0, 0, WristPicture.WIDTH, WristPicture.HEIGHT, (float) wrist.getShown());
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
        double[] alongEnd = new double[3];
        double[] outEnd = new double[3];
        double[] point = wristAt[0];
        room.room(point[0], point[1], point[2], at);
        room.room(point[0] + wristAt[1][0], point[1] + wristAt[1][1], point[2] + wristAt[1][2], alongEnd);
        room.room(point[0] + wristAt[2][0], point[1] + wristAt[2][1], point[2] + wristAt[2][2], outEnd);
        double[] along = unit(alongEnd[0] - at[0], alongEnd[1] - at[1], alongEnd[2] - at[2]);
        double[] out = unit(outEnd[0] - at[0], outEnd[1] - at[1], outEnd[2] - at[2]);
        at[1] -= placement.getLift();
        wrist.placeOn(at, along, out);
    }

    private static double[] unit(double x, double y, double z)
    {
        double length = Math.max(1e-9, Math.sqrt(x * x + y * y + z * z));
        return new double[]{x / length, y / length, z / length};
    }

    /**
     * Repaints the wrist picture from the interfaces while it is in view.
     */
    private void paintWrist()
    {
        if (wrist.getShown() <= 0)
        {
            return;
        }
        int[] pixels = wrist.paint(picture.getPixels(), picture.getWidth(), picture.getHeight(), this::canvasBox, LocalTime.now());
        int[] onWrist = pointer.getWristCursor();
        if (onWrist != null)
        {
            cursor.paint(pixels, WristPicture.WIDTH, WristPicture.HEIGHT, onWrist[0], onWrist[1]);
        }
        wristPicture.upload(pixels);
    }

    /**
     * Gets a component's box on the interfaces, or null if not shown.
     */
    private int[] canvasBox(int component)
    {
        Widget widget = client.getWidget(component);
        if (widget == null || widget.isHidden())
        {
            return null;
        }
        Rectangle bounds = widget.getBounds();
        return bounds == null || bounds.width <= 0 ? null : new int[]{bounds.x, bounds.y, bounds.width, bounds.height};
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
     * Starts the recording once the interfaces' size is known, if the settings ask for one.
     */
    private void startRecording()
    {
        if (recordingTried || !config.recordSession())
        {
            return;
        }
        recordingTried = true;
        try
        {
            Path directory = RuneLite.RUNELITE_DIR.toPath().resolve("vitavr").resolve("recordings");
            Files.createDirectories(directory);
            Path file = directory.resolve("vr-" + LocalDateTime.now().format(RECORDING_STAMP) + ".mp4");
            AbstractPanel settings = settingsPanel.getPanel();
            AbstractPanel keyboard = keyboardPanel.getPanel();
            recording = Recording.start(config.recordMode(), config.oneEyeCamera(), file, config.samples(), new int[]{picture.getWidth(), picture.getHeight()},
                    new int[][]{{WorldMenu.WIDTH, WorldMenu.HEIGHT}, {LABEL_WIDTH, LABEL_HEIGHT}, {settings.width(), settings.height()}, {keyboard.width(), keyboard.height()}});
            Logger.info("[VitaVR] Recording the session to " + file);
        }
        catch (IOException e)
        {
            recordingProblem = "The session was not recorded: " + e.getMessage();
            Logger.warn("[VitaVR] " + recordingProblem);
        }
    }

    /**
     * Notes whether a picture is taken this frame, and keeps a steadied camera following the head. Called only on frames
     * the scene is drawn.
     */
    private void paceRecording()
    {
        long now = System.nanoTime();
        double seconds = lastPacedNanos == 0 ? 0 : (now - lastPacedNanos) / 1e9;
        lastPacedNanos = now;
        recordingDue = recording != null && recording.due(now);
        spectating = false;
        if (recording == null || !recording.isSteadied())
        {
            return;
        }
        Pose head = rawHead();
        head = new Pose(head.x(), head.y() + placement.getLift(), head.z(), head.qx(), head.qy(), head.qz(), head.qw());
        if (!tabletop)
        {
            head = clearedHead(head, config.headClearanceCm() / 100.0 / frameBodyScale);
        }
        float[] matrix = recording.follow(head, seconds, placement.getRoom());
        if (recordingDue)
        {
            spectatorMatrix = matrix;
            spectating = true;
        }
    }

    /**
     * Gets a head moved forward along its level facing, in metres.
     */
    private static Pose clearedHead(Pose head, double metres)
    {
        double[] forward = new double[3];
        head.rotate(0, 0, -1, forward);
        double level = Math.hypot(forward[0], forward[2]);
        if (level < 1e-6)
        {
            return head;
        }
        return new Pose(head.x() + forward[0] / level * metres, head.y(), head.z() + forward[2] / level * metres, head.qx(), head.qy(), head.qz(), head.qw());
    }

    /**
     * Records the frame's picture when one is due.
     */
    private void record()
    {
        if (!recordingDue)
        {
            return;
        }
        long from = System.nanoTime();
        Recording.Layers layers = recordedLayers();
        if (spectating)
        {
            recording.recordSteadied(spectatorMatrix, layers);
        }
        else
        {
            recording.recordEyes(eyes, eyeMatrices, layers);
        }
        times.add(FrameTimes.Part.RECORDING, System.nanoTime() - from);
    }

    /**
     * Gets the layers as recorded, leaving out the chat side when the settings hide it from recordings.
     */
    private Recording.Layers recordedLayers()
    {
        double[][][] strips = null;
        double[][] across = null;
        if (panel != null && panel.shown() && recording.fitsInterfaces(picture.getWidth(), picture.getHeight()))
        {
            recording.interfaces(picture.getPixels());
            double[][][] all = panel.strips();
            double[][] allAcross = panel.stripsAcross();
            double from = config.recordHideChat() ? panel.chatSideEnd() - 1e-9 : -1;
            int kept = 0;
            for (double[] strip : allAcross)
            {
                kept += strip[0] >= from ? 1 : 0;
            }
            strips = new double[kept][][];
            across = new double[kept][];
            int at = 0;
            for (int strip = 0; strip < all.length; strip++)
            {
                if (allAcross[strip][0] >= from)
                {
                    strips[at] = inWorld(all[strip]);
                    across[at] = allAcross[strip];
                    at++;
                }
            }
        }

        double[][][] floating = new double[4][][];
        WorldMenu menu = pointer.getMenu();
        if (!menuLayer.isCleared())
        {
            recording.floating(Recording.MENU, menu.getCrop().getPixels());
            floating[Recording.MENU] = inWorld(menu.getBoard());
        }
        if (!labelLayer.isCleared())
        {
            recording.floating(Recording.LABEL, label.getPixels());
            floating[Recording.LABEL] = inWorld(labelBoard);
        }
        if (settingsPanel.isShown())
        {
            recording.floating(Recording.SETTINGS, settingsPanel.getPanel().pixels());
            floating[Recording.SETTINGS] = inWorld(settingsPanel.getBoard());
        }
        if (keyboardPanel.isShown())
        {
            recording.floating(Recording.KEYBOARD, keyboardPanel.getPanel().pixels());
            floating[Recording.KEYBOARD] = inWorld(keyboardPanel.getBoard());
        }
        return new Recording.Layers(strips, across, floating);
    }

    /**
     * Gets a billboard's corners in the world, offset as the eyes are.
     */
    private double[][] inWorld(Billboard board)
    {
        double[][] corners = new double[4][3];
        board.corners(corners);
        return inWorld(corners);
    }

    /**
     * Gets room corners in the world, offset as the eyes are.
     */
    private double[][] inWorld(double[][] roomCorners)
    {
        double[][] placed = new double[roomCorners.length][3];
        for (int corner = 0; corner < roomCorners.length; corner++)
        {
            double[] at = roomCorners[corner];
            placement.getRoom().point(at[0], at[1] + placement.getLift(), at[2], placed[corner]);
        }
        return placed;
    }

    /**
     * Ends the recording, if there is one, while its context is current.
     */
    private void finishRecording()
    {
        if (recording == null)
        {
            return;
        }
        try
        {
            recorded = recording.finish();
            Logger.info("[VitaVR] Recorded the session to " + recorded + (recording.dropped() > 0 ? ", " + recording.dropped() + " frames dropped" : ""));
        }
        catch (IOException e)
        {
            recordingProblem = "The recording failed: " + e.getMessage();
            Logger.warn("[VitaVR] " + recordingProblem);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            recordingProblem = "The recording was interrupted";
        }
        finally
        {
            recording.close();
            recording = null;
            spectating = false;
            recordingDue = false;
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
        rideWrist();
        for (int eye = 0; eye < EYES; eye++)
        {
            eyes.use(eye);
            drawOver(placement.eye(eye), eyeMatrices[eye]);
        }
        if (spectating)
        {
            recording.useSpectator();
            drawOver(recording.getSpectatorView(), spectatorMatrix);
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
        record();

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
            menuLayer = new FloatingLayer(headset, WorldMenu.WIDTH, WorldMenu.HEIGHT);
            labelLayer = new FloatingLayer(headset, LABEL_WIDTH, LABEL_HEIGHT);
            settingsPanel = new RoomPanel(headset, new SettingsPanel(config, configManager), SETTINGS_METRES, SETTINGS_BELOW_EYES);
            keyboardPanel = new RoomPanel(headset, new KeyboardPanel(keys), KEYBOARD_METRES, KEYBOARD_BELOW_EYES);
            ownPanels = Arrays.asList(settingsPanel, keyboardPanel);
            atlas = new AtlasQuad(ActorOverlays.ATLAS_WIDTH, ActorOverlays.ATLAS_HEIGHT);
            shade = new ComfortShade();
            wristPicture = new AtlasQuad(WristPicture.WIDTH, WristPicture.HEIGHT);
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

    /**
     * Gets whether a top level zone is within three zones of the player.
     */
    private boolean underCeiling(Scene scene, int zoneX, int zoneZ)
    {
        if (ceilingsFrom == NO_CEILINGS || scene.getWorldViewId() != WorldView.TOPLEVEL)
        {
            return false;
        }
        int playerZoneX = (int) Math.floor(placement.anchorX() / ZONE_UNITS) + ZONE_OFFSET;
        int playerZoneZ = (int) Math.floor(placement.anchorZ() / ZONE_UNITS) + ZONE_OFFSET;
        return Math.abs(zoneX - playerZoneX) <= CEILING_ZONES && Math.abs(zoneZ - playerZoneZ) <= CEILING_ZONES;
    }

    /**
     * Draws the undersides of a zone's levels above the player, dimmed, as ceilings.
     */
    private void drawCeilings(Projection projection, Scene scene, int zoneX, int zoneZ)
    {
        float light = config.ceilingLightPercent() / 100f;
        internals.levels(scene, ceilingsFrom, TOP_LEVEL);
        GL11.glCullFace(GL11.GL_FRONT);
        GL14.glBlendColor(light, light, light, 1f);
        GL14.glBlendFuncSeparate(GL14.GL_CONSTANT_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE);
        try
        {
            forEachView(() -> gpu.drawZoneOpaque(projection, scene, zoneX, zoneZ), nearPointer(scene, zoneX, zoneZ));
        }
        finally
        {
            GL11.glCullFace(GL11.GL_BACK);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
            internals.levels(scene, ceilingsMinLevel, TOP_LEVEL);
        }
    }

    /**
     * Gets whether the floors and roofs above are kept.
     */
    private boolean keepRoofs()
    {
        return config.viewMode() == ViewMode.FIRST_PERSON && config.keepRoofsIndoors() && client.getVarbitValue(VarbitID.OPTION_HIDE_ROOFTOPS) == 0;
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
        boolean keep = toplevel && keepRoofs();
        if (toplevel)
        {
            ceilingsFrom = keep && level < TOP_LEVEL ? level + 1 : NO_CEILINGS;
            ceilingsMinLevel = minLevel;
        }
        Set<Integer> roofs = keep ? Collections.emptySet() : hideRoofIds;
        gpu.preSceneDraw(scene, entityProjection, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, keep ? TOP_LEVEL : maxLevel, roofs);
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
                paceRecording();
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
                forEachView(() -> gpu.drawZoneOpaque(projection, scene, zoneX, zoneZ), nearPointer(scene, zoneX, zoneZ));
                if (underCeiling(scene, zoneX, zoneZ))
                {
                    safely(() -> drawCeilings(projection, scene, zoneX, zoneZ));
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
            safely(() ->
            {
                useEye(1);
                internals.drawZoneAlphaAgain(scene, level, zoneX, zoneZ);
                if (spectating)
                {
                    useSpectator();
                    internals.drawZoneAlphaAgain(scene, level, zoneX, zoneZ);
                }
            });
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
        finishRecording();
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
        if (menuLayer != null)
        {
            menuLayer.close();
            menuLayer = null;
        }
        keys.releaseAll();
        ownPanels = Collections.emptyList();
        if (settingsPanel != null)
        {
            settingsPanel.close();
            settingsPanel = null;
        }
        if (keyboardPanel != null)
        {
            keyboardPanel.close();
            keyboardPanel = null;
        }
        if (labelLayer != null)
        {
            labelLayer.close();
            labelLayer = null;
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
        if (wristPicture != null)
        {
            wristPicture.close();
            wristPicture = null;
        }
        overlays.clear();
        if (panel != null)
        {
            panel.close();
            panel = null;
        }
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
