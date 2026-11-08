package com.tonic.vitavr.gestures.actions;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * An item as a step sees it: in the inventory or worn, with its name and the ops it offers there.
 */
@Getter
@AllArgsConstructor
public final class ItemView
{
    private final int id;
    private final String name;
    private final int slot;
    private final List<String> actions;
    private final boolean worn;

    /**
     * @param op An op, such as Eat.
     * @return Whether the item offers it, ignoring case.
     */
    public boolean has(String op)
    {
        for (String action : actions)
        {
            if (action != null && action.equalsIgnoreCase(op))
            {
                return true;
            }
        }
        return false;
    }
}
