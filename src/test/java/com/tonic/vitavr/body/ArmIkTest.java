package com.tonic.vitavr.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the arm reach and hand grip.
 */
class ArmIkTest
{
    private static final int BODY = 8;
    private static final int[][] AT = {{0, 0, 0}, {0, 100, 0}, {0, 200, 0}, {0, 300, 0}, {0, 400, 0}};
    private static final int[] LABELS = {21, 20, 17, 19, 27};

    @Test
    void theHandReachesAPointInReachWithTheElbowTowardsThePole()
    {
        float[][] model = arm();
        double[] target = {150, 250, -100};
        double[] pole = {0, 0, 1};
        assertTrue(ArmIk.reach(model[0], model[1], model[2], groups(), ArmRig.RIGHT, target, pole));

        double[] hand = ArmIk.centroid(model[0], model[1], model[2], groups(), 27);
        double[] elbow = ArmIk.centroid(model[0], model[1], model[2], groups(), 17);
        double[] shoulder = ArmIk.centroid(model[0], model[1], model[2], groups(), 21);
        assertNotNull(hand);
        assertNotNull(elbow);
        assertNotNull(shoulder);
        assertArrayEquals(target, hand, 1.5, "the hand on the point");
        assertEquals(200, distance(shoulder, elbow), 1.5, "the upper arm keeps its length");
        assertEquals(200, distance(elbow, hand), 1.5, "the forearm keeps its length");
        double[] mid = {target[0] / 2, target[1] / 2, target[2] / 2};
        assertTrue(elbow[2] > mid[2], "the elbow bends towards the pole: " + elbow[2] + " against " + mid[2]);
        assertArrayEquals(new float[] {500, 500, 500}, new float[] {model[0][10], model[1][10], model[2][10]}, "the body does not move");
    }

    @Test
    void aPointOutOfReachIsReachedForStraight()
    {
        float[][] model = arm();
        assertTrue(ArmIk.reach(model[0], model[1], model[2], groups(), ArmRig.RIGHT, new double[] {1000, 0, 0}, new double[] {0, 1, 0}));
        double[] hand = ArmIk.centroid(model[0], model[1], model[2], groups(), 27);
        assertNotNull(hand);
        assertEquals(400, hand[0], 1.5, "straight out towards it, as far as the arm goes");
        assertEquals(0, hand[1], 1.5);
        assertEquals(0, hand[2], 1.5);
    }

    @Test
    void aGripTurnsTheHandAndWhatItHolds()
    {
        float[][] armOnly = arm();
        float[][] model = new float[3][];
        for (int axis = 0; axis < 3; axis++)
        {
            model[axis] = java.util.Arrays.copyOf(armOnly[axis], 12);
        }
        model[2][11] = -50;
        model[1][11] = 400;
        double[] target = {150, 250, -100};
        double[][] grip = ArmIk.turning(new double[] {0, 0, -1}, new double[] {1, 0, 0});
        assertTrue(ArmIk.reach(model[0], model[1], model[2], groups(), ArmRig.RIGHT, target, new double[] {0, 0, 1}, new int[] {11}, grip));

        double[] hand = ArmIk.centroid(model[0], model[1], model[2], groups(), 27);
        assertNotNull(hand);
        assertArrayEquals(target, hand, 1.5, "the hand still on the point");
        double[] held = {model[0][11] - hand[0], model[1][11] - hand[1], model[2][11] - hand[2]};
        assertArrayEquals(new double[] {50, 0, 0}, held, 1.5, "the blade turned with the grip, from pointing ahead to across");
        assertArrayEquals(new float[] {500, 500, 500}, new float[] {model[0][10], model[1][10], model[2][10]}, "the body does not move");
    }

    /** Builds a straight arm along y with two vertices per label and one for the body. */
    private static float[][] arm()
    {
        float[] x = new float[11];
        float[] y = new float[11];
        float[] z = new float[11];
        for (int part = 0; part < AT.length; part++)
        {
            for (int copy = 0; copy < 2; copy++)
            {
                x[part * 2 + copy] = AT[part][0] + (copy == 0 ? -1 : 1);
                y[part * 2 + copy] = AT[part][1];
                z[part * 2 + copy] = AT[part][2];
            }
        }
        x[10] = 500;
        y[10] = 500;
        z[10] = 500;
        return new float[][] {x, y, z};
    }

    private static int[][] groups()
    {
        int[][] groups = new int[64][];
        for (int part = 0; part < LABELS.length; part++)
        {
            groups[LABELS[part]] = new int[] {part * 2, part * 2 + 1};
        }
        groups[BODY] = new int[] {10};
        return groups;
    }

    private static double distance(double[] a, double[] b)
    {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
