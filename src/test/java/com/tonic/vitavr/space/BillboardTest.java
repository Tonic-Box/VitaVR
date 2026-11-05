package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests billboard placement, facing and pointer hits.
 */
class BillboardTest
{
    private static final double PIXEL = 0.001;

    @Test
    void aPointerFromTheHeadThroughAPixelFindsIt()
    {
        Pose head = new Pose(0.3, 1.6, 0.2, 0, 0, 0, 1);
        double[][] points = {{0.3, 1.6, -0.8}, {1.1, 1.1, -0.4}, {-0.6, 2.2, 0.9}};
        for (double[] point : points)
        {
            Billboard board = new Billboard();
            board.stand(point[0], point[1], point[2], head, 200, 100, PIXEL, 100, 10);
            for (int[] pixel : new int[][] {{100, 10}, {20, 80}, {190, 5}})
            {
                double[] at = pixelInRoom(board, pixel[0] + 0.5, pixel[1] + 0.5);
                Pose aim = aimedAt(head, at);
                assertArrayEquals(pixel, board.crossing(aim), "pixel " + pixel[0] + "," + pixel[1] + " from " + point[0] + "," + point[1] + "," + point[2]);
            }
            double[] anchor = pixelInRoom(board, 100, 10);
            assertArrayEquals(point, anchor, 1e-9, "the given pixel is on the point");
        }
    }

    @Test
    void itFacesTheHead()
    {
        Pose head = new Pose(0, 1.6, 0, 0, 0, 0, 1);
        Billboard board = new Billboard();
        board.stand(0.5, 0.9, -0.5, head, 10, 10, PIXEL, 5, 5);
        double[] turn = board.orientation();
        Pose facing = new Pose(0, 0, 0, turn[0], turn[1], turn[2], turn[3]);
        double[] normal = new double[3];
        facing.rotate(0, 0, 1, normal);
        double[] toHead = {0 - 0.5, 1.6 - 0.9, 0 + 0.5};
        double length = Math.sqrt(toHead[0] * toHead[0] + toHead[1] * toHead[1] + toHead[2] * toHead[2]);
        for (int axis = 0; axis < 3; axis++)
        {
            assertEquals(toHead[axis] / length, normal[axis], 1e-9, "its front turned to the head, axis " + axis);
        }
        assertEquals(0.01, board.metresWide(), 1e-12);
    }

    private static double[] pixelInRoom(Billboard board, double px, double py)
    {
        double[][] corners = new double[4][3];
        board.corners(corners);
        double u = px / (board.metresWide() / PIXEL);
        double v = py / (board.metresTall() / PIXEL);
        double[] out = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            double top = corners[0][axis] + (corners[1][axis] - corners[0][axis]) * u;
            double bottom = corners[3][axis] + (corners[2][axis] - corners[3][axis]) * u;
            out[axis] = top + (bottom - top) * v;
        }
        return out;
    }

    /** Gets a pose at the head with its -z aimed at the point. */
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
