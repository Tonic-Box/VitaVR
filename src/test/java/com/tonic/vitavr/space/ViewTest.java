package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewTest
{
    private static final double EPSILON = 1e-4;
    private static final Fov SYMMETRIC = new Fov(-Math.toRadians(45), Math.toRadians(45), Math.toRadians(45), -Math.toRadians(45));

    @Test
    void pointAheadIsCentredWithGpuPluginDepth()
    {
        View view = eyeAt(500, -300, 800, 0.4);
        double distance = 640;
        double[] clip = clip(view.worldToClip(SYMMETRIC), 500 - Math.sin(0.4) * distance, -300, 800 + Math.cos(0.4) * distance);

        assertEquals(0, clip[0] / clip[3], EPSILON);
        assertEquals(0, clip[1] / clip[3], EPSILON);
        // Reverse depth: 2 * near / distance with a 5 unit near plane, clip z scaled to the GPU plugin's 2 * 50
        assertEquals(2 * 5 / distance, clip[2] / clip[3], EPSILON);
        assertEquals(100, clip[2], EPSILON);
    }

    @Test
    void rightAndUpLandOnTheirSidesOfTheScreen()
    {
        View view = eyeAt(0, 0, 0, 0);
        float[] matrix = view.worldToClip(SYMMETRIC);

        double[] right = clip(matrix, 100, 0, 100);
        double[] above = clip(matrix, 0, -100, 100);

        assertEquals(1, right[0] / right[3], EPSILON);
        assertEquals(1, above[1] / above[3], EPSILON);
    }

    private static View eyeAt(double x, double y, double z, double yaw)
    {
        RoomToWorld room = new RoomToWorld();
        room.place(x, y, z, yaw, 1);
        View view = new View();
        room.view(Pose.ORIGIN, view);
        return view;
    }

    private static double[] clip(float[] matrix, double x, double y, double z)
    {
        double[] out = new double[4];
        for (int row = 0; row < 4; row++)
        {
            out[row] = matrix[row] * x + matrix[4 + row] * y + matrix[8 + row] * z + matrix[12 + row];
        }
        return out;
    }
}
