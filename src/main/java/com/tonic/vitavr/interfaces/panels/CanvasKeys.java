package com.tonic.vitavr.interfaces.panels;

import net.runelite.api.Client;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import javax.swing.SwingUtilities;

/**
 * Sends synthetic key events to the game canvas on the Swing thread.
 */
public final class CanvasKeys
{
    private final Client client;
    private final Set<Integer> down = new HashSet<>();

    /**
     * @param client The client.
     */
    public CanvasKeys(Client client)
    {
        this.client = client;
    }

    /**
     * Types a character as press, typed and release events.
     *
     * @param character The character to type.
     */
    public void type(char character)
    {
        int code = KeyEvent.getExtendedKeyCodeForChar(character);
        send(KeyEvent.KEY_PRESSED, code, character);
        send(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, character);
        send(KeyEvent.KEY_RELEASED, code, character);
    }

    /**
     * Presses a key.
     *
     * @param code The key code.
     */
    public void press(int code)
    {
        down.add(code);
        send(KeyEvent.KEY_PRESSED, code, KeyEvent.CHAR_UNDEFINED);
    }

    /**
     * Releases a key if it is down.
     *
     * @param code The key code.
     */
    public void release(int code)
    {
        if (down.remove(code))
        {
            send(KeyEvent.KEY_RELEASED, code, KeyEvent.CHAR_UNDEFINED);
        }
    }

    /**
     * Releases every key still down.
     */
    public void releaseAll()
    {
        for (int code : new HashSet<>(down))
        {
            release(code);
        }
    }

    private void send(int id, int code, char character)
    {
        Canvas canvas = client.getCanvas();
        if (canvas == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() -> canvas.dispatchEvent(new KeyEvent(canvas, id, System.currentTimeMillis(), 0, code, character)));
    }
}
