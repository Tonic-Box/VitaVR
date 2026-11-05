package com.tonic.vitavr.interfaces.pointer;

import net.runelite.api.Client;

/**
 * Turns the client's camera yaw to match the headset, so the minimap and compass follow the head.
 */
public final class CameraDriver
{
    private static final int TURN = 2048;
    private static final double UNITS_PER_RADIAN = TURN / (2 * Math.PI);
    private static final long STEP_MILLIS = 20;

    private long nextAt;

    /**
     * Sets the camera yaw target, at most once a client cycle.
     *
     * @param client The client.
     * @param yaw The facing yaw, in radians as the GPU plugin's camera uses.
     * @param now The current time, in milliseconds.
     */
    public void face(Client client, double yaw, long now)
    {
        if (now < nextAt)
        {
            return;
        }
        nextAt = now + STEP_MILLIS;
        int wanted = Math.floorMod((int) Math.round(yaw * UNITS_PER_RADIAN), TURN);
        if (client.getCameraYawTarget() != wanted)
        {
            client.setCameraYawTarget(wanted);
        }
    }
}
