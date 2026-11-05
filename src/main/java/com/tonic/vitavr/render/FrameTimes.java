package com.tonic.vitavr.render;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Collects headset frame timings over a period and summarises them.
 */
public final class FrameTimes
{
    /** A timed part of a frame. */
    public enum Part
    {
        WAIT("wait"),
        SCENE("eyes' scene"),
        FINISH("finishing"),
        INTERFACES("interfaces"),
        RECORDING("recording"),
        SUBMIT("submit");

        private final String shown;

        Part(String shown)
        {
            this.shown = shown;
        }
    }

    private static final int MOST_KEPT = 4096;
    private static final double NANOS_PER_MILLI = 1e6;

    private final long stretchNanos;
    private final long[] frames = new long[MOST_KEPT];
    private final Map<Part, long[]> parts = new EnumMap<>(Part.class);
    private int count;
    private int overBudget;
    private long stretchFrom = -1;
    private long frameFrom;

    /**
     * Creates a frame timer.
     * @param stretchNanos The reporting period in nanoseconds.
     * @throws IllegalArgumentException If stretchNanos is not positive.
     */
    public FrameTimes(long stretchNanos)
    {
        if (stretchNanos <= 0)
        {
            throw new IllegalArgumentException("stretchNanos must be positive");
        }
        this.stretchNanos = stretchNanos;
    }

    /**
     * Marks the start of a frame.
     * @param nowNanos The current time in nanoseconds.
     */
    public void begin(long nowNanos)
    {
        if (stretchFrom < 0)
        {
            stretchFrom = nowNanos;
        }
        frameFrom = nowNanos;
    }

    /**
     * Adds the time a part of the frame took.
     * @param part The part.
     * @param nanos The time taken in nanoseconds.
     */
    public void add(Part part, long nanos)
    {
        long[] sum = parts.computeIfAbsent(part, missing -> new long[2]);
        sum[0] += nanos;
        sum[1]++;
    }

    /**
     * Marks the end of a frame.
     * @param nowNanos The current time in nanoseconds.
     * @param budgetNanos The headset frame budget in nanoseconds, 0 if unknown.
     */
    public void end(long nowNanos, long budgetNanos)
    {
        long took = nowNanos - frameFrom;
        if (count < MOST_KEPT)
        {
            frames[count] = took;
        }
        count++;
        if (budgetNanos > 0 && took > budgetNanos)
        {
            overBudget++;
        }
    }

    /**
     * Summarises the period once it is over and starts the next.
     * @param nowNanos The current time in nanoseconds.
     * @return The summary, or null if the period is not over.
     */
    public String report(long nowNanos)
    {
        if (stretchFrom < 0 || nowNanos - stretchFrom < stretchNanos || count == 0)
        {
            return null;
        }
        double seconds = (nowNanos - stretchFrom) / 1e9;
        int kept = Math.min(count, MOST_KEPT);
        long[] sorted = Arrays.copyOf(frames, kept);
        Arrays.sort(sorted);
        StringBuilder text = new StringBuilder(String.format("%.0f fps, frame %.1f ms, slow %.1f ms, %d over budget", count / seconds, sorted[kept / 2] / NANOS_PER_MILLI,
                sorted[Math.min(kept - 1, (int) (kept * 0.95))] / NANOS_PER_MILLI, overBudget));
        StringBuilder each = new StringBuilder();
        for (Part part : Part.values())
        {
            long[] sum = parts.get(part);
            if (sum != null)
            {
                each.append(each.length() == 0 ? "" : ", ").append(part.shown).append(String.format(" %.1f", sum[0] / NANOS_PER_MILLI / sum[1]));
            }
        }
        if (each.length() > 0)
        {
            text.append(" (").append(each).append(" ms)");
        }
        count = 0;
        overBudget = 0;
        parts.clear();
        stretchFrom = nowNanos;
        return text.toString();
    }
}
