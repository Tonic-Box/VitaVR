package com.tonic.vitavr.gestures.vr;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Paints the few words shown by the hand when a gesture is made: light on dark, green edged when its binding ran and
 * red when it could not.
 */
public final class GestureToast
{
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Color BACKGROUND = new Color(24, 22, 18, 220);
    private static final Color TEXT = new Color(235, 226, 200);
    private static final Color DONE = new Color(110, 200, 110);
    private static final Color FAILED = new Color(220, 90, 80);
    private static final int PAD = 12;

    private final BufferedImage image;
    private final int[] pixels;

    /**
     * @param width  The picture's width in pixels.
     * @param height The picture's height in pixels.
     */
    public GestureToast(int width, int height)
    {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * @param text What to say.
     * @param done Whether it tells of a binding that ran.
     * @return The picture, premultiplied ARGB, top row first.
     */
    public int[] paint(String text, boolean done)
    {
        Arrays.fill(pixels, 0);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(FONT);
            FontMetrics metrics = g.getFontMetrics();
            String shown = text;
            int most = image.getWidth() - 2 * PAD;
            while (shown.length() > 1 && metrics.stringWidth(shown) > most)
            {
                shown = shown.substring(0, shown.length() - 2) + "…";
            }
            int boxWidth = Math.min(image.getWidth(), metrics.stringWidth(shown) + 2 * PAD);
            int boxHeight = metrics.getHeight() + PAD;
            int x = (image.getWidth() - boxWidth) / 2;
            int y = (image.getHeight() - boxHeight) / 2;
            g.setColor(BACKGROUND);
            g.fillRoundRect(x, y, boxWidth, boxHeight, 14, 14);
            g.setColor(done ? DONE : FAILED);
            g.drawRoundRect(x, y, boxWidth - 1, boxHeight - 1, 14, 14);
            g.setColor(TEXT);
            g.drawString(shown, x + PAD, y + PAD / 2 + metrics.getAscent());
        }
        finally
        {
            g.dispose();
        }
        return pixels;
    }
}
