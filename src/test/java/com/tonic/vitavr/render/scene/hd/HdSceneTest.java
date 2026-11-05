package com.tonic.vitavr.render.scene.hd;

import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.View;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HdSceneTest
{
    @Test
    void invertedEyeMatrixTakesClipPointsBackToTheWorld()
    {
        RoomToWorld room = new RoomToWorld();
        room.place(3200, -600, 5100, 0.7, 128);
        View view = new View();
        room.view(Pose.ORIGIN, view);
        float[] worldToClip = view.worldToClip(new Fov(-0.9, 0.8, 0.85, -0.95));
        float[] clipToWorld = new float[16];

        HdScene.invert(worldToClip, new double[16], clipToWorld);

        double[] world = {2800, -450, 5900, 1};
        double[] clip = times(worldToClip, world);
        double[] back = times(clipToWorld, clip);
        for (int axis = 0; axis < 3; axis++)
        {
            assertEquals(world[axis], back[axis] / back[3], 0.5);
        }
    }

    private static double[] times(float[] matrix, double[] point)
    {
        double[] out = new double[4];
        for (int row = 0; row < 4; row++)
        {
            out[row] = matrix[row] * point[0] + matrix[4 + row] * point[1] + matrix[8 + row] * point[2] + matrix[12 + row] * point[3];
        }
        return out;
    }
}
