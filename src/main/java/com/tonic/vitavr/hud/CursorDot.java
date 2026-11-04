package com.tonic.vitavr.hud;

/**
 * A cursor dot painted onto a layer's picture, saving the covered pixels so it can be removed.
 */
public final class CursorDot
{
    private static final int RADIUS = 4;
    private static final int EDGE = 6;
    private static final int DOT = 0xFFFFFFFF;
    private static final int DOT_EDGE = 0xC0000000;

    private final int[] keptAt = new int[(2 * EDGE + 1) * (2 * EDGE + 1)];
    private final int[] kept = new int[keptAt.length];
    private int keptCount;

    /**
     * Paints the dot onto a picture, saving the covered pixels.
     *
     * @param pixels The picture, ARGB, rows from the top.
     * @param width The picture width.
     * @param height The picture height.
     * @param x The dot centre x.
     * @param y The dot centre y.
     */
    public void paint(int[] pixels, int width, int height, int x, int y)
    {
        keptCount = 0;
        for (int dy = -EDGE; dy <= EDGE; dy++)
        {
            for (int dx = -EDGE; dx <= EDGE; dx++)
            {
                int far = dx * dx + dy * dy;
                int px = x + dx;
                int py = y + dy;
                if (far > EDGE * EDGE || px < 0 || py < 0 || px >= width || py >= height)
                {
                    continue;
                }
                int at = py * width + px;
                keptAt[keptCount] = at;
                kept[keptCount++] = pixels[at];
                pixels[at] = far <= RADIUS * RADIUS ? DOT : over(DOT_EDGE, pixels[at]);
            }
        }
    }

    /**
     * Restores the pixels covered by the last paint.
     *
     * @param pixels The picture.
     */
    public void restore(int[] pixels)
    {
        for (int i = 0; i < keptCount; i++)
        {
            pixels[keptAt[i]] = kept[i];
        }
        keptCount = 0;
    }

    // Alpha-blend top over under
    private static int over(int top, int under)
    {
        int alpha = top >>> 24;
        int underAlpha = under >>> 24;
        int outAlpha = alpha + underAlpha * (255 - alpha) / 255;
        int red = ((top >> 16 & 0xFF) * alpha + (under >> 16 & 0xFF) * (255 - alpha)) / 255;
        int green = ((top >> 8 & 0xFF) * alpha + (under >> 8 & 0xFF) * (255 - alpha)) / 255;
        int blue = ((top & 0xFF) * alpha + (under & 0xFF) * (255 - alpha)) / 255;
        return outAlpha << 24 | red << 16 | green << 8 | blue;
    }
}
