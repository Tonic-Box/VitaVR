package com.tonic.vitavr.gestures.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A gesture the user recorded and named, with what recognising it needs; the takes also replay it in the editor.
 */
@Data
public class Gesture
{
    public static final int DEFAULT_SENSITIVITY = 50;
    public static final double DEFAULT_RADIUS = 0.10;

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private GestureKind kind = GestureKind.SHAPE;
    private GestureHand hand = GestureHand.RIGHT;
    private boolean enabled = true;
    private int sensitivity = DEFAULT_SENSITIVITY;
    /** Whether a flick needs the arm input held. */
    private boolean armed = true;
    private List<Take> takes = new ArrayList<>();
    /** A spot's centre in the body frame, x right, y up, z forward. */
    private double[] centre = new double[3];
    private double radius = DEFAULT_RADIUS;
    /** A flick's turn axis in the body frame, of unit length. */
    private double[] axis = new double[]{0, 1, 0};
    /** A flick's slowest recorded peak turn rate, radians a second. */
    private double peakSpin;
    /** The head's height above the floor when recorded, in metres. */
    private double headHeight = 1.65;

    /**
     * @return The hand the takes were made with, 0 left or 1 right; the right when they hold both or neither.
     */
    public int recordedHand()
    {
        for (Take take : takes)
        {
            if (!take.getLeft().isEmpty() && take.getRight().isEmpty())
            {
                return 0;
            }
            if (!take.getRight().isEmpty())
            {
                return 1;
            }
        }
        return hand == GestureHand.LEFT ? 0 : 1;
    }

    /**
     * @param handIndex 0 left or 1 right.
     * @return Whether the gesture is made with that hand, and if so whether it is mirrored from the hand it was recorded with.
     */
    public boolean madeWith(int handIndex)
    {
        switch (hand)
        {
            case LEFT:
                return handIndex == 0;
            case RIGHT:
                return handIndex == 1;
            case EITHER:
                return true;
            case BOTH:
            default:
                return false;
        }
    }

    /**
     * @param handIndex 0 left or 1 right.
     * @return Whether the hand makes this gesture mirrored, being the other hand to the one it was recorded with.
     */
    public boolean mirroredFor(int handIndex)
    {
        return hand == GestureHand.EITHER && handIndex != recordedHand();
    }
}
