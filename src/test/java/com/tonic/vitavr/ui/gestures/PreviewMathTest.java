package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.model.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewMathTest
{
    @Test
    void thePointTheCameraOrbitsIsTheScreensMiddle()
    {
        PreviewCamera camera = new PreviewCamera();
        camera.target(0.1, -0.4, 0.2);
        camera.orbit(1.1, 0.3);
        double[] out = new double[3];

        camera.project(0.1, -0.4, 0.2, 800, 600, out);

        assertEquals(400, out[0], 1e-9);
        assertEquals(300, out[1], 1e-9);
    }

    @Test
    void seenFromBehindTheRightIsOnTheRightAndAheadIsFurther()
    {
        PreviewCamera camera = new PreviewCamera();
        camera.target(0, 0, 0);
        camera.orbit(-PreviewCamera.DEFAULT_YAW, -PreviewCamera.DEFAULT_PITCH);
        double[] right = new double[3];
        double[] ahead = new double[3];
        double[] middle = new double[3];

        camera.project(0.3, 0, 0, 800, 600, right);
        camera.project(0, 0, 0.5, 800, 600, ahead);
        camera.project(0, 0, 0, 800, 600, middle);

        assertTrue(right[0] > 400);
        assertTrue(ahead[2] > middle[2]);
        assertEquals(400, ahead[0], 1e-9);
    }

    @Test
    void takesPlayInTurnEachThenResting()
    {
        double[] lengths = {1.0, 0.5};

        assertArrayEquals(new double[]{0, 0.4}, TakeClock.at(0.4, lengths), 1e-9);
        assertArrayEquals(new double[]{0, 1.0}, TakeClock.at(1.3, lengths), 1e-9);
        assertArrayEquals(new double[]{1, 0.2}, TakeClock.at(1.0 + TakeClock.REST_SECONDS + 0.2, lengths), 1e-9);
        double loop = 1.0 + 0.5 + 2 * TakeClock.REST_SECONDS;
        assertArrayEquals(new double[]{0, 0.4}, TakeClock.at(loop + 0.4, lengths), 1e-9);
    }

    @Test
    void aHandBetweenTwoSamplesIsBetweenThem()
    {
        List<Sample> samples = List.of(new Sample(0, 0, 0, 0, 0, 0, 0, 1), new Sample(0.5, 1, 2, -1, 0, 0, 0, 1));
        double[] out = new double[7];

        TakeClock.place(samples, 0.25, out);

        assertArrayEquals(new double[]{0.5, 1, -0.5}, new double[]{out[0], out[1], out[2]}, 1e-9);
        TakeClock.place(samples, 9, out);
        assertArrayEquals(new double[]{1, 2, -1}, new double[]{out[0], out[1], out[2]}, 1e-9);
    }
}
