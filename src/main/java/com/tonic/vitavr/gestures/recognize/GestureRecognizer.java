package com.tonic.vitavr.gestures.recognize;

import com.tonic.vitavr.gestures.model.Gesture;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Watches both hands frame by frame and says which gestures were made. A grip squeezed in a spot fires the spot and
 * takes the grip. A drawing lasts while a hand holds its arm input and is matched when it is let go. While both hands
 * hold their arm inputs and a two-hand motion exists, the hands draw one motion together, and neither draws its own
 * shape until let go. A snap that matches a flick fires it and drops that hand's drawing.
 */
public final class GestureRecognizer
{
    public static final double LEAST_SECONDS = 0.15;
    public static final double LEAST_METRES = 0.08;
    private static final int HANDS = 2;

    /**
     * One hand this frame.
     */
    public static final class HandState
    {
        /** The hand in the body frame. */
        public final double[] point = new double[3];
        /** The hand's turn rate in the body frame. */
        public final double[] spin = new double[3];
        /** The hand's orientation in the body frame, x, y, z, w. */
        public final double[] orientation = {0, 0, 0, 1};
        public boolean tracked;
        public boolean armed;
        public boolean gripWentDown;
    }

    /**
     * A gesture recognised this frame.
     */
    @Getter
    @AllArgsConstructor
    public static final class Fired
    {
        private final Gesture gesture;
        /** 0 left, 1 right, or -1 for both. */
        private final int hand;
        /** Whether the gesture took the hand's grip from the pointer. */
        private final boolean tookGrip;
    }

    private List<Gesture> library = Collections.emptyList();
    private ShapeTemplates templates = new ShapeTemplates(Collections.emptyList());
    private int version = Integer.MIN_VALUE;

    @SuppressWarnings("unchecked")
    private final List<double[]>[] strokes = new List[]{new ArrayList<double[]>(), new ArrayList<double[]>()};
    private final boolean[] stroking = new boolean[HANDS];
    private final double[] strokeStart = new double[HANDS];
    private final boolean[] blocked = new boolean[HANDS];
    private final FlickDetector[] flicks = {new FlickDetector(), new FlickDetector()};
    private boolean twoStroke;
    private double twoStart;
    private final List<double[]> twoLeft = new ArrayList<>();
    private final List<double[]> twoRight = new ArrayList<>();

    /**
     * Takes the gestures to look for, remaking the templates only when they changed.
     *
     * @param gestures The library.
     * @param changed  A number that changes whenever the library does.
     */
    public void library(List<Gesture> gestures, int changed)
    {
        if (changed == version)
        {
            return;
        }
        version = changed;
        library = gestures;
        templates = new ShapeTemplates(gestures);
    }

    /**
     * @param now   The time in seconds.
     * @param left  The left hand.
     * @param right The right hand.
     * @return The gestures made this frame.
     */
    public List<Fired> frame(double now, HandState left, HandState right)
    {
        List<Fired> fired = new ArrayList<>(1);
        HandState[] hands = {left, right};
        for (int hand = 0; hand < HANDS; hand++)
        {
            HandState state = hands[hand];
            if (state.tracked && state.gripWentDown)
            {
                Gesture spot = PointMatches.spot(library, hand, state.point);
                if (spot != null)
                {
                    fired.add(new Fired(spot, hand, true));
                }
            }
        }
        twoHand(now, left, right, fired);
        for (int hand = 0; hand < HANDS; hand++)
        {
            HandState state = hands[hand];
            if (!twoStroke)
            {
                oneHand(now, hand, state, fired);
            }
            if (!state.armed)
            {
                blocked[hand] = false;
            }
            flick(now, hand, state, fired);
        }
        return fired;
    }

    private void twoHand(double now, HandState left, HandState right, List<Fired> fired)
    {
        boolean both = left.tracked && right.tracked && left.armed && right.armed;
        if (!twoStroke && both && templates.hasTwoHand())
        {
            twoStroke = true;
            twoStart = now;
            twoLeft.clear();
            twoRight.clear();
            for (int hand = 0; hand < HANDS; hand++)
            {
                stroking[hand] = false;
                strokes[hand].clear();
            }
        }
        if (!twoStroke)
        {
            return;
        }
        if (both)
        {
            twoLeft.add(left.point.clone());
            twoRight.add(right.point.clone());
            return;
        }
        twoStroke = false;
        blocked[0] = left.armed;
        blocked[1] = right.armed;
        if (now - twoStart >= LEAST_SECONDS && PathMath.length(twoLeft) + PathMath.length(twoRight) >= LEAST_METRES)
        {
            ShapeTemplates.Match match = templates.matchTwoHand(ShapeTemplates.twoHand(twoLeft, twoRight));
            if (match != null)
            {
                fired.add(new Fired(match.getGesture(), -1, false));
            }
        }
    }

    private void oneHand(double now, int hand, HandState state, List<Fired> fired)
    {
        boolean drawing = state.tracked && state.armed && !blocked[hand];
        if (drawing)
        {
            if (!stroking[hand])
            {
                stroking[hand] = true;
                strokeStart[hand] = now;
                strokes[hand].clear();
            }
            strokes[hand].add(state.point.clone());
            return;
        }
        if (!stroking[hand])
        {
            return;
        }
        stroking[hand] = false;
        List<double[]> stroke = strokes[hand];
        if (now - strokeStart[hand] >= LEAST_SECONDS && PathMath.length(stroke) >= LEAST_METRES)
        {
            ShapeTemplates.Match match = templates.matchShape(ShapeTemplates.shape(stroke), hand);
            if (match != null)
            {
                fired.add(new Fired(match.getGesture(), hand, false));
            }
        }
        stroke.clear();
    }

    private void flick(double now, int hand, HandState state, List<Fired> fired)
    {
        if (!state.tracked)
        {
            flicks[hand].reset();
            return;
        }
        FlickDetector.Peak peak = flicks[hand].feed(now, state.spin, state.armed);
        if (peak == null)
        {
            return;
        }
        Gesture flick = PointMatches.flick(library, hand, peak);
        if (flick != null)
        {
            fired.add(new Fired(flick, hand, false));
            if (stroking[hand])
            {
                stroking[hand] = false;
                strokes[hand].clear();
                blocked[hand] = state.armed;
            }
        }
    }

    /**
     * @param hand 0 left or 1 right.
     * @return The hand's drawing under way in the body frame, empty when there is none.
     */
    public List<double[]> drawing(int hand)
    {
        if (twoStroke)
        {
            return Collections.unmodifiableList(hand == 0 ? twoLeft : twoRight);
        }
        return stroking[hand] ? Collections.unmodifiableList(strokes[hand]) : Collections.emptyList();
    }

    /**
     * Drops every drawing and snap under way, as when gestures are paused.
     */
    public void reset()
    {
        twoStroke = false;
        for (int hand = 0; hand < HANDS; hand++)
        {
            stroking[hand] = false;
            strokes[hand].clear();
            blocked[hand] = false;
            flicks[hand].reset();
        }
    }
}
