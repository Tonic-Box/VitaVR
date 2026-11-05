package com.tonic.vitavr.render.recording;

import com.tonic.vitavr.space.Pose;

/**
 * A smoothed, roll-free camera that follows the head for recordings.
 */
public final class SpectatorView
{
    private static final double POSITION_SECONDS = 0.25;
    private static final double TURN_SECONDS = 0.2;

    private boolean placed;
    private double x;
    private double y;
    private double z;
    private double yaw;
    private double pitch;

    /**
     * Follows the head for a frame.
     * @param head The head pose in the room.
     * @param seconds The time since the last frame.
     * @return The steadied, level camera pose.
     */
    public Pose follow(Pose head, double seconds)
    {
        double[] ahead = new double[3];
        head.rotate(0, 0, -1, ahead);
        double headYaw = Math.atan2(-ahead[0], -ahead[2]);
        double headPitch = Math.asin(Math.max(-1, Math.min(1, ahead[1])));
        if (!placed)
        {
            x = head.x();
            y = head.y();
            z = head.z();
            yaw = headYaw;
            pitch = headPitch;
            placed = true;
        }
        else
        {
            double dt = Math.max(0, seconds);
            double move = 1 - Math.exp(-dt / POSITION_SECONDS);
            double turn = 1 - Math.exp(-dt / TURN_SECONDS);
            x += (head.x() - x) * move;
            y += (head.y() - y) * move;
            z += (head.z() - z) * move;
            yaw += Math.IEEEremainder(headYaw - yaw, 2 * Math.PI) * turn;
            pitch += (headPitch - pitch) * turn;
        }
        double sinYaw = Math.sin(yaw / 2);
        double cosYaw = Math.cos(yaw / 2);
        double sinPitch = Math.sin(pitch / 2);
        double cosPitch = Math.cos(pitch / 2);
        return new Pose(x, y, z, cosYaw * sinPitch, sinYaw * cosPitch, -sinYaw * sinPitch, cosYaw * cosPitch);
    }
}
