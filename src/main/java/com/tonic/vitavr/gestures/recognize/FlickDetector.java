package com.tonic.vitavr.gestures.recognize;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Finds a hand's fast snaps in its turn rate: a snap starts when the hand turns faster than a start speed and ends
 * when it slows to half that, or after a short while, giving its fastest moment.
 */
public final class FlickDetector
{
    public static final double START_SPEED = 4;
    private static final double LONGEST_SECONDS = 0.35;
    private static final double REST_SECONDS = 0.4;

    /**
     * A snap's fastest moment.
     */
    @Getter
    @AllArgsConstructor
    public static final class Peak
    {
        private final double[] axis;
        private final double speed;
        private final boolean armed;
    }

    private boolean snapping;
    private double started;
    private double fastest;
    private final double[] fastestAxis = new double[3];
    private boolean fastestArmed;
    private double restUntil = Double.NEGATIVE_INFINITY;

    /**
     * @param now   The time in seconds.
     * @param spin  The hand's turn rate in the body frame, radians a second about each axis.
     * @param armed Whether the hand holds its arm input.
     * @return The snap that just ended, or null.
     */
    public Peak feed(double now, double[] spin, boolean armed)
    {
        double speed = Math.sqrt(spin[0] * spin[0] + spin[1] * spin[1] + spin[2] * spin[2]);
        if (!snapping)
        {
            if (speed >= START_SPEED && now >= restUntil)
            {
                snapping = true;
                started = now;
                fastest = 0;
            }
            else
            {
                return null;
            }
        }
        if (speed > fastest)
        {
            fastest = speed;
            fastestAxis[0] = spin[0] / speed;
            fastestAxis[1] = spin[1] / speed;
            fastestAxis[2] = spin[2] / speed;
            fastestArmed = armed;
        }
        if (speed < START_SPEED / 2 || now - started > LONGEST_SECONDS)
        {
            snapping = false;
            restUntil = now + REST_SECONDS;
            return new Peak(fastestAxis.clone(), fastest, fastestArmed);
        }
        return null;
    }

    /**
     * Forgets a snap under way, as when the hand is lost.
     */
    public void reset()
    {
        snapping = false;
    }
}
