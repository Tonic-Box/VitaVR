package com.tonic.vitavr.space;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Represents an OpenXR room pose, in metres with x right, y up and facing -z.
 */
@Value
@Accessors(fluent = true)
public class Pose
{
    public static final Pose ORIGIN = new Pose(0, 0, 0, 0, 0, 0, 1);

    double x;
    double y;
    double z;
    double qx;
    double qy;
    double qz;
    double qw;

    /**
     * Rotates a direction by this pose's orientation.
     *
     * @param vx  The direction x.
     * @param vy  The direction y.
     * @param vz  The direction z.
     * @param out Receives the rotated direction.
     */
    public void rotate(double vx, double vy, double vz, double[] out)
    {
        double tx = 2 * (qy * vz - qz * vy);
        double ty = 2 * (qz * vx - qx * vz);
        double tz = 2 * (qx * vy - qy * vx);
        out[0] = vx + qw * tx + (qy * tz - qz * ty);
        out[1] = vy + qw * ty + (qz * tx - qx * tz);
        out[2] = vz + qw * tz + (qx * ty - qy * tx);
    }

    /**
     * Gets this pose moved up.
     *
     * @param dy The distance up, in metres.
     * @return The lifted pose.
     */
    public Pose lifted(double dy)
    {
        return dy == 0 ? this : new Pose(x, y + dy, z, qx, qy, qz, qw);
    }

    /**
     * Gets this pose moved forward along the level direction it faces.
     *
     * @param metres The distance forward, in metres.
     * @return The moved pose, or this pose if it faces straight up or down.
     */
    public Pose ahead(double metres)
    {
        double[] forward = new double[3];
        rotate(0, 0, -1, forward);
        double level = Math.hypot(forward[0], forward[2]);
        if (level < 1e-6)
        {
            return this;
        }
        return new Pose(x + forward[0] / level * metres, y, z + forward[2] / level * metres, qx, qy, qz, qw);
    }

    /**
     * Gets the pose midway between two poses.
     *
     * @param a The first pose, whose orientation is kept.
     * @param b The second pose.
     * @return The pose between them.
     */
    public static Pose between(Pose a, Pose b)
    {
        return new Pose((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2, a.qx, a.qy, a.qz, a.qw);
    }

    /**
     * Gets the yaw of this pose.
     *
     * @return The yaw in radians, 0 facing -z, left positive.
     */
    public double yaw()
    {
        double[] ahead = new double[3];
        rotate(0, 0, -1, ahead);
        return Math.atan2(-ahead[0], -ahead[2]);
    }
}
