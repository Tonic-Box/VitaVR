package com.tonic.vitavr.gestures.model;

import com.tonic.vitavr.gestures.model.steps.AbstractStep;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * What a gesture does in a profile: its steps in order, the conditions all of them need, and how soon it may run again.
 */
@Data
public class Binding
{
    public static final int DEFAULT_COOLDOWN_MS = 600;

    private List<AbstractStep> steps = new ArrayList<>();
    private List<Condition> conditions = new ArrayList<>();
    private int cooldownMs = DEFAULT_COOLDOWN_MS;

    /**
     * @return A short label, such as "Eat: Shark · 2 steps".
     */
    public String describe()
    {
        if (steps.isEmpty())
        {
            return "Not bound";
        }
        String first = steps.get(0).describe();
        return steps.size() == 1 ? first : first + " · " + steps.size() + " steps";
    }
}
