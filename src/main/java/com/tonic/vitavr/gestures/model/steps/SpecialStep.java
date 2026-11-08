package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Turns the special attack bar on, off or over.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpecialStep extends AbstractStep
{
    private Switch mode = Switch.ON;

    @Override
    public String describe()
    {
        return "Special attack " + mode.toString().toLowerCase();
    }
}
