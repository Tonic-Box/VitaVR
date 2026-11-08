package com.tonic.vitavr.gestures.actions;

import com.tonic.vitavr.gestures.model.ItemRef;

import java.util.List;

/**
 * Finds the item a reference names among some items, choosing between several as the reference says.
 */
public final class ItemResolver
{
    private ItemResolver()
    {
    }

    /**
     * @param ref   The reference.
     * @param items The items to look among, in slot order.
     * @return The item to use, or null when none matches.
     */
    public static ItemView pick(ItemRef ref, List<ItemView> items)
    {
        if (ref == null)
        {
            return null;
        }
        if (ref.getMatch() == ItemRef.Match.FIRST_OF)
        {
            for (ItemRef option : ref.getOptions())
            {
                ItemView found = pick(option, items);
                if (found != null)
                {
                    return found;
                }
            }
            return null;
        }
        ItemView chosen = null;
        for (ItemView item : items)
        {
            if (!matches(ref, item))
            {
                continue;
            }
            if (chosen == null || better(ref.getPick(), item, chosen))
            {
                chosen = item;
            }
        }
        return chosen;
    }

    /**
     * @param ref  A reference that is not first-of.
     * @param item An item.
     * @return Whether the reference names the item.
     */
    public static boolean matches(ItemRef ref, ItemView item)
    {
        String name = item.getName() == null ? "" : item.getName();
        switch (ref.getMatch())
        {
            case EXACT:
                return ref.getIds().contains(item.getId());
            case VARIANTS:
                return ItemNames.base(name).equalsIgnoreCase(ItemNames.base(ref.getName()));
            case PATTERN:
                return ItemNames.fits(ref.getName(), name);
            case GROUP:
                return item.has(ref.getOp());
            case FIRST_OF:
                return pick(ref, List.of(item)) != null;
            default:
                return false;
        }
    }

    private static boolean better(ItemRef.Pick pick, ItemView item, ItemView chosen)
    {
        switch (pick)
        {
            case LAST_SLOT:
                return item.getSlot() > chosen.getSlot();
            case FIRST_SLOT:
                return item.getSlot() < chosen.getSlot();
            case LOWEST_DOSE:
            default:
                int dose = ItemNames.count(item.getName());
                int chosenDose = ItemNames.count(chosen.getName());
                if (dose >= 0 && (chosenDose < 0 || dose < chosenDose))
                {
                    return true;
                }
                return dose == chosenDose && item.getSlot() < chosen.getSlot();
        }
    }
}
