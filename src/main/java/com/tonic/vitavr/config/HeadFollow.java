package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * When the first person view follows the body's head as the game poses it.
 */
@RequiredArgsConstructor
public enum HeadFollow
{
    OFF("Off"),
    ACTIONS("Actions"),
    ALWAYS("Always")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
