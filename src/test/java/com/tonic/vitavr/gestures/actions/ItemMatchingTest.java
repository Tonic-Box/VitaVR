package com.tonic.vitavr.gestures.actions;

import com.tonic.vitavr.gestures.model.ItemRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemMatchingTest
{
    @Test
    void aDoseOrChargeComesOffTheName()
    {
        assertEquals("Saradomin brew", ItemNames.base("Saradomin brew(3)"));
        assertEquals("Ring of dueling", ItemNames.base("Ring of dueling (8)"));
        assertEquals("Shark", ItemNames.base("Shark"));
        assertEquals(3, ItemNames.count("Saradomin brew(3)"));
        assertEquals(-1, ItemNames.count("Shark"));
    }

    @Test
    void aPatternFitsTheWholeNameIgnoringCase()
    {
        assertTrue(ItemNames.fits("*shark*", "Cooked shark"));
        assertTrue(ItemNames.fits("Super restore(*)", "super restore(2)"));
        assertFalse(ItemNames.fits("shark", "Cooked shark"));
        assertTrue(ItemNames.fits("a.b", "A.B"));
        assertFalse(ItemNames.fits("a.b", "axb"));
    }

    @Test
    void anyDosePicksTheLowestDoseFirst()
    {
        List<ItemView> bag = List.of(item(6685, "Saradomin brew(4)", 0), item(6691, "Saradomin brew(1)", 5), item(6687, "Saradomin brew(3)", 2));

        ItemView picked = ItemResolver.pick(ItemRef.named(ItemRef.Match.VARIANTS, "Saradomin brew"), bag);

        assertEquals(5, picked.getSlot());
    }

    @Test
    void firstSlotAndLastSlotPickByPlace()
    {
        List<ItemView> bag = List.of(item(385, "Shark", 3), item(385, "Shark", 9), item(385, "Shark", 1));
        ItemRef sharks = ItemRef.exact("Shark", 385);

        sharks.setPick(ItemRef.Pick.FIRST_SLOT);
        assertEquals(1, ItemResolver.pick(sharks, bag).getSlot());
        sharks.setPick(ItemRef.Pick.LAST_SLOT);
        assertEquals(9, ItemResolver.pick(sharks, bag).getSlot());
    }

    @Test
    void aGroupIsAnyItemWithTheOp()
    {
        List<ItemView> bag = List.of(new ItemView(1, "Rune scimitar", 0, List.of("Wield", "Drop"), false), new ItemView(2, "Lobster", 4, List.of("Eat", "Drop"), false));

        assertEquals(2, ItemResolver.pick(ItemRef.group("eat"), bag).getId());
    }

    @Test
    void firstOfTakesTheFirstOptionThatIsThere()
    {
        ItemRef food = new ItemRef();
        food.setMatch(ItemRef.Match.FIRST_OF);
        food.getOptions().add(ItemRef.named(ItemRef.Match.VARIANTS, "Saradomin brew"));
        food.getOptions().add(ItemRef.exact("Shark", 385));
        food.getOptions().add(ItemRef.group("Eat"));

        assertEquals(385, ItemResolver.pick(food, List.of(item(379, "Lobster", 0), item(385, "Shark", 1))).getId());
        assertEquals(379, ItemResolver.pick(food, List.of(item(379, "Lobster", 0))).getId());
        assertNull(ItemResolver.pick(food, List.of(new ItemView(1, "Rune scimitar", 0, List.of("Wield"), false))));
    }

    static ItemView item(int id, String name, int slot)
    {
        return new ItemView(id, name, slot, List.of(name.contains("brew") ? "Drink" : "Eat", "Drop"), false);
    }
}
