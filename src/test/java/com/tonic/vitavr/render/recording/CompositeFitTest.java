package com.tonic.vitavr.render.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CompositeFitTest
{
    @Test
    void aWideWindowGetsBarsLeftAndRight()
    {
        assertArrayEquals(new int[]{200, 0, 1280, 720}, Composite.fit(Composite.WIDTH, Composite.HEIGHT, 0, 1680, 720));
    }

    @Test
    void aTallWindowGetsBarsAboveAndBelow()
    {
        assertArrayEquals(new int[]{0, 240, 800, 450}, Composite.fit(Composite.WIDTH, Composite.HEIGHT, 0, 800, 930));
    }

    @Test
    void aMatchingWindowIsFilled()
    {
        assertArrayEquals(new int[]{0, 0, 1280, 720}, Composite.fit(Composite.WIDTH, Composite.HEIGHT, 0, 1280, 720));
    }
}
