package com.tonic.vitavr.wrist;

import com.tonic.vitavr.space.Pose;

/**
 * Places the wrist image flat on the inside of the left wrist, its top towards the hand.
 */
public final class WristPlate
{
    static final double BACK_METRES = 0.13;
    static final double DOWN_METRES = 0.03;
    static final double OUT_METRES = 0.035;

    private final int width;
    private final int height;
    private final double metresPerPixel;
    private final double[] centre = new double[3];
    private final double[] right = new double[3];
    private final double[] up = new double[3];
    private final double[] normal = new double[3];
    private final double[] direction = new double[3];
    private final double[] hit = new double[3];
    private boolean placed;

    /**
     * Creates a plate of the given size.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param metresPerPixel The size of a pixel in metres.
     * @throws IllegalArgumentException If a size is not positive or metresPerPixel is not above zero.
     */
    public WristPlate(int width, int height, double metresPerPixel)
    {
        if (width < 1 || height < 1)
        {
            throw new IllegalArgumentException("width and height must be positive");
        }
        if (!(metresPerPixel > 0))
        {
            throw new IllegalArgumentException("metresPerPixel must be above zero");
        }
        this.width = width;
        this.height = height;
        this.metresPerPixel = metresPerPixel;
    }

    /**
     * Places the plate on the wrist behind a held controller.
     * @param held The controller aim in the room, or null to clear the plate.
     */
    public void place(Pose held)
    {
        if (held == null)
        {
            placed = false;
            return;
        }
        double[] forward = new double[3];
        held.rotate(0, 0, -1, forward);
        held.rotate(0, 1, 0, up);
        held.rotate(1, 0, 0, normal);
        System.arraycopy(forward, 0, right, 0, 3);
        double[] at = {held.x(), held.y(), held.z()};
        for (int axis = 0; axis < 3; axis++)
        {
            centre[axis] = at[axis] - forward[axis] * BACK_METRES - up[axis] * DOWN_METRES + normal[axis] * OUT_METRES;
        }
        placed = true;
    }

    /**
     * Places the plate on the wrist as drawn.
     * @param at The plate centre in the room.
     * @param along The unit direction along the forearm towards the hand.
     * @param out The unit direction out of the wrist, perpendicular to along.
     */
    public void placeOn(double[] at, double[] along, double[] out)
    {
        System.arraycopy(at, 0, centre, 0, 3);
        System.arraycopy(along, 0, right, 0, 3);
        System.arraycopy(out, 0, normal, 0, 3);
        up[0] = out[1] * along[2] - out[2] * along[1];
        up[1] = out[2] * along[0] - out[0] * along[2];
        up[2] = out[0] * along[1] - out[1] * along[0];
        placed = true;
    }

    /** @return Whether the plate is placed on a tracked wrist. */
    public boolean placed()
    {
        return placed;
    }

    /**
     * Gets how squarely the plate faces a point in the room.
     * @param x The point x.
     * @param y The point y.
     * @param z The point z.
     * @return The cosine of the facing angle, or -1 if not placed.
     */
    public double facing(double x, double y, double z)
    {
        if (!placed)
        {
            return -1;
        }
        double dx = x - centre[0];
        double dy = y - centre[1];
        double dz = z - centre[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9)
        {
            return -1;
        }
        return (dx * normal[0] + dy * normal[1] + dz * normal[2]) / length;
    }

    /**
     * Gets where a pointer crosses the plate from its front.
     * @param aim The pointer pose in the room.
     * @return The pixel {x, y}, or null if not placed or missed.
     */
    public int[] crossing(Pose aim)
    {
        double[] met = hit(aim);
        return met == null ? null : new int[] {(int) met[1], (int) met[2]};
    }

    /**
     * Gets the pointer distance to the plate.
     * @param aim The pointer pose in the room.
     * @return The distance in metres, or infinity if it misses.
     */
    public double reach(Pose aim)
    {
        double[] met = hit(aim);
        return met == null ? Double.POSITIVE_INFINITY : met[0];
    }

    private double[] hit(Pose aim)
    {
        if (!placed || aim == null)
        {
            return null;
        }
        aim.rotate(0, 0, -1, direction);
        double facing = direction[0] * normal[0] + direction[1] * normal[1] + direction[2] * normal[2];
        if (facing >= -1e-6)
        {
            return null;
        }
        double t = ((centre[0] - aim.x()) * normal[0] + (centre[1] - aim.y()) * normal[1] + (centre[2] - aim.z()) * normal[2]) / facing;
        if (t <= 0)
        {
            return null;
        }
        double x = aim.x() + direction[0] * t - centre[0];
        double y = aim.y() + direction[1] * t - centre[1];
        double z = aim.z() + direction[2] * t - centre[2];
        double px = (x * right[0] + y * right[1] + z * right[2]) / metresPerPixel + width / 2.0;
        double py = height / 2.0 - (x * up[0] + y * up[1] + z * up[2]) / metresPerPixel;
        if (px < 0 || py < 0 || px >= width || py >= height)
        {
            return null;
        }
        hit[0] = t;
        hit[1] = Math.floor(px);
        hit[2] = Math.floor(py);
        return hit;
    }

    /**
     * Gets the plate corners in the room.
     * @param out Receives the top left, top right, bottom right and bottom left corners.
     */
    public void corners(double[][] out)
    {
        double halfWide = width * metresPerPixel / 2;
        double halfTall = height * metresPerPixel / 2;
        int[][] signs = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        for (int corner = 0; corner < 4; corner++)
        {
            for (int axis = 0; axis < 3; axis++)
            {
                out[corner][axis] = centre[axis] + right[axis] * signs[corner][0] * halfWide + up[axis] * signs[corner][1] * halfTall;
            }
        }
    }

    public double[] centre()
    {
        return centre;
    }
}
