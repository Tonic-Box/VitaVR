package com.tonic.vitavr.gestures.model.steps;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns prayers on, off or over, named as in VitaLite's PrayerAPI; only these turns the rest off.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PrayerStep extends AbstractStep
{
    /**
     * What happens to the prayers.
     */
    public enum Mode
    {
        ON("On"),
        OFF("Off"),
        TOGGLE("Toggle"),
        ONLY("Only these")
        ;

        private final String name;

        Mode(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private List<String> prayers = new ArrayList<>();
    private Mode mode = Mode.ON;

    @Override
    public String describe()
    {
        List<String> named = new ArrayList<>();
        for (String prayer : prayers)
        {
            String lower = prayer.toLowerCase().replace('_', ' ');
            named.add(lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
        return "Prayer " + mode.toString().toLowerCase() + ": " + (named.isEmpty() ? "none chosen" : String.join(", ", named));
    }
}
