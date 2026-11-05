package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoomToWorldTest
{
    private static final double EPSILON = 1e-9;

    @Test
    void turnedRoomFacesAsTheGpuCameraAtThatYaw()
    {
        double yaw = 0.7;
        RoomToWorld room = new RoomToWorld();
        room.place(0, 0, 0, yaw, 1);
        double[] ahead = new double[3];

        room.direction(0, 0, -1, ahead);

        assertArrayEquals(new double[]{-Math.sin(yaw), 0, Math.cos(yaw)}, ahead, EPSILON);
    }

    @Test
    void roomUndoesPointWithTiltAndTurn()
    {
        RoomToWorld room = new RoomToWorld();
        room.tilt(Math.toRadians(25), 0.3, -0.8);
        room.placeAt(3000, -400, 5000, 0.2, 0.9, -0.55, 1.3, 4.4);
        double[] world = new double[3];
        double[] back = new double[3];

        room.point(0.4, 1.1, -0.7, world);
        room.room(world[0], world[1], world[2], back);

        assertArrayEquals(new double[]{0.4, 1.1, -0.7}, back, 1e-9);
    }

    @Test
    void placeAtPutsTheRoomPointOnTheWorldPoint()
    {
        RoomToWorld room = new RoomToWorld();
        room.tilt(Math.toRadians(30), 1, 0);
        room.placeAt(640, -120, 960, 0.1, 0.8, -0.55, 2.0, 20);
        double[] out = new double[3];

        room.point(0.1, 0.8, -0.55, out);

        assertArrayEquals(new double[]{640, -120, 960}, out, EPSILON);
    }
}
