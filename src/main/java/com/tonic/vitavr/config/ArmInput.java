package com.tonic.vitavr.config;

import lombok.RequiredArgsConstructor;

/**
 * What a hand holds while drawing a gesture.
 */
@RequiredArgsConstructor
public enum ArmInput
{
    THUMBSTICK_CLICK("Thumbstick click"),
    THUMBREST_TOUCH("Thumb rest touch"),
    GRIP_AND_TRIGGER("Grip and trigger")
    ;

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
