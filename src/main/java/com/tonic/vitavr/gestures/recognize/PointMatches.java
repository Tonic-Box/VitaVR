package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureKind;

import java.util.List;

/**
 * Matching the gestures made at a point or in a moment: a grip squeezed in a spot, and a snap about an axis.
 */
public final class PointMatches
{
    public static final double MOST_AXIS_DEGREES = 35;
    public static final double LEAST_FLICK_SPEED = 5;

    private PointMatches()
    {
    }

    /**
     * @param sensitivity 0 to 100.
     * @return How much further than its own size a gesture may be off: half at 0, one at 50, half again at 100.
     */
    static double allowance(int sensitivity)
    {
        return 0.5 + Math.max(0, Math.min(100, sensitivity)) / 100.0;
    }

    /**
     * @param library The gestures.
     * @param hand    0 left or 1 right.
     * @param point   The hand in the body frame.
     * @return The nearest enabled spot the hand is in, or null.
     */
    public static Gesture spot(List<Gesture> library, int hand, double[] point)
    {
        Gesture nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Gesture gesture : library)
        {
            if (!gesture.isEnabled() || gesture.getKind() != GestureKind.SPOT || !gesture.madeWith(hand))
            {
                continue;
            }
            double[] centre = gesture.getCentre();
            double x = gesture.mirroredFor(hand) ? -centre[0] : centre[0];
            double distance = PathMath.distance(point, new double[]{x, centre[1], centre[2]});
            if (distance <= gesture.getRadius() * allowance(gesture.getSensitivity()) && distance < nearestDistance)
            {
                nearest = gesture;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * Matches a snap; mirrored to the other hand, a turn rate keeps its x and turns over its y and z.
     *
     * @param library The gestures.
     * @param hand    0 left or 1 right.
     * @param peak    The snap.
     * @return The enabled flick nearest in axis that the snap was fast enough for and armed as needed, or null.
     */
    public static Gesture flick(List<Gesture> library, int hand, FlickDetector.Peak peak)
    {
        Gesture nearest = null;
        double nearestAngle = Double.MAX_VALUE;
        double[] axis = peak.getAxis();
        for (Gesture gesture : library)
        {
            if (!gesture.isEnabled() || gesture.getKind() != GestureKind.FLICK || !gesture.madeWith(hand) || gesture.isArmed() && !peak.isArmed())
            {
                continue;
            }
            double[] recorded = gesture.getAxis();
            double flip = gesture.mirroredFor(hand) ? -1 : 1;
            double dot = recorded[0] * axis[0] + flip * recorded[1] * axis[1] + flip * recorded[2] * axis[2];
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
            if (angle <= MOST_AXIS_DEGREES && peak.getSpeed() >= leastSpeed(gesture) && angle < nearestAngle)
            {
                nearest = gesture;
                nearestAngle = angle;
            }
        }
        return nearest;
    }

    /**
     * @param gesture A flick.
     * @return The slowest snap that counts: most of the recorded speed at low sensitivity, under half at high.
     */
    static double leastSpeed(Gesture gesture)
    {
        double share = 0.9 - 0.005 * Math.max(0, Math.min(100, gesture.getSensitivity()));
        return Math.max(LEAST_FLICK_SPEED, gesture.getPeakSpin() * share);
    }
}
