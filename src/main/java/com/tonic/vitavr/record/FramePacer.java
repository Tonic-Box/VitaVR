package com.tonic.vitavr.record;

/**
 * Paces recorded frames to a steady rate, repeating frames to keep real time and restarting after a long stall.
 */
public final class FramePacer
{
    private static final int MOST_AT_ONCE = 6;

    private final long intervalNanos;
    private long startNanos = Long.MIN_VALUE;
    private long written;

    /**
     * Creates a pacer.
     * @param framesPerSecond The video frame rate, at least 1.
     */
    public FramePacer(int framesPerSecond)
    {
        this.intervalNanos = 1_000_000_000L / framesPerSecond;
    }

    /**
     * Gets how many times to write the current frame.
     * @param nowNanos The current System.nanoTime.
     * @return The write count, 0 if no frame is due.
     */
    public int due(long nowNanos)
    {
        if (startNanos == Long.MIN_VALUE)
        {
            startNanos = nowNanos;
            written = 1;
            return 1;
        }
        long owed = (nowNanos - startNanos) / intervalNanos + 1 - written;
        if (owed <= 0)
        {
            return 0;
        }
        if (owed > MOST_AT_ONCE)
        {
            startNanos = nowNanos - (written * intervalNanos);
            owed = 1;
        }
        written += owed;
        return (int) owed;
    }

    /**
     * @return The number of frames written so far.
     */
    public long written()
    {
        return written;
    }
}
