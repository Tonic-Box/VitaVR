package com.tonic.vitavr.space;

/**
 * Represents an eye or pointer in the game world, with its position and right, down and forward directions.
 */
public final class View
{
    /** The near plane, in game units. */
    private static final double NEAR = 5.0;
    /** The GPU plugin's near plane, used to scale the matrix so its depth bias still fits. */
    private static final double GPU_NEAR = 50.0;

    final double[] eye = new double[3];
    final double[] right = new double[3];
    final double[] down = new double[3];
    final double[] forward = new double[3];

    /**
     * Moves the view without turning it.
     *
     * @param across The distance across.
     * @param down   The distance down.
     * @param along  The distance along.
     */
    public void shift(double across, double down, double along)
    {
        eye[0] += across;
        eye[1] += down;
        eye[2] += along;
    }

    public double eyeX()
    {
        return eye[0];
    }

    public double eyeY()
    {
        return eye[1];
    }

    public double eyeZ()
    {
        return eye[2];
    }

    /**
     * @param out Receives the forward direction, across, down and along.
     */
    public void forward(double[] out)
    {
        System.arraycopy(forward, 0, out, 0, 3);
    }

    /**
     * @param out Receives the right direction.
     */
    public void right(double[] out)
    {
        System.arraycopy(right, 0, out, 0, 3);
    }

    /**
     * @param out Receives the down direction.
     */
    public void down(double[] out)
    {
        System.arraycopy(down, 0, out, 0, 3);
    }

    /**
     * Gets the yaw of a client camera facing the same way.
     *
     * @return The yaw in radians, as the GPU plugin uses it.
     */
    public double yaw()
    {
        return Math.atan2(-forward[0], forward[2]);
    }

    /**
     * Gets the pitch of a client camera facing the same way.
     *
     * @return The pitch in radians, down positive.
     */
    public double pitch()
    {
        return Math.asin(Math.max(-1, Math.min(1, forward[1])));
    }

    /**
     * @return The near plane distance, in game units.
     */
    public static double nearPlane()
    {
        return NEAR;
    }

    /**
     * Converts a worldToClip depth to a distance ahead of the view.
     *
     * @param depth The depth, 0 to 1, greater nearer.
     * @return The distance along the forward direction, in game units.
     */
    public static double distanceAt(double depth)
    {
        return 2 * NEAR / depth;
    }

    /**
     * Builds the world to clip matrix for this view, with reverse depth and no far plane as the GPU plugin uses.
     *
     * @param fov The eye's field of view.
     * @return The matrix, column-major.
     */
    public float[] worldToClip(Fov fov)
    {
        double tanLeft = Math.tan(fov.left());
        double tanRight = Math.tan(fov.right());
        double tanUp = Math.tan(fov.up());
        double tanDown = Math.tan(fov.down());
        double across = 2 / (tanRight - tanLeft);
        double skewAcross = (tanRight + tanLeft) / (tanRight - tanLeft);
        double vertical = 2 / (tanUp - tanDown);
        double skewVertical = (tanUp + tanDown) / (tanUp - tanDown);

        double eyeRight = dot(right, eye);
        double eyeDown = dot(down, eye);
        double eyeForward = dot(forward, eye);

        // Depth is 2 * near / distance; scale so the shader depth bias matches the GPU plugin's near plane
        double scale = GPU_NEAR / NEAR;
        double[][] rows = {
                {across * right[0] - skewAcross * forward[0], across * right[1] - skewAcross * forward[1], across * right[2] - skewAcross * forward[2],
                        -across * eyeRight + skewAcross * eyeForward},
                {-vertical * down[0] - skewVertical * forward[0], -vertical * down[1] - skewVertical * forward[1], -vertical * down[2] - skewVertical * forward[2],
                        vertical * eyeDown + skewVertical * eyeForward},
                {0, 0, 0, 2 * NEAR},
                {forward[0], forward[1], forward[2], -eyeForward},
        };
        float[] matrix = new float[16];
        for (int row = 0; row < 4; row++)
        {
            for (int column = 0; column < 4; column++)
            {
                matrix[column * 4 + row] = (float) (rows[row][column] * scale);
            }
        }
        return matrix;
    }

    private static double dot(double[] a, double[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
