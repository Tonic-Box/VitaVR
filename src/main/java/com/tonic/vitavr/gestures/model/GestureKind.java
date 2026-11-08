package com.tonic.vitavr.gestures.model;

import lombok.RequiredArgsConstructor;

/**
 * The ways a gesture is made.
 */
@RequiredArgsConstructor
public enum GestureKind
{
    SPOT("Spot"),
    SHAPE("Shape"),
    FLICK("Flick"),
    TWO_HAND("Two-hand")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
