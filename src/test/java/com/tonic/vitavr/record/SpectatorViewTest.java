package com.tonic.vitavr.record;

import com.tonic.vitavr.space.Pose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the steadied spectator camera. */
class SpectatorViewTest
{
    private static final double FRAME = 1 / 90.0;

    @Test
    void itLooksTheWayTheHeadLooksWithoutItsRoll()
    {
        double yaw = 0.8;
        double pitch = -0.3;
        double roll = 0.5;
        Pose head = new Pose(0.2, 1.6, -0.4, 0, 0, 0, 1);
        head = turned(head, yaw, pitch, roll);
        Pose steadied = new SpectatorView().follow(head, FRAME);

        double[] headAhead = new double[3];
        double[] ahead = new double[3];
        head.rotate(0, 0, -1, headAhead);
        steadied.rotate(0, 0, -1, ahead);
        for (int axis = 0; axis < 3; axis++)
        {
            assertEquals(headAhead[axis], ahead[axis], 1e-9, "the same way, axis " + axis);
        }
        double[] right = new double[3];
        steadied.rotate(1, 0, 0, right);
        assertEquals(0, right[1], 1e-9, "level: its right stays flat");
        assertEquals(1.6, steadied.y(), 1e-9, "where the head is");
    }

    /** Turns a pose by yaw, then pitch, then roll. */
    private static Pose turned(Pose at, double yaw, double pitch, double roll)
    {
        double[] q = multiply(multiply(new double[] {0, Math.sin(yaw / 2), 0, Math.cos(yaw / 2)}, new double[] {Math.sin(pitch / 2), 0, 0, Math.cos(pitch / 2)}),
                new double[] {0, 0, Math.sin(roll / 2), Math.cos(roll / 2)});
        return new Pose(at.x(), at.y(), at.z(), q[0], q[1], q[2], q[3]);
    }

    private static double[] multiply(double[] a, double[] b)
    {
        return new double[] {
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }
}
