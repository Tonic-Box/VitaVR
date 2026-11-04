package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * How the thumbstick turns the view.
 */
@RequiredArgsConstructor
public enum TurnMode
{
    SNAP("Snap"),
    SMOOTH("Smooth")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
