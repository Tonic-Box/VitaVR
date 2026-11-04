package com.tonic.vitavr.space;

import lombok.Getter;

/**
 * Maps the room into the game world with an origin, a yaw, an optional tilt and a scale in game units per metre.
 * Room y up becomes world y down and room -z becomes world +z.
 */
public final class RoomToWorld
{
    private double originX;
    private double originY;
    private double originZ;
    private double yaw;
    @Getter
    private double unitsPerMetre = 1;
    private double tiltCos = 1;
    private double tiltSin;
    private double tiltAxisX = 1;
    private double tiltAxisZ;
    private final double[] turned = new double[3];

    /**
     * Places the room origin on a world point.
     *
     * @param originX       The world x (across).
     * @param originY       The world y (down).
     * @param originZ       The world z (along).
     * @param yaw           The room yaw, in radians.
     * @param unitsPerMetre The game units per room metre.
     */
    public void place(double originX, double originY, double originZ, double yaw, double unitsPerMetre)
    {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.yaw = yaw;
        this.unitsPerMetre = unitsPerMetre;
    }

    /**
     * Tilts the world about a level room axis, raising the side ahead of it.
     *
     * @param radians The tilt in radians, 0 for level.
     * @param axisX   The axis x (across) in the room.
     * @param axisZ   The axis z (back) in the room.
     */
    public void tilt(double radians, double axisX, double axisZ)
    {
        double length = Math.hypot(axisX, axisZ);
        tiltCos = Math.cos(radians);
        tiltSin = Math.sin(radians);
        tiltAxisX = axisX / length;
        tiltAxisZ = axisZ / length;
    }

    /**
     * Places the room so a room point lands on a world point.
     *
     * @param worldX        The world x (across).
     * @param worldY        The world y (down).
     * @param worldZ        The world z (along).
     * @param roomX         The room x (across), in metres.
     * @param roomY         The room y (up), in metres.
     * @param roomZ         The room z (back), in metres.
     * @param yaw           The room yaw, in radians.
     * @param unitsPerMetre The game units per room metre.
     */
    public void placeAt(double worldX, double worldY, double worldZ, double roomX, double roomY, double roomZ, double yaw, double unitsPerMetre)
    {
        place(0, 0, 0, yaw, unitsPerMetre);
        direction(roomX, roomY, roomZ, turned);
        place(worldX - turned[0] * unitsPerMetre, worldY - turned[1] * unitsPerMetre, worldZ - turned[2] * unitsPerMetre, yaw, unitsPerMetre);
    }

    /**
     * Converts a room pose to a world view.
     *
     * @param pose The room pose.
     * @param out  Receives the view.
     */
    public void view(Pose pose, View out)
    {
        point(pose.x(), pose.y(), pose.z(), out.eye);
        pose.rotate(1, 0, 0, turned);
        direction(turned[0], turned[1], turned[2], out.right);
        pose.rotate(0, -1, 0, turned);
        direction(turned[0], turned[1], turned[2], out.down);
        pose.rotate(0, 0, -1, turned);
        direction(turned[0], turned[1], turned[2], out.forward);
    }

    /**
     * Converts a room point to the world.
     *
     * @param x   The room x (across), in metres.
     * @param y   The room y (up), in metres.
     * @param z   The room z (back), in metres.
     * @param out Receives the world point, across, down and along.
     */
    public void point(double x, double y, double z, double[] out)
    {
        direction(x, y, z, out);
        out[0] = originX + out[0] * unitsPerMetre;
        out[1] = originY + out[1] * unitsPerMetre;
        out[2] = originZ + out[2] * unitsPerMetre;
    }

    /**
     * Converts a world point to the room.
     *
     * @param x   The world x (across).
     * @param y   The world y (down).
     * @param z   The world z (along).
     * @param out Receives the room point, across, up and back, in metres.
     */
    public void room(double x, double y, double z, double[] out)
    {
        double dx = (x - originX) / unitsPerMetre;
        double dy = (y - originY) / unitsPerMetre;
        double dz = (z - originZ) / unitsPerMetre;
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double across = dx * cos + dz * sin;
        double along = -dx * sin + dz * cos;
        rotate(across, -dy, -along, tiltSin, out);
    }

    /**
     * Converts a room direction to the world, unscaled.
     *
     * @param x   The room x (across).
     * @param y   The room y (up).
     * @param z   The room z (back).
     * @param out Receives the world direction, across, down and along.
     */
    public void direction(double x, double y, double z, double[] out)
    {
        rotate(x, y, z, -tiltSin, turned);
        double across = turned[0];
        double along = -turned[2];
        double up = turned[1];
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        out[0] = across * cos - along * sin;
        out[1] = -up;
        out[2] = across * sin + along * cos;
    }

    // Rotate about the tilt axis; pass -sine to undo
    private void rotate(double x, double y, double z, double sine, double[] out)
    {
        double dot = x * tiltAxisX + z * tiltAxisZ;
        double crossX = -tiltAxisZ * y;
        double crossY = tiltAxisZ * x - tiltAxisX * z;
        double crossZ = tiltAxisX * y;
        out[0] = x * tiltCos + crossX * sine + tiltAxisX * dot * (1 - tiltCos);
        out[1] = y * tiltCos + crossY * sine;
        out[2] = z * tiltCos + crossZ * sine + tiltAxisZ * dot * (1 - tiltCos);
    }
}
