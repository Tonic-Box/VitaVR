package com.tonic.vitavr.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests recording frame pacing.
 */
class FramePacerTest
{
    private static final long MILLIS = 1_000_000L;

    @Test
    void aFasterLoopTakesAFrameOnlyAsOneFallsDue()
    {
        FramePacer pacer = new FramePacer(60);
        int taken = 0;
        for (long at = 0; at < 1000 * MILLIS; at += 11 * MILLIS)
        {
            taken += pacer.due(at);
        }
        assertEquals(60, taken, 1, "sixty a second from a loop at ninety");
    }

    @Test
    void aSlowerLoopWritesTheFrameAgainForEachMissed()
    {
        FramePacer pacer = new FramePacer(60);
        assertEquals(1, pacer.due(0), "the first frame is taken at once");
        assertEquals(0, pacer.due(10 * MILLIS), "none is due yet");
        assertEquals(3, pacer.due(50 * MILLIS), "fifty milliseconds in, frames 1 to 3 are owed");
        assertEquals(4, pacer.written());
    }

    @Test
    void aLongStallStartsAgainFromNow()
    {
        FramePacer pacer = new FramePacer(60);
        pacer.due(0);
        assertEquals(1, pacer.due(5000 * MILLIS), "five seconds missed are not filled in");
        assertEquals(0, pacer.due(5001 * MILLIS));
        assertEquals(1, pacer.due(5017 * MILLIS), "and the pace goes on from there");
    }
}
