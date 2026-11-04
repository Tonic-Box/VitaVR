package com.tonic.vitavr.render;

import com.tonic.Logger;
import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.OneEyeCamera;
import com.tonic.vitavr.config.RecordMode;
import com.tonic.vitavr.hud.HudPicture;
import com.tonic.vitavr.pointer.WorldMenu;
import com.tonic.vitavr.record.FramePacer;
import com.tonic.vitavr.record.SessionRecorder;
import com.tonic.vitavr.record.SpectatorView;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.Placement;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.timing.FrameTimes;
import com.tonic.vitavr.xr.HudPanel;
import com.tonic.vitavr.xr.RoomPanel;
import lombok.Getter;
import net.runelite.client.RuneLite;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Records the session to a 1920 by 1080 video from both eyes, the cropped left eye, or a steadied head camera, with the headset layers drawn in.
 */
final class Recording implements AutoCloseable
{
    private static final int MENU = 0;
    private static final int LABEL = 1;
    private static final int SETTINGS = 2;
    private static final int KEYBOARD = 3;
    private static final int FLOATING = 4;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final VitaVRConfig config;
    private final Layers layers;
    private final Placement placement;
    private final FrameTimes times;
    private Video video;
    private boolean tried;
    private boolean due;
    /** Whether the steadied camera is drawn as its own view this frame. */
    @Getter
    private boolean spectating;
    @Getter
    private float[] spectatorMatrix;
    private long lastPacedNanos;
    /** The finished recording, or null if none was made. */
    @Getter
    private Path recorded;
    /** Why the session was not recorded, or null if nothing went wrong. */
    @Getter
    private String problem;

    /**
     * Creates the recording.
     * @param config The plugin config.
     * @param layers The headset layers drawn into the recording.
     * @param placement The room's placement in the world.
     * @param times The frame times the recording's own time is added to.
     */
    Recording(VitaVRConfig config, Layers layers, Placement placement, FrameTimes times)
    {
        this.config = config;
        this.layers = layers;
        this.placement = placement;
        this.times = times;
    }

    /**
     * Starts the video once, if enabled and the interface size is known, in the current OpenGL context.
     */
    void start()
    {
        if (tried || !config.recordSession() || layers.getPicture().getWidth() == 0)
        {
            return;
        }
        tried = true;
        try
        {
            Path directory = RuneLite.RUNELITE_DIR.toPath().resolve("vitavr").resolve("recordings");
            Files.createDirectories(directory);
            Path file = directory.resolve("vr-" + LocalDateTime.now().format(STAMP) + ".mp4");
            video = Video.start(config, file, layers);
            Logger.info("[VitaVR] Recording the session to " + file);
        }
        catch (IOException e)
        {
            problem = "The session was not recorded: " + e.getMessage();
            Logger.warn("[VitaVR] " + problem);
        }
    }

    /**
     * Resets per-frame state at the start of a frame.
     */
    void frameBegun()
    {
        due = false;
        spectating = false;
    }

    /**
     * Gets whether the recording uses the steadied head camera.
     * @return True if steadied.
     */
    boolean isSteadied()
    {
        return video != null && video.steadied;
    }

    /**
     * Decides whether a frame is captured and updates the steadied camera. Called only on frames the scene is drawn, so loading screens are skipped.
     *
     * @param head The head pose, offset as the eyes are, for a steadied camera; else null.
     */
    void pace(Pose head)
    {
        long now = System.nanoTime();
        double seconds = lastPacedNanos == 0 ? 0 : (now - lastPacedNanos) / 1e9;
        lastPacedNanos = now;
        due = video != null && video.due(now);
        spectating = false;
        if (video == null || head == null || !video.steadied)
        {
            return;
        }
        float[] matrix = video.follow(head, seconds, placement);
        if (due)
        {
            spectatorMatrix = matrix;
            spectating = true;
        }
    }

    View getSpectatorView()
    {
        return video.spectatorView;
    }

    /**
     * Clears the steadied camera's picture to a colour.
     *
     * @param rgb The colour as 0xRRGGBB.
     */
    void clearSpectator(int rgb)
    {
        video.spectatorTarget.clear(rgb);
    }

    /**
     * Binds the steadied camera's picture for drawing.
     */
    void useSpectator()
    {
        video.spectatorTarget.use(0);
    }

    /**
     * Records the frame's picture when one is due.
     *
     * @param eyes The resolved eyes.
     * @param eyeMatrices Each eye's matrix.
     */
    void record(EyesTarget eyes, float[][] eyeMatrices)
    {
        if (!due)
        {
            return;
        }
        long from = System.nanoTime();
        Shown shown = shown();
        if (spectating)
        {
            video.recordSteadied(spectatorMatrix, shown);
        }
        else
        {
            video.recordEyes(eyes, eyeMatrices, shown);
        }
        times.add(FrameTimes.Part.RECORDING, System.nanoTime() - from);
    }

    /**
     * Gets the layers as recorded, leaving out the chat side when the settings hide it from recordings.
     */
    private Shown shown()
    {
        double[][][] strips = null;
        double[][] across = null;
        HudPanel panel = layers.getPanel();
        HudPicture picture = layers.getPicture();
        if (panel != null && panel.shown() && video.fitsInterfaces(picture.getWidth(), picture.getHeight()))
        {
            video.interfaces.upload(picture.getPixels());
            double[][][] all = panel.strips();
            double[][] allAcross = panel.stripsAcross();
            int[] kept = panel.stripsFrom(config.recordHideChat() ? panel.chatSideEnd() : 0);
            strips = new double[kept.length][][];
            across = new double[kept.length][];
            for (int strip = 0; strip < kept.length; strip++)
            {
                strips[strip] = inWorld(all[kept[strip]]);
                across[strip] = allAcross[kept[strip]];
            }
        }

        double[][][] floating = new double[FLOATING][][];
        WorldMenu menu = layers.getMenu();
        if (!layers.getMenuLayer().isCleared())
        {
            video.floating[MENU].upload(menu.getCrop().getPixels());
            floating[MENU] = inWorld(menu.getBoard());
        }
        if (!layers.getLabelLayer().isCleared())
        {
            video.floating[LABEL].upload(layers.getLabel().getPixels());
            floating[LABEL] = inWorld(layers.getLabelBoard());
        }
        floating[SETTINGS] = ownPanel(layers.getSettingsPanel(), SETTINGS);
        floating[KEYBOARD] = ownPanel(layers.getKeyboardPanel(), KEYBOARD);
        return new Shown(strips, across, floating);
    }

    private double[][] ownPanel(RoomPanel panel, int index)
    {
        if (!panel.isShown())
        {
            return null;
        }
        video.floating[index].upload(panel.getPanel().pixels());
        return inWorld(panel.getBoard());
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
            placement.toWorld(at[0], at[1], at[2], placed[corner]);
        }
        return placed;
    }

    /**
     * Ends the video, if any, in its OpenGL context. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (video == null)
        {
            return;
        }
        try
        {
            recorded = video.recorder.finish();
            long dropped = video.recorder.dropped();
            Logger.info("[VitaVR] Recorded the session to " + recorded + (dropped > 0 ? ", " + dropped + " frames dropped" : ""));
        }
        catch (IOException e)
        {
            problem = "The recording failed: " + e.getMessage();
            Logger.warn("[VitaVR] " + problem);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            problem = "The recording was interrupted";
        }
        finally
        {
            video.close();
            video = null;
            due = false;
            spectating = false;
        }
    }

    /**
     * The layers' world corners for a recorded picture, each top left, top right, bottom right, bottom left.
     */
    private static final class Shown
    {
        private final double[][][] strips;
        private final double[][] stripsAcross;
        private final double[][][] floating;

        private Shown(double[][][] strips, double[][] stripsAcross, double[][][] floating)
        {
            this.strips = strips;
            this.stripsAcross = stripsAcross;
            this.floating = floating;
        }
    }

    /**
     * A video in progress: its GPU pictures and the recorder they go to.
     */
    private static final class Video implements AutoCloseable
    {
        private static final double SPECTATOR_FOV = Math.toRadians(90);
        private static final int EYES = 2;

        private final RecordMode mode;
        private final boolean steadied;
        private final SessionRecorder recorder;
        private final FramePacer pacer = new FramePacer(SessionRecorder.FRAMES_PER_SECOND);
        private final EyesTarget picture;
        private final EyesTarget spectatorTarget;
        private final AtlasQuad interfaces;
        private final int interfacesWidth;
        private final int interfacesHeight;
        private final AtlasQuad[] floating;
        private final int[][] floatingSizes;
        private final SpectatorView spectator = new SpectatorView();
        private final View spectatorView = new View();
        private final Fov spectatorFov;
        private int times;
        private boolean closed;

        private Video(VitaVRConfig config, SessionRecorder recorder, int[] interfacesSize, int[][] floatingSizes)
        {
            this.mode = config.recordMode();
            this.steadied = mode == RecordMode.ONE_EYE && config.oneEyeCamera() == OneEyeCamera.STEADIED;
            this.recorder = recorder;
            this.picture = new EyesTarget(SessionRecorder.WIDTH, SessionRecorder.HEIGHT, 1, 1);
            this.spectatorTarget = steadied ? new EyesTarget(SessionRecorder.WIDTH, SessionRecorder.HEIGHT, config.samples(), 1) : null;
            this.interfaces = new AtlasQuad(interfacesSize[0], interfacesSize[1]);
            this.interfacesWidth = interfacesSize[0];
            this.interfacesHeight = interfacesSize[1];
            this.floatingSizes = floatingSizes;
            this.floating = new AtlasQuad[floatingSizes.length];
            for (int index = 0; index < floating.length; index++)
            {
                floating[index] = new AtlasQuad(floatingSizes[index][0], floatingSizes[index][1]);
            }
            double halfUp = Math.tan(SPECTATOR_FOV / 2) * SessionRecorder.HEIGHT / SessionRecorder.WIDTH;
            this.spectatorFov = new Fov(-SPECTATOR_FOV / 2, SPECTATOR_FOV / 2, Math.atan(halfUp), -Math.atan(halfUp));
        }

        /**
         * Starts a video in the current OpenGL context.
         */
        private static Video start(VitaVRConfig config, Path file, Layers layers) throws IOException
        {
            SessionRecorder recorder = SessionRecorder.start(file);
            try
            {
                HudPicture picture = layers.getPicture();
                RoomPanel settings = layers.getSettingsPanel();
                RoomPanel keyboard = layers.getKeyboardPanel();
                return new Video(config, recorder, new int[]{picture.getWidth(), picture.getHeight()}, new int[][]{{WorldMenu.WIDTH, WorldMenu.HEIGHT},
                        {Layers.LABEL_WIDTH, Layers.LABEL_HEIGHT}, {settings.getPanel().width(), settings.getPanel().height()},
                        {keyboard.getPanel().width(), keyboard.getPanel().height()}});
            }
            catch (RuntimeException failed)
            {
                recorder.close();
                throw failed;
            }
        }

        private boolean fitsInterfaces(int width, int height)
        {
            return width == interfacesWidth && height == interfacesHeight;
        }

        private boolean due(long nowNanos)
        {
            times = pacer.due(nowNanos);
            return times > 0;
        }

        private float[] follow(Pose head, double seconds, Placement placement)
        {
            placement.getRoom().view(spectator.follow(head, seconds), spectatorView);
            return spectatorView.worldToClip(spectatorFov);
        }

        private void recordEyes(EyesTarget eyes, float[][] eyeMatrices, Shown shown)
        {
            picture.clear(0);
            int width = eyes.getEyeWidth();
            int height = eyes.getEyeHeight();
            if (mode == RecordMode.BOTH_EYES)
            {
                for (int eye = 0; eye < EYES; eye++)
                {
                    int[] into = fit(width, height, eye * SessionRecorder.WIDTH / 2, SessionRecorder.WIDTH / 2, SessionRecorder.HEIGHT);
                    eyes.copyEye(eye, new int[]{0, 0, width, height}, picture.getFramebuffer(), into);
                    layersOver(eyeMatrices[eye], shown, into);
                }
            }
            else
            {
                int cropHeight = Math.min(height, width * SessionRecorder.HEIGHT / SessionRecorder.WIDTH);
                int cropWidth = cropHeight * SessionRecorder.WIDTH / SessionRecorder.HEIGHT;
                int cropX = (width - cropWidth) / 2;
                int cropY = (height - cropHeight) / 2;
                eyes.copyEye(0, new int[]{cropX, cropY, cropWidth, cropHeight}, picture.getFramebuffer(), new int[]{0, 0, SessionRecorder.WIDTH, SessionRecorder.HEIGHT});
                double scale = (double) SessionRecorder.WIDTH / cropWidth;
                layersOver(eyeMatrices[0], shown, new int[]{(int) Math.round(-cropX * scale), (int) Math.round(-cropY * scale), (int) Math.round(width * scale),
                        (int) Math.round(height * scale)});
            }
            recorder.capture(picture.getFramebuffer(), times);
        }

        private void recordSteadied(float[] worldToClip, Shown shown)
        {
            spectatorTarget.resolve();
            int[] whole = {0, 0, SessionRecorder.WIDTH, SessionRecorder.HEIGHT};
            spectatorTarget.copyEye(0, whole, picture.getFramebuffer(), whole);
            layersOver(worldToClip, shown, whole);
            recorder.capture(picture.getFramebuffer(), times);
        }

        private void layersOver(float[] worldToClip, Shown shown, int[] viewport)
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, picture.getFramebuffer());
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (shown.strips != null)
            {
                for (int strip = 0; strip < shown.strips.length; strip++)
                {
                    double[] across = shown.stripsAcross[strip];
                    int left = (int) Math.round(across[0] * interfacesWidth);
                    int right = (int) Math.round(across[1] * interfacesWidth);
                    interfaces.draw(worldToClip, shown.strips[strip], left, 0, right - left, interfacesHeight, 1);
                }
            }
            for (int index = 0; index < floating.length; index++)
            {
                if (shown.floating[index] != null)
                {
                    floating[index].draw(worldToClip, shown.floating[index], 0, 0, floatingSizes[index][0], floatingSizes[index][1], 1);
                }
            }
        }

        /**
         * Fits a picture's shape centred inside a box, returning x, y, width and height.
         */
        private static int[] fit(int width, int height, int boxX, int boxWidth, int boxHeight)
        {
            double scale = Math.min((double) boxWidth / width, (double) boxHeight / height);
            int fittedWidth = (int) Math.round(width * scale);
            int fittedHeight = (int) Math.round(height * scale);
            return new int[]{boxX + (boxWidth - fittedWidth) / 2, (boxHeight - fittedHeight) / 2, fittedWidth, fittedHeight};
        }

        /**
         * Releases the GL objects and stops ffmpeg if unfinished. Safe to call more than once.
         */
        @Override
        public void close()
        {
            if (closed)
            {
                return;
            }
            closed = true;
            recorder.close();
            picture.close();
            if (spectatorTarget != null)
            {
                spectatorTarget.close();
            }
            interfaces.close();
            for (AtlasQuad quad : floating)
            {
                quad.close();
            }
        }
    }
}
