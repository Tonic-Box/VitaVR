package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Take;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PointMatchesTest
{
    @Test
    void aHandInsideASpotIsInIt()
    {
        Gesture mouth = spot("Mouth", GestureHand.RIGHT, 0.02, 0.05, 0.10);

        assertEquals(mouth, PointMatches.spot(List.of(mouth), 1, new double[]{0.05, 0.06, 0.12}));
    }

    @Test
    void aHandJustOutsideASpotIsNotInIt()
    {
        Gesture mouth = spot("Mouth", GestureHand.RIGHT, 0.02, 0.05, 0.10);

        assertNull(PointMatches.spot(List.of(mouth), 1, new double[]{0.02, 0.05, 0.10 + Gesture.DEFAULT_RADIUS * 1.01}));
    }

    @Test
    void theNearerOfTwoSpotsWins()
    {
        Gesture hip = spot("Hip", GestureHand.RIGHT, 0.2, -0.6, 0);
        Gesture belt = spot("Belt", GestureHand.RIGHT, 0.12, -0.6, 0.05);

        assertEquals(hip, PointMatches.spot(List.of(belt, hip), 1, new double[]{0.18, -0.6, 0.01}));
    }

    @Test
    void eitherHandFindsTheSpotMirroredOnTheOtherSide()
    {
        Gesture shoulder = spot("Shoulder", GestureHand.EITHER, 0.2, 0.05, -0.1);

        assertEquals(shoulder, PointMatches.spot(List.of(shoulder), 0, new double[]{-0.2, 0.05, -0.1}));
        assertNull(PointMatches.spot(List.of(shoulder), 0, new double[]{0.2, 0.05, -0.1}));
    }

    @Test
    void aSnapAboutTheRecordedAxisFastEnoughIsTheFlick()
    {
        Gesture flick = flick(new double[]{1, 0, 0}, 12, false);

        assertEquals(flick, PointMatches.flick(List.of(flick), 1, peak(new double[]{0.9, 0.3, 0}, 11, false)));
    }

    @Test
    void aSnapAboutAnotherAxisIsNot()
    {
        Gesture flick = flick(new double[]{1, 0, 0}, 12, false);

        assertNull(PointMatches.flick(List.of(flick), 1, peak(new double[]{0, 1, 0}, 12, false)));
    }

    @Test
    void aSlowTurnIsNotASnap()
    {
        Gesture flick = flick(new double[]{1, 0, 0}, 12, false);

        assertNull(PointMatches.flick(List.of(flick), 1, peak(new double[]{1, 0, 0}, 5.5, false)));
    }

    @Test
    void anArmedFlickNeedsTheArmInput()
    {
        Gesture flick = flick(new double[]{1, 0, 0}, 12, true);

        assertNull(PointMatches.flick(List.of(flick), 1, peak(new double[]{1, 0, 0}, 12, false)));
        assertEquals(flick, PointMatches.flick(List.of(flick), 1, peak(new double[]{1, 0, 0}, 12, true)));
    }

    @Test
    void theOtherHandSnapsAMirroredFlickWithItsTurnOverAboutUpAndForward()
    {
        Gesture flick = flick(new double[]{0, 0.6, 0.8}, 12, false);
        flick.setHand(GestureHand.EITHER);

        assertEquals(flick, PointMatches.flick(List.of(flick), 0, peak(new double[]{0, -0.6, -0.8}, 12, false)));
        assertNull(PointMatches.flick(List.of(flick), 0, peak(new double[]{0, 0.6, 0.8}, 12, false)));
    }

    private static Gesture spot(String name, GestureHand hand, double x, double y, double z)
    {
        Gesture gesture = new Gesture();
        gesture.setName(name);
        gesture.setKind(GestureKind.SPOT);
        gesture.setHand(hand);
        gesture.setCentre(new double[]{x, y, z});
        gesture.setSensitivity(50);
        Take take = new Take();
        take.getRight().addAll(Drawings.samples(List.of(new double[]{x, y, z}, new double[]{x, y, z})));
        gesture.getTakes().add(take);
        return gesture;
    }

    private static Gesture flick(double[] axis, double peakSpin, boolean armed)
    {
        Gesture gesture = new Gesture();
        gesture.setKind(GestureKind.FLICK);
        gesture.setHand(GestureHand.RIGHT);
        gesture.setAxis(axis);
        gesture.setPeakSpin(peakSpin);
        gesture.setArmed(armed);
        Take take = new Take();
        take.getRight().addAll(Drawings.samples(List.of(new double[]{0, 0, 0.3}, new double[]{0, 0, 0.3})));
        gesture.getTakes().add(take);
        return gesture;
    }

    private static FlickDetector.Peak peak(double[] axis, double speed, boolean armed)
    {
        double length = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        return new FlickDetector.Peak(new double[]{axis[0] / length, axis[1] / length, axis[2] / length}, speed, armed);
    }
}
