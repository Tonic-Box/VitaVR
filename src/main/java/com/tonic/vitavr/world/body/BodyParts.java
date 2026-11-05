package com.tonic.vitavr.world.body;

import net.runelite.api.Model;

import java.util.Arrays;

/**
 * The held item and head parts of a player's body, found once from the body at rest.
 */
public final class BodyParts
{
    private static final int WEAPON_SLOT = 3;
    private static final int SHIELD_SLOT = 5;
    private static final int ABOVE_SHOULDERS = 4;

    private final boolean[] heldFaces;
    private final int[] rightRiding;
    private final int[] leftRiding;
    private final int[] rightHeld;
    private final int[] leftHeld;
    private final boolean[] headFaces;

    private BodyParts(boolean[] heldFaces, int[] rightRiding, int[] leftRiding, int[] rightHeld, int[] leftHeld, boolean[] headFaces)
    {
        this.heldFaces = heldFaces;
        this.rightRiding = rightRiding;
        this.leftRiding = leftRiding;
        this.rightHeld = rightHeld;
        this.leftHeld = leftHeld;
        this.headFaces = headFaces;
    }

    /**
     * Finds the parts of a body.
     *
     * @param model     The body at rest.
     * @param groups    The vertices by label.
     * @param slotFaces The first face of each equipment slot, ending with the face count, or null if unknown.
     * @return The body parts.
     */
    public static BodyParts of(Model model, int[][] groups, int[] slotFaces)
    {
        int faces = model.getFaceCount();
        int vertices = model.getVerticesCount();
        int[] first = model.getFaceIndices1();
        int[] second = model.getFaceIndices2();
        int[] third = model.getFaceIndices3();
        boolean[] heldFaces = new boolean[faces];
        boolean[] heldVertex = new boolean[vertices];
        if (slotFaces != null && slotFaces.length > SHIELD_SLOT + 1 && slotFaces[slotFaces.length - 1] == faces)
        {
            for (int slot : new int[]{WEAPON_SLOT, SHIELD_SLOT})
            {
                for (int face = slotFaces[slot]; face < slotFaces[slot + 1]; face++)
                {
                    heldFaces[face] = true;
                    heldVertex[first[face]] = true;
                    heldVertex[second[face]] = true;
                    heldVertex[third[face]] = true;
                }
            }
        }

        // Assign each vertex an arm by label, else by body side
        boolean[] armVertex = new boolean[vertices];
        int[] side = new int[vertices];
        float[] xs = model.getVerticesX();
        for (ArmRig arm : new ArmRig[]{ArmRig.RIGHT, ArmRig.LEFT})
        {
            for (int label : arm.whole())
            {
                if (label < groups.length && groups[label] != null)
                {
                    for (int vertex : groups[label])
                    {
                        armVertex[vertex] = true;
                        side[vertex] = arm.side();
                    }
                }
            }
        }
        for (int vertex = 0; vertex < vertices; vertex++)
        {
            if (!armVertex[vertex])
            {
                side[vertex] = xs[vertex] < 0 ? ArmRig.RIGHT.side() : ArmRig.LEFT.side();
            }
        }

        int[] rightRiding = new int[vertices];
        int[] leftRiding = new int[vertices];
        int[] rightHeld = new int[vertices];
        int[] leftHeld = new int[vertices];
        int rightRides = 0;
        int leftRides = 0;
        int rightHolds = 0;
        int leftHolds = 0;
        for (int vertex = 0; vertex < vertices; vertex++)
        {
            if (!heldVertex[vertex])
            {
                continue;
            }
            if (side[vertex] == ArmRig.RIGHT.side())
            {
                rightHeld[rightHolds++] = vertex;
                if (!armVertex[vertex])
                {
                    rightRiding[rightRides++] = vertex;
                }
            }
            else
            {
                leftHeld[leftHolds++] = vertex;
                if (!armVertex[vertex])
                {
                    leftRiding[leftRides++] = vertex;
                }
            }
        }
        return new BodyParts(heldFaces, Arrays.copyOf(rightRiding, rightRides), Arrays.copyOf(leftRiding, leftRides), Arrays.copyOf(rightHeld, rightHolds),
                Arrays.copyOf(leftHeld, leftHolds), headFaces(model, groups, heldFaces, armVertex));
    }

    /**
     * Gets the faces fully above the shoulders that are not held and touch no arm vertex.
     */
    private static boolean[] headFaces(Model model, int[][] groups, boolean[] heldFaces, boolean[] armVertex)
    {
        boolean[] head = new boolean[model.getFaceCount()];
        float[] xs = model.getVerticesX();
        float[] ys = model.getVerticesY();
        float[] zs = model.getVerticesZ();
        double[] right = ArmIk.centroid(xs, ys, zs, groups, ArmRig.RIGHT.shoulder());
        double[] left = ArmIk.centroid(xs, ys, zs, groups, ArmRig.LEFT.shoulder());
        if (right == null || left == null)
        {
            return head;
        }
        double line = Math.min(right[1], left[1]) - ABOVE_SHOULDERS;
        int[] first = model.getFaceIndices1();
        int[] second = model.getFaceIndices2();
        int[] third = model.getFaceIndices3();
        for (int face = 0; face < head.length; face++)
        {
            int a = first[face];
            int b = second[face];
            int c = third[face];
            head[face] = !heldFaces[face] && !armVertex[a] && !armVertex[b] && !armVertex[c] && ys[a] < line && ys[b] < line && ys[c] < line;
        }
        return head;
    }

    /**
     * Checks whether a model has the same face count as the body.
     *
     * @param model The posed model.
     * @return Whether the model fits.
     */
    public boolean fits(Model model)
    {
        return model.getFaceCount() == heldFaces.length;
    }

    /**
     * @param face The face index.
     * @return Whether the face is of the held weapon or shield.
     */
    public boolean held(int face)
    {
        return face < heldFaces.length && heldFaces[face];
    }

    /**
     * @param face The face index.
     * @return Whether the face is of the head or headwear.
     */
    public boolean head(int face)
    {
        return face < headFaces.length && headFaces[face];
    }

    /**
     * @param arm The arm.
     * @return The held vertices outside the arm labels that move with the hand.
     */
    public int[] riding(ArmRig arm)
    {
        return arm == ArmRig.RIGHT ? rightRiding : leftRiding;
    }

    /**
     * @param arm The arm.
     * @return Every vertex held in the arm's hand.
     */
    public int[] held(ArmRig arm)
    {
        return arm == ArmRig.RIGHT ? rightHeld : leftHeld;
    }
}
