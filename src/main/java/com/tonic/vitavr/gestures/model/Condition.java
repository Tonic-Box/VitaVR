package com.tonic.vitavr.gestures.model;

import lombok.Data;

/**
 * Something about the game that must hold for a binding or a step to run.
 */
@Data
public class Condition
{
    /**
     * What is checked.
     */
    public enum Kind
    {
        HP_AT_MOST("Hitpoints at most"),
        HP_PERCENT_AT_MOST("Hitpoints % at most"),
        PRAYER_AT_MOST("Prayer at most"),
        SPEC_AT_LEAST("Special attack % at least"),
        RUN_AT_LEAST("Run energy % at least"),
        WEARING("Wearing"),
        NOT_WEARING("Not wearing"),
        CARRYING("Carrying"),
        NOT_CARRYING("Not carrying")
        ;

        private final String name;

        Kind(String name)
        {
            this.name = name;
        }

        /**
         * @return Whether the condition names an item rather than a number.
         */
        public boolean takesItem()
        {
            return this == WEARING || this == NOT_WEARING || this == CARRYING || this == NOT_CARRYING;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private Kind kind = Kind.HP_PERCENT_AT_MOST;
    private int value = 50;
    private ItemRef item = new ItemRef();

    /**
     * @return A short label, such as "Hitpoints % at most 50".
     */
    public String describe()
    {
        return kind + " " + (kind.takesItem() ? item.describe() : String.valueOf(value));
    }
}
