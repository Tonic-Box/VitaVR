package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Take;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.tonic.vitavr.gestures.recognize.Drawings.Z;
import static com.tonic.vitavr.gestures.recognize.Drawings.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestureRecognizerTest
{
    private static final double FRAME = 1 / 90.0;

    private final GestureRecognizer recognizer = new GestureRecognizer();
    private final GestureRecognizer.HandState left = new GestureRecognizer.HandState();
    private final GestureRecognizer.HandState right = new GestureRecognizer.HandState();
    private double now;

    @Test
    void aZDrawnWithTheArmInputHeldFiresWhenLetGo()
    {
        Gesture z = Drawings.shape("Z", Z, 1, GestureHand.RIGHT);
        recognizer.library(List.of(z), 1);
        right.tracked = left.tracked = true;

        List<GestureRecognizer.Fired> fired = draw(right, path(Z, 5, 1, 0));

        assertEquals(1, fired.size());
        assertEquals(z, fired.get(0).getGesture());
        assertEquals(1, fired.get(0).getHand());
    }

    @Test
    void aTwitchWithTheArmInputHeldIsNothing()
    {
        recognizer.library(List.of(Drawings.shape("Z", Z, 1, GestureHand.RIGHT)), 1);
        right.tracked = true;

        List<GestureRecognizer.Fired> fired = draw(right, path(Z, 5, 0.1, 0));

        assertTrue(fired.isEmpty());
    }

    @Test
    void aGripInASpotFiresItAndTakesTheGrip()
    {
        Gesture mouth = new Gesture();
        mouth.setKind(GestureKind.SPOT);
        mouth.setHand(GestureHand.RIGHT);
        mouth.setCentre(new double[]{0, 0.05, 0.1});
        Take take = new Take();
        take.getRight().addAll(Drawings.samples(List.of(new double[]{0, 0.05, 0.1})));
        mouth.getTakes().add(take);
        recognizer.library(List.of(mouth), 1);
        right.tracked = true;
        set(right.point, 0.01, 0.05, 0.11);
        right.gripWentDown = true;

        List<GestureRecognizer.Fired> fired = recognizer.frame(now, left, right);

        assertEquals(1, fired.size());
        assertTrue(fired.get(0).isTookGrip());
    }

    @Test
    void bothHandsArmedDrawTheTwoHandMotionAndNotTheirOwnShapes()
    {
        List<double[]> leftOut = Drawings.line(new double[]{-0.02, 0, 0.4}, new double[]{-0.3, 0, 0.4}, 40);
        List<double[]> rightOut = Drawings.line(new double[]{0.02, 0, 0.4}, new double[]{0.3, 0, 0.4}, 40);
        Gesture apart = Drawings.twoHand("Apart", leftOut, rightOut);
        Gesture line = Drawings.shape("Line", new double[][]{{0.02, 0, 0.4}, {0.3, 0, 0.4}}, 1, GestureHand.RIGHT);
        recognizer.library(List.of(apart, line), 1);
        left.tracked = right.tracked = true;
        List<GestureRecognizer.Fired> fired = new ArrayList<>();

        left.armed = right.armed = true;
        for (int i = 0; i < leftOut.size(); i++)
        {
            copy(leftOut.get(i), left.point);
            copy(rightOut.get(i), right.point);
            fired.addAll(step());
        }
        left.armed = false;
        fired.addAll(step());
        right.armed = false;
        fired.addAll(step());

        assertEquals(1, fired.size(), "fired " + fired.size());
        assertEquals(apart, fired.get(0).getGesture());
        assertEquals(-1, fired.get(0).getHand());
    }

    @Test
    void aSnapMatchingAFlickFiresOnceAsItSlows()
    {
        Gesture flick = new Gesture();
        flick.setKind(GestureKind.FLICK);
        flick.setHand(GestureHand.RIGHT);
        flick.setArmed(false);
        flick.setAxis(new double[]{1, 0, 0});
        flick.setPeakSpin(10);
        Take take = new Take();
        take.getRight().addAll(Drawings.samples(List.of(new double[]{0, 0, 0.3})));
        flick.getTakes().add(take);
        recognizer.library(List.of(flick), 1);
        right.tracked = true;
        List<GestureRecognizer.Fired> fired = new ArrayList<>();

        for (double speed : new double[]{1, 3, 6, 10, 12, 9, 5, 1.5, 0.5, 0.2})
        {
            set(right.spin, speed, 0, 0);
            fired.addAll(step());
        }

        assertEquals(1, fired.size());
        assertEquals(flick, fired.get(0).getGesture());
    }

    private List<GestureRecognizer.Fired> draw(GestureRecognizer.HandState hand, List<double[]> path)
    {
        List<GestureRecognizer.Fired> fired = new ArrayList<>();
        hand.armed = true;
        for (double[] point : path)
        {
            copy(point, hand.point);
            fired.addAll(step());
        }
        hand.armed = false;
        fired.addAll(step());
        return fired;
    }

    private List<GestureRecognizer.Fired> step()
    {
        now += FRAME;
        List<GestureRecognizer.Fired> fired = recognizer.frame(now, left, right);
        left.gripWentDown = right.gripWentDown = false;
        return fired;
    }

    private static void copy(double[] from, double[] to)
    {
        System.arraycopy(from, 0, to, 0, 3);
    }

    private static void set(double[] to, double x, double y, double z)
    {
        to[0] = x;
        to[1] = y;
        to[2] = z;
    }
}
