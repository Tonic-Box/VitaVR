package com.tonic.vitavr.hud;

import lombok.Getter;
import net.runelite.api.BufferProvider;

import java.util.Arrays;

/**
 * A copy of the interfaces the game drew this frame, clear where the world shows through.
 */
@Getter
public final class HudPicture
{
    /** Minimum alpha for the pointer to count as on an interface. */
    private static final int OPAQUE_ENOUGH = 24;

    private int width;
    private int height;
    private int[] pixels = new int[0];

    /**
     * Copies the interfaces the game drew this frame.
     *
     * @param buffer The game's buffer.
     */
    public void copy(BufferProvider buffer)
    {
        width = buffer.getWidth();
        height = buffer.getHeight();
        int[] from = buffer.getPixels();
        if (pixels.length != width * height)
        {
            pixels = new int[width * height];
        }
        System.arraycopy(from, 0, pixels, 0, pixels.length);
    }

    /**
     * Clears a rectangle of the picture.
     *
     * @param x The left edge.
     * @param y The top edge.
     * @param partWidth The width.
     * @param partHeight The height.
     */
    public void clear(int x, int y, int partWidth, int partHeight)
    {
        int fromX = Math.max(0, x);
        int toX = Math.min(width, x + partWidth);
        for (int row = Math.max(0, y); row < Math.min(height, y + partHeight); row++)
        {
            Arrays.fill(pixels, row * width + fromX, row * width + Math.max(fromX, toX), 0);
        }
    }

    /**
     * Checks whether an interface shows at a point.
     *
     * @param x The picture x.
     * @param y The picture y.
     * @return Whether the pixel is opaque enough to be an interface.
     */
    public boolean covers(int x, int y)
    {
        return x >= 0 && y >= 0 && x < width && y < height && pixels[y * width + x] >>> 24 >= OPAQUE_ENOUGH;
    }
}
