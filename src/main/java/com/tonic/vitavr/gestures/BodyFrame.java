package com.tonic.vitavr.gestures;

import com.tonic.vitavr.space.Pose;

/**
 * A frame on the body that gestures are kept in: at the neck below the head, turned with the head's level yaw eased
 * over a moment, so a glance aside does not swing it; x right, y up, z forward, in metres.
 */
public final class BodyFrame
{
    /** How far the frame's origin sits below the head, in metres. */
    public static final double NECK = 0.12;

    private final double easeSeconds;
    private double x;
    private double y;
    private double z;
    private double yaw;
    private boolean seeded;

    /**
     * @param easeSeconds How long the frame takes to follow most of a turn of the head; 0 follows at once.
     */
    public BodyFrame(double easeSeconds)
    {
        this.easeSeconds = Math.max(0, easeSeconds);
    }

    /**
     * Moves the frame to the head, easing its turn.
     *
     * @param head    The head in the room.
     * @param seconds The time since the last frame.
     */
    public void follow(Pose head, double seconds)
    {
        x = head.x();
        y = head.y() - NECK;
        z = head.z();
        double target = head.yaw();
        if (!seeded || easeSeconds == 0)
        {
            yaw = target;
            seeded = true;
            return;
        }
        double turn = Math.atan2(Math.sin(target - yaw), Math.cos(target - yaw));
        yaw += turn * (1 - Math.exp(-Math.max(0, seconds) / easeSeconds));
    }

    /**
     * Snaps the frame to the head's yaw on the next follow, after a recentre or a change of view.
     */
    public void reseed()
    {
        seeded = false;
    }

    /**
     * Maps a room point into the frame.
     *
     * @param px  The point x.
     * @param py  The point y.
     * @param pz  The point z.
     * @param out Receives the point, x right, y up, z forward.
     */
    public void toBody(double px, double py, double pz, double[] out)
    {
        turnIn(px - x, py - y, pz - z, out);
    }

    /**
     * Maps a room direction into the frame.
     *
     * @param vx  The direction x.
     * @param vy  The direction y.
     * @param vz  The direction z.
     * @param out Receives the direction, x right, y up, z forward.
     */
    public void toBodyDirection(double vx, double vy, double vz, double[] out)
    {
        turnIn(vx, vy, vz, out);
    }

    /**
     * Maps a frame point back into the room.
     *
     * @param bx  The point right.
     * @param by  The point up.
     * @param bz  The point forward.
     * @param out Receives the room point.
     */
    public void toRoom(double bx, double by, double bz, double[] out)
    {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        out[0] = x + bx * cos - bz * sin;
        out[1] = y + by;
        out[2] = z - bx * sin - bz * cos;
    }

    private void turnIn(double dx, double dy, double dz, double[] out)
    {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        out[0] = dx * cos - dz * sin;
        out[1] = dy;
        out[2] = -dx * sin - dz * cos;
    }

    /**
     * @return The frame's yaw in radians, 0 facing -z in the room, left positive.
     */
    public double yaw()
    {
        return yaw;
    }
}
