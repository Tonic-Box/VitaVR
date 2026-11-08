package com.tonic.vitavr.gestures.actions;

import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Condition;
import com.tonic.vitavr.gestures.model.Gesture;
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
import com.tonic.vitavr.gestures.model.steps.SwitchProfileStep;
import com.tonic.vitavr.gestures.model.steps.UnequipStep;
import com.tonic.vitavr.gestures.model.steps.UseOnStep;
import com.tonic.vitavr.gestures.model.steps.WaitStep;
import com.tonic.vitavr.gestures.model.steps.WearSetStep;
import com.tonic.vitavr.gestures.vr.GestureActions;
import com.tonic.vitavr.gestures.vr.GestureOutcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Runs bindings on the client thread: the steps up to the first wait at once, and the rest as the game ticks; a gesture
 * made again starts its binding over, and one made too soon after the last is left alone. Within a tick it remembers
 * what it already switched, since the game only tells of a change on the next tick, so a prayer is never toggled twice.
 */
public final class GestureRunner implements GestureActions
{
    private final Game game;
    private final LongSupplier millis;
    private final Consumer<String> switchProfile;
    private final Map<String, Plan> waiting = new LinkedHashMap<>();
    private final Map<String, Long> lastRun = new HashMap<>();
    private final Map<String, Boolean> prayersSent = new HashMap<>();
    private Boolean quickSent;
    private Boolean specialSent;
    private Boolean runSent;

    /** A binding under way. */
    private static final class Plan
    {
        final List<AbstractStep> steps;
        final Object pointed;
        int next;
        int ticks;
        int done;
        int skipped;
        String stopped;

        Plan(List<AbstractStep> steps, Object pointed)
        {
            this.steps = steps;
            this.pointed = pointed;
        }
    }

    /**
     * @param game          The game.
     * @param millis        The time in milliseconds, for cooldowns.
     * @param switchProfile Makes a named profile the one in use.
     */
    public GestureRunner(Game game, LongSupplier millis, Consumer<String> switchProfile)
    {
        this.game = game;
        this.millis = millis;
        this.switchProfile = switchProfile;
    }

    @Override
    public GestureOutcome run(Gesture gesture, Binding binding)
    {
        if (binding == null || binding.getSteps().isEmpty())
        {
            return new GestureOutcome(false, gesture.getName() + ": not bound");
        }
        long now = millis.getAsLong();
        Long last = lastRun.get(gesture.getId());
        if (last != null && now - last < binding.getCooldownMs())
        {
            return new GestureOutcome(false, gesture.getName() + ": too soon");
        }
        String unmet = unmet(binding.getConditions());
        if (unmet != null)
        {
            return new GestureOutcome(false, unmet);
        }
        lastRun.put(gesture.getId(), now);
        Plan plan = new Plan(binding.getSteps(), game.pointed());
        waiting.remove(gesture.getId());
        advance(plan);
        if (plan.next < plan.steps.size() && plan.stopped == null)
        {
            waiting.put(gesture.getId(), plan);
        }
        if (plan.stopped != null && plan.done == 0)
        {
            return new GestureOutcome(false, plan.stopped);
        }
        if (plan.done == 0 && plan.skipped > 0)
        {
            return new GestureOutcome(false, gesture.getName() + ": nothing could run");
        }
        String text = binding.describe();
        if (plan.skipped > 0)
        {
            text += " (" + plan.skipped + " skipped)";
        }
        if (plan.stopped != null)
        {
            text += ", then stopped: " + plan.stopped;
        }
        return new GestureOutcome(true, text);
    }

    /**
     * Moves the bindings under way on by a game tick, and forgets what was switched in the last one.
     */
    public void tick()
    {
        prayersSent.clear();
        quickSent = null;
        specialSent = null;
        runSent = null;
        for (Iterator<Plan> plans = waiting.values().iterator(); plans.hasNext(); )
        {
            Plan plan = plans.next();
            if (--plan.ticks > 0)
            {
                continue;
            }
            advance(plan);
            if (plan.next >= plan.steps.size() || plan.stopped != null)
            {
                plans.remove();
            }
        }
    }

    /**
     * @return Whether any binding still has steps to run.
     */
    public boolean busy()
    {
        return !waiting.isEmpty();
    }

    private void advance(Plan plan)
    {
        while (plan.next < plan.steps.size())
        {
            AbstractStep step = plan.steps.get(plan.next++);
            if (step instanceof WaitStep)
            {
                plan.ticks = Math.max(1, ((WaitStep) step).getTicks());
                return;
            }
            String why = unmet(step.getConditions());
            if (why == null)
            {
                why = perform(step, plan.pointed);
            }
            if (why == null)
            {
                plan.done++;
                continue;
            }
            if (step.getIfCannot() == AbstractStep.IfCannot.STOP)
            {
                plan.stopped = why;
                return;
            }
            plan.skipped++;
        }
    }

    /**
     * @return The first condition that does not hold, as words, or null when they all do.
     */
    String unmet(List<Condition> conditions)
    {
        for (Condition condition : conditions)
        {
            if (!holds(condition))
            {
                return "Needs " + condition.describe().toLowerCase();
            }
        }
        return null;
    }

    private boolean holds(Condition condition)
    {
        int value = condition.getValue();
        switch (condition.getKind())
        {
            case HP_AT_MOST:
                return game.hitpoints() <= value;
            case HP_PERCENT_AT_MOST:
                return game.hitpoints() * 100 <= value * Math.max(1, game.maxHitpoints());
            case PRAYER_AT_MOST:
                return game.prayerPoints() <= value;
            case SPEC_AT_LEAST:
                return game.specialEnergy() >= value;
            case RUN_AT_LEAST:
                return game.runEnergy() >= value;
            case WEARING:
                return ItemResolver.pick(condition.getItem(), game.worn()) != null;
            case NOT_WEARING:
                return ItemResolver.pick(condition.getItem(), game.worn()) == null;
            case CARRYING:
                return ItemResolver.pick(condition.getItem(), game.inventory()) != null;
            case NOT_CARRYING:
                return ItemResolver.pick(condition.getItem(), game.inventory()) == null;
            default:
                return true;
        }
    }

    private String perform(AbstractStep step, Object pointed)
    {
        if (step instanceof ItemOpStep)
        {
            return itemOp((ItemOpStep) step);
        }
        if (step instanceof ItemSubOpStep)
        {
            ItemSubOpStep sub = (ItemSubOpStep) step;
            ItemView item = carriedOrWorn(sub.getItem());
            return item == null ? "No " + sub.getItem().describe() : game.subOp(item, sub.getMenu(), sub.getAction());
        }
        if (step instanceof WearSetStep)
        {
            return wear((WearSetStep) step);
        }
        if (step instanceof UnequipStep)
        {
            return game.unequip(((UnequipStep) step).getSlot());
        }
        if (step instanceof PrayerStep)
        {
            return prayers((PrayerStep) step);
        }
        if (step instanceof QuickPrayersStep)
        {
            boolean on = quickSent != null ? quickSent : game.quickPrayersOn();
            if (((QuickPrayersStep) step).getMode().after(on) == on)
            {
                return null;
            }
            quickSent = !on;
            return game.toggleQuickPrayers();
        }
        if (step instanceof SpecialStep)
        {
            boolean on = specialSent != null ? specialSent : game.specialOn();
            if (((SpecialStep) step).getMode().after(on) == on)
            {
                return null;
            }
            specialSent = !on;
            return game.toggleSpecial();
        }
        if (step instanceof RunStep)
        {
            boolean on = runSent != null ? runSent : game.runOn();
            if (((RunStep) step).getMode().after(on) == on)
            {
                return null;
            }
            runSent = !on;
            return game.toggleRun();
        }
        if (step instanceof OpenTabStep)
        {
            return game.openTab(((OpenTabStep) step).getTab());
        }
        if (step instanceof SwitchProfileStep)
        {
            switchProfile.accept(((SwitchProfileStep) step).getProfile());
            return null;
        }
        if (step instanceof SpellStep)
        {
            return spell((SpellStep) step, pointed);
        }
        if (step instanceof UseOnStep)
        {
            return useOn((UseOnStep) step, pointed);
        }
        return "Unknown step";
    }

    private String itemOp(ItemOpStep step)
    {
        ItemView item = carriedOrWorn(step.getItem());
        if (item == null)
        {
            return "No " + step.getItem().describe();
        }
        String op = step.getOp();
        if (ItemOpStep.CONSUME.equals(op))
        {
            op = item.has("Eat") ? "Eat" : item.has("Drink") ? "Drink" : null;
            if (op == null)
            {
                return item.getName() + " is not eaten or drunk";
            }
        }
        if (!item.isWorn() && !item.has(op))
        {
            return item.getName() + " has no " + op;
        }
        return game.itemOp(item, op);
    }

    private ItemView carriedOrWorn(ItemRef ref)
    {
        ItemView carried = ItemResolver.pick(ref, game.inventory());
        return carried != null ? carried : ItemResolver.pick(ref, game.worn());
    }

    private String wear(WearSetStep step)
    {
        List<ItemView> worn = game.worn();
        List<ItemView> carried = new ArrayList<>(game.inventory());
        int put = 0;
        int already = 0;
        String problem = null;
        for (ItemRef piece : step.getItems())
        {
            if (ItemResolver.pick(piece, worn) != null)
            {
                already++;
                continue;
            }
            ItemView item = ItemResolver.pick(piece, carried);
            if (item == null)
            {
                continue;
            }
            carried.remove(item);
            String why = game.equip(item);
            if (why == null)
            {
                put++;
            }
            else
            {
                problem = why;
            }
        }
        if (put == 0 && already == 0)
        {
            return problem != null ? problem : "Nothing of the set to wear";
        }
        return null;
    }

    private String prayers(PrayerStep step)
    {
        List<String> named = step.getPrayers();
        if (step.getMode() == PrayerStep.Mode.ONLY)
        {
            for (String on : game.prayersOn())
            {
                if (!named.contains(on) && prayerOn(on))
                {
                    togglePrayer(on);
                }
            }
        }
        String problem = null;
        for (String prayer : named)
        {
            boolean on = prayerOn(prayer);
            boolean wanted;
            switch (step.getMode())
            {
                case OFF:
                    wanted = false;
                    break;
                case TOGGLE:
                    wanted = !on;
                    break;
                case ON:
                case ONLY:
                default:
                    wanted = true;
                    break;
            }
            if (wanted != on)
            {
                String why = togglePrayer(prayer);
                problem = problem == null ? why : problem;
            }
        }
        return problem;
    }

    private boolean prayerOn(String prayer)
    {
        Boolean sent = prayersSent.get(prayer);
        return sent != null ? sent : game.prayerOn(prayer);
    }

    private String togglePrayer(String prayer)
    {
        boolean now = prayerOn(prayer);
        String why = game.togglePrayer(prayer);
        if (why == null)
        {
            prayersSent.put(prayer, !now);
        }
        return why;
    }

    private String spell(SpellStep step, Object pointed)
    {
        Object target;
        switch (step.getTarget())
        {
            case POINTED:
                if (pointed == null)
                {
                    return "Nothing pointed at to cast on";
                }
                target = pointed;
                break;
            case ITEM:
                target = ItemResolver.pick(step.getItem(), game.inventory());
                if (target == null)
                {
                    return "No " + step.getItem().describe();
                }
                break;
            case NONE:
            default:
                target = null;
                break;
        }
        return game.cast(step.getSpellbook(), step.getSpell(), target);
    }

    private String useOn(UseOnStep step, Object pointed)
    {
        ItemView item = ItemResolver.pick(step.getItem(), game.inventory());
        if (item == null)
        {
            return "No " + step.getItem().describe();
        }
        Object target = step.isOnPointed() ? pointed : ItemResolver.pick(step.getOnItem(), game.inventory());
        if (target == null)
        {
            return step.isOnPointed() ? "Nothing pointed at" : "No " + step.getOnItem().describe();
        }
        return game.useOn(item, target);
    }
}
