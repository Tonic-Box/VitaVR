package com.tonic.vitavr.gestures.recognize;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PathMathTest
{
    private static final double EPSILON = 1e-9;

    @Test
    void resampledPointsAreEvenlySpacedAndKeepTheEnds()
    {
        List<double[]> path = List.of(new double[]{0, 0, 0}, new double[]{0.3, 0, 0}, new double[]{0.3, 0.1, 0});

        double[][] points = PathMath.resample(path, 9);

        assertArrayEquals(new double[]{0, 0, 0}, points[0], EPSILON);
        assertArrayEquals(new double[]{0.3, 0.1, 0}, points[8], EPSILON);
        for (int i = 1; i < points.length; i++)
        {
            assertEquals(0.05, PathMath.distance(points[i - 1], points[i]), 1e-9, "gap " + i);
        }
    }

    @Test
    void normalisedPointsAreCentredWithUnitSpread()
    {
        double[][] points = {{1, 2, 3}, {3, 2, 3}, {2, 4, 3}, {2, 0, 3}};

        PathMath.normalise(points);

        double sx = 0;
        double sumSquares = 0;
        for (double[] point : points)
        {
            sx += point[0];
            sumSquares += point[0] * point[0] + point[1] * point[1] + point[2] * point[2];
        }
        assertEquals(0, sx, EPSILON);
        assertEquals(1, sumSquares / points.length, EPSILON);
    }

    @Test
    void aLeftTurnTakesForwardToTheLeft()
    {
        double[][] turned = PathMath.turned(new double[][]{{0, 0, 1}}, Math.PI / 2);

        assertArrayEquals(new double[]{-1, 0, 0}, turned[0], EPSILON);
    }
}
