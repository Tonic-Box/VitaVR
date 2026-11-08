package com.tonic.vitavr.gestures.model;

import lombok.RequiredArgsConstructor;

/**
 * Which hand makes a gesture; either hand mirrors it left to right for the other.
 */
@RequiredArgsConstructor
public enum GestureHand
{
    LEFT("Left"),
    RIGHT("Right"),
    EITHER("Either"),
    BOTH("Both")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
