package com.tonic.vitavr.ui;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * A desktop window showing the composite VR picture live, for capturing in streaming software.
 * The render thread fills spare pictures and publishes them; the window paints the newest, never one being filled.
 */
public final class StreamWindow
{
    public static final String TITLE = "VitaVR stream";
    private static final int START_WIDTH = 1280;
    private static final int START_HEIGHT = 720;
    private static final int PICTURES = 3;

    private final Runnable onClosedByUser;
    private final BufferedImage[] pictures = new BufferedImage[PICTURES];
    private BufferedImage newest;
    private BufferedImage painting;
    private JFrame frame;
    private volatile boolean open;
    private volatile int width = START_WIDTH;
    private volatile int height = START_HEIGHT;

    /**
     * @param onClosedByUser Called on the Swing thread when the user closes the window.
     */
    public StreamWindow(Runnable onClosedByUser)
    {
        this.onClosedByUser = onClosedByUser;
    }

    /**
     * Opens the window, or brings it to the front. Must be called on the Swing thread.
     */
    public void open()
    {
        if (frame != null)
        {
            frame.toFront();
            return;
        }
        Screen screen = new Screen();
        frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setBackground(Color.BLACK);
        frame.add(screen);
        frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                close();
                onClosedByUser.run();
            }
        });
        screen.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                width = Math.max(1, screen.getWidth());
                height = Math.max(1, screen.getHeight());
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        open = true;
    }

    /**
     * Closes the window. Safe to call more than once. Must be called on the Swing thread.
     */
    public void close()
    {
        open = false;
        if (frame == null)
        {
            return;
        }
        frame.dispose();
        frame = null;
        synchronized (this)
        {
            newest = null;
            painting = null;
            for (int index = 0; index < PICTURES; index++)
            {
                pictures[index] = null;
            }
        }
    }

    /** @return Whether the window is open and wants pictures; safe on any thread. */
    public boolean isOpen()
    {
        return open;
    }

    /** @return The width the window shows pictures at, in pixels; safe on any thread. */
    public int pictureWidth()
    {
        return width;
    }

    /** @return The height the window shows pictures at, in pixels; safe on any thread. */
    public int pictureHeight()
    {
        return height;
    }

    /**
     * Gets a picture that is neither shown nor waiting to be, for the render thread to fill.
     *
     * @param pictureWidth The picture width, in pixels.
     * @param pictureHeight The picture height, in pixels.
     * @return A picture of that size, RGB in ints, or null if none is free and the frame should be skipped.
     */
    public synchronized BufferedImage spare(int pictureWidth, int pictureHeight)
    {
        for (int index = 0; index < PICTURES; index++)
        {
            BufferedImage picture = pictures[index];
            if (picture != null && (picture == newest || picture == painting))
            {
                continue;
            }
            if (picture == null || picture.getWidth() != pictureWidth || picture.getHeight() != pictureHeight)
            {
                picture = new BufferedImage(pictureWidth, pictureHeight, BufferedImage.TYPE_INT_RGB);
                pictures[index] = picture;
            }
            return picture;
        }
        return null;
    }

    /**
     * Shows a filled picture next.
     *
     * @param picture The picture from spare.
     */
    public void publish(BufferedImage picture)
    {
        JFrame shown;
        synchronized (this)
        {
            newest = picture;
            shown = frame;
        }
        if (shown != null)
        {
            shown.repaint();
        }
    }

    /**
     * Paints the newest picture centred on black.
     */
    private final class Screen extends JComponent
    {
        private Screen()
        {
            setPreferredSize(new Dimension(START_WIDTH, START_HEIGHT));
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            BufferedImage picture;
            synchronized (StreamWindow.this)
            {
                painting = newest;
                picture = painting;
            }
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (picture != null)
            {
                g.drawImage(picture, (getWidth() - picture.getWidth()) / 2, (getHeight() - picture.getHeight()) / 2, null);
            }
        }
    }
}
