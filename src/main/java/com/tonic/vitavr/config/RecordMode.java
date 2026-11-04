package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * What a recorded session shows: one eye's view, or both side by side.
 */
@RequiredArgsConstructor
public enum RecordMode
{
    ONE_EYE("One eye"),
    BOTH_EYES("Both eyes")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
