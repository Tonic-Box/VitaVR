package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the player anchor following.
 */
class PlayerAnchorTest
{
    private static final double FRAME = 1 / 90.0;

    @Test
    void itStartsWhereThePlayerIsAndSettlesOnThemWithoutOvershooting()
    {
        PlayerAnchor anchor = new PlayerAnchor();
        assertFalse(anchor.step(1000, -40, 2000, FRAME), "placing it first is not a jump");
        assertEquals(1000, anchor.x(), 1e-9);

        double furthest = 1000;
        for (int frame = 0; frame < 180; frame++)
        {
            anchor.step(1060, -40, 2000, FRAME);
            furthest = Math.max(furthest, anchor.x());
        }
        assertEquals(1060, anchor.x(), 0.5, "it settles on the player");
        assertTrue(furthest <= 1060 + 1e-6, "and never passes them");
    }

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
