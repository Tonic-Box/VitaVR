package com.tonic.vitavr.gestures.model.steps;

import com.tonic.vitavr.gestures.model.Condition;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One thing a binding does, with its own conditions and what happens to the rest when it cannot run.
 */
@Data
public abstract class AbstractStep
{
    /**
     * What a step that cannot run does to the steps after it.
     */
    public enum IfCannot
    {
        SKIP("Skip it"),
        STOP("Stop the rest")
        ;

        private final String name;

        IfCannot(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private IfCannot ifCannot = IfCannot.SKIP;
    private List<Condition> conditions = new ArrayList<>();

    /**
     * @return A short label for the step list.
     */
    public abstract String describe();
}
