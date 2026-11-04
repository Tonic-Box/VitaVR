package com.tonic.vitavr.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests model space placement and controller hand turning.
 */
class OwnBodyTest
{
    @Test
    void aPlacedPointComesBackToModelSpace()
    {
        double originX = 3200;
        double groundY = -400;
        double originZ = 6400;
        for (double angle : new double[]{0, 0.7, Math.PI, -2.2})
        {
            double[] model = {-28, -110, 15};
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double[] placed = {originX + model[0] * cos + model[2] * sin, groundY + model[1], originZ - model[0] * sin + model[2] * cos};
            assertArrayEquals(model, OwnBody.inModel(placed, originX, groundY, originZ, angle), 1e-9, "angle " + angle);
        }
    }

    @Test
    void aDirectionTurnedOutOfModelSpaceComesBack()
    {
        for (double angle : new double[]{0, 0.7, Math.PI, -2.2})
        {
            double[] model = {-28, -110, 15};
            assertArrayEquals(model, OwnBody.turnedInto(OwnBody.turnedOut(model, angle), angle), 1e-9, "angle " + angle);
        }
    }

    @Test
    void aControllerHeldAsTheModelHoldsItKeepsTheHand()
    {
        double[][] kept = OwnBody.grip(new double[]{0, -1, 0}, new double[]{0, 0, -1});
        for (int row = 0; row < 3; row++)
        {
            assertArrayEquals(new double[]{row == 0 ? 1 : 0, row == 1 ? 1 : 0, row == 2 ? 1 : 0}, kept[row], 1e-9, "no turn");
        }

        double[] up = {0, -0.6, -0.8};
        double[] forward = {1, 0, 0};
        double[][] turned = OwnBody.grip(up, forward);
        assertArrayEquals(forward, ArmIk.apply(turned, new double[]{0, 0, -1}), 1e-9, "the model's front goes the way the controller points");
        assertArrayEquals(up, ArmIk.apply(turned, new double[]{0, -1, 0}), 1e-9, "the model's up goes the way its top faces");
        assertEquals(1, determinant(turned), 1e-9, "a turn, not a mirror");
    }

    private static double determinant(double[][] m)
    {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }
}
