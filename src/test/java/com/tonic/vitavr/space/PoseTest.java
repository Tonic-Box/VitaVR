package com.tonic.vitavr.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PoseTest
{
    private static final double EPSILON = 1e-9;

    @Test
    void aQuarterTurnAboutUpInAQuarterSecondIsTwoPiRadiansASecond()
    {
        double[] out = new double[3];

        Pose.angularVelocity(Pose.ORIGIN, about(0, 1, 0, Math.PI / 2), 0.25, out);

        assertArrayEquals(new double[]{0, 2 * Math.PI, 0}, out, EPSILON);
    }

    @Test
    void theTurnIsMeasuredInTheRoomNotTheHand()
    {
        Pose start = about(0, 1, 0, Math.PI / 2);
        Pose rolled = multiply(about(1, 0, 0, 0.3), start);
        double[] out = new double[3];

        Pose.angularVelocity(start, rolled, 0.1, out);

        assertArrayEquals(new double[]{3, 0, 0}, out, EPSILON);
    }

    @Test
    void theSameOrientationWrittenNegatedIsNoTurn()
    {
        Pose a = about(0.3, 0.5, 0.8, 1.1);
        Pose negated = new Pose(0, 0, 0, -a.qx(), -a.qy(), -a.qz(), -a.qw());
        double[] out = new double[3];

        Pose.angularVelocity(a, negated, 0.01, out);

        assertArrayEquals(new double[]{0, 0, 0}, out, EPSILON);
    }

    private static Pose about(double x, double y, double z, double radians)
    {
        double length = Math.sqrt(x * x + y * y + z * z);
        double sine = Math.sin(radians / 2) / length;
        return new Pose(0, 0, 0, x * sine, y * sine, z * sine, Math.cos(radians / 2));
    }

    private static Pose multiply(Pose a, Pose b)
    {
        return new Pose(0, 0, 0,
                a.qw() * b.qx() + a.qx() * b.qw() + a.qy() * b.qz() - a.qz() * b.qy(),
                a.qw() * b.qy() - a.qx() * b.qz() + a.qy() * b.qw() + a.qz() * b.qx(),
                a.qw() * b.qz() + a.qx() * b.qy() - a.qy() * b.qx() + a.qz() * b.qw(),
                a.qw() * b.qw() - a.qx() * b.qx() - a.qy() * b.qy() - a.qz() * b.qz());
    }
}
