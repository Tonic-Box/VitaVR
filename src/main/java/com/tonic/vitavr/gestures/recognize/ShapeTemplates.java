package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Sample;
import com.tonic.vitavr.gestures.model.Take;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawn shapes and two-hand motions made ready to compare against: each take spaced, centred and sized, with how
 * close a drawing must come to count, and the matching of a fresh drawing against them.
 */
public final class ShapeTemplates
{
    public static final int SHAPE_POINTS = 64;
    public static final int HAND_POINTS = 32;
    static final double TURN_DEGREES = 25;
    static final double TURN_STEP = 5;
    static final double MARGIN = 1.15;
    private static final double SINGLE_TAKE_SPREAD = 0.15;
    private static final double LEAST_THRESHOLD = 0.12;
    private static final double MOST_THRESHOLD = 0.45;
    private static final double SPREAD_ALLOWANCE = 2.0;

    /**
     * One gesture made ready for one hand, or for both.
     */
    @Getter
    @AllArgsConstructor
    public static final class Template
    {
        private final Gesture gesture;
        private final int hand;
        private final List<double[][]> takes;
        private final double threshold;
    }

    /**
     * The best match of a drawing.
     */
    @Getter
    @AllArgsConstructor
    public static final class Match
    {
        private final Gesture gesture;
        private final double distance;
    }

    private final List<Template> shapes = new ArrayList<>();
    private final List<Template> twoHand = new ArrayList<>();

    /**
     * @param library The gestures; disabled ones and other kinds are left out.
     */
    public ShapeTemplates(List<Gesture> library)
    {
        for (Gesture gesture : library)
        {
            if (!gesture.isEnabled() || gesture.getTakes().isEmpty())
            {
                continue;
            }
            if (gesture.getKind() == GestureKind.SHAPE)
            {
                int recorded = gesture.recordedHand();
                List<double[][]> takes = new ArrayList<>();
                for (Take take : gesture.getTakes())
                {
                    if (take.hand(recorded).size() >= 2)
                    {
                        takes.add(shape(points(take.hand(recorded))));
                    }
                }
                if (takes.isEmpty())
                {
                    continue;
                }
                double threshold = threshold(takes, gesture.getSensitivity());
                for (int hand = 0; hand < 2; hand++)
                {
                    if (gesture.madeWith(hand))
                    {
                        shapes.add(new Template(gesture, hand, gesture.mirroredFor(hand) ? mirroredAll(takes) : takes, threshold));
                    }
                }
            }
            else if (gesture.getKind() == GestureKind.TWO_HAND)
            {
                List<double[][]> takes = new ArrayList<>();
                for (Take take : gesture.getTakes())
                {
                    if (take.getLeft().size() >= 2 && take.getRight().size() >= 2)
                    {
                        takes.add(twoHand(points(take.getLeft()), points(take.getRight())));
                    }
                }
                if (!takes.isEmpty())
                {
                    twoHand.add(new Template(gesture, -1, takes, threshold(takes, gesture.getSensitivity())));
                }
            }
        }
    }

    /**
     * @param path One hand's drawing in the body frame.
     * @return It made ready to compare.
     */
    public static double[][] shape(List<double[]> path)
    {
        double[][] points = PathMath.resample(path, SHAPE_POINTS);
        PathMath.normalise(points);
        return points;
    }

    /**
     * @param left The left hand's path.
     * @param right The right hand's path.
     * @return Both made ready to compare as one, centred and sized together so how they move against each other counts.
     */
    public static double[][] twoHand(List<double[]> left, List<double[]> right)
    {
        double[][] a = PathMath.resample(left, HAND_POINTS);
        double[][] b = PathMath.resample(right, HAND_POINTS);
        double[][] both = new double[HAND_POINTS * 2][];
        System.arraycopy(a, 0, both, 0, HAND_POINTS);
        System.arraycopy(b, 0, both, HAND_POINTS, HAND_POINTS);
        PathMath.normalise(both);
        return both;
    }

    /**
     * @param samples A hand's samples.
     * @return Their positions.
     */
    public static List<double[]> points(List<Sample> samples)
    {
        List<double[]> points = new ArrayList<>(samples.size());
        for (Sample sample : samples)
        {
            points.add(new double[]{sample.getX(), sample.getY(), sample.getZ()});
        }
        return points;
    }

    /**
     * How close a drawing must come: twice how far the takes are from each other, kept within sensible bounds, then
     * scaled by the sensitivity, half as far at 0 and half again as far at 100.
     */
    static double threshold(List<double[][]> takes, int sensitivity)
    {
        double spread = SINGLE_TAKE_SPREAD;
        if (takes.size() >= 2)
        {
            double sum = 0;
            int pairs = 0;
            for (int i = 0; i < takes.size(); i++)
            {
                for (int j = i + 1; j < takes.size(); j++)
                {
                    sum += PathMath.bestDistance(takes.get(i), takes.get(j), TURN_DEGREES, TURN_STEP);
                    pairs++;
                }
            }
            spread = sum / pairs;
        }
        double allowed = Math.max(LEAST_THRESHOLD, Math.min(MOST_THRESHOLD, spread * SPREAD_ALLOWANCE));
        return allowed * (0.5 + Math.max(0, Math.min(100, sensitivity)) / 100.0);
    }

    private static List<double[][]> mirroredAll(List<double[][]> takes)
    {
        List<double[][]> out = new ArrayList<>(takes.size());
        for (double[][] take : takes)
        {
            out.add(PathMath.mirrored(take));
        }
        return out;
    }

    /**
     * @param candidate A prepared one-hand drawing.
     * @param hand The hand that drew it.
     * @return The shape it is, or null when none is close enough or two are too alike to tell apart.
     */
    public Match matchShape(double[][] candidate, int hand)
    {
        return best(candidate, shapes, hand);
    }

    /**
     * @param candidate A prepared two-hand motion.
     * @return The motion it is, or null.
     */
    public Match matchTwoHand(double[][] candidate)
    {
        return best(candidate, twoHand, -1);
    }

    /**
     * @return Whether there is any two-hand motion to look for.
     */
    public boolean hasTwoHand()
    {
        return !twoHand.isEmpty();
    }

    private static Match best(double[][] candidate, List<Template> templates, int hand)
    {
        Template best = null;
        double bestDistance = Double.MAX_VALUE;
        double runnerUp = Double.MAX_VALUE;
        for (Template template : templates)
        {
            if (template.getHand() != hand)
            {
                continue;
            }
            double distance = Double.MAX_VALUE;
            for (double[][] take : template.getTakes())
            {
                distance = Math.min(distance, PathMath.bestDistance(candidate, take, TURN_DEGREES, TURN_STEP));
            }
            if (distance < bestDistance)
            {
                if (best != null && best.getGesture() != template.getGesture())
                {
                    runnerUp = bestDistance;
                }
                best = template;
                bestDistance = distance;
            }
            else if (best != null && best.getGesture() != template.getGesture())
            {
                runnerUp = Math.min(runnerUp, distance);
            }
        }
        if (best == null || bestDistance > best.getThreshold() || bestDistance * MARGIN > runnerUp)
        {
            return null;
        }
        return new Match(best.getGesture(), bestDistance);
    }
}
