package com.tonic.vitavr.render.recording;

import com.tonic.Logger;
import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.headset.layers.HudPanel;
import com.tonic.vitavr.headset.layers.RoomPanel;
import com.tonic.vitavr.interfaces.hud.HudPicture;
import com.tonic.vitavr.interfaces.hud.HudPiece;
import com.tonic.vitavr.interfaces.hud.Layers;
import com.tonic.vitavr.interfaces.pointer.WorldMenu;
import com.tonic.vitavr.render.EyesTarget;
import com.tonic.vitavr.render.FrameTimes;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.space.placement.Placement;
import lombok.Getter;
import net.runelite.client.RuneLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Records the session to a 1920 by 1080 video of the composite picture: both eyes, the cropped left eye, or a steadied head camera, with the headset layers drawn in.
 */
public final class Recording implements AutoCloseable
{
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final VitaVRConfig config;
    private final Layers layers;
    private final Placement placement;
    private final FrameTimes times;
    private final FramePacer recordPacer = new FramePacer(SessionRecorder.FRAMES_PER_SECOND);
    private Composite composite;
    private SessionRecorder recorder;
    private boolean tried;
    private int recordTimes;
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
    public Recording(VitaVRConfig config, Layers layers, Placement placement, FrameTimes times)
    {
        this.config = config;
        this.layers = layers;
        this.placement = placement;
        this.times = times;
    }

    /**
     * Starts the video once, if enabled and the interface size is known, in the current OpenGL context.
     */
    public void start()
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
            recorder = SessionRecorder.start(file);
            composite = Composite.create(config, layers);
            Logger.info("[VitaVR] Recording the session to " + file);
        }
        catch (IOException e)
        {
            problem = "The session was not recorded: " + e.getMessage();
            Logger.warn("[VitaVR] " + problem);
        }
        catch (RuntimeException failed)
        {
            if (recorder != null)
            {
                recorder.close();
                recorder = null;
            }
            throw failed;
        }
    }

    /**
     * Resets per-frame state at the start of a frame.
     */
    public void frameBegun()
    {
        due = false;
        spectating = false;
    }

    /**
     * Gets whether the composite picture uses the steadied head camera.
     * @return True if steadied.
     */
    public boolean isSteadied()
    {
        return composite != null && composite.isSteadied();
    }

    /**
     * Decides whether a frame is captured and updates the steadied camera. Called only on frames the scene is drawn, so loading screens are skipped.
     *
     * @param head The head pose, offset as the eyes are, for a steadied camera; else null.
     */
    public void pace(Pose head)
    {
        long now = System.nanoTime();
        double seconds = lastPacedNanos == 0 ? 0 : (now - lastPacedNanos) / 1e9;
        lastPacedNanos = now;
        recordTimes = recorder != null ? recordPacer.due(now) : 0;
        due = recordTimes > 0;
        spectating = false;
        if (composite == null || head == null || !composite.isSteadied())
        {
            return;
        }
        float[] matrix = composite.follow(head, seconds, placement);
        if (due)
        {
            spectatorMatrix = matrix;
            spectating = true;
        }
    }

    public View getSpectatorView()
    {
        return composite.getSpectatorView();
    }

    /**
     * Clears the steadied camera's picture to a colour.
     *
     * @param rgb The colour as 0xRRGGBB.
     */
    public void clearSpectator(int rgb)
    {
        composite.getSpectatorTarget().clear(rgb);
    }

    /**
     * @return The steadied camera's picture, for renderers that draw into it directly.
     */
    public EyesTarget getSpectatorTarget()
    {
        return composite.getSpectatorTarget();
    }

    /**
     * Binds the steadied camera's picture for drawing.
     */
    public void useSpectator()
    {
        composite.getSpectatorTarget().use(0);
    }

    /**
     * Builds the composite picture and hands it on when a frame is due.
     *
     * @param eyes The resolved eyes.
     * @param eyeMatrices Each eye's matrix.
     */
    public void record(EyesTarget eyes, float[][] eyeMatrices)
    {
        if (!due)
        {
            return;
        }
        long from = System.nanoTime();
        Composite.Shown shown = shown();
        int picture = spectating ? composite.drawSteadied(spectatorMatrix, shown) : composite.drawEyes(eyes, eyeMatrices, shown);
        if (recordTimes > 0)
        {
            recorder.capture(picture, recordTimes);
        }
        times.add(FrameTimes.Part.RECORDING, System.nanoTime() - from);
    }

    /**
     * Gets the layers as shown in the picture, leaving out the chat side when the settings hide it.
     */
    private Composite.Shown shown()
    {
        double[][][] strips = null;
        double[][] across = null;
        double[][][] pieceCorners = new double[0][][];
        int[][] pieceBoxes = new int[0][];
        HudPanel panel = layers.getPanel();
        HudPicture picture = layers.getPicture();
        if (panel != null && panel.shown() && composite.fitsInterfaces(picture.getWidth(), picture.getHeight()))
        {
            List<HudPanel.Moved> moved = panel.moved();
            composite.uploadInterfaces(picture.getPixels(), moved);
            pieceCorners = new double[moved.size()][][];
            pieceBoxes = new int[moved.size()][];
            int shownPieces = 0;
            for (HudPanel.Moved piece : moved)
            {
                if (piece.getPiece() == HudPiece.CHAT && config.recordHideChat())
                {
                    continue;
                }
                pieceCorners[shownPieces] = inWorld(piece.getCorners());
                pieceBoxes[shownPieces++] = piece.getBox();
            }
            pieceCorners = Arrays.copyOf(pieceCorners, shownPieces);
            pieceBoxes = Arrays.copyOf(pieceBoxes, shownPieces);
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

        double[][][] floating = new double[Composite.FLOATING][][];
        WorldMenu menu = layers.getMenu();
        if (!layers.getMenuLayer().isCleared())
        {
            composite.uploadFloating(Composite.MENU, menu.getCrop().getPixels());
            floating[Composite.MENU] = inWorld(menu.getBoard());
        }
        if (!layers.getLabelLayer().isCleared())
        {
            composite.uploadFloating(Composite.LABEL, layers.getLabel().getPixels());
            floating[Composite.LABEL] = inWorld(layers.getLabelBoard());
        }
        floating[Composite.SETTINGS] = ownPanel(layers.getSettingsPanel(), Composite.SETTINGS);
        floating[Composite.KEYBOARD] = ownPanel(layers.getKeyboardPanel(), Composite.KEYBOARD);
        return new Composite.Shown(strips, across, pieceCorners, pieceBoxes, floating);
    }

    private double[][] ownPanel(RoomPanel panel, int index)
    {
        if (!panel.isShown())
        {
            return null;
        }
        composite.uploadFloating(index, panel.getPanel().pixels());
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
        if (recorder != null)
        {
            try
            {
                recorded = recorder.finish();
                long dropped = recorder.dropped();
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
                recorder.close();
                recorder = null;
            }
        }
        if (composite != null)
        {
            composite.close();
            composite = null;
        }
        due = false;
        spectating = false;
    }
}
