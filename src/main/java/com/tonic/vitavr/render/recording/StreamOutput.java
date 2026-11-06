package com.tonic.vitavr.render.recording;

import com.tonic.vitavr.render.EyesTarget;
import com.tonic.vitavr.ui.StreamWindow;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Sends the composite picture to the stream window: scaled on the GPU to the window's size with black bars, read back a frame late.
 */
final class StreamOutput implements AutoCloseable
{
    static final int FRAMES_PER_SECOND = 30;

    private final StreamWindow window;
    private final FramePacer pacer = new FramePacer(FRAMES_PER_SECOND);
    private EyesTarget scaled;
    private FrameReadback readback;
    private int width;
    private int height;
    private boolean closed;

    /**
     * @param window The window the pictures are shown in.
     */
    StreamOutput(StreamWindow window)
    {
        this.window = window;
    }

    /**
     * @param nowNanos The current System.nanoTime.
     * @return Whether a stream picture is due.
     */
    boolean due(long nowNanos)
    {
        return pacer.due(nowNanos) > 0;
    }

    /**
     * Scales the composite picture to the window and starts reading it back, in the current OpenGL context.
     *
     * @param picture The composite picture's framebuffer, Composite.WIDTH by Composite.HEIGHT.
     */
    void capture(int picture)
    {
        resize(window.pictureWidth(), window.pictureHeight());
        scaled.clear(0);
        int[] into = Composite.fit(Composite.WIDTH, Composite.HEIGHT, 0, width, height);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, picture);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scaled.getFramebuffer());
        GL30.glBlitFramebuffer(0, 0, Composite.WIDTH, Composite.HEIGHT, into[0], into[1], into[0] + into[2], into[1] + into[3], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        readback.capture(scaled.getFramebuffer(), 0);
    }

    /**
     * Remakes the scaled picture and its readback when the window size changed.
     */
    private void resize(int wantedWidth, int wantedHeight)
    {
        if (scaled != null && wantedWidth == width && wantedHeight == height)
        {
            return;
        }
        release();
        width = wantedWidth;
        height = wantedHeight;
        scaled = new EyesTarget(width, height, 1, 1);
        int pictureWidth = width;
        int pictureHeight = height;
        readback = new FrameReadback(width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (mapped, tag) -> show(mapped, pictureWidth, pictureHeight));
    }

    /**
     * Copies a read-back picture upright into a spare window picture and shows it.
     */
    private void show(ByteBuffer mapped, int pictureWidth, int pictureHeight)
    {
        BufferedImage picture = window.spare(pictureWidth, pictureHeight);
        if (picture == null)
        {
            return;
        }
        int[] pixels = ((DataBufferInt) picture.getRaster().getDataBuffer()).getData();
        mapped.clear();
        IntBuffer rows = mapped.order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int row = 0; row < pictureHeight; row++)
        {
            rows.position((pictureHeight - 1 - row) * pictureWidth);
            rows.get(pixels, row * pictureWidth, pictureWidth);
        }
        window.publish(picture);
    }

    private void release()
    {
        if (readback != null)
        {
            readback.close();
            readback = null;
        }
        if (scaled != null)
        {
            scaled.close();
            scaled = null;
        }
    }

    /**
     * Frees the GL objects, in the context they were made in. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        release();
    }
}
