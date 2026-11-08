package com.tonic.vitavr.gestures.recognize;

import java.util.List;

/**
 * Hand paths made comparable: spaced evenly along their length, centred and sized alike, turned and compared point by
 * point in order, so a shape drawn backwards is another shape.
 */
public final class PathMath
{
    private PathMath()
    {
    }

    /**
     * @param path Points in the body frame, in the order drawn.
     * @return The path's length in metres.
     */
    public static double length(List<double[]> path)
    {
        double total = 0;
        for (int i = 1; i < path.size(); i++)
        {
            total += distance(path.get(i - 1), path.get(i));
        }
        return total;
    }

    /**
     * Spaces a path's points evenly along its length.
     *
     * @param path   Points in the order drawn; at least one.
     * @param points How many points to give back.
     * @return The evenly spaced points.
     */
    public static double[][] resample(List<double[]> path, int points)
    {
        double[][] out = new double[points][];
        double total = length(path);
        if (total < 1e-9 || path.size() == 1)
        {
            for (int i = 0; i < points; i++)
            {
                out[i] = path.get(0).clone();
            }
            return out;
        }
        double step = total / (points - 1);
        out[0] = path.get(0).clone();
        int made = 1;
        double walked = 0;
        for (int i = 1; i < path.size(); i++)
        {
            double[] from = path.get(i - 1);
            double[] to = path.get(i);
            double segment = distance(from, to);
            while (segment > 0 && made < points - 1 && walked + segment >= step * made)
            {
                double t = (step * made - walked) / segment;
                out[made++] = new double[]{from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t};
            }
            walked += segment;
        }
        while (made < points)
        {
            out[made++] = path.get(path.size() - 1).clone();
        }
        return out;
    }

    /**
     * Moves points so their centre is the origin and scales them so their root mean square distance from it is one.
     *
     * @param points The points, changed in place.
     * @return The scale the points were divided by.
     */
    public static double normalise(double[][] points)
    {
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (double[] point : points)
        {
            cx += point[0];
            cy += point[1];
            cz += point[2];
        }
        cx /= points.length;
        cy /= points.length;
        cz /= points.length;
        double sum = 0;
        for (double[] point : points)
        {
            point[0] -= cx;
            point[1] -= cy;
            point[2] -= cz;
            sum += point[0] * point[0] + point[1] * point[1] + point[2] * point[2];
        }
        double scale = Math.sqrt(sum / points.length);
        if (scale > 1e-9)
        {
            for (double[] point : points)
            {
                point[0] /= scale;
                point[1] /= scale;
                point[2] /= scale;
            }
        }
        return scale;
    }

    /**
     * @param points The points.
     * @param radians The turn about the vertical, left positive.
     * @return The points turned, as a new array.
     */
    public static double[][] turned(double[][] points, double radians)
    {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double[][] out = new double[points.length][];
        for (int i = 0; i < points.length; i++)
        {
            double[] p = points[i];
            out[i] = new double[]{p[0] * cos - p[2] * sin, p[1], p[0] * sin + p[2] * cos};
        }
        return out;
    }

    /**
     * @param points The points.
     * @return The points mirrored left to right, as a new array.
     */
    public static double[][] mirrored(double[][] points)
    {
        double[][] out = new double[points.length][];
        for (int i = 0; i < points.length; i++)
        {
            out[i] = new double[]{-points[i][0], points[i][1], points[i][2]};
        }
        return out;
    }

    /**
     * @param a One set of points.
     * @param b Another, the same length.
     * @return The mean distance between points at the same place in each.
     */
    public static double meanDistance(double[][] a, double[][] b)
    {
        double sum = 0;
        for (int i = 0; i < a.length; i++)
        {
            sum += distance(a[i], b[i]);
        }
        return sum / a.length;
    }

    /**
     * @param candidate A prepared path.
     * @param template  A prepared path of the same length.
     * @param degrees   How far either way about the vertical to try turning the candidate.
     * @param stepDegrees The step between turns tried.
     * @return The smallest mean distance over the turns tried.
     */
    public static double bestDistance(double[][] candidate, double[][] template, double degrees, double stepDegrees)
    {
        double best = Double.MAX_VALUE;
        for (double turn = -degrees; turn <= degrees + 1e-9; turn += stepDegrees)
        {
            best = Math.min(best, meanDistance(turned(candidate, Math.toRadians(turn)), template));
        }
        return best;
    }

    static double distance(double[] a, double[] b)
    {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
