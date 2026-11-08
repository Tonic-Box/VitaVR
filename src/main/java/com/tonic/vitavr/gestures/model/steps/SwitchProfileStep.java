package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Makes another profile the one in use.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SwitchProfileStep extends AbstractStep
{
    private String profile = "";

    @Override
    public String describe()
    {
        return "Switch to profile " + profile;
    }
}
