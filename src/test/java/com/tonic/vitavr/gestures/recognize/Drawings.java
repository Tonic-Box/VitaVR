package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Sample;
import com.tonic.vitavr.gestures.model.Take;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Synthetic hand drawings for the recognizer tests, in the body frame.
 */
final class Drawings
{
    static final double[][] Z = {{-0.1, 0.1, 0.4}, {0.1, 0.1, 0.4}, {-0.1, -0.1, 0.4}, {0.1, -0.1, 0.4}};
    static final double[][] SEVEN = {{-0.1, 0.1, 0.4}, {0.1, 0.1, 0.4}, {-0.05, -0.15, 0.4}};
    static final double[][] SWIPE_DOWN = {{0, 0.15, 0.4}, {0, -0.15, 0.4}};

    private Drawings()
    {
    }

    /**
     * @param corners The corners in order.
     * @param seed    Noise seed; 0 for none.
     * @param scale   How large to draw it.
     * @param offset  How far right to move it.
     * @return The path through the corners, 20 points to an edge, jittered by up to 6 mm.
     */
    static List<double[]> path(double[][] corners, long seed, double scale, double offset)
    {
        Random random = new Random(seed);
        List<double[]> path = new ArrayList<>();
        for (int c = 1; c < corners.length; c++)
        {
            for (int i = 0; i < 20; i++)
            {
                double t = i / 20.0;
                double[] a = corners[c - 1];
                double[] b = corners[c];
                path.add(new double[]{
                        (a[0] + (b[0] - a[0]) * t) * scale + offset + jitter(random, seed),
                        (a[1] + (b[1] - a[1]) * t) * scale + jitter(random, seed),
                        a[2] + (b[2] - a[2]) * t + jitter(random, seed)});
            }
        }
        double[] last = corners[corners.length - 1];
        path.add(new double[]{last[0] * scale + offset, last[1] * scale, last[2]});
        return path;
    }

    private static double jitter(Random random, long seed)
    {
        return seed == 0 ? 0 : (random.nextDouble() - 0.5) * 0.012;
    }

    static List<double[]> reversed(List<double[]> path)
    {
        List<double[]> out = new ArrayList<>(path);
        Collections.reverse(out);
        return out;
    }

    static List<double[]> mirrored(List<double[]> path)
    {
        List<double[]> out = new ArrayList<>();
        for (double[] point : path)
        {
            out.add(new double[]{-point[0], point[1], point[2]});
        }
        return out;
    }

    static List<double[]> turned(List<double[]> path, double degrees)
    {
        double radians = Math.toRadians(degrees);
        List<double[]> out = new ArrayList<>();
        for (double[] p : path)
        {
            out.add(new double[]{p[0] * Math.cos(radians) - p[2] * Math.sin(radians), p[1], p[0] * Math.sin(radians) + p[2] * Math.cos(radians)});
        }
        return out;
    }

    /**
     * @return A shape gesture of three jittered takes of the corners, drawn with the given hand.
     */
    static Gesture shape(String name, double[][] corners, int recordedHand, GestureHand hand)
    {
        Gesture gesture = new Gesture();
        gesture.setName(name);
        gesture.setKind(GestureKind.SHAPE);
        gesture.setHand(hand);
        for (long seed = 1; seed <= 3; seed++)
        {
            Take take = new Take();
            take.hand(recordedHand).addAll(samples(path(corners, seed, 1, 0)));
            gesture.getTakes().add(take);
        }
        return gesture;
    }

    /**
     * @return A two-hand gesture whose takes move the hands along the two paths.
     */
    static Gesture twoHand(String name, List<double[]> left, List<double[]> right)
    {
        Gesture gesture = new Gesture();
        gesture.setName(name);
        gesture.setKind(GestureKind.TWO_HAND);
        gesture.setHand(GestureHand.BOTH);
        for (int i = 0; i < 3; i++)
        {
            Take take = new Take();
            take.getLeft().addAll(samples(left));
            take.getRight().addAll(samples(right));
            gesture.getTakes().add(take);
        }
        return gesture;
    }

    static List<Sample> samples(List<double[]> path)
    {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < path.size(); i++)
        {
            double[] p = path.get(i);
            samples.add(new Sample(i / 90.0, p[0], p[1], p[2], 0, 0, 0, 1));
        }
        return samples;
    }

    static List<double[]> line(double[] from, double[] to, int points)
    {
        List<double[]> path = new ArrayList<>();
        for (int i = 0; i < points; i++)
        {
            double t = i / (points - 1.0);
            path.add(new double[]{from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t});
        }
        return path;
    }
}
