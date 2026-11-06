package com.tonic.vitavr.interfaces.hud;

import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HudLayoutTest
{
    private static final double[] EYE = {0, 1.6, 0};
    private static final int WIDTH = 500;
    private static final int HEIGHT = 160;

    @Test
    void aPieceStandsOnItsPointFacingTheEyes()
    {
        HudLayout layout = framed();
        double[] point = {0.5, 1.5, -1.8};

        layout.standAt(HudPiece.CHAT, point, 0.0015);
        Billboard board = new Billboard();
        layout.lay(HudPiece.CHAT, WIDTH, HEIGHT, board);

        assertArrayEquals(point, board.centre(), 1e-9);
        assertEquals(1, board.facing(EYE[0], EYE[1], EYE[2]), 1e-9);
        assertEquals(WIDTH * 0.0015, board.metresWide(), 1e-9);
    }

    @Test
    void draggingKeepsTheGrabbedPixelOnThePointer()
    {
        HudLayout layout = framed();
        layout.standAt(HudPiece.CHAT, new double[]{0.5, 1.5, -1.8}, 0.0015);
        Pose aim = aimed(new double[]{0.25, 1.3, -0.2}, new double[]{-0.6, 1.9, -1.5});

        layout.drag(HudPiece.CHAT, aim, WIDTH, HEIGHT, 120, 40);
        Billboard board = new Billboard();
        layout.lay(HudPiece.CHAT, WIDTH, HEIGHT, board);
        int[] at = board.crossing(aim);

        assertNotNull(at);
        assertEquals(120, at[0], 1);
        assertEquals(40, at[1], 1);
    }

    @Test
    void draggingATurnedAndTiltedPieceKeepsTheGrabbedPixelOnThePointer()
    {
        HudLayout layout = framed();
        layout.standAt(HudPiece.INVENTORY, new double[]{0.5, 1.5, -1.8}, 0.0015);
        layout.angle(HudPiece.INVENTORY, Math.toRadians(35), Math.toRadians(-25));
        Pose aim = aimed(new double[]{0.25, 1.3, -0.2}, new double[]{-0.6, 1.9, -1.5});

        layout.drag(HudPiece.INVENTORY, aim, WIDTH, HEIGHT, 400, 130);
        Billboard board = new Billboard();
        layout.lay(HudPiece.INVENTORY, WIDTH, HEIGHT, board);
        int[] at = board.crossing(aim);

        assertNotNull(at);
        assertEquals(400, at[0], 1);
        assertEquals(130, at[1], 1);
    }

    @Test
    void pushingAPieceAwayKeepsItsSizeInTheRoom()
    {
        HudLayout layout = framed();
        layout.standAt(HudPiece.MINIMAP, new double[]{0, 1.6, -1.5}, 0.002);
        Billboard before = new Billboard();
        layout.lay(HudPiece.MINIMAP, WIDTH, HEIGHT, before);

        layout.pushPull(HudPiece.MINIMAP, 0.7);
        Billboard after = new Billboard();
        layout.lay(HudPiece.MINIMAP, WIDTH, HEIGHT, after);

        assertEquals(before.metresWide(), after.metresWide(), 1e-9);
        assertEquals(2.2, distance(after.centre()), 1e-9);
    }

    @Test
    void theLayoutReadsBackWhatItSavesAndSkipsWhatItCannotRead()
    {
        HudLayout layout = framed();
        layout.standAt(HudPiece.CHAT, new double[]{-0.8, 1.2, -1.4}, 0.0015);
        layout.standAt(HudPiece.INVENTORY, new double[]{0.9, 1.7, -1.1}, 0.002);

        HudLayout read = new HudLayout();
        read.load(layout.format());
        HudLayout broken = new HudLayout();
        broken.load("chat=1,2;bogus=1,2,3,4;minimap=a,b,c,d;=;inventory");

        assertEquals(layout.format(), read.format());
        assertEquals(0, broken.movedCount());
    }

    private static HudLayout framed()
    {
        HudLayout layout = new HudLayout();
        layout.frame(EYE, 0.3, 0.0008);
        return layout;
    }

    private static double distance(double[] point)
    {
        return Math.sqrt(Math.pow(point[0] - EYE[0], 2) + Math.pow(point[1] - EYE[1], 2) + Math.pow(point[2] - EYE[2], 2));
    }

    /**
     * Gets a pose at a point facing towards another, rotating -z onto the direction.
     */
    private static Pose aimed(double[] from, double[] towards)
    {
        double[] d = {towards[0] - from[0], towards[1] - from[1], towards[2] - from[2]};
        double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double[] unit = {d[0] / length, d[1] / length, d[2] / length};
        double[] axis = {-unit[1], unit[0], 0};
        double cosine = -unit[2];
        double half = Math.sqrt((1 + cosine) / 2);
        double sineScale = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1]);
        double sine = Math.sqrt((1 - cosine) / 2);
        return new Pose(from[0], from[1], from[2], axis[0] / sineScale * sine, axis[1] / sineScale * sine, 0, half);
    }
}
