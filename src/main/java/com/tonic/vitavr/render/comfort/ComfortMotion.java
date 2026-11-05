package com.tonic.vitavr.render.comfort;

/**
 * Tracks the comfort vignette while turning or moving and the black fade after a jump.
 */
public final class ComfortMotion
{
    private static final double FULL_TURN = Math.toRadians(90);
    private static final double FULL_SPEED = 4;
    private static final double EASE_IN_SECONDS = 0.08;
    private static final double EASE_OUT_SECONDS = 0.3;
    private static final double FADE_IN_SECONDS = 0.35;

    private double vignette;
    private double black;

    /**
     * Updates the vignette and fade for one frame.
     *
     * @param turnRadiansPerSecond The turn speed this frame, in radians per second.
     * @param metresPerSecond      The movement speed this frame, in metres per second.
     * @param jumped               Whether the player jumped this frame.
     * @param seconds              The frame length, in seconds.
     */
    public void step(double turnRadiansPerSecond, double metresPerSecond, boolean jumped, double seconds)
    {
        double dt = Math.max(0, seconds);
        double wanted = Math.min(1, Math.max(Math.abs(turnRadiansPerSecond) / FULL_TURN, Math.abs(metresPerSecond) / FULL_SPEED));
        double ease = wanted > vignette ? EASE_IN_SECONDS : EASE_OUT_SECONDS;
        vignette += (wanted - vignette) * (1 - Math.exp(-dt / ease));
        if (jumped)
        {
            black = 1;
        }
        else
        {
            black = Math.max(0, black - dt / FADE_IN_SECONDS);
        }
    }

    /** @return The vignette strength, from 0 to 1. */
    public double vignette()
    {
        return vignette;
    }

    /** @return The black fade strength, from 0 to 1. */
    public double black()
    {
        return black;
    }
}
