package com.tonic.vitavr.ui.filechooser;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A flat button that paints its own fill so the chooser's colours hold under any look and feel: an idle
 * colour, a hover colour, and a darker press.
 */
final class ChooserButton extends JButton
{
    private final Color idle;
    private final Color hover;
    private boolean over;

    /** An accent-filled button for the dialog's primary action. */
    static ChooserButton primary(String text)
    {
        ChooserButton button = new ChooserButton(text, ChooserStyle.ACCENT, ChooserStyle.ACCENT_BRIGHT, null);
        button.setPreferredSize(new Dimension(ChooserStyle.px(90), ChooserStyle.px(28)));
        return button;
    }

    /** A plain outlined button. */
    static ChooserButton plain(String text)
    {
        ChooserButton button = new ChooserButton(text, ChooserStyle.BG_TERTIARY, ChooserStyle.HOVER, ChooserStyle.BORDER);
        button.setPreferredSize(new Dimension(ChooserStyle.px(90), ChooserStyle.px(28)));
        return button;
    }

    /** A small square navigation button holding one glyph. */
    static ChooserButton nav(String text, String tooltip)
    {
        ChooserButton button = new ChooserButton(text, ChooserStyle.BG_TERTIARY, ChooserStyle.HOVER, ChooserStyle.BORDER);
        button.setToolTipText(tooltip);
        button.setBorder(BorderFactory.createLineBorder(ChooserStyle.BORDER));
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(ChooserStyle.px(28), ChooserStyle.px(24)));
        return button;
    }

    private ChooserButton(String text, Color idle, Color hover, Color border)
    {
        super(text);
        this.idle = idle;
        this.hover = hover;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(false);
        setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(border == null
                ? BorderFactory.createEmptyBorder(ChooserStyle.px(4), ChooserStyle.px(12), ChooserStyle.px(4), ChooserStyle.px(12))
                : BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border), BorderFactory.createEmptyBorder(ChooserStyle.px(3), ChooserStyle.px(11), ChooserStyle.px(3), ChooserStyle.px(11))));
        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                over = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                over = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Color fill = idle;
        if (isEnabled() && getModel().isPressed())
        {
            fill = hover.darker();
        }
        else if (isEnabled() && over)
        {
            fill = hover;
        }
        g.setColor(fill);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }
}
