package com.tonic.vitavr.interfaces.panels;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.ArmInput;
import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Profile;
import com.tonic.vitavr.gestures.store.Gestures;
import com.tonic.vitavr.gestures.vr.GestureRecorder;
import com.tonic.vitavr.gestures.vr.GestureSession;
import net.runelite.client.config.ConfigManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The settings panel's Gestures tab: the profile in use, the arm inputs, the gestures with their bindings, and
 * recording new gestures a take at a time; what each gesture does is set in the desktop editor.
 */
final class GesturesPage
{
    private static final int PER_PAGE = 5;
    private static final int ROWS = 11;
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font SMALL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Color PROBLEM = new Color(235, 120, 100);
    private static final Color GOOD = new Color(140, 210, 130);

    /** A pressable box on the page. */
    private static final class Button
    {
        final Rectangle bounds;
        final String label;
        final Runnable press;
        final boolean on;

        Button(Rectangle bounds, String label, Runnable press, boolean on)
        {
            this.bounds = bounds;
            this.label = label;
            this.press = press;
            this.on = on;
        }
    }

    private final VitaVRConfig config;
    private final ConfigManager configManager;
    private final int left;
    private final int top;
    private final int width;
    private final int row;
    private GestureSession session;
    private Runnable openKeyboard = () -> { };
    private Runnable closeKeyboard = () -> { };
    private final List<Button> buttons = new ArrayList<>();
    private Object laidOut;
    private int page;
    private String deleting;

    GesturesPage(VitaVRConfig config, ConfigManager configManager, int left, int top, int width, int row)
    {
        this.config = config;
        this.configManager = configManager;
        this.left = left;
        this.top = top;
        this.width = width;
        this.row = row;
    }

    void session(GestureSession gestures, Runnable openKeys, Runnable closeKeys)
    {
        session = gestures;
        openKeyboard = openKeys;
        closeKeyboard = closeKeys;
        laidOut = null;
    }

    /**
     * @return A value that changes whenever what the page shows does.
     */
    Object state()
    {
        if (session == null)
        {
            return null;
        }
        GestureRecorder recorder = session.getRecorder();
        return java.util.Arrays.asList(session.store().version(), recorder.getStage(), recorder.taken(), recorder.getMessage(), recorder.isEither(),
                recorder.isArmedFlick(), session.renamingId(), session.getRenaming().toString(), session.isTesting(), page, deleting,
                config.gestureArmLeft(), config.gestureArmRight(), session.store().getProblem());
    }

    int targets()
    {
        layout();
        return buttons.size();
    }

    Rectangle bounds(int target)
    {
        layout();
        return target < buttons.size() ? buttons.get(target).bounds : null;
    }

    void pressed(int target)
    {
        layout();
        if (target < buttons.size())
        {
            buttons.get(target).press.run();
            laidOut = null;
        }
    }

    private void layout()
    {
        Object now = state();
        if (Objects.equals(now, laidOut) && laidOut != null)
        {
            return;
        }
        laidOut = now;
        buttons.clear();
        if (session == null)
        {
            return;
        }
        if (session.getRecorder().getStage() != GestureRecorder.Stage.IDLE)
        {
            recordingButtons();
        }
        else
        {
            listButtons();
        }
    }

    private Rectangle box(int at, int fromRight, int boxWidth)
    {
        int arrow = 30;
        return new Rectangle(left + width - fromRight - boxWidth, top + at * row + (row - arrow) / 2, boxWidth, arrow);
    }

    private void stepper(int at, Runnable down, Runnable up)
    {
        buttons.add(new Button(box(at, 30 + 150, 30), "<", down, false));
        buttons.add(new Button(box(at, 0, 30), ">", up, false));
    }

    private void listButtons()
    {
        Gestures gestures = session.store().snapshot();
        stepper(0, () -> switchProfile(gestures, -1), () -> switchProfile(gestures, 1));
        stepper(1, () -> stepArm(VitaVRConfig.ARM_LEFT, config.gestureArmLeft(), -1), () -> stepArm(VitaVRConfig.ARM_LEFT, config.gestureArmLeft(), 1));
        stepper(2, () -> stepArm(VitaVRConfig.ARM_RIGHT, config.gestureArmRight(), -1), () -> stepArm(VitaVRConfig.ARM_RIGHT, config.gestureArmRight(), 1));
        buttons.add(new Button(new Rectangle(left, top + 3 * row + 2, 150, row - 4), session.isTesting() ? "Testing: On" : "Testing: Off",
                () -> session.setTesting(!session.isTesting()), session.isTesting()));
        int pages = Math.max(1, (gestures.getLibrary().size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        if (pages > 1)
        {
            stepper(3, () -> page = Math.floorMod(page - 1, pages), () -> page = Math.floorMod(page + 1, pages));
        }
        List<Gesture> library = gestures.getLibrary();
        for (int i = 0; i < PER_PAGE; i++)
        {
            int index = page * PER_PAGE + i;
            if (index >= library.size())
            {
                break;
            }
            Gesture gesture = library.get(index);
            int at = 4 + i;
            String id = gesture.getId();
            buttons.add(new Button(box(at, 0, 52), deleting != null && deleting.equals(id) ? "Sure?" : "Delete", () -> delete(id), false));
            buttons.add(new Button(box(at, 56, 52), "Name", () -> rename(id), Objects.equals(session.renamingId(), id)));
            buttons.add(new Button(box(at, 112, 52), "Redo", () -> session.getRecorder().start(gesture.getKind(), gesture), false));
            buttons.add(new Button(box(at, 168, 44), gesture.isEnabled() ? "On" : "Off", () -> toggle(id), gesture.isEnabled()));
        }
        int newWidth = (width - 3 * 8) / 4;
        GestureKind[] kinds = GestureKind.values();
        for (int i = 0; i < kinds.length; i++)
        {
            GestureKind kind = kinds[i];
            buttons.add(new Button(new Rectangle(left + i * (newWidth + 8), top + 10 * row, newWidth, row - 4), "New " + kind.toString().toLowerCase(),
                    () -> session.getRecorder().start(kind, null), false));
        }
    }

    private void recordingButtons()
    {
        GestureRecorder recorder = session.getRecorder();
        int third = (width - 2 * 8) / 3;
        if (recorder.getStage() == GestureRecorder.Stage.CHOOSE_HAND)
        {
            buttons.add(new Button(new Rectangle(left, top + 3 * row, third, row), "Left hand", () -> recorder.chooseHand(0), false));
            buttons.add(new Button(new Rectangle(left + third + 8, top + 3 * row, third, row), "Right hand", () -> recorder.chooseHand(1), false));
        }
        else if (recorder.getKind() != GestureKind.TWO_HAND)
        {
            buttons.add(new Button(new Rectangle(left, top + 5 * row, third, row - 4), recorder.isEither() ? "Either hand: On" : "Either hand: Off",
                    () -> recorder.either(!recorder.isEither()), recorder.isEither()));
            if (recorder.getKind() == GestureKind.FLICK)
            {
                buttons.add(new Button(new Rectangle(left + third + 8, top + 5 * row, third, row - 4), recorder.isArmedFlick() ? "Needs arming: On" : "Needs arming: Off",
                        () -> recorder.armedFlick(!recorder.isArmedFlick()), recorder.isArmedFlick()));
            }
        }
        int y = top + 10 * row;
        buttons.add(new Button(new Rectangle(left, y, third, row - 4), "Redo take", recorder::redoLast, false));
        buttons.add(new Button(new Rectangle(left + third + 8, y, third, row - 4), "Cancel", recorder::cancel, false));
        if (recorder.getStage() == GestureRecorder.Stage.DONE)
        {
            buttons.add(new Button(new Rectangle(left + 2 * (third + 8), y, third, row - 4), "Save", session::saveRecording, true));
        }
    }

    private void switchProfile(Gestures gestures, int direction)
    {
        List<Profile> profiles = gestures.getProfiles();
        if (profiles.size() < 2)
        {
            return;
        }
        int at = profiles.indexOf(gestures.activeProfile());
        String next = profiles.get(Math.floorMod(at + direction, profiles.size())).getName();
        session.store().update(changing -> changing.setActive(next));
    }

    private void stepArm(String key, ArmInput chosen, int direction)
    {
        ArmInput[] all = ArmInput.values();
        configManager.setConfiguration(VitaVRConfig.GROUP, key, all[Math.floorMod(chosen.ordinal() + direction, all.length)]);
    }

    private void toggle(String id)
    {
        session.store().update(gestures ->
        {
            Gesture gesture = gestures.gesture(id);
            if (gesture != null)
            {
                gesture.setEnabled(!gesture.isEnabled());
            }
        });
    }

    private void delete(String id)
    {
        if (!id.equals(deleting))
        {
            deleting = id;
            return;
        }
        deleting = null;
        session.store().update(gestures -> gestures.getLibrary().removeIf(gesture -> gesture.getId().equals(id)));
    }

    private void rename(String id)
    {
        if (id.equals(session.renamingId()))
        {
            session.finishRename();
            return;
        }
        session.rename(id, () ->
        {
            laidOut = null;
            if (session.renamingId() == null)
            {
                closeKeyboard.run();
            }
        });
        openKeyboard.run();
    }

    void paint(Graphics2D g, int hovered, Color text, Color note, Color button, Color lit, Color chosen, Font font)
    {
        layout();
        if (session == null)
        {
            g.setFont(font);
            g.setColor(note);
            g.drawString("Gestures start with the headset", left, top + row / 2);
            return;
        }
        if (session.getRecorder().getStage() != GestureRecorder.Stage.IDLE)
        {
            paintRecording(g, text, note, font);
        }
        else
        {
            paintList(g, text, note, font);
        }
        for (int i = 0; i < buttons.size(); i++)
        {
            Button each = buttons.get(i);
            g.setColor(each.on ? chosen : i == hovered ? lit : button);
            g.fillRoundRect(each.bounds.x, each.bounds.y, each.bounds.width, each.bounds.height, 8, 8);
            g.setColor(text);
            g.setFont(each.bounds.width < 60 ? SMALL_FONT : font);
            centred(g, each.label, each.bounds);
        }
    }

    private void paintList(Graphics2D g, Color text, Color note, Font font)
    {
        Gestures gestures = session.store().snapshot();
        g.setFont(font);
        g.setColor(text);
        label(g, 0, "Profile", font);
        value(g, 0, gestures.activeProfile() == null ? "" : gestures.activeProfile().getName());
        label(g, 1, "Left hand arms with", font);
        value(g, 1, config.gestureArmLeft().toString());
        label(g, 2, "Right hand arms with", font);
        value(g, 2, config.gestureArmRight().toString());
        List<Gesture> library = gestures.getLibrary();
        if (library.size() > PER_PAGE)
        {
            value(g, 3, "Page " + (page + 1));
        }
        if (session.renamingId() != null)
        {
            g.setFont(font);
            g.setColor(GOOD);
            g.drawString("Name: " + session.getRenaming() + "_", left + 160, middle(3) + 6);
        }
        String problem = session.store().getProblem();
        if (library.isEmpty())
        {
            g.setFont(font);
            g.setColor(note);
            g.drawString("No gestures yet: record one below.", left, middle(5) + 6);
            g.drawString("Bind them in the desktop gesture editor.", left, middle(6) + 6);
        }
        for (int i = 0; i < PER_PAGE; i++)
        {
            int index = page * PER_PAGE + i;
            if (index >= library.size())
            {
                break;
            }
            Gesture gesture = library.get(index);
            int middle = middle(4 + i);
            Binding binding = gestures.binding(gesture.getId());
            g.setFont(NAME_FONT);
            g.setColor(gesture.isEnabled() ? text : note);
            g.drawString(clip(g, gesture.getName() + "  (" + gesture.getKind() + ", " + gesture.getHand() + ")", width - 230), left, middle - 2);
            g.setFont(SMALL_FONT);
            g.setColor(note);
            g.drawString(clip(g, binding == null ? "Not bound" : binding.describe(), width - 230), left, middle + 12);
        }
        if (!problem.isEmpty())
        {
            g.setFont(SMALL_FONT);
            g.setColor(PROBLEM);
            g.drawString(clip(g, problem, width), left, middle(9) + 6);
        }
        g.setFont(SMALL_FONT);
        g.setColor(note);
        g.drawString("Record a new gesture:", left, top + 10 * row - 6);
    }

    private void paintRecording(Graphics2D g, Color text, Color note, Font font)
    {
        GestureRecorder recorder = session.getRecorder();
        g.setFont(NAME_FONT);
        g.setColor(text);
        Gesture redoing = recorder.getRedoing();
        g.drawString(redoing == null ? "New " + recorder.getKind().toString().toLowerCase() : "New takes for " + redoing.getName(), left, middle(0) + 6);
        g.setFont(font);
        if (recorder.getStage() == GestureRecorder.Stage.CHOOSE_HAND)
        {
            g.drawString("Which hand makes it?", left, middle(2) + 6);
            return;
        }
        g.drawString(instruction(recorder), left, middle(1) + 6);
        g.setColor(note);
        g.drawString(recorder.getStage() == GestureRecorder.Stage.DONE ? "Done: save it, or redo the last take" : "Take " + (recorder.taken() + 1) + " of " + recorder.wanted(),
                left, middle(2) + 6);
        if (!recorder.getMessage().isEmpty())
        {
            g.setColor(PROBLEM);
            g.drawString(recorder.getMessage(), left, middle(3) + 6);
        }
    }

    private String instruction(GestureRecorder recorder)
    {
        String hand = recorder.getHand() == 0 ? "left" : "right";
        switch (recorder.getKind())
        {
            case SPOT:
                return "Put your " + hand + " hand on the spot and squeeze grip";
            case FLICK:
                return recorder.isArmedFlick() ? "Hold the " + hand + " arm input and snap your wrist" : "Snap your " + hand + " wrist";
            case TWO_HAND:
                return "Hold both arm inputs and move both hands";
            case SHAPE:
            default:
                return "Hold the " + hand + " arm input and draw the shape";
        }
    }

    private int middle(int at)
    {
        return top + at * row + row / 2;
    }

    private void label(Graphics2D g, int at, String name, Font font)
    {
        g.setFont(font);
        g.drawString(name, left, middle(at) + 6);
    }

    private void value(Graphics2D g, int at, String shown)
    {
        Rectangle between = new Rectangle(left + width - 30 - 150, top + at * row, 150, row);
        centred(g, shown, between);
    }

    private static String clip(Graphics2D g, String text, int most)
    {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= most)
        {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && metrics.stringWidth(cut + "…") > most)
        {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static void centred(Graphics2D g, String text, Rectangle bounds)
    {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, bounds.x + (bounds.width - metrics.stringWidth(text)) / 2, bounds.y + bounds.height / 2 + metrics.getAscent() / 2 - 2);
    }
}
