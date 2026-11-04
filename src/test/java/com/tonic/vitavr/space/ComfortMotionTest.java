package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The edges close in quickly while the world moves and open slowly after; a jump goes black and fades back. */
class ComfortMotionTest
{
    private static final double FRAME = 1 / 90.0;

    @Test
    void theEdgesCloseWhileTurningAndOpenAfter()
    {
        ComfortMotion motion = new ComfortMotion();
        motion.step(0, 0, false, FRAME);
        assertEquals(0, motion.vignette(), 1e-12, "still, nothing closes");
        for (int frame = 0; frame < 45; frame++)
        {
            motion.step(Math.toRadians(90), 0, false, FRAME);
        }
        assertTrue(motion.vignette() > 0.95, "half a second of a full turn closes them: " + motion.vignette());
        for (int frame = 0; frame < 9; frame++)
        {
            motion.step(0, 0, false, FRAME);
        }
        assertTrue(motion.vignette() > 0.6, "a tenth of a second after, they are still mostly closed: " + motion.vignette());
        for (int frame = 0; frame < 180; frame++)
        {
            motion.step(0, 0, false, FRAME);
        }
        assertTrue(motion.vignette() < 0.01, "two seconds after, open");
    }

    @Test
    void walkingClosesThemPartWay()
    {
        ComfortMotion motion = new ComfortMotion();
        for (int frame = 0; frame < 180; frame++)
        {
            motion.step(0, 2, false, FRAME);
        }
        assertEquals(0.5, motion.vignette(), 1e-3, "two metres a second is half of full");
    }

    @Test
    void aJumpGoesBlackAndFadesBack()
    {
        ComfortMotion motion = new ComfortMotion();
        motion.step(0, 0, true, FRAME);
        assertEquals(1, motion.black(), 1e-12);
        for (int frame = 0; frame < 18; frame++)
        {
            motion.step(0, 0, false, FRAME);
        }
        assertTrue(motion.black() > 0.3 && motion.black() < 0.6, "a fifth of a second on, half faded: " + motion.black());
        for (int frame = 0; frame < 90; frame++)
        {
            motion.step(0, 0, false, FRAME);
        }
        assertEquals(0, motion.black(), 1e-12);
    }
}
