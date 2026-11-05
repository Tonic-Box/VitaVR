package com.tonic.vitavr.interfaces.pointer;

import lombok.Getter;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The hover text for the pointer: the top menu option, its target in game colours, and the count of other options.
 * Repainted only when it changes.
 */
public final class PointerLabel
{
    private static final Color BOX = new Color(0, 0, 0, 160);
    private static final Color MORE = new Color(0xC8C8C8);
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final int PAD = 5;
    private static final Pattern COLOUR = Pattern.compile("<col=([0-9a-fA-F]{1,6})>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private final BufferedImage image;
    @Getter
    private final int[] pixels;
    private String shown = "";
    @Getter
    private boolean empty = true;

    /**
     * @param width The picture width, in pixels.
     * @param height The picture height, in pixels.
     */
    public PointerLabel(int width, int height)
    {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * Repaints the label from the game's menu if it changed.
     *
     * @param entries The game's menu entries, top entry last.
     * @return Whether the picture changed.
     */
    public boolean update(MenuEntry[] entries)
    {
        MenuEntry top = entries.length == 0 || entries[entries.length - 1].getType() == MenuAction.CANCEL ? null : entries[entries.length - 1];
        int more = entries.length - 2;
        String key = top == null ? "" : top.getOption() + "|" + top.getTarget() + "|" + more;
        if (key.equals(shown))
        {
            return false;
        }
        shown = key;
        Arrays.fill(pixels, 0);
        empty = top == null;
        if (!empty)
        {
            paint(top, more);
        }
        return true;
    }

    private void paint(MenuEntry top, int more)
    {
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(FONT);
            FontMetrics metrics = g.getFontMetrics();
            int lineHeight = metrics.getHeight();

            // Split the target into its colour runs
            String[] parts = COLOUR.split(top.getTarget() == null ? "" : top.getTarget(), -1);
            Matcher colours = COLOUR.matcher(top.getTarget() == null ? "" : top.getTarget());
            String option = strip(top.getOption());
            String moreText = more > 0 ? more + " more options" : null;
            int width = metrics.stringWidth(option);
            StringBuilder plain = new StringBuilder();
            for (String part : parts)
            {
                plain.append(strip(part));
            }
            int firstWidth = width + (plain.length() == 0 ? 0 : metrics.stringWidth(" " + plain));
            box(g, Math.max(firstWidth, moreText == null ? 0 : metrics.stringWidth(moreText)), lineHeight * (moreText == null ? 1 : 2));

            int baseline = PAD + metrics.getAscent();
            shadowed(g, option, Color.WHITE, PAD, baseline);
            int x = PAD + width + (plain.length() == 0 ? 0 : metrics.stringWidth(" "));
            Color colour = Color.WHITE;
            for (String part : parts)
            {
                String text = strip(part);
                shadowed(g, text, colour, x, baseline);
                x += metrics.stringWidth(text);
                if (colours.find())
                {
                    colour = new Color(Integer.parseInt(colours.group(1), 16));
                }
            }
            if (moreText != null)
            {
                shadowed(g, moreText, MORE, PAD, baseline + lineHeight);
            }
        }
        finally
        {
            g.dispose();
        }
    }

    private static String strip(String text)
    {
        return text == null ? "" : TAG.matcher(text).replaceAll("");
    }

    private void box(Graphics2D g, int textWidth, int textHeight)
    {
        g.setColor(BOX);
        g.fillRect(0, 0, Math.min(image.getWidth(), textWidth + 2 * PAD), Math.min(image.getHeight(), textHeight + 2 * PAD));
    }

    private static void shadowed(Graphics2D g, String text, Color colour, int x, int y)
    {
        g.setColor(Color.BLACK);
        g.drawString(text, x + 1, y + 1);
        g.setColor(colour);
        g.drawString(text, x, y);
    }

    /**
     * @return The picture width.
     */
    public int width()
    {
        return image.getWidth();
    }

    /**
     * @return The picture height.
     */
    public int height()
    {
        return image.getHeight();
    }
}
