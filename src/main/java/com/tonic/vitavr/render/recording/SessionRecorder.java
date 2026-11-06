package com.tonic.vitavr.render.recording;

import org.lwjgl.opengl.GL11;

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
    public static final int WIDTH = Composite.WIDTH;
    public static final int HEIGHT = Composite.HEIGHT;
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
    private final FrameReadback readback;
    private final Thread writer;
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
        readback = new FrameReadback(WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this::queue);
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
        readback.capture(framebuffer, times);
    }

    /**
     * Flips a read-back frame upright and queues it, or drops it if no buffer is free.
     */
    private void queue(ByteBuffer mapped, int repeats)
    {
        byte[] pixels = free.poll();
        if (pixels == null)
        {
            dropped++;
            return;
        }
        for (int row = 0; row < HEIGHT; row++)
        {
            mapped.position((HEIGHT - 1 - row) * ROW);
            mapped.get(pixels, row * ROW, ROW);
        }
        if (!queue.offer(new Frame(pixels, repeats)))
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
        readback.flush();
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
        readback.close();
    }
}
