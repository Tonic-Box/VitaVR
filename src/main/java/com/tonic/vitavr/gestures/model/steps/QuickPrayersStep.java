package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Turns the quick prayers on, off or over.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuickPrayersStep extends AbstractStep
{
    private Switch mode = Switch.TOGGLE;

    @Override
    public String describe()
    {
        return "Quick prayers " + mode.toString().toLowerCase();
    }
}
