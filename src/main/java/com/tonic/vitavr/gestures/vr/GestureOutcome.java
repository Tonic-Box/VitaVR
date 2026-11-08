package com.tonic.vitavr.gestures.vr;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What came of a gesture: whether its binding ran, and a few words to show by the hand.
 */
@Getter
@AllArgsConstructor
public final class GestureOutcome
{
    private final boolean done;
    private final String text;
}
