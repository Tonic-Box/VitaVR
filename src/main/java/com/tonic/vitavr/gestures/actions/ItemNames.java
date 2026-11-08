package com.tonic.vitavr.gestures.actions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Item names as references match them: the name without its dose or charge, the dose or charge itself, and names
 * against a pattern with * wildcards, all ignoring case.
 */
public final class ItemNames
{
    private static final Pattern COUNTED = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)\\s*$");

    private ItemNames()
    {
    }

    /**
     * @param name An item name, such as Saradomin brew(3).
     * @return The name without a trailing dose or charge in brackets, such as Saradomin brew.
     */
    public static String base(String name)
    {
        Matcher counted = COUNTED.matcher(name);
        return counted.matches() ? counted.group(1).trim() : name.trim();
    }

    /**
     * @param name An item name.
     * @return The dose or charge in brackets at its end, or -1 when it has none.
     */
    public static int count(String name)
    {
        Matcher counted = COUNTED.matcher(name);
        if (!counted.matches())
        {
            return -1;
        }
        try
        {
            return Integer.parseInt(counted.group(2));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /**
     * @param pattern A name with * for any run of characters.
     * @param name    An item name.
     * @return Whether the whole name fits the pattern, ignoring case.
     */
    public static boolean fits(String pattern, String name)
    {
        String[] parts = pattern.trim().split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
            {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(name.trim()).matches();
    }
}
