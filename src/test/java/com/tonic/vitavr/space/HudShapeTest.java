package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the interface column shape, pointer hits and recutting.
 */
class HudShapeTest
{
    private static final int WIDTH = 1760;
    private static final int HEIGHT = 880;
    private static final int LEFT_PIXELS = 520;
    private static final int RIGHT_PIXELS = 260;

    @Test
    void aPointerFromTheHeadFindsThePixelInEachColumn()
    {
        for (boolean curved : new boolean[] {true, false})
        {
            HudShape shape = shaped(curved);
            Pose head = new Pose(0.2, 1.5, -0.1, 0, Math.sin(0.35), 0, Math.cos(0.35));
            shape.place(head);
            for (int column = 0; column < HudShape.COLUMNS; column++)
            {
                int width = shape.width(column);
                double[][][] strips = shape.strips(width);
                for (int pixel : new int[] {3, width / 2, width - 4})
                {
                    double[][] strip = strips[(curved ? column * width : column) + (curved ? pixel : 0)];
                    double across = curved ? 0.5 : (pixel + 0.5) / width;
                    double[] point = between(strip, across, 0.3);
                    double[] hit = shape.crossing(aimedAt(head, point));
                    String where = (curved ? "curved" : "flat") + " column " + column + " pixel " + pixel;
                    assertEquals(shape.fromPixel(column) + pixel, hit[1], 1, where + " across");
                    assertEquals(HEIGHT * 0.3, hit[2], 1, where + " down");
                    assertEquals(1, hit[3], where + " is on it");
                }
            }
        }
    }

    @Test
    void betweenTheColumnsItFindsNothingAndTheSidesStandOut()
    {
        HudShape shape = shaped(true);
        Pose head = new Pose(0, 1.6, 0, 0, 0, 0, 1);
        shape.place(head);
        double[][][] columns = shape.strips(1);
        double[] gap = between(new double[][] {columns[0][1], columns[1][0], columns[1][3], columns[0][2]}, 0.5, 0.5);
        double[] hit = shape.crossing(aimedAt(head, gap));
        assertEquals(-1, hit[1], "between the chat side and the middle");
        assertEquals(0, hit[3]);
        assertTrue(shape.tall(HudShape.RIGHT) > shape.tall(HudShape.MIDDLE), "the inventory side is drawn larger");
        assertEquals(shape.tall(HudShape.LEFT), shape.tall(HudShape.MIDDLE), 1e-12, "the chat side at the middle's size");

        shape.columns(400, 330);
        assertEquals(1430, shape.fromPixel(HudShape.RIGHT), "cut anew");
        double[][][] recut = shape.strips(1);
        double[] inventory = between(recut[HudShape.RIGHT], 0.5, 0.5);
        double[] onIt = shape.crossing(aimedAt(head, inventory));
        assertEquals(1430 + 165, onIt[1], 1, "the pixel in the inventory side, cut anew");

        shape.clear();
        assertNull(shape.crossing(head), "taken down");
    }

    @Test
    void joinedTheSidesMeetTheMiddleWhereItStands()
    {
        HudShape shape = shaped(true);
        Pose head = new Pose(0.3, 1.6, 0.2, 0, Math.sin(0.2), 0, Math.cos(0.2));
        shape.place(head);
        double middleYaw = shape.yawOf(HudShape.MIDDLE);
        shape.sides(1, 1, 0);
        assertTrue(shape.placed(), "still up");
        assertEquals(middleYaw, shape.yawOf(HudShape.MIDDLE), 1e-12, "the middle stands where it stood");
        assertEquals(shape.tall(HudShape.MIDDLE), shape.tall(HudShape.RIGHT), 1e-12, "the inventory side at the middle's size");
        assertEquals((shape.angle(HudShape.LEFT) + shape.angle(HudShape.MIDDLE)) / 2, shape.yawOf(HudShape.LEFT) - shape.yawOf(HudShape.MIDDLE), 1e-12, "the chat side meets the middle");
        assertEquals((shape.angle(HudShape.MIDDLE) + shape.angle(HudShape.RIGHT)) / 2, shape.yawOf(HudShape.MIDDLE) - shape.yawOf(HudShape.RIGHT), 1e-12, "the inventory side meets the middle");
        double[][][] strips = shape.strips(1);
        double[] seam = between(strips[HudShape.LEFT], 0.999, 0.5);
        assertEquals(LEFT_PIXELS - 1, shape.crossing(aimedAt(head, seam))[1], 1, "no gap at the seam");
    }

    @Test
    void cutAnewEveryPixelLeftInItsColumnStaysWhereItStood()
    {
        HudShape shape = shaped(true);
        shape.place(Pose.ORIGIN);
        int[] pixels = {10, 300, 880, 1200, 1700};
        double[] before = new double[pixels.length];
        for (int at = 0; at < pixels.length; at++)
        {
            before[at] = angleOf(shape, pixels[at]);
        }
        double inventoryTall = shape.tall(HudShape.RIGHT);

        shape.columns(LEFT_PIXELS - 80, RIGHT_PIXELS + 70);
        for (int at = 0; at < pixels.length; at++)
        {
            assertEquals(before[at], angleOf(shape, pixels[at]), 1e-12, "pixel " + pixels[at] + " stays where it stood");
        }
        assertEquals(inventoryTall, shape.tall(HudShape.RIGHT), 1e-12, "the inventory side keeps its size");
        assertTrue(angleOf(shape, WIDTH - RIGHT_PIXELS - 30) > angleOf(shape, WIDTH - RIGHT_PIXELS - 71), "the pixels handed to the inventory side run on from the middle's");

        shape.columns(LEFT_PIXELS, RIGHT_PIXELS);
        assertEquals(before[2], angleOf(shape, pixels[2]), 1e-12, "and cut back, the middle is where it was");
    }

    /** Gets a pixel's angle from the panel's facing, rightwards positive, for a panel facing -z. */
    private static double angleOf(HudShape shape, int pixel)
    {
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            int from = shape.fromPixel(column);
            if (pixel >= from && pixel < from + shape.width(column))
            {
                double middle = -shape.yawOf(column);
                return middle - shape.angle(column) / 2 + (pixel - from + 0.5) * shape.angle(column) / shape.width(column);
            }
        }
        throw new IllegalArgumentException("pixel " + pixel + " is in no column");
    }

    private static HudShape shaped(boolean curved)
    {
        HudShape shape = new HudShape(WIDTH, HEIGHT, LEFT_PIXELS, RIGHT_PIXELS, curved);
        shape.size(1.4, 0.00065, 1.0, 1.4, Math.toRadians(10));
        return shape;
    }

    /** Gets a point on a quad, as fractions across and down. */
    private static double[] between(double[][] corners, double across, double down)
    {
        double[] out = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            double top = corners[0][axis] + (corners[1][axis] - corners[0][axis]) * across;
            double bottom = corners[3][axis] + (corners[2][axis] - corners[3][axis]) * across;
            out[axis] = top + (bottom - top) * down;
        }
        return out;
    }

    /** Gets a pose at the head aimed at the point. */
    private static Pose aimedAt(Pose head, double[] point)
    {
        double dx = point[0] - head.x();
        double dy = point[1] - head.y();
        double dz = point[2] - head.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double yaw = Math.atan2(-dx, -dz);
        double pitch = Math.asin(dy / length);
        double sinYaw = Math.sin(yaw / 2);
        double cosYaw = Math.cos(yaw / 2);
        double sinPitch = Math.sin(pitch / 2);
        double cosPitch = Math.cos(pitch / 2);
        return new Pose(head.x(), head.y(), head.z(), cosYaw * sinPitch, sinYaw * cosPitch, -sinYaw * sinPitch, cosYaw * cosPitch);
    }
}
