package com.tonic.vitavr.pointer;

import com.tonic.vitavr.hud.MenuCrop;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;
import lombok.Getter;

/**
 * A right-click menu cropped from the HUD picture and placed on the pointer where it was opened.
 */
public final class WorldMenu
{
    /** The menu picture size, the largest menu it holds. */
    public static final int WIDTH = 512;
    public static final int HEIGHT = 768;
    private static final double PIXEL_AT_ONE_METRE = 0.0022;
    private static final double ON_POINTER_METRES = 0.8;
    private static final double NEARER_THAN_WORLD = 0.95;
    private static final double MISSED_METRES = 3;
    private static final double HEADER = 8;
    private static final long OPENING_MILLIS = 600;

    private enum State
    {
        NONE,
        OPENING,
        OPEN
    }

    @Getter
    private final MenuCrop crop = new MenuCrop(WIDTH, HEIGHT);
    @Getter
    private final Billboard board = new Billboard();
    private State state = State.NONE;
    private long openingUntil;
    private double[] aim;

    /**
     * Starts opening a menu on the pointer.
     *
     * @param held The pointer pose, in the room.
     * @param end The world hit in the room, or null if none.
     */
    void opening(Pose held, double[] end)
    {
        double[] direction = new double[3];
        held.rotate(0, 0, -1, direction);
        double along = MISSED_METRES;
        if (end != null)
        {
            along = Math.sqrt(square(end[0] - held.x()) + square(end[1] - held.y()) + square(end[2] - held.z()));
        }
        along = Math.min(ON_POINTER_METRES, along * NEARER_THAN_WORLD);
        aim = new double[]{held.x() + direction[0] * along, held.y() + direction[1] * along, held.z() + direction[2] * along};
        board.clear();
        state = State.OPENING;
        openingUntil = System.currentTimeMillis() + OPENING_MILLIS;
    }

    /**
     * Tracks the game's menu state for a frame.
     *
     * @param gameMenuOpen Whether the game shows a menu.
     * @return Whether the menu still holds the pointer.
     */
    boolean follow(boolean gameMenuOpen)
    {
        if (state == State.OPENING)
        {
            if (gameMenuOpen)
            {
                state = State.OPEN;
            }
            else if (System.currentTimeMillis() > openingUntil)
            {
                end();
            }
        }
        else if (state == State.OPEN && !gameMenuOpen)
        {
            end();
        }
        return state != State.NONE;
    }

    /**
     * @return Whether the game's menu is open and holds the pointer.
     */
    boolean open()
    {
        return state == State.OPEN;
    }

    /**
     * Ends the menu.
     */
    void end()
    {
        state = State.NONE;
        board.clear();
        aim = null;
    }

    /**
     * Crops the game's menu from the HUD picture and places it the first time.
     *
     * @param pixels The HUD picture, ARGB, rows from the top.
     * @param frameWidth The picture width.
     * @param frameHeight The picture height.
     * @param x The menu's left edge.
     * @param y The menu's top edge.
     * @param width The menu width.
     * @param height The menu height.
     * @param head The head pose in the room, which the menu faces.
     * @return Whether the menu is shown.
     */
    public boolean lift(int[] pixels, int frameWidth, int frameHeight, int x, int y, int width, int height, Pose head)
    {
        if (state != State.OPEN || aim == null)
        {
            return false;
        }
        crop.lift(pixels, frameWidth, frameHeight, x, y, width, height);
        if (!board.placed())
        {
            double fromHead = Math.max(0.1, Math.sqrt(square(aim[0] - head.x()) + square(aim[1] - head.y()) + square(aim[2] - head.z())));
            board.stand(aim[0], aim[1], aim[2], head, WIDTH, HEIGHT, PIXEL_AT_ONE_METRE * fromHead, crop.getCropWidth() / 2.0, HEADER);
        }
        return true;
    }

    /**
     * Gets the pointer position on the menu, in menu pixels.
     *
     * @param held The pointer pose, in the room.
     * @return The x and y on the menu, or null when off it.
     */
    int[] crossing(Pose held)
    {
        int[] at = board.crossing(held);
        if (at == null || at[0] >= crop.getCropWidth() || at[1] >= crop.getCropHeight())
        {
            return null;
        }
        return at;
    }

    /**
     * Gets the distance from the pointer to the menu.
     *
     * @param held The pointer pose, in the room.
     * @return The distance in metres, or infinity if it misses or the menu is not open.
     */
    double reach(Pose held)
    {
        return state == State.OPEN ? board.reach(held) : Double.POSITIVE_INFINITY;
    }

    private static double square(double value)
    {
        return value * value;
    }
}
