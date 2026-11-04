package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * Where a one-eye recording is seen from: a steadied head camera or the exact left eye.
 */
@RequiredArgsConstructor
public enum OneEyeCamera
{
    STEADIED("Steadied"),
    EXACT("Exact")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
