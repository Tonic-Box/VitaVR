package com.tonic.vitavr.ui.filechooser;


import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;

/**
 * The file chooser's palette, fonts, sizes and small widget factories, in the OSH client's dark side theme colours.
 * The client may run with Windows display scaling turned off and its own game font as the default, so the chooser
 * scales its own sizes to the screen and uses the system UI font.
 */
final class ChooserStyle
{
    static final Color BG_PRIMARY = new Color(0x2B2B2B);
    static final Color BG_SECONDARY = new Color(0x333333);
    static final Color BG_TERTIARY = new Color(0x3A3A3A);
    static final Color TEXT = new Color(0xE0E0E0);
    static final Color TEXT_MUTED = new Color(0x9AA0A6);
    static final Color ACCENT = new Color(0x3A6EA5);
    static final Color ACCENT_BRIGHT = new Color(0x4A82BD);
    static final Color SELECTION = new Color(0x3A6EA5);
    static final Color HOVER = new Color(0x4A4A4A);
    static final Color BORDER = new Color(0x3C3C3C);
    static final Color ERROR = new Color(0xA33A3A);
    /** How much bigger than 96 DPI to draw, beyond whatever scaling Java already applies. */
    private static final double SCALE = scale();
    private static final String FAMILY = family();
    static final int ROW_HEIGHT = px(24);
    static final int FONT_SIZE = 12;

    private ChooserStyle()
    {
    }

    /**
     * Scales a size given at 96 DPI to the screen.
     *
     * @param pixels The size at 96 DPI.
     * @return The size on this screen.
     */
    static int px(int pixels)
    {
        return (int) Math.round(pixels * SCALE);
    }

    static Font font(int size)
    {
        return new Font(FAMILY, Font.PLAIN, size).deriveFont((float) (size * SCALE));
    }

    private static double scale()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            return 1;
        }
        double applied = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
        double wanted = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
        return Math.max(1, wanted / applied);
    }

    private static String family()
    {
        if (!GraphicsEnvironment.isHeadless())
        {
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            {
                if (name.equals("Segoe UI"))
                {
                    return name;
                }
            }
        }
        return Font.SANS_SERIF;
    }

    static Font boldFont(int size)
    {
        return font(size).deriveFont(Font.BOLD);
    }

    /** A text field border: a line in the given colour around a little padding. */
    static Border fieldBorder(Color line)
    {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(line),
                BorderFactory.createEmptyBorder(4, 8, 4, 8));
    }

    static JPopupMenu menu()
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(BORDER));
        return menu;
    }

    static void menuItem(JPopupMenu menu, String text, Runnable action)
    {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }
}
