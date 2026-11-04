package com.tonic.vitavr.space;

/**
 * A flat pixel image placed in the room, facing a head or laid along given axes, that pointers can hit.
 */
public final class Billboard
{
    private final double[] centre = new double[3];
    private final double[] right = new double[3];
    private final double[] up = new double[3];
    private final double[] normal = new double[3];
    private int width;
    private int height;
    private double metresPerPixel;
    private double yaw;
    private double pitch;
    private boolean placed;

    /**
     * Places the picture facing the head, with the given pixel on a room point.
     *
     * @param pointX         The room x (across).
     * @param pointY         The room y (up).
     * @param pointZ         The room z (back).
     * @param head           The head pose to face.
     * @param width          The width, in pixels.
     * @param height         The height, in pixels.
     * @param metresPerPixel The size of a pixel, in metres.
     * @param pixelX         The pixel x put on the point.
     * @param pixelY         The pixel y (down) put on the point.
     */
    public void stand(double pointX, double pointY, double pointZ, Pose head, int width, int height, double metresPerPixel, double pixelX, double pixelY)
    {
        this.width = width;
        this.height = height;
        this.metresPerPixel = metresPerPixel;

        double aheadX = pointX - head.x();
        double aheadY = pointY - head.y();
        double aheadZ = pointZ - head.z();
        double length = Math.sqrt(aheadX * aheadX + aheadY * aheadY + aheadZ * aheadZ);
        if (length < 1e-9)
        {
            aheadX = 0;
            aheadY = 0;
            aheadZ = -1;
            length = 1;
        }
        yaw = Math.atan2(-aheadX, -aheadZ);
        pitch = Math.asin(Math.max(-1, Math.min(1, aheadY / length)));

        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        right[0] = cosYaw;
        right[1] = 0;
        right[2] = -sinYaw;
        up[0] = sinPitch * sinYaw;
        up[1] = cosPitch;
        up[2] = sinPitch * cosYaw;
        normal[0] = cosPitch * sinYaw;
        normal[1] = -sinPitch;
        normal[2] = cosPitch * cosYaw;

        double across = (width / 2.0 - pixelX) * metresPerPixel;
        double above = (pixelY - height / 2.0) * metresPerPixel;
        double[] point = {pointX, pointY, pointZ};
        for (int axis = 0; axis < 3; axis++)
        {
            centre[axis] = point[axis] + right[axis] * across + up[axis] * above;
        }
        placed = true;
    }

    /**
     * Removes the picture so pointers no longer hit it.
     */
    public void clear()
    {
        placed = false;
    }

    public boolean placed()
    {
        return placed;
    }

    /**
     * Gets the pixel a pointer hits.
     *
     * @param aim The pointer pose in the room.
     * @return The pixel x and y, or null on a miss.
     */
    public int[] crossing(Pose aim)
    {
        double[] hit = hit(aim);
        return hit == null ? null : new int[]{(int) hit[1], (int) hit[2]};
    }

    /**
     * Gets the distance from a pointer to the picture.
     *
     * @param aim The pointer pose in the room.
     * @return The distance in metres, or infinity on a miss.
     */
    public double reach(Pose aim)
    {
        double[] hit = hit(aim);
        return hit == null ? Double.POSITIVE_INFINITY : hit[0];
    }

    private double[] hit(Pose aim)
    {
        if (!placed || aim == null)
        {
            return null;
        }
        double[] direction = new double[3];
        aim.rotate(0, 0, -1, direction);
        double facing = direction[0] * normal[0] + direction[1] * normal[1] + direction[2] * normal[2];
        if (facing >= -1e-6)
        {
            return null;
        }
        double t = ((centre[0] - aim.x()) * normal[0] + (centre[1] - aim.y()) * normal[1] + (centre[2] - aim.z()) * normal[2]) / facing;
        if (t <= 0)
        {
            return null;
        }
        double x = aim.x() + direction[0] * t - centre[0];
        double y = aim.y() + direction[1] * t - centre[1];
        double z = aim.z() + direction[2] * t - centre[2];
        double px = (x * right[0] + y * right[1] + z * right[2]) / metresPerPixel + width / 2.0;
        double py = height / 2.0 - (x * up[0] + y * up[1] + z * up[2]) / metresPerPixel;
        if (px < 0 || py < 0 || px >= width || py >= height)
        {
            return null;
        }
        return new double[]{t, Math.floor(px), Math.floor(py)};
    }

    /**
     * Gets the picture's corners in the room.
     *
     * @param out Receives the top left, top right, bottom right and bottom left corners.
     */
    public void corners(double[][] out)
    {
        double halfWide = width * metresPerPixel / 2;
        double halfTall = height * metresPerPixel / 2;
        int[][] signs = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        for (int corner = 0; corner < 4; corner++)
        {
            for (int axis = 0; axis < 3; axis++)
            {
                out[corner][axis] = centre[axis] + right[axis] * signs[corner][0] * halfWide + up[axis] * signs[corner][1] * halfTall;
            }
        }
    }

    /**
     * @return The centre in the room; the array is not a copy.
     */
    public double[] centre()
    {
        return centre;
    }

    /**
     * @return The orientation as a quaternion, x, y, z, w.
     */
    public double[] orientation()
    {
        double sinYaw = Math.sin(yaw / 2);
        double cosYaw = Math.cos(yaw / 2);
        double sinPitch = Math.sin(pitch / 2);
        double cosPitch = Math.cos(pitch / 2);
        return new double[]{cosYaw * sinPitch, sinYaw * cosPitch, -sinYaw * sinPitch, cosYaw * cosPitch};
    }

    public double metresWide()
    {
        return width * metresPerPixel;
    }

    public double metresTall()
    {
        return height * metresPerPixel;
    }
}
