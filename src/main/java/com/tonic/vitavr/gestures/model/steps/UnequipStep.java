package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Takes off whatever is worn in a slot, named as in EquipmentInventorySlot.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UnequipStep extends AbstractStep
{
    private String slot = "WEAPON";

    @Override
    public String describe()
    {
        return "Take off " + slot.toLowerCase().replace('_', ' ');
    }
}
