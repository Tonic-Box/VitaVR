package com.tonic.vitavr.interfaces.pointer;

import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.View;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerProbeTest
{
    private static final double EPSILON = 1e-3;
    private static final double REACH = 1 << 15;
    private static final Fov SYMMETRIC = new Fov(-Math.toRadians(45), Math.toRadians(45), Math.toRadians(45), -Math.toRadians(45));

    @Test
    void rayCrossingTheViewIsWalkedOnlyWhereTheEyeSeesIt()
    {
        double[] span = new double[2];

        assertTrue(PointerProbe.span(eyeMatrix(), new double[]{-1000, 0, 500, 1, 0, 0}, 0, REACH, span));
        assertEquals(500, span[0], EPSILON);
        assertEquals(1500, span[1], EPSILON);
    }

    @Test
    void rayAheadIsWalkedOutToItsReach()
    {
        double[] span = new double[2];

        assertTrue(PointerProbe.span(eyeMatrix(), new double[]{0, 0, 0, 0, 0, 1}, View.nearPlane(), REACH, span));
        assertEquals(REACH, span[1], EPSILON);
    }

    @Test
    void rayBehindTheEyeIsNotWalked()
    {
        assertFalse(PointerProbe.span(eyeMatrix(), new double[]{0, 0, 0, 0, 0, -1}, 0, REACH, new double[2]));
    }

    private static float[] eyeMatrix()
    {
        RoomToWorld room = new RoomToWorld();
        room.place(0, 0, 0, 0, 1);
        View view = new View();
        room.view(Pose.ORIGIN, view);
        return view.worldToClip(SYMMETRIC);
    }
}
