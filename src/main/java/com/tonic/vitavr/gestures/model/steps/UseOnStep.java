package com.tonic.vitavr.gestures.model.steps;

import com.tonic.vitavr.gestures.model.ItemRef;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Uses an item on what the laser points at, or on another item.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UseOnStep extends AbstractStep
{
    private ItemRef item = new ItemRef();
    private boolean onPointed = true;
    private ItemRef onItem = new ItemRef();

    @Override
    public String describe()
    {
        return "Use " + item.describe() + " on " + (onPointed ? "the pointed target" : onItem.describe());
    }
}
