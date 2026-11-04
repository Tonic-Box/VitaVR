package com.tonic.vitavr.space;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.TurnMode;
import com.tonic.vitavr.xr.Controllers;
import com.tonic.vitavr.xr.Headset;
import lombok.Getter;

/**
 * Places the room and both eyes in the world each frame, in first person or on a tabletop.
 */
public final class Placement
{
    private static final int EYES = 2;
    /** A standing player's eye height, in metres. */
    private static final double STANDING_EYE = 1.65;
    private static final float STICK_PUSHED = 0.7f;
    private static final float STICK_LET_GO = 0.3f;
    private static final float STICK_DEAD = 0.2f;
    private static final double TABLE_AHEAD_METRES = 0.55;
    private static final double TABLE_RADIUS_METRES = 0.6;
    private static final double TILT_DEGREES_PER_SECOND = 40;
    private static final double MOST_TABLE_TILT = 60;

    private final PlayerAnchor anchor = new PlayerAnchor();
    @Getter
    private final RoomToWorld room = new RoomToWorld();
    private final View[] eyes = {new View(), new View()};
    @Getter
    private final View centre = new View();
    private double turnYaw;
    private boolean snapArmed = true;
    private double calibratedHead = Double.NaN;
    @Getter
    private double lift;
    @Getter
    private double turnRate;
    private double[] table;
    private double tableHeight;
    private double tableTilt = Double.NaN;
    private boolean tableMoved;
    private double unitsPerMetre = 1;

    /**
     * Turns the room with the right thumbstick, snapped or smooth.
     *
     * @param controllers The controllers.
     * @param config      The plugin config.
     * @param seconds     The time since the last frame, in seconds.
     */
    public void turn(Controllers controllers, VitaVRConfig config, double seconds)
    {
        double before = turnYaw;
        float across = controllers.stickX(Controllers.RIGHT);
        if (config.turnMode() == TurnMode.SNAP)
        {
            if (snapArmed && Math.abs(across) > STICK_PUSHED)
            {
                turnYaw -= Math.signum(across) * Math.toRadians(config.snapDegrees());
                snapArmed = false;
            }
            else if (Math.abs(across) < STICK_LET_GO)
            {
                snapArmed = true;
            }
        }
        else if (Math.abs(across) > STICK_DEAD)
        {
            turnYaw -= across * Math.toRadians(config.smoothDegreesPerSecond()) * seconds;
        }
        turnRate = seconds > 0 ? Math.abs(turnYaw - before) / seconds : 0;
    }

    /**
     * Re-reads the real head height on the next frame.
     */
    public void recalibrate()
    {
        calibratedHead = Double.NaN;
    }

    /**
     * Places the room under the player in first person, and the eyes in it.
     *
     * @param headset   The headset.
     * @param config    The plugin config.
     * @param playerX   The player x (across).
     * @param groundY   The ground height under the player (down).
     * @param playerZ   The player z (along).
     * @param eyeHeight The body's resting eye height, in game units.
     * @param bodyUnitsPerMetre The game units per metre at the body's size.
     * @param shift     The body's head offset from rest, across, down and along, in game units.
     * @param seconds   The time since the last frame, in seconds.
     * @return Whether the room jumped instead of following.
     */
    public boolean place(Headset headset, VitaVRConfig config, double playerX, double groundY, double playerZ, double eyeHeight, double bodyUnitsPerMetre, double[] shift,
                         double seconds)
    {
        boolean jumped = anchor.step(playerX, groundY, playerZ, seconds);
        double unitsPerMetre = config.unitsPerMetre();
        if (Double.isNaN(calibratedHead))
        {
            calibratedHead = (headset.eyePose(0).y() + headset.eyePose(1).y()) / 2;
        }
        lift = eyeHeight / unitsPerMetre - calibratedHead;
        Pose left = headset.eyePose(0).lifted(lift);
        Pose right = headset.eyePose(1).lifted(lift);
        Pose head = Pose.between(left, right);

        room.tilt(0, 1, 0);
        room.place(anchor.x() + shift[0], anchor.y() + shift[1], anchor.z() + shift[2], turnYaw, unitsPerMetre);
        room.view(head, centre);
        room.view(left, eyes[0]);
        room.view(right, eyes[1]);

        // Move the eyes in front of the body's head
        double bodyScale = bodyUnitsPerMetre / unitsPerMetre;
        clearHead(config.headClearanceCm() / 100.0 / bodyScale * unitsPerMetre);
        return jumped;
    }

    /**
     * Places the world on the tabletop, and the eyes over it.
     *
     * @param headset The headset.
     * @param config  The plugin config.
     * @param playerX The player x (across).
     * @param groundY The ground height under the player (down).
     * @param playerZ The player z (along).
     * @param seconds The time since the last frame, in seconds.
     * @return Whether the world jumped instead of following.
     */
    public boolean placeTable(Headset headset, VitaVRConfig config, double playerX, double groundY, double playerZ, double seconds)
    {
        boolean jumped = anchor.step(playerX, groundY, playerZ, seconds);
        unitsPerMetre = config.unitsPerMetre() / config.tabletopScale();
        lift = 0;
        Pose left = headset.eyePose(0);
        Pose right = headset.eyePose(1);
        Pose head = Pose.between(left, right);
        if (table == null || tableHeight != config.tableHeight())
        {
            tableHeight = config.tableHeight();
            double floor = headset.floorHeight();
            table = tableBefore(head, (Double.isNaN(floor) ? head.y() - STANDING_EYE : floor) + tableHeight);
        }
        if (Double.isNaN(tableTilt))
        {
            tableTilt = config.tableTiltDegrees();
        }

        room.tilt(Math.toRadians(tableTilt), table[3], table[4]);
        room.placeAt(anchor.x(), anchor.y(), anchor.z(), table[0], table[1], table[2], turnYaw, unitsPerMetre);
        room.view(head, centre);
        room.view(left, eyes[0]);
        room.view(right, eyes[1]);
        return jumped;
    }

    /**
     * Places the table in front of the head again on the next frame.
     */
    public void resetTable()
    {
        table = null;
    }

    /**
     * Tilts the table with the right thumbstick; up raises the far side.
     *
     * @param controllers The controllers.
     * @param seconds     The time since the last frame, in seconds.
     * @return Whether the stick was just released after tilting.
     */
    public boolean tiltTable(Controllers controllers, double seconds)
    {
        float up = controllers.stickY(Controllers.RIGHT);
        if (Math.abs(up) > STICK_DEAD && !Double.isNaN(tableTilt))
        {
            tableTilt = Math.max(0, Math.min(MOST_TABLE_TILT, tableTilt + up * TILT_DEGREES_PER_SECOND * seconds));
            tableMoved = true;
            return false;
        }
        boolean settled = tableMoved;
        tableMoved = false;
        return settled;
    }

    /**
     * @return The table tilt, in whole degrees.
     */
    public int tableTiltDegrees()
    {
        return (int) Math.round(tableTilt);
    }

    /**
     * @return The ground speed, in game units per second.
     */
    public double groundSpeed()
    {
        return anchor.groundSpeed();
    }

    /**
     * @return The radius shown on the table, in game units.
     */
    public double tableReach()
    {
        return TABLE_RADIUS_METRES * unitsPerMetre;
    }

    /**
     * @param eye 0 for left, 1 for right.
     * @return The eye's view this frame.
     */
    public View eye(int eye)
    {
        return eyes[eye];
    }

    public double anchorX()
    {
        return anchor.x();
    }

    public double anchorZ()
    {
        return anchor.z();
    }

    /**
     * Moves the eyes level along the head's facing, in game units.
     */
    private void clearHead(double units)
    {
        double[] forward = new double[3];
        centre.forward(forward);
        double level = Math.hypot(forward[0], forward[2]);
        if (level < 1e-6 || units <= 0)
        {
            return;
        }
        double across = forward[0] / level * units;
        double along = forward[2] / level * units;
        centre.shift(across, 0, along);
        for (View eye : eyes)
        {
            eye.shift(across, 0, along);
        }
    }

    /**
     * Gets the table middle in front of the head, then the level tilt axis x and z.
     */
    private static double[] tableBefore(Pose head, double height)
    {
        double[] ahead = new double[3];
        head.rotate(0, 0, -1, ahead);
        double level = Math.hypot(ahead[0], ahead[2]);
        double x = level < 1e-6 ? 0 : ahead[0] / level;
        double z = level < 1e-6 ? -1 : ahead[2] / level;
        return new double[]{head.x() + x * TABLE_AHEAD_METRES, height, head.z() + z * TABLE_AHEAD_METRES, -z, x};
    }

    /**
     * Converts a room point to the world, lifted as the eyes are.
     *
     * @param x   The room x, in metres.
     * @param y   The room y (up), in metres.
     * @param z   The room z (back), in metres.
     * @param out Receives the world point, across, down and along.
     */
    public void toWorld(double x, double y, double z, double[] out)
    {
        room.point(x, y + lift, z, out);
    }

    /**
     * Converts a world point to the room, removing the eye lift.
     *
     * @param x   The world x (across).
     * @param y   The world y (down).
     * @param z   The world z (along).
     * @param out Receives the room point, across, up and back, in metres.
     */
    public void toRoom(double x, double y, double z, double[] out)
    {
        room.room(x, y, z, out);
        out[1] -= lift;
    }
}
