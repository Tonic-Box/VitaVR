package com.tonic.vitavr.interfaces.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Base for pointer-driven VR panels drawn to an image, repainted only after a change.
 */
public abstract class AbstractPanel
{
    /** No target under the pointer. */
    protected static final int NONE = -1;

    private final BufferedImage image;
    private final int[] pixels;
    private static final Color RING = Color.WHITE;
    private static final Color RING_EDGE = new Color(0, 0, 0, 192);
    private static final int RING_RADIUS = 6;

    private int hovered = NONE;
    private int pointerX = -1;
    private int pointerY = -1;
    private boolean dirty = true;

    /**
     * Creates a panel of the given pixel size.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @throws IllegalArgumentException If a size is not positive.
     */
    protected AbstractPanel(int width, int height)
    {
        if (width < 1 || height < 1)
        {
            throw new IllegalArgumentException("width and height must be positive");
        }
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * Repaints the panel if anything changed since the last paint.
     * @return Whether it was painted.
     */
    public final boolean repaint()
    {
        if (!dirty)
        {
            return false;
        }
        dirty = false;
        Arrays.fill(pixels, 0);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paint(g);
            if (pointerX >= 0)
            {
                g.setStroke(new BasicStroke(4));
                g.setColor(RING_EDGE);
                g.drawOval(pointerX - RING_RADIUS, pointerY - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);
                g.setStroke(new BasicStroke(2));
                g.setColor(RING);
                g.drawOval(pointerX - RING_RADIUS, pointerY - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);
            }
        }
        finally
        {
            g.dispose();
        }
        return true;
    }

    /**
     * Moves the pointer over the panel.
     * @param x The pixel x.
     * @param y The pixel y.
     */
    public final void hover(int x, int y)
    {
        movePointer(x, y);
        lightUp(targetAt(x, y));
    }

    /** Removes the pointer from the panel. */
    public final void leave()
    {
        movePointer(-1, -1);
        lightUp(NONE);
    }

    private void movePointer(int x, int y)
    {
        if (x != pointerX || y != pointerY)
        {
            pointerX = x;
            pointerY = y;
            changed();
        }
    }

    /**
     * Presses the target at a point.
     * @param x The pixel x.
     * @param y The pixel y.
     */
    public final void press(int x, int y)
    {
        int target = targetAt(x, y);
        lightUp(target);
        if (target != NONE)
        {
            pressed(target);
            changed();
        }
    }

    private void lightUp(int target)
    {
        if (target != hovered)
        {
            hovered = target;
            changed();
        }
    }

    private int targetAt(int x, int y)
    {
        for (int target = 0; target < targets(); target++)
        {
            Rectangle bounds = bounds(target);
            if (bounds != null && bounds.contains(x, y))
            {
                return target;
            }
        }
        return NONE;
    }

    /** Marks the panel for repainting. */
    protected final void changed()
    {
        dirty = true;
    }

    /** @return The target under the pointer, or NONE. */
    protected final int hovered()
    {
        return hovered;
    }

    /** @return The number of targets, numbered from 0. */
    protected abstract int targets();

    /**
     * Gets a target's bounds.
     * @param target The target number.
     * @return The bounds, or null if it is not shown.
     */
    protected abstract Rectangle bounds(int target);

    /**
     * Handles a press on a target.
     * @param target The target number.
     */
    protected abstract void pressed(int target);

    /**
     * Paints the panel onto a cleared image.
     * @param g The image graphics.
     */
    protected abstract void paint(Graphics2D g);

    /** @return The pixels as premultiplied ARGB, top row first. */
    public final int[] pixels()
    {
        return pixels;
    }

    /** @return The width in pixels. */
    public final int width()
    {
        return image.getWidth();
    }

    /** @return The height in pixels. */
    public final int height()
    {
        return image.getHeight();
    }
}
