package com.tonic.vitavr.gestures.vr;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.recognize.GestureRecognizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GestureRecorderTest
{
    private static final double FRAME = 1 / 90.0;
    private static final double[][] Z = {{-0.1, 0.1, 0.4}, {0.1, 0.1, 0.4}, {-0.1, -0.1, 0.4}, {0.1, -0.1, 0.4}};
    private static final double[][] LINE = {{0, 0.15, 0.4}, {0, -0.15, 0.4}};

    private final GestureRecorder recorder = new GestureRecorder();
    private final GestureRecognizer.HandState left = new GestureRecognizer.HandState();
    private final GestureRecognizer.HandState right = new GestureRecognizer.HandState();
    private double now;

    @Test
    void threeLikeTakesMakeAShape()
    {
        recorder.start(GestureKind.SHAPE, null);
        recorder.chooseHand(1);
        right.tracked = true;

        for (int take = 0; take < 3; take++)
        {
            draw(right, path(Z, 1 + take * 0.1));
        }

        assertEquals(GestureRecorder.Stage.DONE, recorder.getStage());
        Gesture made = recorder.result("Shape 1", 1.7);
        assertEquals(3, made.getTakes().size());
        assertEquals(GestureHand.RIGHT, made.getHand());
        assertEquals(1, made.recordedHand());
        assertFalse(made.getTakes().get(0).getRight().isEmpty());
        assertEquals(0, made.getTakes().get(0).getRight().get(0).getT(), 1e-12);
    }

    @Test
    void aTakeTooSmallIsAskedForAgain()
    {
        recorder.start(GestureKind.SHAPE, null);
        recorder.chooseHand(1);
        right.tracked = true;

        draw(right, path(Z, 0.1));

        assertEquals(0, recorder.taken());
        assertFalse(recorder.getMessage().isEmpty());
    }

    @Test
    void aTakeUnlikeTheFirstIsAskedForAgain()
    {
        recorder.start(GestureKind.SHAPE, null);
        recorder.chooseHand(1);
        right.tracked = true;
        draw(right, path(Z, 1));

        draw(right, path(LINE, 1));

        assertEquals(1, recorder.taken());
        assertFalse(recorder.getMessage().isEmpty());
    }

    @Test
    void aSpotIsWhereTheGripWasSqueezed()
    {
        recorder.start(GestureKind.SPOT, null);
        recorder.chooseHand(0);
        left.tracked = true;
        for (double[] point : path(LINE, 1))
        {
            System.arraycopy(point, 0, left.point, 0, 3);
            step();
        }
        left.gripWentDown = true;

        int took = recorder.frame(now += FRAME, left, right);

        assertEquals(1, took);
        Gesture made = recorder.result("Spot 1", 1.7);
        assertNotNull(made);
        assertEquals(GestureHand.LEFT, made.getHand());
        assertArrayEquals(left.point, made.getCentre(), 1e-12);
        assertFalse(made.getTakes().get(0).getLeft().isEmpty());
    }

    @Test
    void redoingAGestureKeepsItsIdAndName()
    {
        Gesture old = new Gesture();
        old.setName("Mine");
        old.setKind(GestureKind.SHAPE);
        old.setHand(GestureHand.EITHER);
        recorder.start(GestureKind.SPOT, old);
        right.tracked = true;

        for (int take = 0; take < 3; take++)
        {
            draw(right, path(Z, 1));
        }

        Gesture made = recorder.result("Shape 9", 1.7);
        assertEquals(old.getId(), made.getId());
        assertEquals("Mine", made.getName());
        assertEquals(GestureKind.SHAPE, made.getKind());
        assertEquals(GestureHand.EITHER, made.getHand());
    }

    @Test
    void nothingIsMadeBeforeTheTakesAreDone()
    {
        recorder.start(GestureKind.SHAPE, null);

        assertNull(recorder.result("Shape 1", 1.7));
    }

    private void draw(GestureRecognizer.HandState hand, List<double[]> path)
    {
        hand.armed = true;
        for (double[] point : path)
        {
            System.arraycopy(point, 0, hand.point, 0, 3);
            step();
        }
        hand.armed = false;
        step();
    }

    private void step()
    {
        now += FRAME;
        recorder.frame(now, left, right);
        left.gripWentDown = right.gripWentDown = false;
    }

    private static List<double[]> path(double[][] corners, double scale)
    {
        List<double[]> path = new ArrayList<>();
        for (int c = 1; c < corners.length; c++)
        {
            for (int i = 0; i < 20; i++)
            {
                double t = i / 20.0;
                double[] a = corners[c - 1];
                double[] b = corners[c];
                path.add(new double[]{(a[0] + (b[0] - a[0]) * t) * scale, (a[1] + (b[1] - a[1]) * t) * scale, a[2]});
            }
        }
        double[] last = corners[corners.length - 1];
        path.add(new double[]{last[0] * scale, last[1] * scale, last[2]});
        return path;
    }
}
