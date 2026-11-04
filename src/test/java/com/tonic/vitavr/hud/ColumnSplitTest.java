package com.tonic.vitavr.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests where the interface columns are cut.
 */
class ColumnSplitTest
{
    private static final int WIDTH = 200;
    private static final int HEIGHT = 40;

    @Test
    void theCutsGoThroughTheClearColumnsBesideTheSides()
    {
        int[] pixels = new int[WIDTH * HEIGHT];
        fill(pixels, 0, 52, 30, 40);
        fill(pixels, 150, 200, 0, 12);
        fill(pixels, 158, 200, 15, 40);
        fill(pixels, 120, 130, 2, 6);
        assertArrayEquals(new int[] {52, 150}, ColumnSplit.find(pixels, WIDTH, HEIGHT, 40, 80, 100, 175, 60, 170),
                "the chat side ends after the chatbox; the inventory side starts with the minimap, though the panel below starts further right");
        assertFalse(ColumnSplit.covered(pixels, WIDTH, HEIGHT, 40, 80, 100, 175), "both cuts have clear columns");

        fill(pixels, 0, WIDTH, 20, 22);
        assertArrayEquals(new int[] {60, 170}, ColumnSplit.find(pixels, WIDTH, HEIGHT, 40, 80, 100, 175, 60, 170), "no clear column: kept");
        assertTrue(ColumnSplit.covered(pixels, WIDTH, HEIGHT, 40, 80, 100, 175), "a full-screen interface lies across both");

        int[] chatCovered = new int[WIDTH * HEIGHT];
        fill(chatCovered, 30, 90, 10, 30);
        assertTrue(ColumnSplit.covered(chatCovered, WIDTH, HEIGHT, 40, 80, 100, 175), "one lying across the chat side's cut");
    }

    private static void fill(int[] pixels, int fromX, int toX, int fromY, int toY)
    {
        for (int y = fromY; y < toY; y++)
        {
            for (int x = fromX; x < toX; x++)
            {
                pixels[y * WIDTH + x] = 0xFF808080;
            }
        }
    }
}
