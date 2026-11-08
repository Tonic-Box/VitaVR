package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Opens a side tab, named as in VitaLite's Tab.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenTabStep extends AbstractStep
{
    private String tab = "INVENTORY_TAB";

    @Override
    public String describe()
    {
        return "Open " + tab.toLowerCase().replace("_tab", "").replace('_', ' ') + " tab";
    }
}
