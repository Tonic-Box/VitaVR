package com.tonic.vitavr.ui.gestures;

import com.tonic.api.widgets.PrayerAPI;
import com.tonic.data.EquipmentSlot;
import com.tonic.data.Tab;
import com.tonic.data.magic.spellbooks.Ancient;
import com.tonic.data.magic.spellbooks.Lunar;
import com.tonic.data.magic.spellbooks.Necromancy;
import com.tonic.data.magic.spellbooks.Standard;
import com.tonic.vitavr.gestures.actions.ItemView;
import com.tonic.vitavr.gestures.model.ItemRef;
import com.tonic.vitavr.gestures.model.steps.AbstractStep;
import com.tonic.vitavr.gestures.model.steps.ItemOpStep;
import com.tonic.vitavr.gestures.model.steps.ItemSubOpStep;
import com.tonic.vitavr.gestures.model.steps.OpenTabStep;
import com.tonic.vitavr.gestures.model.steps.PrayerStep;
import com.tonic.vitavr.gestures.model.steps.QuickPrayersStep;
import com.tonic.vitavr.gestures.model.steps.RunStep;
import com.tonic.vitavr.gestures.model.steps.SpecialStep;
import com.tonic.vitavr.gestures.model.steps.SpellStep;
import com.tonic.vitavr.gestures.model.steps.Switch;
import com.tonic.vitavr.gestures.model.steps.SwitchProfileStep;
import com.tonic.vitavr.gestures.model.steps.UnequipStep;
import com.tonic.vitavr.gestures.model.steps.UseOnStep;
import com.tonic.vitavr.gestures.model.steps.WaitStep;
import com.tonic.vitavr.gestures.model.steps.WearSetStep;
import com.tonic.vitavr.gestures.store.GestureJson;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The form for each kind of step, editing its own copy of the step and handing back a fresh copy after every change.
 */
final class StepForms
{
    /** The kinds of step offered by the Add menu, by name. */
    static final Map<String, Supplier<AbstractStep>> KINDS = new LinkedHashMap<>();

    static
    {
        KINDS.put("Use an item's op (eat, drink, wield…)", ItemOpStep::new);
        KINDS.put("Use an item's sub-option (teleports)", ItemSubOpStep::new);
        KINDS.put("Wear a set", WearSetStep::new);
        KINDS.put("Take off a slot", UnequipStep::new);
        KINDS.put("Prayers", PrayerStep::new);
        KINDS.put("Quick prayers", QuickPrayersStep::new);
        KINDS.put("Cast a spell", SpellStep::new);
        KINDS.put("Use an item on something", UseOnStep::new);
        KINDS.put("Special attack", SpecialStep::new);
        KINDS.put("Run", RunStep::new);
        KINDS.put("Open a tab", OpenTabStep::new);
        KINDS.put("Switch profile", SwitchProfileStep::new);
        KINDS.put("Wait ticks", WaitStep::new);
    }

    private static final String[] OPS = {ItemOpStep.CONSUME, "Eat", "Drink", "Wield", "Wear", "Rub", "Break", "Read", "Empty", "Check"};

    private StepForms()
    {
    }

    /**
     * @param context  The editor's context.
     * @param original The step to edit; it is not changed.
     * @param profiles The profile names, for switching profile.
     * @param changed  Given a fresh copy of the step after every change.
     * @return The form.
     */
    static JComponent form(EditorContext context, AbstractStep original, List<String> profiles, Consumer<AbstractStep> changed)
    {
        AbstractStep step = GestureJson.copy(original, AbstractStep.class);
        Runnable send = () -> changed.accept(GestureJson.copy(step, AbstractStep.class));
        EditorKit.Form form = new EditorKit.Form();
        if (step instanceof ItemOpStep)
        {
            ItemOpStep op = (ItemOpStep) step;
            form.field("Item", new ItemRefField(context, op.getItem(), item ->
            {
                op.setItem(item);
                send.run();
            }));
            JComboBox<String> ops = EditorKit.combo(OPS, op.getOp(), chosen ->
            {
                op.setOp(chosen);
                send.run();
            });
            ops.setEditable(true);
            form.field("Op", ops);
        }
        else if (step instanceof ItemSubOpStep)
        {
            ItemSubOpStep sub = (ItemSubOpStep) step;
            form.field("Item", new ItemRefField(context, sub.getItem(), item ->
            {
                sub.setItem(item);
                send.run();
            }));
            form.field("Op", EditorKit.field(sub.getMenu(), text ->
            {
                sub.setMenu(text.trim());
                send.run();
            }));
            form.field("Option", EditorKit.field(sub.getAction(), text ->
            {
                sub.setAction(text.trim());
                send.run();
            }));
            form.wide(EditorKit.muted("Such as Rub then Edgeville; worn, the option is used on the worn item"));
        }
        else if (step instanceof WearSetStep)
        {
            wearSet(context, (WearSetStep) step, form, send);
        }
        else if (step instanceof UnequipStep)
        {
            UnequipStep unequip = (UnequipStep) step;
            form.field("Slot", EditorKit.combo(names(EquipmentSlot.values()), unequip.getSlot(), slot ->
            {
                unequip.setSlot(slot);
                send.run();
            }));
        }
        else if (step instanceof PrayerStep)
        {
            prayers(context, (PrayerStep) step, form, send);
        }
        else if (step instanceof QuickPrayersStep)
        {
            QuickPrayersStep quick = (QuickPrayersStep) step;
            form.field("Turn them", EditorKit.combo(Switch.values(), quick.getMode(), mode ->
            {
                quick.setMode(mode);
                send.run();
            }));
        }
        else if (step instanceof SpecialStep)
        {
            SpecialStep special = (SpecialStep) step;
            form.field("Turn it", EditorKit.combo(Switch.values(), special.getMode(), mode ->
            {
                special.setMode(mode);
                send.run();
            }));
        }
        else if (step instanceof RunStep)
        {
            RunStep run = (RunStep) step;
            form.field("Turn it", EditorKit.combo(Switch.values(), run.getMode(), mode ->
            {
                run.setMode(mode);
                send.run();
            }));
        }
        else if (step instanceof SpellStep)
        {
            spell(context, (SpellStep) step, form, send);
        }
        else if (step instanceof UseOnStep)
        {
            UseOnStep use = (UseOnStep) step;
            form.field("Use", new ItemRefField(context, use.getItem(), item ->
            {
                use.setItem(item);
                send.run();
            }));
            ItemRefField onItem = new ItemRefField(context, use.getOnItem(), item ->
            {
                use.setOnItem(item);
                send.run();
            });
            onItem.setVisible(!use.isOnPointed());
            form.field("On", EditorKit.combo(new String[]{"What I point at", "An item"}, use.isOnPointed() ? "What I point at" : "An item", chosen ->
            {
                use.setOnPointed(chosen.startsWith("What"));
                onItem.setVisible(!use.isOnPointed());
                send.run();
            }));
            form.field("Item", onItem);
        }
        else if (step instanceof OpenTabStep)
        {
            OpenTabStep tab = (OpenTabStep) step;
            form.field("Tab", EditorKit.combo(names(Tab.values()), tab.getTab(), chosen ->
            {
                tab.setTab(chosen);
                send.run();
            }));
        }
        else if (step instanceof SwitchProfileStep)
        {
            SwitchProfileStep profile = (SwitchProfileStep) step;
            form.field("Profile", EditorKit.combo(profiles.toArray(new String[0]), profile.getProfile(), chosen ->
            {
                profile.setProfile(chosen);
                send.run();
            }));
        }
        else if (step instanceof WaitStep)
        {
            WaitStep wait = (WaitStep) step;
            form.field("Ticks", EditorKit.spinner(wait.getTicks(), 1, 100, 1, ticks ->
            {
                wait.setTicks(ticks);
                send.run();
            }));
            form.wide(EditorKit.muted("The steps after it run that many game ticks later; without a wait, steps run in the same tick"));
        }
        if (!(step instanceof WaitStep))
        {
            form.field("If it can't run", EditorKit.combo(AbstractStep.IfCannot.values(), step.getIfCannot(), chosen ->
            {
                step.setIfCannot(chosen);
                send.run();
            }));
            form.wide(new ConditionsPanel(context, step.getConditions(), "This step only runs when", conditions ->
            {
                step.setConditions(conditions);
                send.run();
            }));
        }
        return form.end();
    }

    private static void wearSet(EditorContext context, WearSetStep wear, EditorKit.Form form, Runnable send)
    {
        form.field("Set name", EditorKit.field(wear.getName(), text ->
        {
            wear.setName(text.trim());
            send.run();
        }));
        JPanel pieces = new JPanel(new GridLayout(0, 1, 0, 2));
        pieces.setOpaque(false);
        Runnable[] fill = new Runnable[1];
        fill[0] = () ->
        {
            pieces.removeAll();
            for (int i = 0; i < wear.getItems().size(); i++)
            {
                int index = i;
                JPanel row = new JPanel(new java.awt.BorderLayout(6, 0));
                row.setOpaque(false);
                row.add(new ItemRefField(context, wear.getItems().get(i), item ->
                {
                    wear.getItems().set(index, item);
                    send.run();
                }), java.awt.BorderLayout.CENTER);
                row.add(EditorKit.button("Remove", () ->
                {
                    wear.getItems().remove(index);
                    fill[0].run();
                    send.run();
                }), java.awt.BorderLayout.EAST);
                pieces.add(row);
            }
            if (wear.getItems().isEmpty())
            {
                pieces.add(EditorKit.muted("No pieces yet"));
            }
            pieces.revalidate();
            pieces.repaint();
        };
        fill[0].run();
        form.field("Pieces", pieces);
        form.wide(EditorKit.row(EditorKit.button("Add a piece…", () ->
        {
            ItemRef picked = ItemPicker.choose(SwingUtilities.getWindowAncestor(pieces), context, null);
            if (picked != null)
            {
                wear.getItems().add(picked);
                fill[0].run();
                send.run();
            }
        }), EditorKit.button("Use what I'm wearing now", () -> context.read(game -> game.worn(), worn ->
        {
            wear.getItems().clear();
            for (ItemView item : worn)
            {
                wear.getItems().add(ItemRef.exact(item.getName(), item.getId()));
            }
            fill[0].run();
            send.run();
        }))));
    }

    private static void prayers(EditorContext context, PrayerStep prayer, EditorKit.Form form, Runnable send)
    {
        form.field("Turn them", EditorKit.combo(PrayerStep.Mode.values(), prayer.getMode(), mode ->
        {
            prayer.setMode(mode);
            send.run();
        }));
        JPanel boxes = new JPanel(new GridLayout(0, 2, 4, 0));
        boxes.setOpaque(false);
        List<JCheckBox> all = new ArrayList<>();
        for (PrayerAPI each : PrayerAPI.values())
        {
            JCheckBox box = EditorKit.check(readable(each.name()), prayer.getPrayers().contains(each.name()), on ->
            {
                prayer.getPrayers().remove(each.name());
                if (on)
                {
                    prayer.getPrayers().add(each.name());
                }
                send.run();
            });
            box.setName(each.name());
            box.setOpaque(false);
            all.add(box);
            boxes.add(box);
        }
        form.field("Prayers", boxes);
        form.wide(EditorKit.row(EditorKit.button("Use the prayers on now", () -> context.read(game -> game.prayersOn(), on ->
        {
            prayer.getPrayers().clear();
            prayer.getPrayers().addAll(on);
            for (JCheckBox box : all)
            {
                box.setSelected(on.contains(box.getName()));
            }
            send.run();
        }))));
    }

    private static void spell(EditorContext context, SpellStep spell, EditorKit.Form form, Runnable send)
    {
        JComboBox<String> spells = new JComboBox<>(spellsOf(spell.getSpellbook()));
        spells.setSelectedItem(spell.getSpell());
        if (spell.getSpell().isEmpty() && spells.getItemCount() > 0)
        {
            spell.setSpell((String) spells.getItemAt(0));
        }
        spells.addActionListener(e ->
        {
            Object chosen = spells.getSelectedItem();
            if (chosen != null && !chosen.equals(spell.getSpell()))
            {
                spell.setSpell((String) chosen);
                send.run();
            }
        });
        form.field("Spellbook", EditorKit.combo(new String[]{"STANDARD", "ANCIENT", "LUNAR", "NECROMANCY"}, spell.getSpellbook(), book ->
        {
            if (book.equals(spell.getSpellbook()))
            {
                return;
            }
            spell.setSpellbook(book);
            spells.removeAllItems();
            for (String name : spellsOf(book))
            {
                spells.addItem(name);
            }
            spell.setSpell(spells.getItemCount() > 0 ? (String) spells.getItemAt(0) : "");
            send.run();
        }));
        form.field("Spell", spells);
        ItemRefField item = new ItemRefField(context, spell.getItem(), chosen ->
        {
            spell.setItem(chosen);
            send.run();
        });
        item.setVisible(spell.getTarget() == SpellStep.Target.ITEM);
        form.field("Cast on", EditorKit.combo(SpellStep.Target.values(), spell.getTarget(), target ->
        {
            spell.setTarget(target);
            item.setVisible(target == SpellStep.Target.ITEM);
            send.run();
        }));
        form.field("Item", item);
        form.wide(EditorKit.muted("What I point at is the NPC or player under the laser when the gesture is made"));
    }

    private static String[] spellsOf(String book)
    {
        switch (book)
        {
            case "ANCIENT":
                return names(Ancient.values());
            case "LUNAR":
                return names(Lunar.values());
            case "NECROMANCY":
                return names(Necromancy.values());
            case "STANDARD":
            default:
                return names(Standard.values());
        }
    }

    private static String[] names(Enum<?>[] values)
    {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }

    static String readable(String constant)
    {
        String lower = constant.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
