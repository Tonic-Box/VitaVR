package com.tonic.vitavr.render.recording;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

/**
 * Reads framebuffers back to memory a frame late through two pixel buffers, so the GPU is never waited on.
 */
final class FrameReadback implements AutoCloseable
{
    /**
     * Takes a read-back frame while it is mapped.
     */
    @FunctionalInterface
    interface Sink
    {
        /**
         * @param mapped The pixels, rows from the bottom, valid only during the call.
         * @param tag The tag the frame was captured with.
         */
        void take(ByteBuffer mapped, int tag);
    }

    private final int width;
    private final int height;
    private final int format;
    private final int type;
    private final Sink sink;
    private final int[] buffers = new int[2];
    private final long[] fences = new long[2];
    private final int[] tags = new int[2];
    private int slot;
    private boolean closed;

    /**
     * Creates the pixel buffers in the current OpenGL context.
     *
     * @param width The read width, in pixels.
     * @param height The read height, in pixels.
     * @param format The GL pixel format to read, four bytes a pixel.
     * @param type The GL pixel type to read.
     * @param sink Takes each frame once it has arrived.
     */
    FrameReadback(int width, int height, int format, int type, Sink sink)
    {
        this.width = width;
        this.height = height;
        this.format = format;
        this.type = type;
        this.sink = sink;
        for (int i = 0; i < 2; i++)
        {
            buffers[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    /**
     * Starts reading back a framebuffer's bottom left corner, handing on the frame read before it.
     *
     * @param framebuffer The framebuffer, at least the read size.
     * @param tag Passed back with the frame.
     */
    void capture(int framebuffer, int tag)
    {
        collect(slot ^ 1);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
        GL11.glReadPixels(0, 0, width, height, format, type, 0L);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        fences[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        tags[slot] = tag;
        slot ^= 1;
    }

    /**
     * Hands on every frame still being read, oldest first.
     */
    void flush()
    {
        collect(slot ^ 1);
        collect(slot);
    }

    private void collect(int from)
    {
        if (fences[from] == 0)
        {
            return;
        }
        GL32.glClientWaitSync(fences[from], GL32.GL_SYNC_FLUSH_COMMANDS_BIT, GL32.GL_TIMEOUT_IGNORED);
        GL32.glDeleteSync(fences[from]);
        fences[from] = 0;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[from]);
        ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);
        if (mapped != null)
        {
            sink.take(mapped, tags[from]);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    /**
     * Frees the pixel buffers, dropping frames still being read. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        for (int i = 0; i < 2; i++)
        {
            if (fences[i] != 0)
            {
                GL32.glDeleteSync(fences[i]);
            }
            GL15.glDeleteBuffers(buffers[i]);
        }
    }
}
