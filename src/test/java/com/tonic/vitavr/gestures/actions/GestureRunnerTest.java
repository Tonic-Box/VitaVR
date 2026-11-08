package com.tonic.vitavr.gestures.actions;

import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Condition;
import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.ItemRef;
import com.tonic.vitavr.gestures.model.steps.AbstractStep;
import com.tonic.vitavr.gestures.model.steps.ItemOpStep;
import com.tonic.vitavr.gestures.model.steps.PrayerStep;
import com.tonic.vitavr.gestures.model.steps.SpecialStep;
import com.tonic.vitavr.gestures.model.steps.SpellStep;
import com.tonic.vitavr.gestures.model.steps.Switch;
import com.tonic.vitavr.gestures.model.steps.WaitStep;
import com.tonic.vitavr.gestures.model.steps.WearSetStep;
import com.tonic.vitavr.gestures.vr.GestureOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestureRunnerTest
{
    private final FakeGame game = new FakeGame();
    private long millis = 10_000;
    private final List<String> profiles = new ArrayList<>();
    private final GestureRunner runner = new GestureRunner(game, () -> millis, profiles::add);
    private final Gesture gesture = new Gesture();

    @Test
    void stepsBeforeAWaitRunAtOnceAndTheRestOnLaterTicks()
    {
        Binding binding = binding(eat(ItemRef.group("Eat")), wait(2), special(Switch.ON));
        game.inventory.add(new ItemView(385, "Shark", 3, List.of("Eat"), false));

        GestureOutcome outcome = runner.run(gesture, binding);

        assertTrue(outcome.isDone());
        assertEquals(List.of("Eat Shark@3"), game.done);
        runner.tick();
        assertEquals(1, game.done.size());
        runner.tick();
        assertEquals(List.of("Eat Shark@3", "special"), game.done);
        assertFalse(runner.busy());
    }

    @Test
    void aStepThatCannotRunIsSkippedByDefault()
    {
        Binding binding = binding(eat(ItemRef.group("Eat")), special(Switch.ON));

        GestureOutcome outcome = runner.run(gesture, binding);

        assertTrue(outcome.isDone());
        assertTrue(outcome.getText().contains("1 skipped"), outcome.getText());
        assertEquals(List.of("special"), game.done);
    }

    @Test
    void aStepSetToStopStopsTheRest()
    {
        AbstractStep eat = eat(ItemRef.group("Eat"));
        eat.setIfCannot(AbstractStep.IfCannot.STOP);
        Binding binding = binding(eat, special(Switch.ON));

        GestureOutcome outcome = runner.run(gesture, binding);

        assertFalse(outcome.isDone());
        assertTrue(game.done.isEmpty());
    }

    @Test
    void madeAgainTooSoonDoesNothing()
    {
        Binding binding = binding(special(Switch.TOGGLE));
        runner.run(gesture, binding);
        runner.tick();
        millis += 100;

        GestureOutcome again = runner.run(gesture, binding);

        assertFalse(again.isDone());
        assertEquals(1, game.done.size());
    }

    @Test
    void madeAgainStartsTheBindingOver()
    {
        Binding binding = binding(special(Switch.TOGGLE), wait(3), eat(ItemRef.group("Eat")));
        game.inventory.add(new ItemView(385, "Shark", 0, List.of("Eat"), false));
        runner.run(gesture, binding);
        runner.tick();
        millis += 2000;

        runner.run(gesture, binding);
        runner.tick();
        runner.tick();

        assertEquals(List.of("special", "special"), game.done);
        runner.tick();
        assertEquals(List.of("special", "special", "Eat Shark@0"), game.done);
    }

    @Test
    void anUnmetConditionKeepsTheBindingFromRunning()
    {
        Binding binding = binding(special(Switch.ON));
        Condition low = new Condition();
        low.setKind(Condition.Kind.HP_PERCENT_AT_MOST);
        low.setValue(50);
        binding.getConditions().add(low);
        game.hitpoints = 80;

        GestureOutcome outcome = runner.run(gesture, binding);

        assertFalse(outcome.isDone());
        assertTrue(game.done.isEmpty());
    }

    @Test
    void onlyTheseTurnsTheOthersOffAndNeverTogglesOneTwiceInATick()
    {
        game.prayers.add("PROTECT_FROM_MELEE");
        game.prayers.add("PIETY");
        PrayerStep magic = new PrayerStep();
        magic.setMode(PrayerStep.Mode.ONLY);
        magic.getPrayers().add("PROTECT_FROM_MAGIC");
        magic.getPrayers().add("PIETY");
        PrayerStep magicAgain = new PrayerStep();
        magicAgain.getPrayers().add("PROTECT_FROM_MAGIC");

        runner.run(gesture, binding(magic, magicAgain));

        assertEquals(List.of("pray PROTECT_FROM_MELEE", "pray PROTECT_FROM_MAGIC"), game.done);
    }

    @Test
    void aSetWearsWhatIsCarriedAndLeavesWhatIsWorn()
    {
        game.inventory.add(new ItemView(4151, "Abyssal whip", 0, List.of("Wield"), false));
        game.inventory.add(new ItemView(8850, "Rune defender", 1, List.of("Wield"), false));
        game.worn.add(new ItemView(10828, "Helm of neitiznot", 0, List.of(), true));
        WearSetStep set = new WearSetStep();
        set.getItems().add(ItemRef.exact("Abyssal whip", 4151));
        set.getItems().add(ItemRef.exact("Helm of neitiznot", 10828));
        set.getItems().add(ItemRef.exact("Dragon defender", 12954));

        GestureOutcome outcome = runner.run(gesture, binding(set));

        assertTrue(outcome.isDone());
        assertEquals(List.of("equip Abyssal whip"), game.done);
    }

    @Test
    void aSpellOnThePointedTargetUsesWhatWasPointedAtWhenMade()
    {
        game.pointed = "Goblin";
        SpellStep barrage = new SpellStep();
        barrage.setSpellbook("ANCIENT");
        barrage.setSpell("ICE_BARRAGE");
        barrage.setTarget(SpellStep.Target.POINTED);
        Binding binding = binding(wait(1), barrage);

        runner.run(gesture, binding);
        game.pointed = "Man";
        runner.tick();

        assertEquals(List.of("cast ANCIENT ICE_BARRAGE on Goblin"), game.done);
    }

    @Test
    void consumeEatsOrElseDrinks()
    {
        game.inventory.add(new ItemView(3024, "Super restore(4)", 0, List.of("Drink"), false));
        ItemOpStep consume = eat(ItemRef.named(ItemRef.Match.VARIANTS, "Super restore"));

        runner.run(gesture, binding(consume));

        assertEquals(List.of("Drink Super restore(4)@0"), game.done);
    }

    private static Binding binding(AbstractStep... steps)
    {
        Binding binding = new Binding();
        binding.getSteps().addAll(List.of(steps));
        return binding;
    }

    private static ItemOpStep eat(ItemRef ref)
    {
        ItemOpStep step = new ItemOpStep();
        step.setItem(ref);
        return step;
    }

    private static WaitStep wait(int ticks)
    {
        WaitStep step = new WaitStep();
        step.setTicks(ticks);
        return step;
    }

    private static SpecialStep special(Switch mode)
    {
        SpecialStep step = new SpecialStep();
        step.setMode(mode);
        return step;
    }

    private static final class FakeGame implements Game
    {
        final List<ItemView> inventory = new ArrayList<>();
        final List<ItemView> worn = new ArrayList<>();
        final List<String> prayers = new ArrayList<>();
        final List<String> done = new ArrayList<>();
        int hitpoints = 99;
        Object pointed;

        @Override
        public List<ItemView> inventory()
        {
            return inventory;
        }

        @Override
        public List<ItemView> worn()
        {
            return worn;
        }

        @Override
        public int hitpoints()
        {
            return hitpoints;
        }

        @Override
        public int maxHitpoints()
        {
            return 99;
        }

        @Override
        public int prayerPoints()
        {
            return 70;
        }

        @Override
        public int specialEnergy()
        {
            return 100;
        }

        @Override
        public boolean specialOn()
        {
            return false;
        }

        @Override
        public int runEnergy()
        {
            return 100;
        }

        @Override
        public boolean runOn()
        {
            return true;
        }

        @Override
        public boolean prayerOn(String prayer)
        {
            return prayers.contains(prayer);
        }

        @Override
        public List<String> prayersOn()
        {
            return new ArrayList<>(prayers);
        }

        @Override
        public boolean quickPrayersOn()
        {
            return false;
        }

        @Override
        public Object pointed()
        {
            return pointed;
        }

        @Override
        public String itemOp(ItemView item, String op)
        {
            done.add(op + " " + item.getName() + "@" + item.getSlot());
            return null;
        }

        @Override
        public String subOp(ItemView item, String menu, String action)
        {
            done.add(menu + ">" + action + " " + item.getName());
            return null;
        }

        @Override
        public String equip(ItemView item)
        {
            done.add("equip " + item.getName());
            return null;
        }

        @Override
        public String unequip(String slot)
        {
            done.add("unequip " + slot);
            return null;
        }

        @Override
        public String togglePrayer(String prayer)
        {
            done.add("pray " + prayer);
            return null;
        }

        @Override
        public String toggleQuickPrayers()
        {
            done.add("quick");
            return null;
        }

        @Override
        public String toggleSpecial()
        {
            done.add("special");
            return null;
        }

        @Override
        public String toggleRun()
        {
            done.add("run");
            return null;
        }

        @Override
        public String openTab(String tab)
        {
            done.add("tab " + tab);
            return null;
        }

        @Override
        public String cast(String spellbook, String spell, Object target)
        {
            done.add("cast " + spellbook + " " + spell + (target == null ? "" : " on " + target));
            return null;
        }

        @Override
        public String useOn(ItemView item, Object target)
        {
            done.add("use " + item.getName() + " on " + target);
            return null;
        }
    }
}
