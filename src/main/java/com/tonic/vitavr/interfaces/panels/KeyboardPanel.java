package com.tonic.vitavr.interfaces.panels;


import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * An on-screen keyboard that types into the game. Shift applies to the next key; Hide asks to close it.
 */
public final class KeyboardPanel extends AbstractPanel
{
    private static final int UNIT = 62;
    private static final int GAP = 6;
    private static final int PAD = 14;
    private static final int ROWS = 5;
    public static final int WIDTH = PAD * 2 + 15 * UNIT;
    public static final int HEIGHT = PAD * 2 + ROWS * UNIT;

    private static final int TYPES = 0;
    private static final int SHIFT = 1;
    private static final int HIDE = 2;
    private static final int TAPS = 3;
    private static final String NORMAL = "`1234567890-=qwertyuiop[]asdfghjkl;'zxcvbnm,./";
    private static final String SHIFTED = "~!@#$%^&*()_+QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?";
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
    private static final Font WORD_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
    private static final Color BACKGROUND = new Color(24, 22, 18, 235);
    private static final Color KEY = new Color(62, 56, 44);
    private static final Color LIT = new Color(110, 96, 62);
    private static final Color HELD = new Color(150, 120, 60);
    private static final Color TEXT = new Color(235, 226, 200);

    /** A key's label, action and bounds. */
    private static final class Key
    {
        final String label;
        final int kind;
        final char normal;
        final char shifted;
        final int code;
        final Rectangle bounds;

        Key(String label, int kind, char normal, char shifted, int code, Rectangle bounds)
        {
            this.label = label;
            this.kind = kind;
            this.normal = normal;
            this.shifted = shifted;
            this.code = code;
            this.bounds = bounds;
        }
    }

    private final CanvasKeys keys;
    private final List<Key> layout = new ArrayList<>();
    private boolean shift;
    private boolean hideAsked;

    /**
     * Creates the keyboard.
     * @param keys The canvas the keys are sent to.
     */
    public KeyboardPanel(CanvasKeys keys)
    {
        super(WIDTH, HEIGHT);
        this.keys = keys;
        int at = 0;
        characters(0, 0, at, 13);
        tap(0, 13, 2, "Back", KeyEvent.VK_BACK_SPACE);
        at += 13;
        characters(1, 1.5, at, 12);
        at += 12;
        characters(2, 1.75, at, 11);
        tap(2, 1.75 + 11, 2.25, "Enter", KeyEvent.VK_ENTER);
        at += 11;
        add(3, 0, 2.25, "Shift", SHIFT, (char) 0, (char) 0, 0);
        characters(3, 2.25, at, 10);
        add(3, 12.25, 2.75, "Shift", SHIFT, (char) 0, (char) 0, 0);
        tap(4, 0, 2, "Esc", KeyEvent.VK_ESCAPE);
        add(4, 2.5, 9, "", TYPES, ' ', ' ', 0);
        add(4, 12, 3, "Hide", HIDE, (char) 0, (char) 0, 0);
    }

    private void characters(int row, double from, int first, int count)
    {
        for (int index = 0; index < count; index++)
        {
            char normal = NORMAL.charAt(first + index);
            add(row, from + index, 1, String.valueOf(normal), TYPES, normal, SHIFTED.charAt(first + index), 0);
        }
    }

    private void tap(int row, double from, double units, String label, int code)
    {
        add(row, from, units, label, TAPS, (char) 0, (char) 0, code);
    }

    private void add(int row, double from, double units, String label, int kind, char normal, char shifted, int code)
    {
        Rectangle bounds = new Rectangle(PAD + (int) Math.round(from * UNIT) + GAP / 2, PAD + row * UNIT + GAP / 2, (int) Math.round(units * UNIT) - GAP, UNIT - GAP);
        layout.add(new Key(label, kind, normal, shifted, code, bounds));
    }

    /**
     * Checks and clears whether Hide was pressed since the last call.
     * @return Whether Hide was pressed.
     */
    public boolean takeHideAsked()
    {
        boolean asked = hideAsked;
        hideAsked = false;
        return asked;
    }

    @Override
    protected int targets()
    {
        return layout.size();
    }

    @Override
    protected Rectangle bounds(int target)
    {
        return layout.get(target).bounds;
    }

    @Override
    protected void pressed(int target)
    {
        Key key = layout.get(target);
        switch (key.kind)
        {
            case SHIFT:
                shift = !shift;
                break;
            case HIDE:
                hideAsked = true;
                break;
            case TAPS:
                keys.press(key.code);
                keys.release(key.code);
                break;
            default:
                keys.type(shift ? key.shifted : key.normal);
                shift = false;
                break;
        }
    }

    @Override
    protected void paint(Graphics2D g)
    {
        g.setColor(BACKGROUND);
        g.fillRoundRect(0, 0, WIDTH, HEIGHT, 18, 18);
        for (int target = 0; target < layout.size(); target++)
        {
            Key key = layout.get(target);
            Rectangle bounds = key.bounds;
            g.setColor(key.kind == SHIFT && shift ? HELD : target == hovered() ? LIT : KEY);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
            String label = key.kind == TYPES && shift ? String.valueOf(key.shifted) : key.label;
            g.setFont(label.length() > 1 ? WORD_FONT : FONT);
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(TEXT);
            g.drawString(label, bounds.x + (bounds.width - metrics.stringWidth(label)) / 2, bounds.y + (bounds.height + metrics.getAscent()) / 2 - 3);
        }
    }
}
