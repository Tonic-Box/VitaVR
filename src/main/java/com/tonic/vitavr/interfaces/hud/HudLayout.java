package com.tonic.vitavr.interfaces.hud;

import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where moved HUD pieces stand round the eyes, kept relative to the way the HUD faced when shown and saved as text.
 * A piece stands at a yaw (right positive) and pitch (up positive) from the eyes, at a distance, facing them,
 * then turned (right edge away positive) and tilted (top edge away positive) by its own angles.
 */
public final class HudLayout
{
    /** The nearest and furthest a piece stands, in metres. */
    public static final double NEAREST = 0.5;
    public static final double FURTHEST = 4;
    /** The smallest and largest a piece is drawn, against the middle of the HUD at the same distance. */
    public static final double SMALLEST = 0.3;
    public static final double LARGEST = 4;
    /** The most a piece turns or tilts away from facing the eyes, in radians. */
    public static final double MOST_ANGLE = Math.toRadians(80);
    private static final int YAW = 0;
    private static final int PITCH = 1;
    private static final int DISTANCE = 2;
    private static final int SCALE = 3;
    private static final int TURN = 4;
    private static final int TILT = 5;
    private static final int VALUES = 6;
    private static final int OLD_VALUES = 4;
    private static final int DRAG_STEPS = 4;

    private final Map<HudPiece, double[]> places = new EnumMap<>(HudPiece.class);
    private final double[] eye = new double[3];
    private double frameYaw;
    private double pixelRadians = 1;
    private boolean dropped;

    /**
     * Sets the frame pieces stand in: the eyes and facing of the HUD as placed, and its pixel size.
     *
     * @param eyeAt The eye point in the room.
     * @param yaw The HUD's facing, as HudShape measures it.
     * @param radiansPerPixel The angle of one middle HUD pixel, in radians.
     */
    public void frame(double[] eyeAt, double yaw, double radiansPerPixel)
    {
        System.arraycopy(eyeAt, 0, eye, 0, 3);
        frameYaw = yaw;
        pixelRadians = radiansPerPixel;
    }

    public boolean moved(HudPiece piece)
    {
        return places.containsKey(piece);
    }

    /**
     * @return How many pieces stand away from the default layout.
     */
    public int movedCount()
    {
        return places.size();
    }

    /**
     * Moves a piece to stand with its middle on a room point, keeping its size.
     *
     * @param piece The piece.
     * @param point The room point for its middle.
     * @param metresPerPixel The size of one of its pixels, in metres.
     */
    public void standAt(HudPiece piece, double[] point, double metresPerPixel)
    {
        double[] place = places.computeIfAbsent(piece, unused -> new double[VALUES]);
        aimAt(place, point);
        place[SCALE] = clamp(metresPerPixel / (pixelRadians * place[DISTANCE]), SMALLEST, LARGEST);
    }

    /**
     * Lays out a moved piece facing the eyes.
     *
     * @param piece The piece.
     * @param width Its width, in pixels.
     * @param height Its height, in pixels.
     * @param out Receives the placement.
     */
    public void lay(HudPiece piece, int width, int height, Billboard out)
    {
        double[] place = places.get(piece);
        double[] middle = new double[3];
        double[] across = new double[3];
        double[] upward = new double[3];
        axes(place, middle, across, upward);
        out.lay(middle, across, upward, width, height, metresPerPixel(place));
    }

    /**
     * Moves a held piece so the pixel it was grabbed by stays on the pointer, at the same distance from the eyes.
     *
     * @param piece The piece.
     * @param aim The pointer pose in the room.
     * @param width Its width, in pixels.
     * @param height Its height, in pixels.
     * @param grabX The grabbed pixel x.
     * @param grabY The grabbed pixel y, down.
     */
    public void drag(HudPiece piece, Pose aim, int width, int height, double grabX, double grabY)
    {
        double[] place = places.get(piece);
        double metresPerPixel = metresPerPixel(place);
        double right = (grabX - width / 2.0) * metresPerPixel;
        double up = (height / 2.0 - grabY) * metresPerPixel;
        double[] middle = new double[3];
        double[] across = new double[3];
        double[] upward = new double[3];
        double[] wanted = new double[3];
        // The piece is flat and may be turned, so the grabbed pixel is not as far from the eyes as its middle
        axes(place, middle, across, upward);
        double reachSquared = 0;
        for (int axis = 0; axis < 3; axis++)
        {
            double offset = middle[axis] + across[axis] * right + upward[axis] * up - eye[axis];
            reachSquared += offset * offset;
        }
        double reach = Math.sqrt(reachSquared);
        double[] direction = new double[3];
        aim.rotate(0, 0, -1, direction);
        double ox = aim.x() - eye[0];
        double oy = aim.y() - eye[1];
        double oz = aim.z() - eye[2];
        double along = ox * direction[0] + oy * direction[1] + oz * direction[2];
        double inside = along * along - (ox * ox + oy * oy + oz * oz - reach * reach);
        if (inside < 0)
        {
            return;
        }
        double t = -along + Math.sqrt(inside);
        if (t <= 0)
        {
            return;
        }
        double[] target = {aim.x() + direction[0] * t, aim.y() + direction[1] * t, aim.z() + direction[2] * t};
        double distance = place[DISTANCE];
        aimAt(place, target);
        place[DISTANCE] = distance;
        // The middle sits off the grabbed point along the piece's own axes, which turn as it moves
        for (int step = 0; step < DRAG_STEPS; step++)
        {
            axes(place, middle, across, upward);
            for (int axis = 0; axis < 3; axis++)
            {
                wanted[axis] = target[axis] - across[axis] * right - upward[axis] * up;
            }
            aimAt(place, wanted);
            place[DISTANCE] = distance;
        }
    }

    /**
     * Moves a held piece nearer or further, keeping its size in the room.
     *
     * @param piece The piece.
     * @param metres How much further, negative for nearer.
     */
    public void pushPull(HudPiece piece, double metres)
    {
        double[] place = places.get(piece);
        double was = place[DISTANCE];
        place[DISTANCE] = clamp(was + metres, NEAREST, FURTHEST);
        place[SCALE] = clamp(place[SCALE] * was / place[DISTANCE], SMALLEST, LARGEST);
    }

    /**
     * Grows or shrinks a held piece.
     *
     * @param piece The piece.
     * @param factor The size multiplier.
     */
    public void resize(HudPiece piece, double factor)
    {
        double[] place = places.get(piece);
        place[SCALE] = clamp(place[SCALE] * factor, SMALLEST, LARGEST);
    }

    /**
     * Turns and tilts a held piece away from facing the eyes.
     *
     * @param piece The piece.
     * @param turn How much more to turn it, right edge away, in radians.
     * @param tilt How much more to tilt it, top edge away, in radians.
     */
    public void angle(HudPiece piece, double turn, double tilt)
    {
        double[] place = places.get(piece);
        place[TURN] = clamp(place[TURN] + turn, -MOST_ANGLE, MOST_ANGLE);
        place[TILT] = clamp(place[TILT] + tilt, -MOST_ANGLE, MOST_ANGLE);
    }

    /**
     * Marks the layout for saving once a held piece is let go.
     */
    public void drop()
    {
        dropped = true;
    }

    /**
     * @return Whether a piece was let go since the last call.
     */
    public boolean takeDropped()
    {
        boolean was = dropped;
        dropped = false;
        return was;
    }

    /**
     * Puts every piece back in the default layout.
     */
    public void reset()
    {
        places.clear();
    }

    /**
     * @return The layout as text, for the config.
     */
    public String format()
    {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<HudPiece, double[]> entry : places.entrySet())
        {
            double[] place = entry.getValue();
            if (text.length() > 0)
            {
                text.append(';');
            }
            text.append(entry.getKey().getKey()).append('=')
                    .append(String.format(Locale.ROOT, "%.5f,%.5f,%.4f,%.4f,%.4f,%.4f", place[YAW], place[PITCH], place[DISTANCE], place[SCALE], place[TURN], place[TILT]));
        }
        return text.toString();
    }

    /**
     * Replaces the layout with one read from text; anything unreadable stays in the default layout.
     *
     * @param text The layout as format gives it.
     */
    public void load(String text)
    {
        places.clear();
        if (text == null)
        {
            return;
        }
        for (String entry : text.split(";"))
        {
            int equals = entry.indexOf('=');
            HudPiece piece = equals < 0 ? null : HudPiece.named(entry.substring(0, equals).trim());
            if (piece == null)
            {
                continue;
            }
            String[] values = entry.substring(equals + 1).split(",");
            if (values.length != VALUES && values.length != OLD_VALUES)
            {
                continue;
            }
            try
            {
                double yaw = Double.parseDouble(values[YAW]);
                double pitch = Double.parseDouble(values[PITCH]);
                double distance = Double.parseDouble(values[DISTANCE]);
                double scale = Double.parseDouble(values[SCALE]);
                double turn = values.length == VALUES ? Double.parseDouble(values[TURN]) : 0;
                double tilt = values.length == VALUES ? Double.parseDouble(values[TILT]) : 0;
                if (Double.isFinite(yaw) && Double.isFinite(pitch) && Double.isFinite(distance) && Double.isFinite(scale) && Double.isFinite(turn) && Double.isFinite(tilt))
                {
                    places.put(piece, new double[]{yaw, clamp(pitch, -Math.PI / 2, Math.PI / 2), clamp(distance, NEAREST, FURTHEST), clamp(scale, SMALLEST, LARGEST),
                            clamp(turn, -MOST_ANGLE, MOST_ANGLE), clamp(tilt, -MOST_ANGLE, MOST_ANGLE)});
                }
            }
            catch (NumberFormatException ignored)
            {
                // A broken entry leaves that piece in the default layout
            }
        }
    }

    private double metresPerPixel(double[] place)
    {
        return place[SCALE] * pixelRadians * place[DISTANCE];
    }

    /**
     * Sets a place's yaw, pitch and distance to stand at a room point.
     */
    private void aimAt(double[] place, double[] point)
    {
        double dx = point[0] - eye[0];
        double dy = point[1] - eye[1];
        double dz = point[2] - eye[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double rightward = dx * Math.cos(frameYaw) - dz * Math.sin(frameYaw);
        double forward = -dx * Math.sin(frameYaw) - dz * Math.cos(frameYaw);
        place[YAW] = Math.atan2(rightward, forward);
        place[PITCH] = distance < 1e-9 ? 0 : Math.asin(clamp(dy / distance, -1, 1));
        place[DISTANCE] = clamp(distance, NEAREST, FURTHEST);
    }

    /**
     * Gets the middle and axes of a piece from its place: facing the eyes, then turned and tilted.
     */
    private void axes(double[] place, double[] middle, double[] across, double[] upward)
    {
        double yaw = place[YAW];
        double pitch = place[PITCH];
        double turn = place[TURN];
        double tilt = place[TILT];
        double[] rightOfFrame = {Math.cos(frameYaw), 0, -Math.sin(frameYaw)};
        double[] forwardOfFrame = {-Math.sin(frameYaw), 0, -Math.cos(frameYaw)};
        for (int axis = 0; axis < 3; axis++)
        {
            double outward = Math.sin(yaw) * rightOfFrame[axis] + Math.cos(yaw) * forwardOfFrame[axis];
            double sideways = Math.cos(yaw) * rightOfFrame[axis] - Math.sin(yaw) * forwardOfFrame[axis];
            double rising = axis == 1 ? 1 : 0;
            double away = Math.cos(pitch) * outward + Math.sin(pitch) * rising;
            double facingUp = Math.cos(pitch) * rising - Math.sin(pitch) * outward;
            middle[axis] = eye[axis] + place[DISTANCE] * away;
            double turnedAway = Math.cos(turn) * away - Math.sin(turn) * sideways;
            across[axis] = Math.cos(turn) * sideways + Math.sin(turn) * away;
            upward[axis] = Math.cos(tilt) * facingUp + Math.sin(tilt) * turnedAway;
        }
    }

    private static double clamp(double value, double low, double high)
    {
        return Math.max(low, Math.min(high, value));
    }
}
