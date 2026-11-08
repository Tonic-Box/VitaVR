package com.tonic.vitavr.gestures.model.steps;

/**
 * Turning something on, off, or the other way to how it is.
 */
public enum Switch
{
    ON("On"),
    OFF("Off"),
    TOGGLE("Toggle")
    ;

    private final String name;

    Switch(String name)
    {
        this.name = name;
    }

    /**
     * @param now Whether it is on now.
     * @return Whether it should be on after.
     */
    public boolean after(boolean now)
    {
        return this == ON || this == TOGGLE && !now;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
