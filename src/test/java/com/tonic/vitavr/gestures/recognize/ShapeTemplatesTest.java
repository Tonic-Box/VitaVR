package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tonic.vitavr.gestures.recognize.Drawings.SEVEN;
import static com.tonic.vitavr.gestures.recognize.Drawings.SWIPE_DOWN;
import static com.tonic.vitavr.gestures.recognize.Drawings.Z;
import static com.tonic.vitavr.gestures.recognize.Drawings.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShapeTemplatesTest
{
    private static final int RIGHT = 1;
    private static final int LEFT = 0;

    @Test
    void aFreshZLargerAndElsewhereIsTheZ()
    {
        Gesture z = Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT);
        ShapeTemplates templates = new ShapeTemplates(List.of(z, Drawings.shape("Swipe", SWIPE_DOWN, RIGHT, GestureHand.RIGHT)));

        ShapeTemplates.Match match = templates.matchShape(ShapeTemplates.shape(path(Z, 9, 1.6, 0.2)), RIGHT);

        assertNotNull(match);
        assertEquals(z, match.getGesture());
    }

    @Test
    void aZDrawnBackwardsIsNotTheZ()
    {
        ShapeTemplates templates = new ShapeTemplates(List.of(Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT)));

        assertNull(templates.matchShape(ShapeTemplates.shape(Drawings.reversed(path(Z, 9, 1, 0))), RIGHT));
    }

    @Test
    void aZDrawnFacingALittleAsideStillCounts()
    {
        ShapeTemplates templates = new ShapeTemplates(List.of(Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT)));

        assertNotNull(templates.matchShape(ShapeTemplates.shape(Drawings.turned(path(Z, 9, 1, 0), 20)), RIGHT));
    }

    @Test
    void aSevenIsNotAZ()
    {
        ShapeTemplates templates = new ShapeTemplates(List.of(Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT)));

        assertNull(templates.matchShape(ShapeTemplates.shape(path(SEVEN, 9, 1, 0)), RIGHT));
    }

    @Test
    void twoGesturesTooAlikeToTellApartMatchNeither()
    {
        Gesture z = Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT);
        Gesture alsoZ = Drawings.shape("Also Z", Z, RIGHT, GestureHand.RIGHT);
        ShapeTemplates templates = new ShapeTemplates(List.of(z, alsoZ));

        assertNull(templates.matchShape(ShapeTemplates.shape(path(Z, 9, 1, 0)), RIGHT));
    }

    @Test
    void aRightHandShapeIsNotMadeWithTheLeft()
    {
        ShapeTemplates templates = new ShapeTemplates(List.of(Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT)));

        assertNull(templates.matchShape(ShapeTemplates.shape(path(Z, 9, 1, 0)), LEFT));
    }

    @Test
    void eitherHandMirrorsTheShapeForTheOtherHand()
    {
        ShapeTemplates templates = new ShapeTemplates(List.of(Drawings.shape("Seven", SEVEN, RIGHT, GestureHand.EITHER)));

        assertNotNull(templates.matchShape(ShapeTemplates.shape(path(SEVEN, 9, 1, 0)), RIGHT));
        assertNotNull(templates.matchShape(ShapeTemplates.shape(Drawings.mirrored(path(SEVEN, 9, 1, 0))), LEFT));
        assertNull(templates.matchShape(ShapeTemplates.shape(path(SEVEN, 9, 1, 0)), LEFT));
    }

    @Test
    void aDisabledShapeIsNotLookedFor()
    {
        Gesture z = Drawings.shape("Z", Z, RIGHT, GestureHand.RIGHT);
        z.setEnabled(false);
        ShapeTemplates templates = new ShapeTemplates(List.of(z));

        assertNull(templates.matchShape(ShapeTemplates.shape(path(Z, 9, 1, 0)), RIGHT));
    }

    @Test
    void handsPullingApartAreNotHandsComingTogether()
    {
        List<double[]> leftOut = Drawings.line(new double[]{-0.02, 0, 0.4}, new double[]{-0.3, 0, 0.4}, 30);
        List<double[]> rightOut = Drawings.line(new double[]{0.02, 0, 0.4}, new double[]{0.3, 0, 0.4}, 30);
        Gesture apart = Drawings.twoHand("Apart", leftOut, rightOut);
        Gesture together = Drawings.twoHand("Together", Drawings.reversed(leftOut), Drawings.reversed(rightOut));
        ShapeTemplates templates = new ShapeTemplates(List.of(apart, together));

        ShapeTemplates.Match match = templates.matchTwoHand(ShapeTemplates.twoHand(leftOut, rightOut));

        assertNotNull(match);
        assertEquals(apart, match.getGesture());
    }

    @Test
    void bothHandsMovingTheSameWayIsNotPullingApart()
    {
        List<double[]> leftOut = Drawings.line(new double[]{-0.02, 0, 0.4}, new double[]{-0.3, 0, 0.4}, 30);
        List<double[]> rightOut = Drawings.line(new double[]{0.02, 0, 0.4}, new double[]{0.3, 0, 0.4}, 30);
        ShapeTemplates templates = new ShapeTemplates(List.of(Drawings.twoHand("Apart", leftOut, rightOut)));
        List<double[]> leftAlong = Drawings.line(new double[]{-0.3, 0, 0.4}, new double[]{-0.02, 0, 0.4}, 30);

        assertNull(templates.matchTwoHand(ShapeTemplates.twoHand(leftAlong, rightOut)));
    }
}
