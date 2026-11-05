package com.tonic.vitavr.render.comfort;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the view darkening near and inside models.
 */
class EyeClearanceTest
{

    @Test
    void aClosedBoxHoldsThePointInsideAndAnOpenPanelDoesNot()
    {
        float[] box = box(0, 0, 0, 10);
        assertTrue(EyeClearance.inside(box, box.length, new double[]{5, 5, 5}), "in the middle of a closed box");
        assertFalse(EyeClearance.inside(box, box.length, new double[]{15, 5, 5}), "beside it");
        assertEquals(5, EyeClearance.nearest(box, box.length, new double[]{15, 5, 5}), 1e-6, "five from its side");
        assertEquals(Math.sqrt(2) * 5, EyeClearance.nearest(box, box.length, new double[]{15, 5, 15}), 1e-6, "from its edge");

        float[] panel = {0, 0, 0, 10, 0, 0, 10, 10, 0, 0, 0, 0, 10, 10, 0, 0, 10, 0};
        assertFalse(EyeClearance.inside(panel, panel.length, new double[]{5, 5, 0.5}), "a single panel encloses nothing");
        assertEquals(0.5, EyeClearance.nearest(panel, panel.length, new double[]{5, 5, 0.5}), 1e-6);
    }

    private static float[] box(float x, float y, float z, float side)
    {
        float[][] corners = new float[8][];
        for (int at = 0; at < 8; at++)
        {
            corners[at] = new float[]{x + ((at & 1) == 0 ? 0 : side), y + ((at & 2) == 0 ? 0 : side), z + ((at & 4) == 0 ? 0 : side)};
        }
        int[][] quads = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        float[] out = new float[quads.length * 2 * 9];
        int at = 0;
        for (int[] quad : quads)
        {
            for (int[] triangle : new int[][]{{quad[0], quad[1], quad[2]}, {quad[0], quad[2], quad[3]}})
            {
                for (int corner : triangle)
                {
                    out[at++] = corners[corner][0];
                    out[at++] = corners[corner][1];
                    out[at++] = corners[corner][2];
                }
            }
        }
        return out;
    }
}
