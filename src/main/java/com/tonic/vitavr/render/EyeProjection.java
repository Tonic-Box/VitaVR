package com.tonic.vitavr.render;

import net.runelite.api.Projection;

/**
 * Projects world points relative to the head as across, down and depth, undivided, for the GPU plugin's culling and sorting.
 */
final class EyeProjection implements Projection
{
    private final double eyeX;
    private final double eyeY;
    private final double eyeZ;
    private final double[] right;
    private final double[] down;
    private final double[] forward;

    /**
     * Creates a projection from the head's position and axes.
     * @param eye The head position, across, down and along.
     * @param right The head's right axis in the world.
     * @param down The head's down axis in the world.
     * @param forward The head's forward axis in the world.
     */
    EyeProjection(double[] eye, double[] right, double[] down, double[] forward)
    {
        this.eyeX = eye[0];
        this.eyeY = eye[1];
        this.eyeZ = eye[2];
        this.right = right;
        this.down = down;
        this.forward = forward;
    }

    @Override
    public float[] project(float x, float y, float z)
    {
        return project(x, y, z, new float[3]);
    }

    @Override
    public float[] project(float x, float y, float z, float[] out)
    {
        double dx = x - eyeX;
        double dy = y - eyeY;
        double dz = z - eyeZ;
        out[0] = (float) (right[0] * dx + right[1] * dy + right[2] * dz);
        out[1] = (float) (down[0] * dx + down[1] * dy + down[2] * dz);
        out[2] = (float) (forward[0] * dx + forward[1] * dy + forward[2] * dz);
        return out;
    }
}
