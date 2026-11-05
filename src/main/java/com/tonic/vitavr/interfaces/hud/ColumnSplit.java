package com.tonic.vitavr.interfaces.hud;

/**
 * Finds clear pixel columns to cut the HUD picture into chat, middle and inventory columns.
 */
public final class ColumnSplit
{
    private static final int OPAQUE_ENOUGH = 24;

    private ColumnSplit()
    {
    }

    /**
     * Finds the cuts in a picture, keeping the current ones where no clear column is found.
     *
     * @param pixels The picture, premultiplied ARGB, rows from the top.
     * @param width The picture width.
     * @param height The picture height.
     * @param leftFrom The first column for the left cut.
     * @param leftTo The last column for the left cut.
     * @param rightFrom The first column for the right cut.
     * @param rightTo The last column for the right cut.
     * @param currentLeft The left cut to keep if none is found.
     * @param currentRight The right cut to keep if none is found.
     * @return The first column of the middle, then the first column of the inventory side.
     */
    public static int[] find(int[] pixels, int width, int height, int leftFrom, int leftTo, int rightFrom, int rightTo, int currentLeft, int currentRight)
    {
        int left = currentLeft;
        for (int column = leftFrom; column <= leftTo; column++)
        {
            if (clear(pixels, width, height, column))
            {
                left = column;
                break;
            }
        }
        int right = currentRight;
        for (int column = rightTo; column >= rightFrom; column--)
        {
            if (clear(pixels, width, height, column))
            {
                right = column + 1;
                break;
            }
        }
        return new int[] {left, right};
    }

    /**
     * Checks whether an interface spans a cut range, such as a full-screen interface.
     *
     * @param pixels The picture, premultiplied ARGB, rows from the top.
     * @param width The picture width.
     * @param height The picture height.
     * @param leftFrom The first column for the left cut.
     * @param leftTo The last column for the left cut.
     * @param rightFrom The first column for the right cut.
     * @param rightTo The last column for the right cut.
     * @return Whether either range has no clear column.
     */
    public static boolean covered(int[] pixels, int width, int height, int leftFrom, int leftTo, int rightFrom, int rightTo)
    {
        return !anyClear(pixels, width, height, leftFrom, leftTo) || !anyClear(pixels, width, height, rightFrom, rightTo);
    }

    private static boolean anyClear(int[] pixels, int width, int height, int from, int to)
    {
        for (int column = from; column <= to; column++)
        {
            if (clear(pixels, width, height, column))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean clear(int[] pixels, int width, int height, int column)
    {
        for (int row = 0; row < height; row++)
        {
            if (pixels[row * width + column] >>> 24 >= OPAQUE_ENOUGH)
            {
                return false;
            }
        }
        return true;
    }
}
