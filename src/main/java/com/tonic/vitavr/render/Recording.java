package com.tonic.vitavr.render;

import com.tonic.vitavr.config.OneEyeCamera;
import com.tonic.vitavr.config.RecordMode;
import com.tonic.vitavr.record.FramePacer;
import com.tonic.vitavr.record.SessionRecorder;
import com.tonic.vitavr.record.SpectatorView;
import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.View;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Records the session to a 1920 by 1080 video from both eyes, the cropped left eye, or a steadied head camera, with the headset layers drawn in.
 */
final class Recording implements AutoCloseable
{
    static final int MENU = 0;
    static final int LABEL = 1;
    static final int SETTINGS = 2;
    static final int KEYBOARD = 3;

    private static final double SPECTATOR_FOV = Math.toRadians(90);
    private static final int EYES = 2;

    /**
     * Where the layers stand in the world for a recorded picture.
     */
    static final class Layers
    {
        private final double[][][] strips;
        private final double[][] stripsAcross;
        private final double[][][] floating;

        /**
         * @param strips       The interfaces' strips, left to right, or null while hidden.
         * @param stripsAcross Each strip's start and end across the picture, 0 to 1.
         * @param floating     Each floating picture's corners, or null for one not shown.
         */
        Layers(double[][][] strips, double[][] stripsAcross, double[][][] floating)
        {
            this.strips = strips;
            this.stripsAcross = stripsAcross;
            this.floating = floating;
        }
    }

    private final RecordMode mode;
    private final OneEyeCamera camera;
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
    @Getter
    private final View spectatorView = new View();
    private final Fov spectatorFov;
    private int times;
    private boolean closed;

    private Recording(RecordMode mode, OneEyeCamera camera, SessionRecorder recorder, int samples, int[] interfacesSize, int[][] floatingSizes)
    {
        this.mode = mode;
        this.camera = camera;
        this.recorder = recorder;
        this.picture = new EyesTarget(SessionRecorder.WIDTH, SessionRecorder.HEIGHT, 1, 1);
        this.spectatorTarget = isSteadied() ? new EyesTarget(SessionRecorder.WIDTH, SessionRecorder.HEIGHT, samples, 1) : null;
        this.interfaces = new AtlasQuad(interfacesSize[0], interfacesSize[1]);
        this.interfacesWidth = interfacesSize[0];
        this.interfacesHeight = interfacesSize[1];
        this.floatingSizes = floatingSizes;
        this.floating = new AtlasQuad[floatingSizes.length];
        for (int index = 0; index < floating.length; index++)
        {
            floating[index] = new AtlasQuad(floatingSizes[index][0], floatingSizes[index][1]);
        }
        double halfAcross = Math.tan(SPECTATOR_FOV / 2);
        double halfUp = halfAcross * SessionRecorder.HEIGHT / SessionRecorder.WIDTH;
        this.spectatorFov = new Fov(-SPECTATOR_FOV / 2, SPECTATOR_FOV / 2, Math.atan(halfUp), -Math.atan(halfUp));
    }

    /**
     * Starts recording, in the OpenGL context current on the calling thread.
     *
     * @param mode           One eye or both.
     * @param camera         For one eye, the exact left eye or a steadied camera.
     * @param file           The video file.
     * @param samples        The steadied camera's samples per pixel.
     * @param interfacesSize The interfaces' picture width and height.
     * @param floatingSizes  Each floating picture's width and height.
     * @return The recording.
     * @throws IOException if ffmpeg cannot be run
     */
    static Recording start(RecordMode mode, OneEyeCamera camera, Path file, int samples, int[] interfacesSize, int[][] floatingSizes) throws IOException
    {
        SessionRecorder recorder = SessionRecorder.start(file);
        try
        {
            return new Recording(mode, camera, recorder, samples, interfacesSize, floatingSizes);
        }
        catch (RuntimeException failed)
        {
            recorder.close();
            throw failed;
        }
    }

    /**
     * Gets whether the recording uses the steadied head camera.
     * @return True if steadied.
     */
    boolean isSteadied()
    {
        return mode == RecordMode.ONE_EYE && camera == OneEyeCamera.STEADIED;
    }

    /**
     * Gets whether the interfaces' picture is still the size the recording started with.
     *
     * @param width  The current width.
     * @param height The current height.
     * @return Whether it fits.
     */
    boolean fitsInterfaces(int width, int height)
    {
        return width == interfacesWidth && height == interfacesHeight;
    }

    /**
     * Gets whether a picture is due.
     *
     * @param nowNanos The current System.nanoTime.
     * @return Whether one is due.
     */
    boolean due(long nowNanos)
    {
        times = pacer.due(nowNanos);
        return times > 0;
    }

    /**
     * Copies the interfaces' picture for the recording.
     *
     * @param pixels The picture, premultiplied ARGB, rows from the top.
     */
    void interfaces(int[] pixels)
    {
        interfaces.upload(pixels);
    }

    /**
     * Copies a floating picture for the recording.
     *
     * @param index  The picture's number.
     * @param pixels The picture, premultiplied ARGB, rows from the top.
     */
    void floating(int index, int[] pixels)
    {
        floating[index].upload(pixels);
    }

    /**
     * Moves the steadied camera after the head for a frame.
     *
     * @param head    The head in the room, lifted and cleared as the eyes are.
     * @param seconds The time since the last frame.
     * @param room    Where the room stands in the world.
     * @return The camera's matrix this frame.
     */
    float[] follow(Pose head, double seconds, RoomToWorld room)
    {
        room.view(spectator.follow(head, seconds), spectatorView);
        return spectatorView.worldToClip(spectatorFov);
    }

    /**
     * Clears the steadied camera's picture to a colour.
     *
     * @param rgb The colour as 0xRRGGBB.
     */
    void clearSpectator(int rgb)
    {
        spectatorTarget.clear(rgb);
    }

    /**
     * Binds the steadied camera's picture for drawing.
     */
    void useSpectator()
    {
        spectatorTarget.use(0);
    }

    /**
     * Records the due picture from the eyes.
     *
     * @param eyes        Both eyes, resolved.
     * @param eyeMatrices Each eye's matrix.
     * @param layers      Where the layers stand.
     */
    void recordEyes(EyesTarget eyes, float[][] eyeMatrices, Layers layers)
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
                layersOver(eyeMatrices[eye], layers, into);
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
            layersOver(eyeMatrices[0], layers, new int[]{(int) Math.round(-cropX * scale), (int) Math.round(-cropY * scale), (int) Math.round(width * scale),
                    (int) Math.round(height * scale)});
        }
        recorder.capture(picture.getFramebuffer(), times);
    }

    /**
     * Records the due picture from the steadied camera.
     *
     * @param worldToClip The camera's matrix.
     * @param layers      Where the layers stand.
     */
    void recordSteadied(float[] worldToClip, Layers layers)
    {
        spectatorTarget.resolve();
        int[] whole = {0, 0, SessionRecorder.WIDTH, SessionRecorder.HEIGHT};
        spectatorTarget.copyEye(0, whole, picture.getFramebuffer(), whole);
        layersOver(worldToClip, layers, whole);
        recorder.capture(picture.getFramebuffer(), times);
    }

    private void layersOver(float[] worldToClip, Layers layers, int[] viewport)
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, picture.getFramebuffer());
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (layers.strips != null)
        {
            for (int strip = 0; strip < layers.strips.length; strip++)
            {
                double[] across = layers.stripsAcross[strip];
                int left = (int) Math.round(across[0] * interfacesWidth);
                int right = (int) Math.round(across[1] * interfacesWidth);
                interfaces.draw(worldToClip, layers.strips[strip], left, 0, right - left, interfacesHeight, 1);
            }
        }
        for (int index = 0; index < floating.length && index < layers.floating.length; index++)
        {
            if (layers.floating[index] != null)
            {
                floating[index].draw(worldToClip, layers.floating[index], 0, 0, floatingSizes[index][0], floatingSizes[index][1], 1);
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
     * @return The number of frames dropped for lack of a free buffer.
     */
    long dropped()
    {
        return recorder.dropped();
    }

    /**
     * Ends the video.
     *
     * @return The video file.
     * @throws IOException          if ffmpeg failed
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    Path finish() throws IOException, InterruptedException
    {
        return recorder.finish();
    }

    /**
     * Ends the video, if any, in its OpenGL context. Safe to call more than once.
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
