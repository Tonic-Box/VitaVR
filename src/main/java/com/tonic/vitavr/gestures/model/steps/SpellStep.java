package com.tonic.vitavr.gestures.model.steps;

import com.tonic.vitavr.gestures.model.ItemRef;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Casts a spell, named by its spellbook and its constant there, on nothing, on what the laser points at, or on an item.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpellStep extends AbstractStep
{
    /**
     * What the spell is cast on.
     */
    public enum Target
    {
        NONE("Nothing"),
        POINTED("What I point at"),
        ITEM("An item")
        ;

        private final String name;

        Target(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private String spellbook = "STANDARD";
    private String spell = "";
    private Target target = Target.NONE;
    private ItemRef item = new ItemRef();

    @Override
    public String describe()
    {
        String named = spell.toLowerCase().replace('_', ' ');
        switch (target)
        {
            case POINTED:
                return "Cast " + named + " on the pointed target";
            case ITEM:
                return "Cast " + named + " on " + item.describe();
            case NONE:
            default:
                return "Cast " + named;
        }
    }
}
