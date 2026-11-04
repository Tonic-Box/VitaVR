package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * When the body's weapon and shield are shown in first person.
 */
@RequiredArgsConstructor
public enum HeldItems
{
    ALWAYS("Always"),
    WHEN_ACTING("When acting"),
    NEVER("Never")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
