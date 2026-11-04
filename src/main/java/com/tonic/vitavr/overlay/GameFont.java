package com.tonic.vitavr.overlay;

import net.runelite.api.SpritePixels;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a game font with cached tinted glyphs.
 */
final class GameFont
{
    private static final int CHARACTERS = 256;
    private static final int MOST_TINTS = 4096;

    private final int[] advances;
    private final int baseline;
    private final SpritePixels[] glyphs;
    private final Map<Long, BufferedImage> tints = new HashMap<>();

    private GameFont(int[] advances, int baseline, SpritePixels[] glyphs)
    {
        this.advances = advances;
        this.baseline = baseline;
        this.glyphs = glyphs;
    }

    /**
     * Creates a font from its metrics and glyphs.
     * @param metrics The font file: an advance per character, then the baseline.
     * @param glyphs The glyph sprites.
     * @return The font, or null if the metrics have the wrong length.
     */
    static GameFont of(byte[] metrics, SpritePixels[] glyphs)
    {
        if (metrics.length != CHARACTERS + 1)
        {
            return null;
        }
        int[] advances = new int[CHARACTERS];
        for (int character = 0; character < CHARACTERS; character++)
        {
            advances[character] = metrics[character] & 0xFF;
        }
        return new GameFont(advances, metrics[CHARACTERS] & 0xFF, glyphs);
    }

    /**
     * @return The height of the baseline above the glyph origin.
     */
    int baseline()
    {
        return baseline;
    }

    /**
     * Gets the advance of a character.
     * @param character The character, 0 to 255.
     * @return How far the pen moves past it.
     */
    int advance(char character)
    {
        return character < CHARACTERS ? advances[character] : 0;
    }

    /**
     * Gets the drawn width of some text.
     * @param text The text.
     * @return The width in pixels.
     */
    int width(String text)
    {
        int width = 0;
        for (int i = 0; i < text.length(); i++)
        {
            width += advance(text.charAt(i));
        }
        return width;
    }

    /**
     * Draws text with its left edge at x and its baseline at y.
     * @param g The graphics to draw on.
     * @param text The text.
     * @param x The left edge.
     * @param y The baseline.
     * @param colour The colour as 0xRRGGBB.
     * @param shadow Whether to draw a black shadow one pixel down and right.
     */
    void draw(Graphics2D g, String text, int x, int y, int colour, boolean shadow)
    {
        int pen = x;
        for (int i = 0; i < text.length(); i++)
        {
            char character = text.charAt(i);
            glyph(g, character, pen, y, colour, shadow);
            pen += advance(character);
        }
    }

    /**
     * Draws one character with its left edge at penX and the baseline at y.
     */
    void glyph(Graphics2D g, char character, int penX, int y, int colour, boolean shadow)
    {
        if (character >= glyphs.length || glyphs[character] == null || glyphs[character].getWidth() <= 0)
        {
            return;
        }
        SpritePixels glyph = glyphs[character];
        int glyphX = penX + glyph.getOffsetX();
        int glyphY = y - baseline + glyph.getOffsetY();
        if (shadow)
        {
            g.drawImage(tinted(character, 0), glyphX + 1, glyphY + 1, null);
        }
        g.drawImage(tinted(character, colour), glyphX, glyphY, null);
    }

    private BufferedImage tinted(char character, int rgb)
    {
        long key = (long) character << 32 | rgb & 0xFFFFFFFFL;
        BufferedImage held = tints.get(key);
        if (held != null)
        {
            return held;
        }
        if (tints.size() >= MOST_TINTS)
        {
            tints.clear();
        }
        SpritePixels glyph = glyphs[character];
        int[] pixels = glyph.getPixels();
        BufferedImage image = new BufferedImage(glyph.getWidth(), glyph.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < glyph.getHeight(); y++)
        {
            for (int x = 0; x < glyph.getWidth(); x++)
            {
                if (pixels[y * glyph.getWidth() + x] != 0)
                {
                    image.setRGB(x, y, 0xFF000000 | rgb);
                }
            }
        }
        tints.put(key, image);
        return image;
    }
}
