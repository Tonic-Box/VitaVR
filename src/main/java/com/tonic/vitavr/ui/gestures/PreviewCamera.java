package com.tonic.vitavr.ui.gestures;

/**
 * The preview's camera: it stands behind the body, so its right is the screen's right, orbiting a point on it at a
 * distance, turned about the vertical and tipped down, and projects body-frame points (x right, y up, z forward) onto
 * the screen in perspective.
 */
final class PreviewCamera
{
    static final double DEFAULT_PITCH = Math.toRadians(15);
    static final double DEFAULT_YAW = Math.toRadians(-35);
    private static final double MOST_PITCH = Math.toRadians(80);

    private double yaw = DEFAULT_YAW;
    private double pitch = DEFAULT_PITCH;
    private double distance = 2.6;
    private final double[] target = {0, -0.45, 0.1};

    /**
     * Turns the camera round the body.
     *
     * @param byYaw   Radians about the vertical.
     * @param byPitch Radians tipped further down.
     */
    void orbit(double byYaw, double byPitch)
    {
        yaw += byYaw;
        pitch = Math.max(-MOST_PITCH, Math.min(MOST_PITCH, pitch + byPitch));
    }

    /**
     * Puts the camera back where it starts.
     */
    void reset()
    {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
    }

    /**
     * @param metres How far from the point it orbits.
     */
    void distance(double metres)
    {
        distance = metres;
    }

    /**
     * @param x Right.
     * @param y Up.
     * @param z Forward.
     */
    void target(double x, double y, double z)
    {
        target[0] = x;
        target[1] = y;
        target[2] = z;
    }

    double yaw()
    {
        return yaw;
    }

    /**
     * Projects a body-frame point.
     *
     * @param x      Right.
     * @param y      Up.
     * @param z      Forward.
     * @param width  The screen's width in pixels.
     * @param height The screen's height in pixels.
     * @param out    Receives the screen x and y in pixels, and the depth in metres ahead of the camera.
     */
    void project(double x, double y, double z, int width, int height, double[] out)
    {
        double dx = x - target[0];
        double dy = y - target[1];
        double dz = z - target[2];
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double across = dx * cosYaw - dz * sinYaw;
        double along = dx * sinYaw + dz * cosYaw;
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double up = dy * cosPitch + along * sinPitch;
        double toward = -dy * sinPitch + along * cosPitch;
        double depth = distance + toward;
        double focal = Math.min(width, height) * 1.1;
        double safe = Math.max(0.05, depth);
        out[0] = width / 2.0 + across * focal / safe;
        out[1] = height / 2.0 - up * focal / safe;
        out[2] = depth;
    }
}
