package com.tonic.vitavr.render;

import com.tonic.vitavr.space.Vectors;
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

    /**
     * Gets this projection turned to face a point, from the same eye, so a model there lies wholly ahead of it.
     * Points behind a projection turn its winding over, which culled the wrong faces of actors below a raised head.
     *
     * @param x The point, across.
     * @param y The point, down.
     * @param z The point, along.
     * @return The turned projection, or this one for a point at the eye.
     */
    EyeProjection facing(double x, double y, double z)
    {
        double[] towards = {x - eyeX, y - eyeY, z - eyeZ};
        double away = Vectors.length(towards);
        if (away < 1)
        {
            return this;
        }
        double[] ahead = Vectors.scale(towards, 1 / away);
        double[] reference = Math.abs(Vectors.dot(ahead, down)) > 0.99 ? forward : down;
        // Keep the head's handedness so faces wind the same way
        double handed = Math.signum(Vectors.dot(Vectors.cross(down, forward), right));
        double[] across = Vectors.scale(Vectors.unit(Vectors.cross(reference, ahead)), handed);
        double[] below = Vectors.scale(Vectors.cross(ahead, across), handed);
        return new EyeProjection(new double[]{eyeX, eyeY, eyeZ}, across, below, ahead);
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
