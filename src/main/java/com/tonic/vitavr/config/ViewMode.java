package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * How the world is seen.
 */
@RequiredArgsConstructor
public enum ViewMode
{
    FIRST_PERSON("First person"),
    TABLETOP("Tabletop")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
