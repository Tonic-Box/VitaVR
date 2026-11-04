package com.tonic.vitavr.body;

import com.tonic.api.TModel;
import com.tonic.api.TPlayerComposition;
import com.tonic.vitavr.config.HeadFollow;
import com.tonic.vitavr.config.HeldItems;
import lombok.Getter;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;

import java.util.Arrays;

import static com.tonic.vitavr.space.Vectors.cross;
import static com.tonic.vitavr.space.Vectors.dot;
import static com.tonic.vitavr.space.Vectors.minus;
import static com.tonic.vitavr.space.Vectors.scale;
import static com.tonic.vitavr.space.Vectors.unit;

/**
 * Shapes the player's own body for first person: idle arms follow the controllers, otherwise the game pose is used.
 * Changes are client side only.
 */
public final class OwnBody
{
    private static final int HEAD = 1;
    private static final double USUAL_EYE_HEIGHT = 182;
    private static final double STANDING_EYE_METRES = 1.65;
    private static final double TURN_SECONDS = 0.25;
    private static final double FOLLOW_SECONDS = 0.3;
    private static final double[] RIGHT_POLE = {-0.3, 1, 0.5};
    private static final double[] LEFT_POLE = {0.3, 1, 0.5};
    private static final double[] MODEL_UP = {0, -1, 0};
    private static final double[] MODEL_FORWARD = {0, 0, -1};
    private static final double FADE_FROM_METRES = 0.35;
    private static final double FADE_MOST_METRES = 0.12;
    private static final int MOST_FADE = 220;
    private static final int TURN = 2048;
    private static final int HIDDEN = -2;
    private static final double WRIST_TOWARDS_HAND = 0.4;
    private static final double WRIST_GAP_METRES = 0.005;

    private HeldItems heldItems = HeldItems.WHEN_ACTING;
    private double heldScale = 1;
    private boolean fadeNearEyes;
    private boolean hideHead;

    /** The last drawn ground height (down), or NaN before the first draw. */
    @Getter
    private double ground = Double.NaN;
    private double eyeHeight = Double.NaN;
    @Getter
    private boolean armsFollowing;
    private double[] restHead;
    private BodyParts parts;
    private int measured;
    private final double[] headMoved = new double[3];
    private final double[] viewShift = new double[3];
    private boolean acting;
    private boolean moving;
    private double facing = Double.NaN;
    private double headYaw;
    private double[] eye;
    private TrackedHand rightHand;
    private TrackedHand leftHand;
    private double seconds;
    private double[][] leftWrist;

    private int[] hiddenFaces = new int[64];
    private int[] hiddenColours = new int[64];
    private int hiddenCount;
    private byte[] fades = new byte[0];
    private byte[] ownTransparencies;
    private boolean faded;
    private float[] keptX = new float[0];
    private float[] keptY = new float[0];
    private float[] keptZ = new float[0];
    private int keptVertices;

    /**
     * Sets how the body looks.
     *
     * @param heldItems    When the weapon and shield are shown.
     * @param heldPercent  The weapon and shield size, in percent.
     * @param fadeNearEyes Whether faces near the eyes fade.
     * @param hideHead     Whether the head and headwear are hidden.
     */
    public void look(HeldItems heldItems, int heldPercent, boolean fadeNearEyes, boolean hideHead)
    {
        this.heldItems = heldItems;
        this.heldScale = Math.min(1, Math.max(1, heldPercent) / 100.0);
        this.fadeNearEyes = fadeNearEyes;
        this.hideHead = hideHead;
    }

    /**
     * Prepares the body for the next frame.
     *
     * @param headYaw   The head yaw, as the GPU plugin's camera yaw.
     * @param eye       The eye position, across, down and along.
     * @param rightHand The right controller, or null if not tracked.
     * @param leftHand  The left controller, or null if not tracked.
     * @param seconds   The time since the last frame, in seconds.
     */
    public void frame(double headYaw, double[] eye, TrackedHand rightHand, TrackedHand leftHand, double seconds)
    {
        this.headYaw = headYaw;
        this.eye = eye;
        this.rightHand = rightHand;
        this.leftHand = leftHand;
        this.seconds = seconds;
        leftWrist = null;
    }

    /**
     * @return The resting eye height in game units, or the usual height until measured.
     */
    public double restEyeHeight()
    {
        return Double.isNaN(eyeHeight) ? USUAL_EYE_HEIGHT : eyeHeight;
    }

    /**
     * @return The game units per metre at the body's size.
     */
    public double unitsPerMetre()
    {
        return restEyeHeight() / STANDING_EYE_METRES;
    }

    /**
     * Resets the facing and head tracking.
     */
    public void reset()
    {
        facing = Double.NaN;
        armsFollowing = false;
        Arrays.fill(headMoved, 0);
        Arrays.fill(viewShift, 0);
    }

    /**
     * Eases the view shift towards the head's offset from rest.
     *
     * @param follow When the view follows the head.
     * @return The view shift, across, down and along, in game units.
     */
    public double[] viewShift(HeadFollow follow)
    {
        boolean following = follow == HeadFollow.ALWAYS || follow == HeadFollow.ACTIONS && acting && !moving;
        for (int axis = 0; axis < 3; axis++)
        {
            viewShift[axis] = eased(viewShift[axis], following ? headMoved[axis] : 0, seconds, FOLLOW_SECONDS);
        }
        return viewShift.clone();
    }

    /**
     * Gets the eased body facing: the head's when idle, else the game's.
     *
     * @param player      The player.
     * @param orientation The game orientation, 0 to 2047.
     * @return The orientation to draw, 0 to 2047.
     */
    public int facing(Player player, int orientation)
    {
        int pose = player.getPoseAnimation();
        acting = player.getAnimation() != -1;
        moving = pose != player.getIdlePoseAnimation() && pose != player.getIdleRotateLeft() && pose != player.getIdleRotateRight();
        double wanted = !acting && !moving ? TURN / 2.0 - headYaw * TURN / (2 * Math.PI) : orientation;
        if (Double.isNaN(facing))
        {
            facing = wanted;
        }
        else
        {
            facing += Math.IEEEremainder(wanted - facing, TURN) * (1 - Math.exp(-Math.max(0, seconds) / TURN_SECONDS));
        }
        return Math.floorMod((int) Math.round(facing), TURN);
    }

    /**
     * Shapes the player's model before it is drawn. Call restore after drawing.
     *
     * @param player      The player.
     * @param model       The posed player model.
     * @param orientation The draw orientation.
     * @param x           The draw x (across).
     * @param y           The ground y (down).
     * @param z           The draw z (along).
     */
    public void shape(Player player, Model model, int orientation, int x, int y, int z)
    {
        hiddenCount = 0;
        faded = false;
        keptVertices = 0;
        ground = y;
        armsFollowing = false;
        leftWrist = null;
        int[][] groups = ((TModel) model).getVertexGroups();
        if (groups == null)
        {
            return;
        }
        measure(player, model, groups);
        double[] head = centroid(model, groups, HEAD);
        if (head != null && restHead != null)
        {
            noteHead(head, orientation);
        }
        if (parts == null || !parts.fits(model))
        {
            return;
        }

        double angle = orientation * 2 * Math.PI / TURN;
        boolean idle = !acting && !moving;
        boolean heldShown = heldItems == HeldItems.ALWAYS || heldItems == HeldItems.WHEN_ACTING && acting;
        boolean shrinking = heldShown && heldScale < 1;
        armsFollowing = idle;
        if (idle && (rightHand != null || leftHand != null) || shrinking)
        {
            keep(model);
        }
        if (idle)
        {
            follow(model, groups, ArmRig.RIGHT, rightHand, RIGHT_POLE, angle, x, y, z);
            follow(model, groups, ArmRig.LEFT, leftHand, LEFT_POLE, angle, x, y, z);
            leftWrist = wrist(model, groups, angle, x, y, z);
        }
        if (shrinking)
        {
            shrinkHeld(model, groups, ArmRig.RIGHT);
            shrinkHeld(model, groups, ArmRig.LEFT);
        }
        hide(model, heldShown);
        if (fadeNearEyes && eye != null)
        {
            fadeNearEyes(model, inModel(eye, x, y, z, angle));
        }
    }

    /**
     * Measures a new body's eye height and parts while it is at rest.
     */
    private void measure(Player player, Model model, int[][] groups)
    {
        PlayerComposition composition = player.getPlayerComposition();
        int[] slotFaces = composition == null ? null : ((TPlayerComposition) composition).getSlotFaces();
        int key = appearance(composition, model, slotFaces);
        if (key == measured && parts != null || acting || moving)
        {
            return;
        }
        measured = key;
        double[] head = centroid(model, groups, HEAD);
        eyeHeight = head == null || head[1] >= 0 ? USUAL_EYE_HEIGHT : -head[1];
        restHead = head;
        parts = BodyParts.of(model, groups, slotFaces);
    }

    private static int appearance(PlayerComposition composition, Model model, int[] slotFaces)
    {
        int equipment = composition == null ? 0 : Arrays.hashCode(composition.getEquipmentIds());
        return ((equipment * 31 + model.getVerticesCount()) * 31 + model.getFaceCount()) * 31 + Arrays.hashCode(slotFaces);
    }

    /**
     * Stores the posed head's world offset from rest.
     */
    private void noteHead(double[] head, int orientation)
    {
        double[] moved = turnedOut(new double[]{head[0] - restHead[0], head[1] - restHead[1], head[2] - restHead[2]}, orientation * 2 * Math.PI / TURN);
        System.arraycopy(moved, 0, headMoved, 0, 3);
    }

    /**
     * Bends an arm to a tracked hand and turns the hand to the controller's grip.
     */
    private void follow(Model model, int[][] groups, ArmRig arm, TrackedHand hand, double[] pole, double angle, int x, int y, int z)
    {
        if (hand == null)
        {
            return;
        }
        double[] target = inModel(hand.getAt(), x, y, z, angle);
        double[][] grip = grip(turnedInto(hand.getUp(), angle), turnedInto(hand.getForward(), angle));
        ArmIk.reach(model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), groups, arm, target, pole, parts.riding(arm), grip);
    }

    /**
     * Gets the left wrist surface point, forearm direction and surface normal in the world.
     */
    private double[][] wrist(Model model, int[][] groups, double angle, int x, int y, int z)
    {
        float[] xs = model.getVerticesX();
        float[] ys = model.getVerticesY();
        float[] zs = model.getVerticesZ();
        int forearmLabel = ArmRig.LEFT.forearm();
        double[] forearm = ArmIk.centroid(xs, ys, zs, groups, forearmLabel);
        double[] hand = ArmIk.centroid(xs, ys, zs, groups, ArmRig.LEFT.hand());
        if (leftHand == null || forearm == null || hand == null)
        {
            return null;
        }
        double[] along = minus(hand, forearm);
        if (dot(along, along) < 1e-9)
        {
            return null;
        }
        along = unit(along);
        double[] side = turnedInto(leftHand.getRight(), angle);
        double[] out = unit(minus(side, scale(along, dot(side, along))));
        double[] at = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            at[axis] = forearm[axis] + (hand[axis] - forearm[axis]) * WRIST_TOWARDS_HAND;
        }

        // Find the forearm surface on that side
        double surface = 0;
        for (int vertex : groups[forearmLabel])
        {
            surface = Math.max(surface, (xs[vertex] - at[0]) * out[0] + (ys[vertex] - at[1]) * out[1] + (zs[vertex] - at[2]) * out[2]);
        }
        double unitsPerMetre = unitsPerMetre();
        double[] onSurface = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            onSurface[axis] = at[axis] + out[axis] * (surface + WRIST_GAP_METRES * unitsPerMetre);
        }
        double[] placed = turnedOut(onSurface, angle);
        placed[0] += x;
        placed[1] += y;
        placed[2] += z;
        return new double[][]{placed, turnedOut(along, angle), turnedOut(out, angle)};
    }

    /**
     * Gets the left wrist from the last drawn frame.
     *
     * @return The surface point, forearm direction and surface normal, across, down and along, or null if the arms
     * did not follow.
     */
    public double[][] getLeftWrist()
    {
        return leftWrist;
    }

    /**
     * Shrinks the held item about the hand.
     */
    private void shrinkHeld(Model model, int[][] groups, ArmRig arm)
    {
        int[] held = parts.held(arm);
        float[] xs = model.getVerticesX();
        float[] ys = model.getVerticesY();
        float[] zs = model.getVerticesZ();
        double[] hand = ArmIk.centroid(xs, ys, zs, groups, arm.hand());
        if (held.length == 0 || hand == null)
        {
            return;
        }
        for (int vertex : held)
        {
            xs[vertex] = (float) (hand[0] + (xs[vertex] - hand[0]) * heldScale);
            ys[vertex] = (float) (hand[1] + (ys[vertex] - hand[1]) * heldScale);
            zs[vertex] = (float) (hand[2] + (zs[vertex] - hand[2]) * heldScale);
        }
    }

    /**
     * Saves the posed vertices for restore.
     */
    private void keep(Model model)
    {
        int vertices = model.getVerticesCount();
        if (keptX.length < vertices)
        {
            keptX = new float[vertices];
            keptY = new float[vertices];
            keptZ = new float[vertices];
        }
        System.arraycopy(model.getVerticesX(), 0, keptX, 0, vertices);
        System.arraycopy(model.getVerticesY(), 0, keptY, 0, vertices);
        System.arraycopy(model.getVerticesZ(), 0, keptZ, 0, vertices);
        keptVertices = vertices;
    }

    /**
     * Hides the held items and head as configured.
     */
    private void hide(Model model, boolean heldShown)
    {
        if (heldShown && !hideHead)
        {
            return;
        }
        int[] colours = model.getFaceColors3();
        int faces = model.getFaceCount();
        for (int face = 0; face < faces; face++)
        {
            boolean hidden = !heldShown && parts.held(face) || hideHead && parts.head(face);
            if (hidden && colours[face] != HIDDEN)
            {
                if (hiddenCount == hiddenFaces.length)
                {
                    hiddenFaces = Arrays.copyOf(hiddenFaces, hiddenCount * 2);
                    hiddenColours = Arrays.copyOf(hiddenColours, hiddenCount * 2);
                }
                hiddenFaces[hiddenCount] = face;
                hiddenColours[hiddenCount] = colours[face];
                hiddenCount++;
                colours[face] = HIDDEN;
            }
        }
    }

    /**
     * Fades faces between 35 cm and 12 cm from the eyes, at the body's size.
     */
    private void fadeNearEyes(Model model, double[] eyeInModel)
    {
        double unitsPerMetre = unitsPerMetre();
        double from = FADE_FROM_METRES * unitsPerMetre;
        double most = FADE_MOST_METRES * unitsPerMetre;
        int faces = model.getFaceCount();
        ownTransparencies = model.getFaceTransparencies();
        if (fades.length < faces)
        {
            fades = new byte[faces];
        }
        float[] xs = model.getVerticesX();
        float[] ys = model.getVerticesY();
        float[] zs = model.getVerticesZ();
        int[] first = model.getFaceIndices1();
        int[] second = model.getFaceIndices2();
        int[] third = model.getFaceIndices3();
        boolean any = false;
        for (int face = 0; face < faces; face++)
        {
            int own = ownTransparencies == null ? 0 : ownTransparencies[face] & 0xFF;
            int alpha = own;
            if (own < MOST_FADE)
            {
                int a = first[face];
                int b = second[face];
                int c = third[face];
                double dx = (xs[a] + xs[b] + xs[c]) / 3.0 - eyeInModel[0];
                double dy = (ys[a] + ys[b] + ys[c]) / 3.0 - eyeInModel[1];
                double dz = (zs[a] + zs[b] + zs[c]) / 3.0 - eyeInModel[2];
                double away = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int fade = (int) Math.round(MOST_FADE * Math.max(0, Math.min(1, (from - away) / (from - most))));
                if (fade > alpha)
                {
                    alpha = fade;
                    any = true;
                }
            }
            fades[face] = (byte) alpha;
        }
        if (any)
        {
            ((TModel) model).setFaceTransparencies(fades);
            faded = true;
        }
    }

    /**
     * Restores the model's vertices, faces and transparencies from before shape.
     *
     * @param model The model passed to shape.
     */
    public void restore(Model model)
    {
        int[] colours = model.getFaceColors3();
        for (int i = 0; i < hiddenCount; i++)
        {
            colours[hiddenFaces[i]] = hiddenColours[i];
        }
        hiddenCount = 0;
        if (faded)
        {
            ((TModel) model).setFaceTransparencies(ownTransparencies);
            faded = false;
        }
        ownTransparencies = null;
        if (keptVertices > 0)
        {
            System.arraycopy(keptX, 0, model.getVerticesX(), 0, keptVertices);
            System.arraycopy(keptY, 0, model.getVerticesY(), 0, keptVertices);
            System.arraycopy(keptZ, 0, model.getVerticesZ(), 0, keptVertices);
            keptVertices = 0;
        }
    }

    /**
     * Gets the hand rotation in model space; a level controller facing the body's way gives none.
     */
    static double[][] grip(double[] up, double[] forward)
    {
        double[] f = unit(forward);
        double[] u = unit(minus(up, scale(f, dot(up, f))));
        double[] r = scale(cross(u, f), -1);
        double[] modelRight = scale(cross(MODEL_UP, MODEL_FORWARD), -1);
        double[][] held = {{r[0], u[0], f[0]}, {r[1], u[1], f[1]}, {r[2], u[2], f[2]}};
        double[][] model = {modelRight, MODEL_UP, MODEL_FORWARD};
        return ArmIk.times(held, model);
    }

    /** Converts a world direction to model space. */
    static double[] turnedInto(double[] direction, double angle)
    {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{direction[0] * cos - direction[2] * sin, direction[1], direction[0] * sin + direction[2] * cos};
    }

    /** Converts a model space direction to the world. */
    static double[] turnedOut(double[] direction, double angle)
    {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{direction[0] * cos + direction[2] * sin, direction[1], -direction[0] * sin + direction[2] * cos};
    }

    /** Converts a world point to model space. */
    static double[] inModel(double[] point, double originX, double groundY, double originZ, double angle)
    {
        return turnedInto(new double[]{point[0] - originX, point[1] - groundY, point[2] - originZ}, angle);
    }

    private static double[] centroid(Model model, int[][] groups, int label)
    {
        return ArmIk.centroid(model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), groups, label);
    }

    private static double eased(double from, double to, double seconds, double timeConstant)
    {
        return from + (to - from) * (1 - Math.exp(-Math.max(0, seconds) / timeConstant));
    }
}
