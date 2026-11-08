package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Turns running on, off or over.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RunStep extends AbstractStep
{
    private Switch mode = Switch.TOGGLE;

    @Override
    public String describe()
    {
        return "Run " + mode.toString().toLowerCase();
    }
}
