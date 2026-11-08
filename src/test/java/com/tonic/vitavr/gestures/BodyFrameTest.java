package com.tonic.vitavr.gestures;

import com.tonic.vitavr.space.Pose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyFrameTest
{
    private static final double EPSILON = 1e-9;

    @Test
    void aHandAheadAndRightOfTheHeadIsAheadAndRightInTheFrame()
    {
        BodyFrame frame = new BodyFrame(0);
        frame.follow(facing(Math.toRadians(90), 1, 1.6, 2), 0);
        double[] out = new double[3];

        frame.toBody(1 - 0.5, 1.6 - 0.3, 2 - 0.2, out);

        assertArrayEquals(new double[]{0.2, -0.3 + BodyFrame.NECK, 0.5}, out, EPSILON);
    }

    @Test
    void theFrameMapsBackToTheRoomPointItCameFrom()
    {
        BodyFrame frame = new BodyFrame(0);
        frame.follow(facing(-2.3, 0.4, 1.7, -0.9), 0);
        double[] body = new double[3];
        double[] back = new double[3];

        frame.toBody(0.7, 1.1, -0.2, body);
        frame.toRoom(body[0], body[1], body[2], back);

        assertArrayEquals(new double[]{0.7, 1.1, -0.2}, back, EPSILON);
    }

    @Test
    void aGlanceAsideTurnsTheFrameOnlyPartWay()
    {
        BodyFrame frame = new BodyFrame(0.4);
        frame.follow(facing(0, 0, 1.6, 0), 0);

        frame.follow(facing(Math.toRadians(60), 0, 1.6, 0), 0.1);

        assertTrue(frame.yaw() > 0 && frame.yaw() < Math.toRadians(20), "eased yaw " + Math.toDegrees(frame.yaw()));
    }

    @Test
    void aTurnHeldLongEnoughIsFollowed()
    {
        BodyFrame frame = new BodyFrame(0.4);
        frame.follow(facing(0, 0, 1.6, 0), 0);
        for (int i = 0; i < 300; i++)
        {
            frame.follow(facing(Math.toRadians(170), 0, 1.6, 0), 1 / 90.0);
        }

        assertEquals(Math.toRadians(170), frame.yaw(), 1e-3);
    }

    @Test
    void aTurnPastBehindGoesTheShortWayRound()
    {
        BodyFrame frame = new BodyFrame(0.4);
        frame.follow(facing(Math.toRadians(170), 0, 1.6, 0), 0);

        frame.follow(facing(Math.toRadians(-170), 0, 1.6, 0), 0.1);

        assertTrue(frame.yaw() > Math.toRadians(170), "turned the long way: " + Math.toDegrees(frame.yaw()));
    }

    @Test
    void aReseedSnapsToTheHead()
    {
        BodyFrame frame = new BodyFrame(0.4);
        frame.follow(facing(0, 0, 1.6, 0), 0);
        frame.reseed();

        frame.follow(facing(1.2, 0, 1.6, 0), 0.01);

        assertEquals(1.2, frame.yaw(), EPSILON);
    }

    static Pose facing(double yaw, double x, double y, double z)
    {
        return new Pose(x, y, z, 0, Math.sin(yaw / 2), 0, Math.cos(yaw / 2));
    }
}
