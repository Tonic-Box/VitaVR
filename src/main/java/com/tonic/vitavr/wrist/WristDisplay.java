package com.tonic.vitavr.wrist;

import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import lombok.Getter;

import java.time.LocalTime;
import java.util.function.IntFunction;

/**
 * A left wrist plate showing the orbs, compass and time, shown when the wrist is turned to the eyes.
 */
public final class WristDisplay
{
    private static final double WIDE_METRES = 0.09;
    private static final double METRES_PER_PIXEL = WIDE_METRES / WristPicture.WIDTH;
    private static final double GLANCE_FROM = 0.5;
    private static final double GLANCE_FULL = 0.75;
    private static final double SHOW_SECONDS = 0.15;
    private static final double HIDE_SECONDS = 0.25;
    private static final double POINTABLE = 0.5;

    private final WristPicture picture = new WristPicture();
    private final WristPlate plate = new WristPlate(WristPicture.WIDTH, WristPicture.HEIGHT, METRES_PER_PIXEL);
    private final double[][] roomCorners = new double[4][3];
    /** Visibility from 0 hidden to 1 shown. */
    @Getter
    private double shown;

    /**
     * Repaints the plate from the interfaces image.
     * @param interfaces The interfaces pixels as ARGB, top row first.
     * @param width The interfaces width.
     * @param height The interfaces height.
     * @param boxes A component's drawn {x, y, width, height}, or null if not drawn.
     * @param time The time of day shown.
     * @return The plate pixels as premultiplied ARGB, top row first.
     */
    public int[] paint(int[] interfaces, int width, int height, IntFunction<int[]> boxes, LocalTime time)
    {
        picture.paint(interfaces, width, height, boxes, time);
        return picture.pixels();
    }

    /**
     * Places the plate on the left wrist and fades it in or out by how the wrist faces the head.
     * @param held The left hand pose in the room, or null if not tracked.
     * @param head The head pose in the room.
     * @param seconds The time since the last frame.
     */
    public void place(Pose held, Pose head, double seconds)
    {
        plate.place(held);
        double wanted = head == null ? 0 : glance(plate.facing(head.x(), head.y(), head.z()));
        double step = seconds / (wanted > shown ? SHOW_SECONDS : HIDE_SECONDS);
        shown = wanted > shown ? Math.min(wanted, shown + step) : Math.max(wanted, shown - step);
    }

    /**
     * Moves the plate onto the wrist as drawn this frame, keeping its visibility.
     * @param at The plate centre in the room.
     * @param along The unit direction along the forearm towards the hand.
     * @param out The unit direction out of the wrist, perpendicular to along.
     */
    public void placeOn(double[] at, double[] along, double[] out)
    {
        plate.placeOn(at, along, out);
    }

    /**
     * Gets the plate visibility for how squarely the wrist faces the eyes.
     * @param facing The facing, -1 away to 1 straight at them.
     * @return The visibility, 0 hidden to 1 shown.
     */
    static double glance(double facing)
    {
        double along = Math.max(0, Math.min(1, (facing - GLANCE_FROM) / (GLANCE_FULL - GLANCE_FROM)));
        return along * along * (3 - 2 * along);
    }

    /**
     * Checks whether a pointer is on the plate while it is pointable.
     * @param aim The pointer pose in the room.
     * @return Whether the pointer is on the plate.
     */
    public boolean takes(Pose aim)
    {
        return shown >= POINTABLE && plate.crossing(aim) != null;
    }

    /**
     * Gets where a pointer crosses the plate while it is pointable.
     * @param aim The pointer pose in the room.
     * @return The plate pixel {x, y}, or null if not on it.
     */
    public int[] crossing(Pose aim)
    {
        return shown >= POINTABLE ? plate.crossing(aim) : null;
    }

    /**
     * Gets the interfaces point a press lands on: the centre of the orb under the pointer.
     * @param aim The pointer pose in the room.
     * @return The interfaces pixel, or null if not on an orb.
     */
    public int[] pressAt(Pose aim)
    {
        int[] on = shown >= POINTABLE ? plate.crossing(aim) : null;
        return on == null ? null : picture.pressAt(on[0], on[1]);
    }

    /**
     * Gets the pointer distance to the plate.
     * @param aim The pointer pose in the room.
     * @return The distance in metres, or infinity if it misses or the plate is hidden.
     */
    public double reach(Pose aim)
    {
        return shown >= POINTABLE ? plate.reach(aim) : Double.POSITIVE_INFINITY;
    }

    /**
     * Gets the plate corners in the world.
     * @param room The room to world transform.
     * @param lift The eye lift of the room space, in metres.
     * @param out Receives the top left, top right, bottom right and bottom left corners.
     * @return Whether the plate should be drawn.
     */
    public boolean corners(RoomToWorld room, double lift, double[][] out)
    {
        if (shown <= 0 || !plate.placed())
        {
            return false;
        }
        plate.corners(roomCorners);
        for (int corner = 0; corner < 4; corner++)
        {
            room.point(roomCorners[corner][0], roomCorners[corner][1] + lift, roomCorners[corner][2], out[corner]);
        }
        return true;
    }
}
