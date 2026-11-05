package com.tonic.vitavr.render.recording;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Pipes raw RGBA frames to ffmpeg for H.264 MP4 encoding. ffmpeg is taken from the PATH or -Dvitavr.ffmpeg.
 */
public final class FfmpegEncoder implements AutoCloseable
{
    private static final long STOP_SECONDS = 5;
    private static final long STOP_POLL_MILLIS = 20;
    private static final String FFMPEG = System.getProperty("vitavr.ffmpeg", "ffmpeg");
    private static final String PRESET = "veryfast";
    private static final int RATE_FACTOR = 20;

    private final Process ffmpeg;
    private final OutputStream frames;
    private final Path log;
    private boolean finished;
    private boolean closed;

    private FfmpegEncoder(Process ffmpeg, Path log)
    {
        this.ffmpeg = ffmpeg;
        this.frames = ffmpeg.getOutputStream();
        this.log = log;
    }

    /**
     * Starts ffmpeg writing a video, replacing any existing file.
     * @param width The frame width in pixels.
     * @param height The frame height in pixels.
     * @param framesPerSecond The video frame rate.
     * @param out The video file.
     * @return The encoder.
     * @throws IOException If ffmpeg cannot be run.
     */
    public static FfmpegEncoder start(int width, int height, int framesPerSecond, Path out) throws IOException
    {
        Path log = Files.createTempFile("vitavr-ffmpeg", ".log");
        List<String> command = Arrays.asList(FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-framerate", String.valueOf(framesPerSecond), "-i", "-", "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2", "-c:v", "libx264", "-preset", PRESET, "-crf", String.valueOf(RATE_FACTOR), "-pix_fmt", "yuv420p", "-movflags", "+faststart", out.toAbsolutePath().toString());
        try
        {
            return new FfmpegEncoder(new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log.toFile()).start(), log);
        }
        catch (IOException missing)
        {
            forget(log);
            throw new IOException("ffmpeg could not be run as \"" + FFMPEG + "\": install it on the PATH or name it with -Dvitavr.ffmpeg=<path>", missing);
        }
    }

    /**
     * Writes a frame.
     * @param frame The frame bytes as RGBA, top row first.
     * @throws IOException If ffmpeg stopped taking frames.
     */
    public void write(byte[] frame) throws IOException
    {
        try
        {
            frames.write(frame);
        }
        catch (IOException failure)
        {
            throw new IOException("ffmpeg stopped taking frames: " + tail(), failure);
        }
    }

    /**
     * Ends the video and waits for ffmpeg to finish.
     * @throws IOException If ffmpeg fails.
     * @throws InterruptedException If interrupted while waiting.
     */
    public void finish() throws IOException, InterruptedException
    {
        frames.close();
        int exit = ffmpeg.waitFor();
        finished = true;
        if (exit != 0)
        {
            throw new IOException("ffmpeg failed with exit code " + exit + ": " + tail());
        }
    }

    /**
     * Kills ffmpeg and its child processes, since ffmpeg is often a shim, and waits briefly for them to exit.
     */
    private void abort()
    {
        List<ProcessHandle> started = ffmpeg.descendants().collect(Collectors.toCollection(ArrayList::new));
        started.add(ffmpeg.toHandle());
        started.forEach(ProcessHandle::destroyForcibly);
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STOP_SECONDS);
        while (started.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline)
        {
            try
            {
                Thread.sleep(STOP_POLL_MILLIS);
            }
            catch (InterruptedException again)
            {
                interrupted = true;
            }
        }
        if (interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Aborts ffmpeg if unfinished and deletes its log. Safe to call more than once.
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
            abort();
        }
        forget(log);
    }

    private String tail() throws IOException
    {
        String text = Files.readString(log).trim();
        return text.isEmpty() ? "it said nothing" : text.length() > 600 ? text.substring(text.length() - 600) : text;
    }

    private static void forget(Path log)
    {
        try
        {
            Files.deleteIfExists(log);
        }
        catch (IOException held)
        {
            log.toFile().deleteOnExit();
        }
    }
}
