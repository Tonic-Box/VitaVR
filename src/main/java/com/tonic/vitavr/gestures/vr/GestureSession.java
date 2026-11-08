package com.tonic.vitavr.gestures.vr;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.ArmInput;
import com.tonic.vitavr.gestures.BodyFrame;
import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Sample;
import com.tonic.vitavr.gestures.model.Take;
import com.tonic.vitavr.gestures.recognize.GestureRecognizer;
import com.tonic.vitavr.gestures.store.GestureStore;
import com.tonic.vitavr.gestures.store.Gestures;
import com.tonic.vitavr.headset.input.Controllers;
import com.tonic.vitavr.interfaces.panels.CanvasKeys;
import com.tonic.vitavr.space.Pose;
import lombok.Getter;
import lombok.Setter;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestures in a VR session, on the client thread: keeps the body frame, reads the hands into it, records while the
 * Gestures tab records, and otherwise recognises gestures and runs their bindings, showing what came of each by the
 * hand with a buzz.
 */
public final class GestureSession
{
    private static final int HANDS = 2;
    private static final long FIRED_NANOS = 40_000_000L;
    private static final float FIRED_AMPLITUDE = 0.7f;
    private static final long FAILED_NANOS = 25_000_000L;
    private static final float FAILED_AMPLITUDE = 0.5f;
    private static final double FAILED_GAP_SECONDS = 0.12;
    private static final double TOAST_SECONDS = 1.6;
    private static final double TOAST_ABOVE_HAND = 0.08;
    private static final double DEFAULT_HEAD_HEIGHT = 1.65;

    private final VitaVRConfig config;
    private final GestureStore store;
    private final CanvasKeys keys;
    @Getter
    private final GestureRecorder recorder = new GestureRecorder();
    private final GestureRecognizer recognizer = new GestureRecognizer();
    @Getter
    private final BodyFrame body;
    private final GestureRecognizer.HandState[] hands = {new GestureRecognizer.HandState(), new GestureRecognizer.HandState()};
    private final double[] scratch = new double[3];
    private final double[] handRoom = new double[3];
    @Setter
    private GestureActions actions;
    @Getter
    @Setter
    private boolean testing;
    private double now;
    private double headY;
    private double floorY = Double.NaN;

    private String toastText = "";
    private boolean toastDone;
    private double toastUntil;
    private final double[] toastAt = new double[3];
    private int toastVersion;
    private int secondBuzzHand = -1;
    private double secondBuzzAt;

    private String renamingId;
    @Getter
    private final StringBuilder renaming = new StringBuilder();
    private Runnable renameEnded = () -> { };

    /**
     * @param config The settings, read for the arm inputs and the body frame's ease.
     * @param store  The gestures.
     * @param keys   The keys, taken while a gesture is renamed.
     */
    public GestureSession(VitaVRConfig config, GestureStore store, CanvasKeys keys)
    {
        this.config = config;
        this.store = store;
        this.keys = keys;
        this.body = new BodyFrame(config.gestureBodyEaseMs() / 1000.0);
    }

    /**
     * @return The gestures.
     */
    public GestureStore store()
    {
        return store;
    }

    /**
     * Reads the hands, then records or recognises for a frame.
     *
     * @param controllers The controllers, read this frame.
     * @param head        The head in the room.
     * @param floor       The floor's height in the room, or NaN.
     * @param seconds     The time since the last frame.
     * @param paused      Whether gestures should not run now, as while a panel or the keyboard is open.
     */
    public void frame(Controllers controllers, Pose head, double floor, double seconds, boolean paused)
    {
        now += Math.max(0, seconds);
        headY = head.y();
        floorY = floor;
        body.ease(config.gestureBodyEaseMs() / 1000.0);
        body.follow(head, seconds);
        for (int hand = 0; hand < HANDS; hand++)
        {
            read(controllers, hand);
        }
        buzzAgain(controllers);
        if (recorder.getStage() != GestureRecorder.Stage.IDLE)
        {
            recognizer.reset();
            int took = recorder.frame(now, hands[0], hands[1]);
            for (int hand = 0; hand < HANDS; hand++)
            {
                if ((took & 1 << hand) != 0)
                {
                    controllers.consumeGrip(hand);
                    controllers.tick(hand);
                }
            }
            return;
        }
        if (paused && !testing)
        {
            recognizer.reset();
            return;
        }
        Gestures gestures = store.snapshot();
        recognizer.library(gestures.getLibrary(), store.version());
        for (GestureRecognizer.Fired fired : recognizer.frame(now, hands[0], hands[1]))
        {
            if (fired.isTookGrip())
            {
                controllers.consumeGrip(fired.getHand());
            }
            fire(controllers, fired, gestures);
        }
    }

    private void read(Controllers controllers, int hand)
    {
        GestureRecognizer.HandState state = hands[hand];
        Pose grip = controllers.grip(hand);
        state.tracked = grip != null;
        state.gripWentDown = controllers.gripWentDown(hand);
        ArmInput arm = hand == Controllers.LEFT ? config.gestureArmLeft() : config.gestureArmRight();
        boolean wasArmed = state.armed;
        state.armed = controllers.armHeld(hand, arm);
        if (state.armed && !wasArmed && arm == ArmInput.GRIP_AND_TRIGGER)
        {
            controllers.consumeTrigger(hand);
            controllers.consumeGrip(hand);
        }
        if (grip == null)
        {
            return;
        }
        body.toBody(grip.x(), grip.y(), grip.z(), state.point);
        double[] spin = controllers.spin(hand);
        body.toBodyDirection(spin[0], spin[1], spin[2], state.spin);
        double half = body.yaw() / 2;
        double sy = -Math.sin(half);
        double cy = Math.cos(half);
        state.orientation[0] = cy * grip.qx() + sy * grip.qz();
        state.orientation[1] = cy * grip.qy() + sy * grip.qw();
        state.orientation[2] = cy * grip.qz() - sy * grip.qx();
        state.orientation[3] = cy * grip.qw() - sy * grip.qy();
    }

    private void fire(Controllers controllers, GestureRecognizer.Fired fired, Gestures gestures)
    {
        Gesture gesture = fired.getGesture();
        Binding binding = gestures.binding(gesture.getId());
        GestureOutcome outcome;
        if (testing || actions == null)
        {
            outcome = new GestureOutcome(true, gesture.getName() + ": " + (binding == null ? "Not bound" : binding.describe()));
        }
        else
        {
            outcome = actions.run(gesture, binding);
        }
        int hand = fired.getHand() < 0 ? Controllers.RIGHT : fired.getHand();
        buzz(controllers, fired.getHand(), outcome.isDone());
        toast(outcome, hand);
    }

    private void buzz(Controllers controllers, int hand, boolean done)
    {
        for (int each = 0; each < HANDS; each++)
        {
            if (hand < 0 || hand == each)
            {
                controllers.pulse(each, done ? FIRED_NANOS : FAILED_NANOS, done ? FIRED_AMPLITUDE : FAILED_AMPLITUDE);
            }
        }
        if (!done)
        {
            secondBuzzHand = hand < 0 ? Controllers.RIGHT : hand;
            secondBuzzAt = now + FAILED_GAP_SECONDS;
        }
    }

    private void buzzAgain(Controllers controllers)
    {
        if (secondBuzzHand >= 0 && now >= secondBuzzAt)
        {
            controllers.pulse(secondBuzzHand, FAILED_NANOS, FAILED_AMPLITUDE);
            secondBuzzHand = -1;
        }
    }

    private void toast(GestureOutcome outcome, int hand)
    {
        toastText = outcome.getText();
        toastDone = outcome.isDone();
        toastUntil = now + TOAST_SECONDS;
        GestureRecognizer.HandState state = hands[hand];
        body.toRoom(state.point[0], state.point[1] + TOAST_ABOVE_HAND, state.point[2], toastAt);
        toastVersion++;
    }

    /**
     * @return The text to show by the hand, or empty when there is none now.
     */
    public String toastText()
    {
        return now < toastUntil ? toastText : "";
    }

    /**
     * @return Whether the shown text tells of a binding that ran, rather than one that could not.
     */
    public boolean toastDone()
    {
        return toastDone;
    }

    /**
     * @return Where the text stands in the room.
     */
    public double[] toastAt()
    {
        return toastAt;
    }

    /**
     * @return A number that changes with every new text.
     */
    public int toastVersion()
    {
        return toastVersion;
    }

    /**
     * Gathers the lines to draw this frame in the room: the drawing under way, and while recording, the takes made.
     *
     * @param out Receives each line as six numbers, from x, y, z then to x, y, z, in the room.
     * @param colours Receives each line's colour, red in the high byte, alpha in the low.
     */
    public void trails(List<double[]> out, List<Integer> colours)
    {
        if (recorder.getStage() != GestureRecorder.Stage.IDLE)
        {
            for (int hand = 0; hand < HANDS; hand++)
            {
                line(recorder.live(hand), out, colours, 0xFFE070FF);
            }
            for (Take take : recorder.takes())
            {
                for (int hand = 0; hand < HANDS; hand++)
                {
                    line(take.hand(hand), out, colours, 0xFFE07060);
                }
            }
            return;
        }
        for (int hand = 0; hand < HANDS; hand++)
        {
            List<double[]> drawing = recognizer.drawing(hand);
            for (int i = 1; i < drawing.size(); i++)
            {
                segment(drawing.get(i - 1), drawing.get(i), out, colours, 0x80E0FFC0);
            }
        }
    }

    private void line(List<Sample> samples, List<double[]> out, List<Integer> colours, int colour)
    {
        for (int i = 1; i < samples.size(); i++)
        {
            Sample a = samples.get(i - 1);
            Sample b = samples.get(i);
            segment(new double[]{a.getX(), a.getY(), a.getZ()}, new double[]{b.getX(), b.getY(), b.getZ()}, out, colours, colour);
        }
    }

    private void segment(double[] from, double[] to, List<double[]> out, List<Integer> colours, int colour)
    {
        double[] line = new double[6];
        body.toRoom(from[0], from[1], from[2], scratch);
        System.arraycopy(scratch, 0, line, 0, 3);
        body.toRoom(to[0], to[1], to[2], scratch);
        System.arraycopy(scratch, 0, line, 3, 3);
        out.add(line);
        colours.add(colour);
    }

    /**
     * @param spotsShown Whether to give the spots at all.
     * @return The enabled spots' centres in the room with their radius, as x, y, z, radius, for marking while the tab is open.
     */
    public List<double[]> spots(boolean spotsShown)
    {
        List<double[]> out = new ArrayList<>();
        if (!spotsShown)
        {
            return out;
        }
        for (Gesture gesture : store.snapshot().getLibrary())
        {
            if (gesture.getKind() != GestureKind.SPOT)
            {
                continue;
            }
            double[] centre = gesture.getCentre();
            body.toRoom(centre[0], centre[1], centre[2], handRoom);
            out.add(new double[]{handRoom[0], handRoom[1], handRoom[2], gesture.getRadius(), gesture.isEnabled() ? 1 : 0});
            if (gesture.getHand() == com.tonic.vitavr.gestures.model.GestureHand.EITHER)
            {
                body.toRoom(-centre[0], centre[1], centre[2], handRoom);
                out.add(new double[]{handRoom[0], handRoom[1], handRoom[2], gesture.getRadius(), gesture.isEnabled() ? 1 : 0});
            }
        }
        return out;
    }

    /**
     * Saves the gesture the recorder made, naming a new one after its kind.
     */
    public void saveRecording()
    {
        Gesture made = recorder.result(nextName(recorder.getKind()), headHeight());
        if (made == null)
        {
            return;
        }
        store.update(gestures ->
        {
            List<Gesture> library = gestures.getLibrary();
            for (int i = 0; i < library.size(); i++)
            {
                if (library.get(i).getId().equals(made.getId()))
                {
                    library.set(i, made);
                    return;
                }
            }
            library.add(made);
        });
        recorder.cancel();
    }

    private String nextName(GestureKind kind)
    {
        int highest = 0;
        String prefix = kind + " ";
        for (Gesture gesture : store.snapshot().getLibrary())
        {
            if (gesture.getName().startsWith(prefix))
            {
                try
                {
                    highest = Math.max(highest, Integer.parseInt(gesture.getName().substring(prefix.length()).trim()));
                }
                catch (NumberFormatException ignored)
                {
                    highest = Math.max(highest, 1);
                }
            }
        }
        return prefix + (highest + 1);
    }

    private double headHeight()
    {
        return Double.isNaN(floorY) ? DEFAULT_HEAD_HEIGHT : Math.max(0.5, headY - floorY);
    }

    /**
     * Takes the keys for a gesture's new name until enter is pressed or the keyboard is put away.
     *
     * @param gestureId The gesture to rename.
     * @param ended     Run once the renaming ends.
     */
    public void rename(String gestureId, Runnable ended)
    {
        Gesture gesture = store.gesture(gestureId);
        if (gesture == null)
        {
            return;
        }
        renamingId = gestureId;
        renameEnded = ended;
        renaming.setLength(0);
        renaming.append(gesture.getName());
        keys.capture(new CanvasKeys.Capture()
        {
            @Override
            public void typed(char character)
            {
                if (character >= ' ' && renaming.length() < 32)
                {
                    renaming.append(character);
                    renameEnded.run();
                }
            }

            @Override
            public void pressed(int code)
            {
                if (code == KeyEvent.VK_BACK_SPACE && renaming.length() > 0)
                {
                    renaming.setLength(renaming.length() - 1);
                    renameEnded.run();
                }
                else if (code == KeyEvent.VK_ENTER)
                {
                    finishRename();
                }
                else if (code == KeyEvent.VK_ESCAPE)
                {
                    cancelRename();
                }
            }
        });
    }

    /**
     * @return The id of the gesture being renamed, or null.
     */
    public String renamingId()
    {
        return renamingId;
    }

    /**
     * Saves the name typed, unless it is blank, and gives the keys back.
     */
    public void finishRename()
    {
        String id = renamingId;
        String name = renaming.toString().trim();
        if (id != null && !name.isEmpty())
        {
            store.update(gestures ->
            {
                Gesture gesture = gestures.gesture(id);
                if (gesture != null)
                {
                    gesture.setName(name);
                }
            });
        }
        cancelRename();
    }

    /**
     * Drops the name typed and gives the keys back.
     */
    public void cancelRename()
    {
        if (renamingId == null)
        {
            return;
        }
        renamingId = null;
        keys.capture(null);
        Runnable ended = renameEnded;
        renameEnded = () -> { };
        ended.run();
    }

    /**
     * Reseeds the body frame, after a recentre or a change of view.
     */
    public void reseed()
    {
        body.reseed();
    }

    /**
     * Drops everything under way and gives back the keys, when the session ends.
     */
    public void close()
    {
        recorder.cancel();
        recognizer.reset();
        cancelRename();
    }
}
