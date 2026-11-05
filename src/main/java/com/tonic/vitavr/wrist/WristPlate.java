package com.tonic.vitavr.wrist;

import com.tonic.vitavr.space.Billboard;
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
    private final Billboard board = new Billboard();
    private final double[] centre = new double[3];
    private final double[] forward = new double[3];
    private final double[] up = new double[3];
    private final double[] out = new double[3];

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
            board.clear();
            return;
        }
        held.rotate(0, 0, -1, forward);
        held.rotate(0, 1, 0, up);
        held.rotate(1, 0, 0, out);
        centre[0] = held.x() - forward[0] * BACK_METRES - up[0] * DOWN_METRES + out[0] * OUT_METRES;
        centre[1] = held.y() - forward[1] * BACK_METRES - up[1] * DOWN_METRES + out[1] * OUT_METRES;
        centre[2] = held.z() - forward[2] * BACK_METRES - up[2] * DOWN_METRES + out[2] * OUT_METRES;
        layOn(centre, forward, out);
    }

    /**
     * Places the plate on the wrist as drawn.
     * @param at The plate centre in the room.
     * @param along The unit direction along the forearm towards the hand.
     * @param out The unit direction out of the wrist, perpendicular to along.
     */
    public void placeOn(double[] at, double[] along, double[] out)
    {
        layOn(at, along, out);
    }

    /**
     * Lays the plate with its top along the forearm, facing out of the wrist.
     */
    private void layOn(double[] at, double[] along, double[] facing)
    {
        double[] across = {along[1] * facing[2] - along[2] * facing[1], along[2] * facing[0] - along[0] * facing[2], along[0] * facing[1] - along[1] * facing[0]};
        board.lay(at, across, along, width, height, metresPerPixel);
    }

    /** @return Whether the plate is placed on a tracked wrist. */
    public boolean placed()
    {
        return board.placed();
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
        return board.facing(x, y, z);
    }

    /**
     * Gets where a pointer crosses the plate from its front.
     * @param aim The pointer pose in the room.
     * @return The pixel {x, y}, or null if not placed or missed.
     */
    public int[] crossing(Pose aim)
    {
        return board.crossing(aim);
    }

    /**
     * Gets the pointer distance to the plate.
     * @param aim The pointer pose in the room.
     * @return The distance in metres, or infinity if it misses.
     */
    public double reach(Pose aim)
    {
        return board.reach(aim);
    }

    /**
     * Gets the plate corners in the room.
     * @param out Receives the top left, top right, bottom right and bottom left corners.
     */
    public void corners(double[][] out)
    {
        board.corners(out);
    }
}
