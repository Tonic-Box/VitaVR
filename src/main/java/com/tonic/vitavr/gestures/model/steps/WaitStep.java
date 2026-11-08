package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Holds the steps after it back for some game ticks; without one, the steps run in the same tick.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WaitStep extends AbstractStep
{
    private int ticks = 1;

    @Override
    public String describe()
    {
        return "Wait " + ticks + (ticks == 1 ? " tick" : " ticks");
    }
}
