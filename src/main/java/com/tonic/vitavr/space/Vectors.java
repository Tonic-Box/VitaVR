package com.tonic.vitavr.space;

/**
 * A collection of 3D vector helpers that return new vectors.
 */
public final class Vectors
{
    private static final double[] AHEAD = {0, 0, -1};

    private Vectors()
    {
    }

    public static double dot(double[] a, double[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b)
    {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double[] plus(double[] a, double[] b)
    {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] minus(double[] a, double[] b)
    {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double[] scale(double[] v, double by)
    {
        return new double[]{v[0] * by, v[1] * by, v[2] * by};
    }

    public static double length(double[] v)
    {
        return Math.sqrt(dot(v, v));
    }

    public static double distance(double[] a, double[] b)
    {
        return length(minus(a, b));
    }

    /**
     * Gets the direction of a vector.
     *
     * @param v The vector.
     * @return The vector at unit length, or -z if it is too short to have a direction.
     */
    public static double[] unit(double[] v)
    {
        double length = length(v);
        return length < 1e-9 ? AHEAD.clone() : scale(v, 1 / length);
    }

    public static double square(double value)
    {
        return value * value;
    }
}
