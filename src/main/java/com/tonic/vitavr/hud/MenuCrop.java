package com.tonic.vitavr.hud;

import lombok.Getter;

import java.util.Arrays;

/**
 * A menu cropped from the HUD picture into its own picture, from the top left.
 */
public final class MenuCrop
{
    private final int width;
    private final int height;
    private final int[] pixels;
    @Getter
    private int cropWidth;
    @Getter
    private int cropHeight;

    /**
     * @param width The picture width, the widest menu it holds.
     * @param height The picture height, the tallest menu it holds.
     */
    public MenuCrop(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    /**
     * @return The cropped menu, ARGB, rows from the top.
     */
    public int[] getPixels()
    {
        return pixels;
    }

    /**
     * Crops the menu from the HUD picture, up to this picture's size.
     *
     * @param frame The HUD picture, ARGB, rows from the top.
     * @param frameWidth The HUD picture width.
     * @param frameHeight The HUD picture height.
     * @param x The menu's left edge.
     * @param y The menu's top edge.
     * @param menuWidth The menu width.
     * @param menuHeight The menu height.
     */
    public void lift(int[] frame, int frameWidth, int frameHeight, int x, int y, int menuWidth, int menuHeight)
    {
        Arrays.fill(pixels, 0);
        int fromX = Math.max(0, x);
        int fromY = Math.max(0, y);
        int toX = Math.min(frameWidth, x + menuWidth);
        int toY = Math.min(frameHeight, y + menuHeight);
        cropWidth = Math.max(0, Math.min(width, toX - fromX));
        cropHeight = Math.max(0, Math.min(height, toY - fromY));
        // Copy the menu fully opaque
        for (int row = 0; row < cropHeight; row++)
        {
            int from = (fromY + row) * frameWidth + fromX;
            int to = row * width;
            for (int column = 0; column < cropWidth; column++)
            {
                pixels[to + column] = frame[from + column] | 0xFF000000;
            }
        }
    }
}
