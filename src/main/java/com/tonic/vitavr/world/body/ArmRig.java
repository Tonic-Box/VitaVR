package com.tonic.vitavr.world.body;

/**
 * The vertex labels of a player model's arms. The model faces -z with up as -y, so the right arm is on the -x side.
 */
public final class ArmRig
{
    public static final ArmRig RIGHT = new ArmRig(21, 20, 17, 19, 27, -1);
    public static final ArmRig LEFT = new ArmRig(25, 26, 23, 22, 28, 1);

    private final int shoulder;
    private final int upperArm;
    private final int elbow;
    private final int forearm;
    private final int hand;
    private final int side;

    private ArmRig(int shoulder, int upperArm, int elbow, int forearm, int hand, int side)
    {
        this.shoulder = shoulder;
        this.upperArm = upperArm;
        this.elbow = elbow;
        this.forearm = forearm;
        this.hand = hand;
        this.side = side;
    }

    public int shoulder()
    {
        return shoulder;
    }

    public int upperArm()
    {
        return upperArm;
    }

    public int elbow()
    {
        return elbow;
    }

    /** @return The forearm label, which includes the wrist. */
    public int forearm()
    {
        return forearm;
    }

    public int hand()
    {
        return hand;
    }

    /** @return The model side, -1 for -x and 1 for +x. */
    public int side()
    {
        return side;
    }

    /** @return Every arm label, turned about the shoulder. */
    public int[] whole()
    {
        return new int[] {shoulder, upperArm, elbow, forearm, hand};
    }

    /** @return The labels below the elbow, turned about it. */
    public int[] belowElbow()
    {
        return new int[] {forearm, hand};
    }
}
