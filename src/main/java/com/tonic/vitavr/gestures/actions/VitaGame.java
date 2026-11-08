package com.tonic.vitavr.gestures.actions;

import com.tonic.api.game.CombatAPI;
import com.tonic.api.game.MovementAPI;
import com.tonic.api.widgets.EquipmentAPI;
import com.tonic.api.widgets.InventoryAPI;
import com.tonic.api.widgets.PrayerAPI;
import com.tonic.api.widgets.TabsAPI;
import com.tonic.api.widgets.WidgetAPI;
import com.tonic.data.EquipmentSlot;
import com.tonic.data.Tab;
import com.tonic.data.magic.Spell;
import com.tonic.data.magic.spellbooks.Ancient;
import com.tonic.data.magic.spellbooks.Lunar;
import com.tonic.data.magic.spellbooks.Necromancy;
import com.tonic.data.magic.spellbooks.Standard;
import com.tonic.data.wrappers.ActorEx;
import com.tonic.data.wrappers.ItemEx;
import com.tonic.data.wrappers.NpcEx;
import com.tonic.data.wrappers.PlayerEx;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The game as VitaLite reads and acts on it, on the client thread.
 */
public final class VitaGame implements Game
{
    private final Client client;

    /**
     * @param client The client.
     */
    public VitaGame(Client client)
    {
        this.client = client;
    }

    @Override
    public List<ItemView> inventory()
    {
        List<ItemView> out = new ArrayList<>();
        for (ItemEx item : InventoryAPI.getItems())
        {
            if (item != null && item.getId() > 0)
            {
                String[] actions = item.getActions();
                out.add(new ItemView(item.getId(), item.getName(), item.getSlot(), actions == null ? Collections.emptyList() : Arrays.asList(actions), false));
            }
        }
        return out;
    }

    @Override
    public List<ItemView> worn()
    {
        List<ItemView> out = new ArrayList<>();
        for (ItemEx item : EquipmentAPI.getAll())
        {
            if (item != null && item.getId() > 0)
            {
                out.add(new ItemView(item.getId(), item.getName(), item.getSlot(), Collections.emptyList(), true));
            }
        }
        return out;
    }

    @Override
    public int hitpoints()
    {
        return CombatAPI.getHealth();
    }

    @Override
    public int maxHitpoints()
    {
        return client.getRealSkillLevel(Skill.HITPOINTS);
    }

    @Override
    public int prayerPoints()
    {
        return client.getBoostedSkillLevel(Skill.PRAYER);
    }

    @Override
    public int specialEnergy()
    {
        return CombatAPI.getSpecEnergy();
    }

    @Override
    public boolean specialOn()
    {
        return CombatAPI.isSpecEnabled();
    }

    @Override
    public int runEnergy()
    {
        return MovementAPI.getRunEnergy();
    }

    @Override
    public boolean runOn()
    {
        return MovementAPI.isRunEnabled();
    }

    @Override
    public boolean prayerOn(String prayer)
    {
        PrayerAPI found = prayer(prayer);
        return found != null && found.isActive();
    }

    @Override
    public List<String> prayersOn()
    {
        List<String> out = new ArrayList<>();
        for (PrayerAPI prayer : PrayerAPI.getActivePrayers())
        {
            out.add(prayer.name());
        }
        return out;
    }

    @Override
    public boolean quickPrayersOn()
    {
        return PrayerAPI.isQuickPrayerEnabled();
    }

    @Override
    public Object pointed()
    {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        if (entries == null || entries.length == 0)
        {
            return null;
        }
        MenuEntry top = entries[entries.length - 1];
        Actor actor = top.getNpc() != null ? top.getNpc() : top.getPlayer();
        return actor;
    }

    @Override
    public String itemOp(ItemView item, String op)
    {
        ItemEx live = find(item);
        if (live == null)
        {
            return "No " + item.getName();
        }
        if (item.isWorn())
        {
            if (ItemEx.getEquippedActionIndex(live, op) <= 0)
            {
                return item.getName() + " has no " + op + " worn";
            }
            EquipmentAPI.interact(live, op);
            return null;
        }
        InventoryAPI.interact(live, op);
        return null;
    }

    @Override
    public String subOp(ItemView item, String menu, String action)
    {
        ItemEx live = find(item);
        if (live == null)
        {
            return "No " + item.getName();
        }
        if (item.isWorn())
        {
            if (ItemEx.getEquippedActionIndex(live, action) <= 0)
            {
                return item.getName() + " has no " + action + " worn";
            }
            EquipmentAPI.interact(live, action);
            return null;
        }
        InventoryAPI.interactSubOp(live, menu, action);
        return null;
    }

    @Override
    public String equip(ItemView item)
    {
        ItemEx live = find(item);
        if (live == null)
        {
            return "No " + item.getName();
        }
        InventoryAPI.interact(live, 3);
        return null;
    }

    @Override
    public String unequip(String slot)
    {
        EquipmentSlot named;
        try
        {
            named = EquipmentSlot.valueOf(slot);
        }
        catch (IllegalArgumentException e)
        {
            return "No slot " + slot;
        }
        if (EquipmentAPI.fromSlot(named) == null)
        {
            return "Nothing worn in " + slot.toLowerCase();
        }
        EquipmentAPI.unEquip(named);
        return null;
    }

    @Override
    public String togglePrayer(String prayer)
    {
        PrayerAPI found = prayer(prayer);
        if (found == null)
        {
            return "No prayer " + prayer;
        }
        if (!found.hasLevelFor())
        {
            return "Prayer level too low for " + readable(prayer);
        }
        if (prayerPoints() <= 0)
        {
            return "Out of prayer points";
        }
        found.toggle();
        return null;
    }

    @Override
    public String toggleQuickPrayers()
    {
        if (prayerPoints() <= 0 && !PrayerAPI.isQuickPrayerEnabled())
        {
            return "Out of prayer points";
        }
        PrayerAPI.toggleQuickPrayer();
        return null;
    }

    @Override
    public String toggleSpecial()
    {
        WidgetAPI.interact(1, InterfaceID.CombatInterface.SPECIAL_ATTACK, -1, -1);
        return null;
    }

    @Override
    public String toggleRun()
    {
        MovementAPI.toggleRun();
        return null;
    }

    @Override
    public String openTab(String tab)
    {
        try
        {
            TabsAPI.open(Tab.valueOf(tab));
            return null;
        }
        catch (IllegalArgumentException e)
        {
            return "No tab " + tab;
        }
    }

    @Override
    public String cast(String spellbook, String spell, Object target)
    {
        Spell found = spell(spellbook, spell);
        if (found == null)
        {
            return "No spell " + spell;
        }
        if (!found.canCast())
        {
            return "Cannot cast " + readable(spell);
        }
        if (target == null)
        {
            found.cast();
            return null;
        }
        if (target instanceof ItemView)
        {
            ItemEx live = find((ItemView) target);
            if (live == null)
            {
                return "No " + ((ItemView) target).getName();
            }
            found.castOn(live);
            return null;
        }
        ActorEx<?> actor = target instanceof Actor ? ActorEx.fromActor((Actor) target) : null;
        if (actor instanceof NpcEx)
        {
            found.castOn((NpcEx) actor);
            return null;
        }
        if (actor instanceof PlayerEx)
        {
            found.castOn((PlayerEx) actor);
            return null;
        }
        return "Nothing to cast on";
    }

    @Override
    public String useOn(ItemView item, Object target)
    {
        ItemEx live = find(item);
        if (live == null)
        {
            return "No " + item.getName();
        }
        if (target instanceof ItemView)
        {
            ItemEx other = find((ItemView) target);
            if (other == null)
            {
                return "No " + ((ItemView) target).getName();
            }
            InventoryAPI.useOn(live, other);
            return null;
        }
        ActorEx<?> actor = target instanceof Actor ? ActorEx.fromActor((Actor) target) : null;
        if (actor instanceof NpcEx)
        {
            InventoryAPI.useOn(live, (NpcEx) actor);
            return null;
        }
        if (actor instanceof PlayerEx)
        {
            InventoryAPI.useOn(live, (PlayerEx) actor);
            return null;
        }
        return "Nothing to use it on";
    }

    private static ItemEx find(ItemView item)
    {
        List<ItemEx> items = item.isWorn() ? EquipmentAPI.getAll() : InventoryAPI.getItems();
        for (ItemEx each : items)
        {
            if (each != null && each.getId() == item.getId() && each.getSlot() == item.getSlot())
            {
                return each;
            }
        }
        return null;
    }

    private static PrayerAPI prayer(String name)
    {
        try
        {
            return PrayerAPI.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static Spell spell(String spellbook, String name)
    {
        try
        {
            switch (spellbook)
            {
                case "ANCIENT":
                    return Ancient.valueOf(name);
                case "LUNAR":
                    return Lunar.valueOf(name);
                case "NECROMANCY":
                    return Necromancy.valueOf(name);
                case "STANDARD":
                default:
                    return Standard.valueOf(name);
            }
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static String readable(String constant)
    {
        return constant.toLowerCase().replace('_', ' ');
    }
}
