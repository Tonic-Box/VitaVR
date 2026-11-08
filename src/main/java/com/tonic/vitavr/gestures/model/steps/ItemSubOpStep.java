package com.tonic.vitavr.gestures.model.steps;

import com.tonic.vitavr.gestures.model.ItemRef;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Uses an option inside an item's op, such as Rub then Edgeville, or the option itself on the item worn.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemSubOpStep extends AbstractStep
{
    private ItemRef item = new ItemRef();
    private String menu = "Rub";
    private String action = "";

    @Override
    public String describe()
    {
        return menu + " > " + action + ": " + item.describe();
    }
}
