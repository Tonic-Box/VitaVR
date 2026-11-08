package com.tonic.vitavr.gestures.actions;

import java.util.List;

/**
 * What steps read from and do in the game, on the client thread; each action says why it could not, or returns null.
 */
public interface Game
{
    List<ItemView> inventory();

    List<ItemView> worn();

    int hitpoints();

    int maxHitpoints();

    int prayerPoints();

    int specialEnergy();

    boolean specialOn();

    int runEnergy();

    boolean runOn();

    /**
     * @param prayer A prayer named as in VitaLite's PrayerAPI.
     * @return Whether it is on now, as the game last told.
     */
    boolean prayerOn(String prayer);

    /**
     * @return The prayers on now, as the game last told.
     */
    List<String> prayersOn();

    boolean quickPrayersOn();

    /**
     * @return What the pointer is on now, kept to act on later, or null when it is on nothing a spell or item can be used on.
     */
    Object pointed();

    /**
     * @param item An inventory or worn item.
     * @param op   The op, such as Eat or Teleport.
     * @return Why it could not, or null.
     */
    String itemOp(ItemView item, String op);

    String subOp(ItemView item, String menu, String action);

    String equip(ItemView item);

    /**
     * @param slot A slot named as in VitaLite's EquipmentSlot.
     * @return Why it could not, or null.
     */
    String unequip(String slot);

    String togglePrayer(String prayer);

    String toggleQuickPrayers();

    String toggleSpecial();

    String toggleRun();

    /**
     * @param tab A tab named as in VitaLite's Tab.
     * @return Why it could not, or null.
     */
    String openTab(String tab);

    /**
     * @param spellbook A spellbook: STANDARD, ANCIENT, LUNAR or NECROMANCY.
     * @param spell     The spell's constant in that spellbook.
     * @param target    What pointed gave, an item, or null to cast on nothing.
     * @return Why it could not, or null.
     */
    String cast(String spellbook, String spell, Object target);

    /**
     * @param item   An inventory item.
     * @param target What pointed gave, or an item.
     * @return Why it could not, or null.
     */
    String useOn(ItemView item, Object target);
}
