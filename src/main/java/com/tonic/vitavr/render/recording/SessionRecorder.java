package com.tonic.vitavr.render.recording;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Records a session to video, reading frames back a frame late and encoding on a separate thread.
 * Frames are dropped and counted when no buffer is free.
 */
public final class SessionRecorder implements AutoCloseable
{
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    public static final int FRAMES_PER_SECOND = 60;

    private static final int QUEUED = 6;
    private static final int ROW = WIDTH * 4;
    private static final Frame END = new Frame(null, 0);

    private static final class Frame
    {
        private final byte[] pixels;
        private final int repeats;

        private Frame(byte[] pixels, int repeats)
        {
            this.pixels = pixels;
            this.repeats = repeats;
        }
    }

    private final Path file;
    private final FfmpegEncoder encoder;
    private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(QUEUED);
    private final BlockingQueue<byte[]> free = new ArrayBlockingQueue<>(QUEUED + 1);
    private final int[] readBuffers = new int[2];
    private final long[] fences = new long[2];
    private final int[] repeats = new int[2];
    private final Thread writer;
    private int slot;
    private volatile IOException failure;
    private long dropped;
    private boolean finished;
    private boolean closed;

    private SessionRecorder(Path file, FfmpegEncoder encoder)
    {
        this.file = file;
        this.encoder = encoder;
        for (int buffer = 0; buffer <= QUEUED; buffer++)
        {
            free.add(new byte[ROW * HEIGHT]);
        }
        for (int i = 0; i < 2; i++)
        {
            readBuffers[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) ROW * HEIGHT, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        writer = new Thread(this::write, "vitavr-recording");
        writer.setDaemon(true);
        writer.start();
    }

    /**
     * Starts recording to a video file using the current OpenGL context.
     * @param file The video file.
     * @return The recorder.
     * @throws IOException If ffmpeg cannot be run.
     */
    public static SessionRecorder start(Path file) throws IOException
    {
        return new SessionRecorder(file, FfmpegEncoder.start(WIDTH, HEIGHT, FRAMES_PER_SECOND, file));
    }

    /**
     * Starts reading back a frame, handing on the previous one.
     * @param framebuffer The WIDTH by HEIGHT framebuffer to read.
     * @param times The number of video frames it fills.
     */
    public void capture(int framebuffer, int times)
    {
        collect(slot ^ 1);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[slot]);
        GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        fences[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        repeats[slot] = times;
        slot ^= 1;
    }

    /**
     * Flips a read-back frame upright and queues it, or drops it if no buffer is free.
     */
    private void collect(int from)
    {
        if (fences[from] == 0)
        {
            return;
        }
        GL32.glClientWaitSync(fences[from], GL32.GL_SYNC_FLUSH_COMMANDS_BIT, GL32.GL_TIMEOUT_IGNORED);
        GL32.glDeleteSync(fences[from]);
        fences[from] = 0;
        byte[] pixels = free.poll();
        if (pixels == null)
        {
            dropped++;
            return;
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[from]);
        ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);
        if (mapped != null)
        {
            for (int row = 0; row < HEIGHT; row++)
            {
                mapped.position((HEIGHT - 1 - row) * ROW);
                mapped.get(pixels, row * ROW, ROW);
            }
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        if (!queue.offer(new Frame(pixels, repeats[from])))
        {
            free.add(pixels);
            dropped++;
        }
    }

    private void write()
    {
        try
        {
            while (true)
            {
                Frame frame = queue.take();
                if (frame == END)
                {
                    return;
                }
                if (failure == null)
                {
                    for (int time = 0; time < frame.repeats; time++)
                    {
                        encoder.write(frame.pixels);
                    }
                }
                free.add(frame.pixels);
            }
        }
        catch (IOException stopped)
        {
            failure = stopped;
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return The number of frames dropped for lack of a free buffer.
     */
    public long dropped()
    {
        return dropped;
    }

    /**
     * Flushes the last frames and finishes the video. Must be called on the context's thread.
     * @return The video file.
     * @throws IOException If ffmpeg failed or stopped taking frames.
     * @throws InterruptedException If interrupted while waiting.
     */
    public Path finish() throws IOException, InterruptedException
    {
        collect(slot ^ 1);
        collect(slot);
        queue.put(END);
        writer.join();
        if (failure != null)
        {
            throw failure;
        }
        encoder.finish();
        finished = true;
        return file;
    }

    /**
     * Frees the GPU buffers and aborts ffmpeg if unfinished. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        if (!finished)
        {
            writer.interrupt();
        }
        encoder.close();
        for (int i = 0; i < 2; i++)
        {
            if (fences[i] != 0)
            {
                GL32.glDeleteSync(fences[i]);
            }
            GL15.glDeleteBuffers(readBuffers[i]);
        }
    }
}
