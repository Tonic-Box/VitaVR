package com.tonic.vitavr.gestures.model.steps;

import com.tonic.vitavr.gestures.model.ItemRef;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Uses an item's op, such as Eat or Wield, on the item wherever it lies or is worn.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemOpStep extends AbstractStep
{
    /** Eat, or Drink where the item has no Eat. */
    public static final String CONSUME = "Eat or drink";

    private ItemRef item = new ItemRef();
    private String op = CONSUME;

    @Override
    public String describe()
    {
        return op + ": " + item.describe();
    }
}
