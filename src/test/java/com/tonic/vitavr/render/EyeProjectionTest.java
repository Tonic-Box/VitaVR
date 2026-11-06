package com.tonic.vitavr.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyeProjectionTest
{
    private static final double[] EYE = {0, -170, 0};

    @Test
    void facingKeepsTheHeadsWindingForAModelAhead()
    {
        EyeProjection head = lookingUp();
        float[][] triangle = {{-20, -100, 300}, {20, -100, 300}, {0, -140, 300}};

        assertEquals(winding(head, triangle), winding(head.facing(0, -120, 300), triangle));
    }

    @Test
    void facingPutsAModelBelowARaisedHeadAhead()
    {
        EyeProjection turned = lookingUp().facing(40, 0, 60);

        assertTrue(turned.project(40, 0, 60)[2] > 0);
        assertTrue(turned.project(60, -10, 80)[2] > 0);
    }

    /**
     * A head looking 60 degrees up, along the world's z axis, with y down.
     */
    private static EyeProjection lookingUp()
    {
        double pitch = Math.toRadians(60);
        double[] forward = {0, -Math.sin(pitch), Math.cos(pitch)};
        double[] down = {0, Math.cos(pitch), Math.sin(pitch)};
        double[] right = {-1, 0, 0};
        return new EyeProjection(EYE, right, down, forward);
    }

    private static double winding(EyeProjection projection, float[][] triangle)
    {
        float[][] screen = new float[3][];
        for (int corner = 0; corner < 3; corner++)
        {
            float[] p = projection.project(triangle[corner][0], triangle[corner][1], triangle[corner][2]);
            screen[corner] = new float[]{p[0] / p[2], p[1] / p[2]};
        }
        double area = (screen[0][0] - screen[1][0]) * (screen[2][1] - screen[1][1]) - (screen[2][0] - screen[1][0]) * (screen[0][1] - screen[1][1]);
        return Math.signum(area);
    }
}
