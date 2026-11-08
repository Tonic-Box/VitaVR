package com.tonic.vitavr.gestures.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * An item named by what it is rather than where it lies, found in the inventory or worn when a step runs.
 */
@Data
public class ItemRef
{
    /**
     * How the item is matched.
     */
    public enum Match
    {
        /** Any of the ids. */
        EXACT,
        /** The name with any dose or charge, such as Saradomin brew(1) to (4). */
        VARIANTS,
        /** A name with * wildcards. */
        PATTERN,
        /** Any item with the op, such as Eat. */
        GROUP,
        /** The first of the options found. */
        FIRST_OF
    }

    /**
     * Which of several matching items is used.
     */
    public enum Pick
    {
        LOWEST_DOSE,
        FIRST_SLOT,
        LAST_SLOT
    }

    private Match match = Match.EXACT;
    private List<Integer> ids = new ArrayList<>();
    private String name = "";
    private String op = "";
    private List<ItemRef> options = new ArrayList<>();
    private Pick pick = Pick.LOWEST_DOSE;

    /**
     * @param ids The item ids.
     * @param name What to show it as.
     * @return A reference to exactly those items.
     */
    public static ItemRef exact(String name, Integer... ids)
    {
        ItemRef ref = new ItemRef();
        ref.setName(name);
        ref.getIds().addAll(List.of(ids));
        return ref;
    }

    /**
     * @param match How the name is matched.
     * @param name The name, base name or pattern.
     * @return A reference by name.
     */
    public static ItemRef named(Match match, String name)
    {
        ItemRef ref = new ItemRef();
        ref.setMatch(match);
        ref.setName(name);
        return ref;
    }

    /**
     * @param op The op the items have, such as Eat.
     * @return A reference to any item with it.
     */
    public static ItemRef group(String op)
    {
        ItemRef ref = new ItemRef();
        ref.setMatch(Match.GROUP);
        ref.setOp(op);
        return ref;
    }

    /**
     * @return A short label for lists, such as "Saradomin brew (any dose)".
     */
    public String describe()
    {
        switch (match)
        {
            case VARIANTS:
                return name + " (any dose)";
            case PATTERN:
                return "\"" + name + "\"";
            case GROUP:
                return "Any item to " + op;
            case FIRST_OF:
                StringBuilder text = new StringBuilder();
                for (ItemRef option : options)
                {
                    text.append(text.length() == 0 ? "" : ", else ").append(option.describe());
                }
                return text.length() == 0 ? "Nothing" : text.toString();
            case EXACT:
            default:
                return name.isEmpty() ? "Item " + ids : name;
        }
    }
}
