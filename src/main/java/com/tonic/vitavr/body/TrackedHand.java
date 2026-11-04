package com.tonic.vitavr.body;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a tracked controller in the world: its position and its unit up, forward and right directions.
 */
@Getter
@RequiredArgsConstructor
public final class TrackedHand
{
    private final double[] at;
    private final double[] up;
    private final double[] forward;
    private final double[] right;
}
