package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.model.Sample;

import java.util.List;

/**
 * The preview's timing: each take plays in real time then rests a moment before the next, and a hand's place at a
 * moment is found between the two samples around it.
 */
final class TakeClock
{
    static final double REST_SECONDS = 0.6;
    private static final double SHORTEST_SECONDS = 0.2;

    private TakeClock()
    {
    }

    /**
     * @param elapsed Seconds since the preview started.
     * @param lengths Each take's length in seconds.
     * @return The take playing, and the time within it, as {take, seconds}; the time stays at the take's end while it rests.
     */
    static double[] at(double elapsed, double[] lengths)
    {
        if (lengths.length == 0)
        {
            return new double[]{0, 0};
        }
        double loop = 0;
        for (double length : lengths)
        {
            loop += Math.max(SHORTEST_SECONDS, length) + REST_SECONDS;
        }
        double t = elapsed % loop;
        for (int take = 0; take < lengths.length; take++)
        {
            double length = Math.max(SHORTEST_SECONDS, lengths[take]);
            if (t < length + REST_SECONDS)
            {
                return new double[]{take, Math.min(t, lengths[take])};
            }
            t -= length + REST_SECONDS;
        }
        return new double[]{lengths.length - 1, lengths[lengths.length - 1]};
    }

    /**
     * @param samples A hand's samples, in time order; at least one.
     * @param t       Seconds into the take.
     * @param out     Receives x, y, z, qx, qy, qz, qw, between the samples around the time.
     */
    static void place(List<Sample> samples, double t, double[] out)
    {
        Sample before = samples.get(0);
        Sample after = before;
        for (Sample sample : samples)
        {
            if (sample.getT() <= t)
            {
                before = sample;
                after = sample;
            }
            else
            {
                after = sample;
                break;
            }
        }
        double span = after.getT() - before.getT();
        double f = span <= 0 ? 0 : (t - before.getT()) / span;
        out[0] = before.getX() + (after.getX() - before.getX()) * f;
        out[1] = before.getY() + (after.getY() - before.getY()) * f;
        out[2] = before.getZ() + (after.getZ() - before.getZ()) * f;
        Sample nearer = f < 0.5 ? before : after;
        out[3] = nearer.getQx();
        out[4] = nearer.getQy();
        out[5] = nearer.getQz();
        out[6] = nearer.getQw();
    }
}
