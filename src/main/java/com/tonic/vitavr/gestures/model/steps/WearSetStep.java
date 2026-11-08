package com.tonic.vitavr.gestures.model.steps;

import com.tonic.vitavr.gestures.model.ItemRef;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Wears every piece of a set that is carried and not yet worn, all at once.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WearSetStep extends AbstractStep
{
    private String name = "";
    private List<ItemRef> items = new ArrayList<>();

    @Override
    public String describe()
    {
        return "Wear " + (name.isEmpty() ? items.size() + " items" : name);
    }
}
