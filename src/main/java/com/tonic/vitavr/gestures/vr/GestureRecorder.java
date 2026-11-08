package com.tonic.vitavr.gestures.vr;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Sample;
import com.tonic.vitavr.gestures.model.Take;
import com.tonic.vitavr.gestures.recognize.FlickDetector;
import com.tonic.vitavr.gestures.recognize.GestureRecognizer;
import com.tonic.vitavr.gestures.recognize.PathMath;
import com.tonic.vitavr.gestures.recognize.PointMatches;
import com.tonic.vitavr.gestures.recognize.ShapeTemplates;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Records a new gesture, or new takes for one, a take at a time: a spot is squeezed once, while shapes, flicks and
 * two-hand motions are made three times, and a take too small, too quick or unlike the others is asked for again.
 */
public final class GestureRecorder
{
    public static final int TAKES = 3;
    private static final double HISTORY_SECONDS = 1.0;
    private static final double FLICK_SECONDS = 0.6;
    private static final double MOST_TAKE_DISTANCE = 0.4;
    private static final int HANDS = 2;

    /**
     * Where the recording is.
     */
    public enum Stage
    {
        IDLE,
        CHOOSE_HAND,
        TAKING,
        DONE
    }

    @Getter
    private Stage stage = Stage.IDLE;
    @Getter
    private GestureKind kind = GestureKind.SHAPE;
    /** 0 left, 1 right, or -1 for both. */
    @Getter
    private int hand = 1;
    @Getter
    private boolean either;
    @Getter
    private boolean armedFlick = true;
    @Getter
    private String message = "";
    @Getter
    private Gesture redoing;
    private final List<Take> takes = new ArrayList<>();
    private final List<double[]> flickAxes = new ArrayList<>();
    private final List<Double> flickSpeeds = new ArrayList<>();
    private double[] spotCentre;

    @SuppressWarnings("unchecked")
    private final Deque<Sample>[] history = new Deque[]{new ArrayDeque<Sample>(), new ArrayDeque<Sample>()};
    @SuppressWarnings("unchecked")
    private final List<Sample>[] live = new List[]{new ArrayList<Sample>(), new ArrayList<Sample>()};
    private boolean taking;
    private double takeStart;
    private boolean waitRelease;
    private final FlickDetector flicks = new FlickDetector();

    /**
     * Starts recording a gesture.
     *
     * @param newKind  The kind to record.
     * @param existing The gesture to record new takes for, or null for a new one.
     */
    public void start(GestureKind newKind, Gesture existing)
    {
        kind = existing == null ? newKind : existing.getKind();
        redoing = existing;
        either = existing != null && existing.getHand() == GestureHand.EITHER;
        armedFlick = existing == null || existing.isArmed();
        hand = kind == GestureKind.TWO_HAND ? -1 : existing == null ? 1 : existing.recordedHand();
        clearTakes();
        message = "";
        stage = kind == GestureKind.TWO_HAND || existing != null ? Stage.TAKING : Stage.CHOOSE_HAND;
    }

    /**
     * @param chosen 0 left or 1 right.
     */
    public void chooseHand(int chosen)
    {
        hand = chosen;
        stage = Stage.TAKING;
        message = "";
    }

    /**
     * @param on Whether the gesture may be made with either hand, mirrored for the other.
     */
    public void either(boolean on)
    {
        either = on;
    }

    /**
     * @param on Whether a flick needs the arm input held.
     */
    public void armedFlick(boolean on)
    {
        armedFlick = on;
    }

    /**
     * Drops the last take to make it again.
     */
    public void redoLast()
    {
        if (!takes.isEmpty())
        {
            takes.remove(takes.size() - 1);
            if (!flickAxes.isEmpty() && flickAxes.size() > takes.size())
            {
                flickAxes.remove(flickAxes.size() - 1);
                flickSpeeds.remove(flickSpeeds.size() - 1);
            }
        }
        stage = Stage.TAKING;
        message = "";
    }

    /**
     * Stops recording and forgets the takes.
     */
    public void cancel()
    {
        stage = Stage.IDLE;
        redoing = null;
        clearTakes();
    }

    private void clearTakes()
    {
        takes.clear();
        flickAxes.clear();
        flickSpeeds.clear();
        spotCentre = null;
        taking = false;
        waitRelease = false;
        for (int each = 0; each < HANDS; each++)
        {
            live[each].clear();
        }
        flicks.reset();
    }

    /**
     * @return The takes made so far.
     */
    public int taken()
    {
        return takes.size();
    }

    /**
     * @return The takes wanted in all.
     */
    public int wanted()
    {
        return kind == GestureKind.SPOT ? 1 : TAKES;
    }

    /**
     * @param which 0 left or 1 right.
     * @return The take under way for a hand, in the body frame.
     */
    public List<Sample> live(int which)
    {
        return live[which];
    }

    /**
     * @return Every take made so far.
     */
    public List<Take> takes()
    {
        return takes;
    }

    /**
     * Records from both hands for a frame.
     *
     * @param now   The time in seconds.
     * @param left  The left hand.
     * @param right The right hand.
     * @return Which grips the recording took: bit 0 the left, bit 1 the right.
     */
    public int frame(double now, GestureRecognizer.HandState left, GestureRecognizer.HandState right)
    {
        GestureRecognizer.HandState[] hands = {left, right};
        for (int each = 0; each < HANDS; each++)
        {
            remember(now, each, hands[each]);
        }
        if (stage != Stage.TAKING)
        {
            return 0;
        }
        switch (kind)
        {
            case SPOT:
                return spot(now, hands[hand]);
            case FLICK:
                flick(now, hands[hand]);
                return 0;
            case TWO_HAND:
                twoHand(now, left, right);
                return 0;
            case SHAPE:
            default:
                shape(now, hands[hand]);
                return 0;
        }
    }

    private void remember(double now, int which, GestureRecognizer.HandState state)
    {
        Deque<Sample> kept = history[which];
        if (state.tracked)
        {
            kept.addLast(sample(now, state));
        }
        while (!kept.isEmpty() && now - kept.peekFirst().getT() > HISTORY_SECONDS)
        {
            kept.removeFirst();
        }
    }

    private static Sample sample(double now, GestureRecognizer.HandState state)
    {
        return new Sample(now, state.point[0], state.point[1], state.point[2], state.orientation[0], state.orientation[1], state.orientation[2], state.orientation[3]);
    }

    private int spot(double now, GestureRecognizer.HandState state)
    {
        if (!state.tracked || !state.gripWentDown)
        {
            return 0;
        }
        Take take = new Take();
        take.hand(hand).addAll(fromStart(new ArrayList<>(history[hand]), now - HISTORY_SECONDS));
        takes.add(take);
        spotCentre = state.point.clone();
        stage = Stage.DONE;
        message = "";
        return 1 << hand;
    }

    private void shape(double now, GestureRecognizer.HandState state)
    {
        boolean drawing = state.tracked && state.armed;
        if (drawing)
        {
            if (!taking)
            {
                taking = true;
                takeStart = now;
                live[hand].clear();
            }
            live[hand].add(sample(now, state));
            return;
        }
        if (!taking)
        {
            return;
        }
        taking = false;
        List<Sample> made = fromStart(live[hand], takeStart);
        live[hand].clear();
        if (!bigEnough(now, ShapeTemplates.points(made)))
        {
            return;
        }
        double[][] prepared = ShapeTemplates.shape(ShapeTemplates.points(made));
        if (!likeTheOthers(prepared, false))
        {
            return;
        }
        Take take = new Take();
        take.hand(hand).addAll(made);
        accept(take);
    }

    private void twoHand(double now, GestureRecognizer.HandState left, GestureRecognizer.HandState right)
    {
        boolean both = left.tracked && right.tracked && left.armed && right.armed;
        if (waitRelease)
        {
            waitRelease = left.armed || right.armed;
            return;
        }
        if (both)
        {
            if (!taking)
            {
                taking = true;
                takeStart = now;
                live[0].clear();
                live[1].clear();
            }
            live[0].add(sample(now, left));
            live[1].add(sample(now, right));
            return;
        }
        if (!taking)
        {
            return;
        }
        taking = false;
        waitRelease = left.armed || right.armed;
        List<Sample> madeLeft = fromStart(live[0], takeStart);
        List<Sample> madeRight = fromStart(live[1], takeStart);
        live[0].clear();
        live[1].clear();
        if (now - takeStart < GestureRecognizer.LEAST_SECONDS
                || PathMath.length(ShapeTemplates.points(madeLeft)) + PathMath.length(ShapeTemplates.points(madeRight)) < GestureRecognizer.LEAST_METRES)
        {
            message = "Too small or too quick: move your hands further";
            return;
        }
        double[][] prepared = ShapeTemplates.twoHand(ShapeTemplates.points(madeLeft), ShapeTemplates.points(madeRight));
        if (!likeTheOthers(prepared, true))
        {
            return;
        }
        Take take = new Take();
        take.getLeft().addAll(madeLeft);
        take.getRight().addAll(madeRight);
        accept(take);
    }

    private void flick(double now, GestureRecognizer.HandState state)
    {
        if (!state.tracked)
        {
            flicks.reset();
            return;
        }
        FlickDetector.Peak peak = flicks.feed(now, state.spin, state.armed);
        if (peak == null)
        {
            return;
        }
        if (armedFlick && !peak.isArmed())
        {
            message = "Hold the arm input while you snap";
            return;
        }
        if (peak.getSpeed() < PointMatches.LEAST_FLICK_SPEED)
        {
            message = "Too slow: snap your wrist faster";
            return;
        }
        if (!flickAxes.isEmpty())
        {
            double[] mean = meanAxis();
            double dot = mean[0] * peak.getAxis()[0] + mean[1] * peak.getAxis()[1] + mean[2] * peak.getAxis()[2];
            if (Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot)))) > PointMatches.MOST_AXIS_DEGREES)
            {
                message = "That snap turned another way: try again";
                return;
            }
        }
        Take take = new Take();
        take.hand(hand).addAll(fromStart(new ArrayList<>(history[hand]), now - FLICK_SECONDS));
        flickAxes.add(peak.getAxis());
        flickSpeeds.add(peak.getSpeed());
        accept(take);
    }

    private boolean bigEnough(double now, List<double[]> path)
    {
        if (now - takeStart < GestureRecognizer.LEAST_SECONDS || PathMath.length(path) < GestureRecognizer.LEAST_METRES)
        {
            message = "Too small or too quick: draw it bigger";
            return false;
        }
        return true;
    }

    private boolean likeTheOthers(double[][] prepared, boolean bothHands)
    {
        for (Take other : takes)
        {
            double[][] before = bothHands ? ShapeTemplates.twoHand(ShapeTemplates.points(other.getLeft()), ShapeTemplates.points(other.getRight()))
                    : ShapeTemplates.shape(ShapeTemplates.points(other.hand(hand)));
            if (PathMath.bestDistance(prepared, before, 25, 5) <= MOST_TAKE_DISTANCE)
            {
                return true;
            }
        }
        if (takes.isEmpty())
        {
            return true;
        }
        message = "That one did not look like the others: try again";
        return false;
    }

    private void accept(Take take)
    {
        takes.add(take);
        message = "";
        if (takes.size() >= wanted())
        {
            stage = Stage.DONE;
        }
    }

    private double[] meanAxis()
    {
        double[] sum = new double[3];
        for (double[] axis : flickAxes)
        {
            sum[0] += axis[0];
            sum[1] += axis[1];
            sum[2] += axis[2];
        }
        double length = Math.sqrt(sum[0] * sum[0] + sum[1] * sum[1] + sum[2] * sum[2]);
        return length < 1e-9 ? new double[]{0, 1, 0} : new double[]{sum[0] / length, sum[1] / length, sum[2] / length};
    }

    /**
     * Keeps the samples from a time on, timed from it.
     */
    private static List<Sample> fromStart(List<Sample> samples, double start)
    {
        List<Sample> out = new ArrayList<>(samples.size());
        double first = Double.NaN;
        for (Sample sample : samples)
        {
            if (sample.getT() < start)
            {
                continue;
            }
            if (Double.isNaN(first))
            {
                first = sample.getT();
            }
            out.add(new Sample(sample.getT() - first, sample.getX(), sample.getY(), sample.getZ(), sample.getQx(), sample.getQy(), sample.getQz(), sample.getQw()));
        }
        return out;
    }

    /**
     * Makes the recorded gesture, keeping the name, id and settings of one being recorded again.
     *
     * @param name       The name for a new gesture.
     * @param headHeight The head's height above the floor, in metres.
     * @return The gesture, or null before the takes are done.
     */
    public Gesture result(String name, double headHeight)
    {
        if (stage != Stage.DONE)
        {
            return null;
        }
        Gesture gesture = new Gesture();
        if (redoing != null)
        {
            gesture.setId(redoing.getId());
            gesture.setName(redoing.getName());
            gesture.setEnabled(redoing.isEnabled());
            gesture.setSensitivity(redoing.getSensitivity());
            gesture.setRadius(redoing.getRadius());
        }
        else
        {
            gesture.setName(name);
        }
        gesture.setKind(kind);
        gesture.setHand(kind == GestureKind.TWO_HAND ? GestureHand.BOTH : either ? GestureHand.EITHER : hand == 0 ? GestureHand.LEFT : GestureHand.RIGHT);
        gesture.setArmed(armedFlick);
        gesture.getTakes().addAll(takes);
        gesture.setHeadHeight(headHeight);
        if (kind == GestureKind.SPOT && spotCentre != null)
        {
            gesture.setCentre(spotCentre);
        }
        if (kind == GestureKind.FLICK && !flickAxes.isEmpty())
        {
            gesture.setAxis(meanAxis());
            double slowest = Double.MAX_VALUE;
            for (double speed : flickSpeeds)
            {
                slowest = Math.min(slowest, speed);
            }
            gesture.setPeakSpin(slowest);
        }
        return gesture;
    }
}
