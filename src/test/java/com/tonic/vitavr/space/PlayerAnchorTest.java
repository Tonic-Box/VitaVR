package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the player anchor following.
 */
class PlayerAnchorTest
{
    private static final double FRAME = 1 / 90.0;

    @Test
    void itNeverTrailsMoreThanHalfATile()
    {
        PlayerAnchor anchor = new PlayerAnchor();
        anchor.step(0, 0, 0, FRAME);
        double x = 0;
        for (int frame = 0; frame < 90; frame++)
        {
            x += 4;
            anchor.step(x, 0, 0, FRAME);
            assertTrue(x - anchor.x() <= 64 + 1e-9, "at most half a tile behind on frame " + frame);
        }
    }

    @Test
    void aTeleportIsJumpedTo()
    {
        PlayerAnchor anchor = new PlayerAnchor();
        anchor.step(0, 0, 0, FRAME);
        assertTrue(anchor.step(5000, 0, 0, FRAME), "further than walking at once is a jump");
        assertEquals(5000, anchor.x(), 1e-9, "taken straight away");
    }
}
