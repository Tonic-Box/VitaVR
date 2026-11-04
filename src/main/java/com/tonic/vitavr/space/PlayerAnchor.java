package com.tonic.vitavr.space;

/**
 * Smoothly follows the local player to anchor the room, at most half a tile behind, and jumps on teleports.
 */
public final class PlayerAnchor
{
    private static final double SMOOTH_SECONDS = 0.15;
    private static final double MOST_BEHIND = 64;
    private static final double JUMP = 256;

    private final double[] at = new double[3];
    private final double[] speed = new double[3];
    private boolean placed;

    /**
     * Follows the player for one frame.
     *
     * @param x       The player x (across).
     * @param y       The ground height under the player (down).
     * @param z       The player z (along).
     * @param seconds The time since the last frame, in seconds.
     * @return Whether the anchor jumped instead of following.
     */
    public boolean step(double x, double y, double z, double seconds)
    {
        double dx = x - at[0];
        double dz = z - at[2];
        if (!placed || dx * dx + dz * dz > JUMP * JUMP)
        {
            at[0] = x;
            at[1] = y;
            at[2] = z;
            speed[0] = 0;
            speed[1] = 0;
            speed[2] = 0;
            boolean jumped = placed;
            placed = true;
            return jumped;
        }
        double dt = Math.max(0, Math.min(0.1, seconds));
        at[0] = damp(0, at[0], x, dt);
        at[1] = damp(1, at[1], y, dt);
        at[2] = damp(2, at[2], z, dt);
        double behindX = at[0] - x;
        double behindZ = at[2] - z;
        double behind = Math.sqrt(behindX * behindX + behindZ * behindZ);
        if (behind > MOST_BEHIND)
        {
            at[0] = x + behindX * MOST_BEHIND / behind;
            at[2] = z + behindZ * MOST_BEHIND / behind;
        }
        return false;
    }

    public double x()
    {
        return at[0];
    }

    public double y()
    {
        return at[1];
    }

    public double z()
    {
        return at[2];
    }

    /**
     * @return The ground speed, in game units per second.
     */
    public double groundSpeed()
    {
        return Math.sqrt(speed[0] * speed[0] + speed[2] * speed[2]);
    }

    // Critically damped spring step, no overshoot
    private double damp(int axis, double from, double to, double dt)
    {
        double omega = 2 / SMOOTH_SECONDS;
        double x = omega * dt;
        double decay = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
        double change = from - to;
        double temp = (speed[axis] + omega * change) * dt;
        speed[axis] = (speed[axis] - omega * temp) * decay;
        return to + (change + temp) * decay;
    }
}
