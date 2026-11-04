package com.tonic.vitavr.wrist;

import net.runelite.api.gameval.InterfaceID;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.time.LocalTime;
import java.util.function.IntFunction;

/**
 * The wrist image: the orbs and compass cut from the interfaces and enlarged, with the time of day.
 */
public final class WristPicture
{
    public static final int WIDTH = 360;
    public static final int HEIGHT = 200;
    private static final int PAD = 8;
    private static final int ORB_CELL_WIDTH = 116;
    private static final int ORB_CELL_HEIGHT = 84;
    private static final int COMPASS_CELL = 96;
    private static final int COMPASS_LEFT = PAD + 2 * ORB_CELL_WIDTH + 2 * PAD;
    private static final int CLOCK_TOP = PAD + COMPASS_CELL + PAD;
    private static final int CORNER = 18;
    private static final Color PLATE = new Color(16, 14, 12, 200);
    private static final Color EDGE = new Color(90, 80, 60, 230);
    private static final Color CLOCK = new Color(255, 152, 31);
    private static final Font CLOCK_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 34);

    /** Each orb's group and button id on every orb layout, in display order. */
    private static final int[][] HEALTH = {{InterfaceID.Orbs.ORB_HEALTH, InterfaceID.Orbs.HEALTHBUTTON}, {InterfaceID.OrbsNomap.ORB_HEALTH, InterfaceID.OrbsNomap.HEALTHBUTTON},
            {InterfaceID.OrbsOsm.ORB_HEALTH, InterfaceID.OrbsOsm.HEALTHBUTTON}, {InterfaceID.OrbsOsmNomap.ORB_HEALTH, InterfaceID.OrbsOsmNomap.HEALTHBUTTON}};
    private static final int[][] PRAYER = {{InterfaceID.Orbs.ORB_PRAYER, InterfaceID.Orbs.PRAYERBUTTON}, {InterfaceID.OrbsNomap.ORB_PRAYER, InterfaceID.OrbsNomap.PRAYERBUTTON},
            {InterfaceID.OrbsOsm.ORB_PRAYER, InterfaceID.OrbsOsm.PRAYERBUTTON}, {InterfaceID.OrbsOsmNomap.ORB_PRAYER, InterfaceID.OrbsOsmNomap.PRAYERBUTTON}};
    private static final int[][] RUN = {{InterfaceID.Orbs.ORB_RUNENERGY, InterfaceID.Orbs.RUNBUTTON}, {InterfaceID.OrbsNomap.ORB_RUNENERGY, InterfaceID.OrbsNomap.RUNBUTTON},
            {InterfaceID.OrbsOsm.ORB_RUNENERGY, InterfaceID.OrbsOsm.RUNBUTTON}, {InterfaceID.OrbsOsmNomap.ORB_RUNENERGY, InterfaceID.OrbsOsmNomap.RUNBUTTON}};
    private static final int[][] SPECIAL = {{InterfaceID.Orbs.ORB_SPECENERGY, InterfaceID.Orbs.SPECBUTTON}, {InterfaceID.OrbsNomap.ORB_SPECENERGY, InterfaceID.OrbsNomap.SPECBUTTON},
            {InterfaceID.OrbsOsm.ORB_SPECENERGY, InterfaceID.OrbsOsm.SPECBUTTON}, {InterfaceID.OrbsOsmNomap.ORB_SPECENERGY, InterfaceID.OrbsOsmNomap.SPECBUTTON}};
    private static final int[][][] ORBS = {HEALTH, PRAYER, RUN, SPECIAL};
    /** The compass id on every top level that has one. */
    private static final int[] COMPASSES = {InterfaceID.ToplevelOsrsStretch.COMPASSCLICK, InterfaceID.ToplevelPreEoc.COMPASSCLICK, InterfaceID.Toplevel.COMPASSCLICK,
            InterfaceID.ToplevelOsm.COMPASSCLICK, InterfaceID.OrbsOsmNomap.COMPASSCLICK};

    private final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB_PRE);
    private final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    private final int[][] cells = new int[ORBS.length][];
    private final int[][] buttons = new int[ORBS.length][];

    /**
     * Repaints the image from the interfaces image.
     * @param interfaces The interfaces pixels as premultiplied ARGB, top row first.
     * @param width The interfaces width.
     * @param height The interfaces height.
     * @param boxes A component's drawn {x, y, width, height}, or null if not drawn.
     * @param time The time of day shown.
     * @throws IllegalArgumentException If interfaces, boxes or time is null.
     */
    public void paint(int[] interfaces, int width, int height, IntFunction<int[]> boxes, LocalTime time)
    {
        if (interfaces == null || boxes == null || time == null)
        {
            throw new IllegalArgumentException("interfaces, boxes and time cannot be null");
        }
        Graphics2D g = image.createGraphics();
        try
        {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PLATE);
            g.fillRoundRect(0, 0, WIDTH, HEIGHT, CORNER, CORNER);
            g.setColor(EDGE);
            g.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, CORNER, CORNER);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int orb = 0; orb < ORBS.length; orb++)
            {
                cells[orb] = null;
                buttons[orb] = null;
                for (int[] layout : ORBS[orb])
                {
                    int[] group = boxes.apply(layout[0]);
                    if (group == null)
                    {
                        continue;
                    }
                    int cellX = PAD + orb % 2 * (ORB_CELL_WIDTH + PAD);
                    int cellY = PAD + orb / 2 * (ORB_CELL_HEIGHT + PAD);
                    cells[orb] = enlarge(g, interfaces, width, height, group, cellX, cellY, ORB_CELL_WIDTH, ORB_CELL_HEIGHT);
                    buttons[orb] = boxes.apply(layout[1]);
                    break;
                }
            }
            for (int compass : COMPASSES)
            {
                int[] box = boxes.apply(compass);
                if (box != null)
                {
                    enlarge(g, interfaces, width, height, box, COMPASS_LEFT, PAD, COMPASS_CELL, COMPASS_CELL);
                    break;
                }
            }
            drawClock(g, time);
        }
        finally
        {
            g.dispose();
        }
    }

    /**
     * Draws a piece of the interfaces centred in a cell, scaled by whole pixels or shrunk to fit.
     * @return The drawn {x, y, width, height}, or null if the piece is off the interfaces image.
     */
    private static int[] enlarge(Graphics2D g, int[] interfaces, int width, int height, int[] box, int cellX, int cellY, int cellWidth, int cellHeight)
    {
        int fromX = Math.max(0, box[0]);
        int fromY = Math.max(0, box[1]);
        int toX = Math.min(width, box[0] + box[2]);
        int toY = Math.min(height, box[1] + box[3]);
        if (toX <= fromX || toY <= fromY)
        {
            return null;
        }
        int pieceWidth = toX - fromX;
        int pieceHeight = toY - fromY;
        BufferedImage piece = new BufferedImage(pieceWidth, pieceHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] into = ((DataBufferInt) piece.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < pieceHeight; row++)
        {
            System.arraycopy(interfaces, (fromY + row) * width + fromX, into, row * pieceWidth, pieceWidth);
        }
        double fit = Math.min((double) cellWidth / pieceWidth, (double) cellHeight / pieceHeight);
        double scale = fit >= 1 ? Math.floor(fit) : fit;
        int drawnWidth = Math.max(1, (int) (pieceWidth * scale));
        int drawnHeight = Math.max(1, (int) (pieceHeight * scale));
        int x = cellX + (cellWidth - drawnWidth) / 2;
        int y = cellY + (cellHeight - drawnHeight) / 2;
        g.drawImage(piece, x, y, drawnWidth, drawnHeight, null);
        return new int[] {x, y, drawnWidth, drawnHeight};
    }

    private static void drawClock(Graphics2D g, LocalTime time)
    {
        String text = String.format("%02d:%02d", time.getHour(), time.getMinute());
        g.setFont(CLOCK_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int x = COMPASS_LEFT + (COMPASS_CELL - metrics.stringWidth(text)) / 2;
        int y = CLOCK_TOP + (HEIGHT - PAD - CLOCK_TOP + metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(Color.BLACK);
        g.drawString(text, x + 2, y + 2);
        g.setColor(CLOCK);
        g.drawString(text, x, y);
    }

    /**
     * Gets the interfaces point a press goes to: the centre of the orb button under it.
     * @param x The wrist image x.
     * @param y The wrist image y.
     * @return The interfaces pixel, or null if no orb button is under it.
     */
    public int[] pressAt(int x, int y)
    {
        for (int orb = 0; orb < ORBS.length; orb++)
        {
            int[] cell = cells[orb];
            int[] button = buttons[orb];
            if (cell != null && button != null && x >= cell[0] && y >= cell[1] && x < cell[0] + cell[2] && y < cell[1] + cell[3])
            {
                return new int[] {button[0] + button[2] / 2, button[1] + button[3] / 2};
            }
        }
        return null;
    }

    /** @return The live pixel array as premultiplied ARGB, top row first. */
    public int[] pixels()
    {
        return pixels;
    }
}
