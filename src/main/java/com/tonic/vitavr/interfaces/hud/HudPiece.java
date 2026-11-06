package com.tonic.vitavr.interfaces.hud;

import lombok.Getter;
import net.runelite.api.gameval.InterfaceID;

import java.util.function.IntFunction;

/**
 * A part of the resizable interfaces that can be moved on its own: the frame's own container for it, as the desktop
 * client moves them, plus the tab stone layers the classic frame keeps beside the side panel.
 */
public enum HudPiece
{
    CHAT("chat", InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER, InterfaceID.ToplevelPreEoc.CHAT_CONTAINER),
    MINIMAP("minimap", InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.MAP_CONTAINER),
    INVENTORY("inventory", InterfaceID.ToplevelOsrsStretch.SIDE_MENU, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER, InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER,
            InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER);

    /** The piece's name in the saved layout. */
    @Getter
    private final String key;
    /** The widgets of both resizable layouts; only the shown layout's are found. */
    private final int[] widgets;

    HudPiece(String key, int... widgets)
    {
        this.key = key;
        this.widgets = widgets;
    }

    /**
     * Finds the piece's box on the canvas, the union of its shown widgets.
     *
     * @param canvasBox Gets a shown widget's x, y, width and height, or null.
     * @return The box as x, y, width and height, or null if none of its widgets is shown.
     */
    public int[] box(IntFunction<int[]> canvasBox)
    {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int widget : widgets)
        {
            int[] found = canvasBox.apply(widget);
            if (found == null)
            {
                continue;
            }
            left = Math.min(left, found[0]);
            top = Math.min(top, found[1]);
            right = Math.max(right, found[0] + found[2]);
            bottom = Math.max(bottom, found[1] + found[3]);
        }
        return right <= left || bottom <= top ? null : new int[]{left, top, right - left, bottom - top};
    }

    /**
     * Finds a piece by its saved name.
     *
     * @param key The saved name.
     * @return The piece, or null if none has that name.
     */
    public static HudPiece named(String key)
    {
        for (HudPiece piece : values())
        {
            if (piece.key.equals(key))
            {
                return piece;
            }
        }
        return null;
    }
}
