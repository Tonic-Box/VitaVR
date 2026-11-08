package com.tonic.vitavr.gestures.vr;

import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Gesture;

/**
 * Runs what a recognised gesture is bound to, on the client thread.
 */
public interface GestureActions
{
    /**
     * @param gesture The gesture made.
     * @param binding What it does in the profile in use, or null when it is not bound there.
     * @return What came of it.
     */
    GestureOutcome run(Gesture gesture, Binding binding);
}
