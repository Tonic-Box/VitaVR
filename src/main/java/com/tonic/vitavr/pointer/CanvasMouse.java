package com.tonic.vitavr.pointer;

import net.runelite.api.Client;

import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Sends synthetic mouse events to the game canvas on the Swing thread.
 */
public final class CanvasMouse
{
    private final Client client;
    private int x = -1;
    private int y = -1;
    private int buttonsDown;

    /**
     * @param client The client.
     */
    public CanvasMouse(Client client)
    {
        this.client = client;
    }

    /**
     * Moves the mouse on the canvas, dragging while a button is down.
     *
     * @param canvasX The canvas x, in pixels.
     * @param canvasY The canvas y, in pixels.
     */
    public void moveTo(int canvasX, int canvasY)
    {
        if (canvasX == x && canvasY == y)
        {
            return;
        }
        x = canvasX;
        y = canvasY;
        if (buttonsDown != 0)
        {
            send(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, buttonsDown, 1, false);
            return;
        }
        send(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, 0, 0, false);
    }

    /**
     * Moves the mouse to the centre of the game viewport.
     */
    public void moveToViewport()
    {
        moveTo(client.getViewportXOffset() + client.getViewportWidth() / 2, client.getViewportYOffset() + client.getViewportHeight() / 2);
    }

    /**
     * Presses a mouse button.
     *
     * @param right Whether it is the right button.
     * @param ctrl Whether ctrl is held.
     */
    public void press(boolean right, boolean ctrl)
    {
        buttonsDown |= right ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        send(MouseEvent.MOUSE_PRESSED, button(right), buttonsDown | ctrl(ctrl), 1, right);
    }

    /**
     * Releases a mouse button and sends a click.
     *
     * @param right Whether it is the right button.
     * @param ctrl Whether ctrl is held.
     */
    public void release(boolean right, boolean ctrl)
    {
        buttonsDown &= ~(right ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK);
        send(MouseEvent.MOUSE_RELEASED, button(right), buttonsDown | ctrl(ctrl), 1, false);
        send(MouseEvent.MOUSE_CLICKED, button(right), buttonsDown | ctrl(ctrl), 1, false);
    }

    /**
     * Scrolls the mouse wheel.
     *
     * @param notches The notches, positive towards the user.
     */
    public void wheel(int notches)
    {
        Canvas canvas = client.getCanvas();
        if (canvas == null || x < 0)
        {
            return;
        }
        int atX = x;
        int atY = y;
        SwingUtilities.invokeLater(() -> canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, atX, atY,
                0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, notches)));
    }

    private static int button(boolean right)
    {
        return right ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
    }

    private static int ctrl(boolean ctrl)
    {
        return ctrl ? InputEvent.CTRL_DOWN_MASK : 0;
    }

    private void send(int id, int button, int modifiers, int clicks, boolean popup)
    {
        Canvas canvas = client.getCanvas();
        if (canvas == null || x < 0)
        {
            return;
        }
        int atX = x;
        int atY = y;
        SwingUtilities.invokeLater(() -> canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, atX, atY, clicks, popup, button)));
    }
}
