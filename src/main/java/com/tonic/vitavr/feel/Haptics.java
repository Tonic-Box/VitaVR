package com.tonic.vitavr.feel;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.xr.Controllers;
import net.runelite.api.Client;
import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Controller vibration for hits taken and dealt, level ups and a low hitpoints heartbeat. Used on the client thread.
 */
public final class Haptics
{
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long CYCLE_MILLIS = 20;
    private static final int WEAPON_HAND = Controllers.RIGHT;
    private static final double FULL_HIT_SHARE = 0.25;
    private static final long BLOCK_MILLIS = 15;
    private static final float BLOCK_AMPLITUDE = 0.25f;
    private static final long TAKEN_MILLIS = 40;
    private static final long TAKEN_MORE_MILLIS = 80;
    private static final float TAKEN_AMPLITUDE = 0.35f;
    private static final float TAKEN_MORE_AMPLITUDE = 0.65f;
    private static final long DEALT_MILLIS = 30;
    private static final long DEALT_MORE_MILLIS = 50;
    private static final float DEALT_AMPLITUDE = 0.3f;
    private static final float DEALT_MORE_AMPLITUDE = 0.5f;
    private static final long LEVEL_MILLIS = 120;
    private static final long LEVEL_GAP_MILLIS = 200;
    private static final long BEAT_EVERY_MILLIS = 1000;
    private static final long BEAT_MILLIS = 60;
    private static final long SECOND_BEAT_AFTER_MILLIS = 180;
    private static final long SECOND_BEAT_MILLIS = 50;
    private static final float BEAT_AMPLITUDE = 0.6f;
    private static final float SECOND_BEAT_AMPLITUDE = 0.4f;

    /** A pending pulse for one hand, before the vibration setting is applied. */
    private static final class Pulse
    {
        final long dueMillis;
        final int hand;
        final long durationNanos;
        final float amplitude;

        Pulse(long dueMillis, int hand, long durationNanos, float amplitude)
        {
            this.dueMillis = dueMillis;
            this.hand = hand;
            this.durationNanos = durationNanos;
            this.amplitude = amplitude;
        }
    }

    private final Client client;
    private final VitaVRConfig config;
    private final List<Pulse> pending = new ArrayList<>();
    private final int[] levels = new int[Skill.values().length];
    private long nextBeat = Long.MIN_VALUE;

    /**
     * Creates the haptics.
     * @param client The client.
     * @param config The VitaVR settings.
     */
    public Haptics(Client client, VitaVRConfig config)
    {
        this.client = client;
        this.config = config;
    }

    /**
     * Queues a pulse for a hitsplat the player took or dealt, due when it shows.
     * @param taken Whether it is on the player.
     * @param dealt Whether the player dealt it.
     * @param amount The hit amount.
     * @param showsIn The game cycles until it shows.
     */
    public void hitsplat(boolean taken, boolean dealt, int amount, int showsIn)
    {
        long due = System.currentTimeMillis() + showsIn * CYCLE_MILLIS;
        double share = Math.min(1, amount / (FULL_HIT_SHARE * Math.max(1, client.getRealSkillLevel(Skill.HITPOINTS))));
        if (taken && config.feelHitsTaken())
        {
            long millis = amount <= 0 ? BLOCK_MILLIS : TAKEN_MILLIS + Math.round(TAKEN_MORE_MILLIS * share);
            float amplitude = amount <= 0 ? BLOCK_AMPLITUDE : (float) (TAKEN_AMPLITUDE + TAKEN_MORE_AMPLITUDE * share);
            both(due, millis, amplitude);
        }
        else if (dealt && config.feelHitsDealt())
        {
            long millis = amount <= 0 ? BLOCK_MILLIS : DEALT_MILLIS + Math.round(DEALT_MORE_MILLIS * share);
            float amplitude = amount <= 0 ? BLOCK_AMPLITUDE : (float) (DEALT_AMPLITUDE + DEALT_MORE_AMPLITUDE * share);
            pending.add(new Pulse(due, WEAPON_HAND, millis * NANOS_PER_MILLI, amplitude));
        }
    }

    /**
     * Checks level ups and the heartbeat, then sends the pulses now due.
     * @param controllers The controllers to pulse.
     */
    public void update(Controllers controllers)
    {
        long now = System.currentTimeMillis();
        feelLevelUps(now);
        feelHeartbeat(now);
        float strength = config.vibrationPercent() / 100f;
        for (Iterator<Pulse> each = pending.iterator(); each.hasNext(); )
        {
            Pulse pulse = each.next();
            if (pulse.dueMillis > now)
            {
                continue;
            }
            each.remove();
            if (strength > 0)
            {
                controllers.pulse(pulse.hand, pulse.durationNanos, pulse.amplitude * strength);
            }
        }
    }

    private void feelLevelUps(long now)
    {
        boolean rose = false;
        Skill[] skills = Skill.values();
        for (int skill = 0; skill < skills.length; skill++)
        {
            int level = client.getRealSkillLevel(skills[skill]);
            rose |= levels[skill] > 0 && level > levels[skill];
            levels[skill] = level;
        }
        if (rose && config.feelLevelUps())
        {
            both(now, LEVEL_MILLIS, 1);
            both(now + LEVEL_GAP_MILLIS, LEVEL_MILLIS, 1);
        }
    }

    private void feelHeartbeat(long now)
    {
        int hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
        int left = client.getBoostedSkillLevel(Skill.HITPOINTS);
        boolean low = config.heartbeat() && hitpoints > 0 && left > 0 && left * 100 < config.heartbeatBelowPercent() * hitpoints;
        if (!low)
        {
            nextBeat = Long.MIN_VALUE;
            return;
        }
        if (now < nextBeat)
        {
            return;
        }
        nextBeat = now + BEAT_EVERY_MILLIS;
        both(now, BEAT_MILLIS, BEAT_AMPLITUDE);
        both(now + SECOND_BEAT_AFTER_MILLIS, SECOND_BEAT_MILLIS, SECOND_BEAT_AMPLITUDE);
    }

    private void both(long due, long millis, float amplitude)
    {
        pending.add(new Pulse(due, Controllers.LEFT, millis * NANOS_PER_MILLI, amplitude));
        pending.add(new Pulse(due, Controllers.RIGHT, millis * NANOS_PER_MILLI, amplitude));
    }
}
