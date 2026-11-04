package com.tonic.vitavr.body;

/**
 * Two-bone IK that bends a posed model's arm to reach a point, with the elbow bent towards a pole.
 */
public final class ArmIk
{
    private static final double SLACK = 1e-3;

    private ArmIk()
    {
    }

    /**
     * Bends an arm in place, keeping the hand's turn from the forearm.
     *
     * @param x          The vertex x values (across), changed in place.
     * @param y          The vertex y values (down), changed in place.
     * @param z          The vertex z values (along), changed in place.
     * @param groups     The vertices by label.
     * @param arm        The arm labels.
     * @param target     The point to reach, in model space.
     * @param pole       The elbow bend direction, in model space, any length.
     * @return Whether the arm was bent.
     */
    public static boolean reach(float[] x, float[] y, float[] z, int[][] groups, ArmRig arm, double[] target, double[] pole)
    {
        return reach(x, y, z, groups, arm, target, pole, new int[0], null);
    }

    /**
     * Bends an arm in place, then turns the hand to a grip.
     *
     * @param x      The vertex x values (across), changed in place.
     * @param y      The vertex y values (down), changed in place.
     * @param z      The vertex z values (along), changed in place.
     * @param groups The vertices by label.
     * @param arm    The arm labels.
     * @param target The point to reach, in model space.
     * @param pole   The elbow bend direction, in model space, any length.
     * @param riding The extra vertices that move with the hand, such as a held weapon.
     * @param grip   The hand rotation from its model pose, row-major, or null to keep the forearm's.
     * @return Whether the arm was bent.
     */
    public static boolean reach(float[] x, float[] y, float[] z, int[][] groups, ArmRig arm, double[] target, double[] pole, int[] riding, double[][] grip)
    {
        double[] shoulder = centroid(x, y, z, groups, arm.shoulder());
        double[] elbow = centroid(x, y, z, groups, arm.elbow());
        double[] hand = centroid(x, y, z, groups, arm.hand());
        if (shoulder == null || elbow == null || hand == null)
        {
            return false;
        }
        double upper = distance(shoulder, elbow);
        double lower = distance(elbow, hand);
        double[] toTarget = minus(target, shoulder);
        double reach = length(toTarget);
        if (upper < SLACK || lower < SLACK || reach < SLACK)
        {
            return false;
        }
        double[] along = scale(toTarget, 1 / reach);
        reach = Math.max(Math.abs(upper - lower) + SLACK, Math.min(upper + lower - SLACK, reach));
        double[] across = minus(pole, scale(along, dot(pole, along)));
        if (length(across) < SLACK)
        {
            across = perpendicular(along);
        }
        across = scale(across, 1 / length(across));
        double toElbow = (upper * upper - lower * lower + reach * reach) / (2 * reach);
        double out = Math.sqrt(Math.max(0, upper * upper - toElbow * toElbow));
        double[] bentElbow = plus(shoulder, plus(scale(along, toElbow), scale(across, out)));
        double[] reached = plus(shoulder, scale(along, reach));

        double[][] first = turning(minus(elbow, shoulder), minus(bentElbow, shoulder));
        for (int label : arm.whole())
        {
            turn(x, y, z, groups, label, first, shoulder, shoulder);
        }
        turn(x, y, z, riding, first, shoulder, shoulder);
        double[] movedHand = plus(shoulder, apply(first, minus(hand, shoulder)));
        double[][] second = turning(minus(movedHand, bentElbow), minus(reached, bentElbow));
        for (int label : arm.belowElbow())
        {
            turn(x, y, z, groups, label, second, bentElbow, bentElbow);
        }
        turn(x, y, z, riding, second, bentElbow, bentElbow);
        if (grip != null)
        {
            double[][] wrist = times(grip, transposed(times(second, first)));
            turn(x, y, z, groups, arm.hand(), wrist, reached, reached);
            turn(x, y, z, riding, wrist, reached, reached);
        }
        return true;
    }

    /** Gets the mean of a label's vertices, or null if it has none. */
    static double[] centroid(float[] x, float[] y, float[] z, int[][] groups, int label)
    {
        if (label >= groups.length || groups[label] == null || groups[label].length == 0)
        {
            return null;
        }
        double[] sum = new double[3];
        for (int vertex : groups[label])
        {
            sum[0] += x[vertex];
            sum[1] += y[vertex];
            sum[2] += z[vertex];
        }
        return scale(sum, 1.0 / groups[label].length);
    }

    /** Rotates a label's vertices about a pivot and moves the pivot. */
    private static void turn(float[] x, float[] y, float[] z, int[][] groups, int label, double[][] rotation, double[] pivot, double[] movedPivot)
    {
        if (label >= groups.length || groups[label] == null)
        {
            return;
        }
        turn(x, y, z, groups[label], rotation, pivot, movedPivot);
    }

    /** Rotates vertices about a pivot and moves the pivot. */
    private static void turn(float[] x, float[] y, float[] z, int[] vertices, double[][] rotation, double[] pivot, double[] movedPivot)
    {
        for (int vertex : vertices)
        {
            double[] moved = plus(movedPivot, apply(rotation, new double[] {x[vertex] - pivot[0], y[vertex] - pivot[1], z[vertex] - pivot[2]}));
            x[vertex] = (float) moved[0];
            y[vertex] = (float) moved[1];
            z[vertex] = (float) moved[2];
        }
    }

    /** Gets the shortest rotation from one direction to another, row-major. */
    static double[][] turning(double[] from, double[] to)
    {
        double[] a = scale(from, 1 / length(from));
        double[] b = scale(to, 1 / length(to));
        double[] axis = cross(a, b);
        double sine = length(axis);
        double cosine = dot(a, b);
        if (sine < 1e-9)
        {
            if (cosine > 0)
            {
                return new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            }
            axis = perpendicular(a);
            sine = 0;
            cosine = -1;
        }
        else
        {
            axis = scale(axis, 1 / sine);
        }
        double k = 1 - cosine;
        double ax = axis[0];
        double ay = axis[1];
        double az = axis[2];
        return new double[][] {
                {cosine + ax * ax * k, ax * ay * k - az * sine, ax * az * k + ay * sine},
                {ay * ax * k + az * sine, cosine + ay * ay * k, ay * az * k - ax * sine},
                {az * ax * k - ay * sine, az * ay * k + ax * sine, cosine + az * az * k}};
    }

    /** Multiplies two rotations; b is applied first. */
    static double[][] times(double[][] a, double[][] b)
    {
        double[][] out = new double[3][3];
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                out[row][column] = a[row][0] * b[0][column] + a[row][1] * b[1][column] + a[row][2] * b[2][column];
            }
        }
        return out;
    }

    /** Gets a rotation's inverse by transposing it. */
    static double[][] transposed(double[][] m)
    {
        return new double[][] {{m[0][0], m[1][0], m[2][0]}, {m[0][1], m[1][1], m[2][1]}, {m[0][2], m[1][2], m[2][2]}};
    }

    static double[] apply(double[][] m, double[] v)
    {
        return new double[] {m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2], m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    private static double[] perpendicular(double[] v)
    {
        double[] other = Math.abs(v[0]) < 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        double[] out = cross(v, other);
        return scale(out, 1 / length(out));
    }

    static double[] cross(double[] a, double[] b)
    {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] plus(double[] a, double[] b)
    {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] minus(double[] a, double[] b)
    {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] scale(double[] v, double by)
    {
        return new double[] {v[0] * by, v[1] * by, v[2] * by};
    }

    private static double length(double[] v)
    {
        return Math.sqrt(dot(v, v));
    }

    private static double distance(double[] a, double[] b)
    {
        return length(minus(a, b));
    }
}
