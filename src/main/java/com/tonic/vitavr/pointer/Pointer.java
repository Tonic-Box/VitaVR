package com.tonic.vitavr.pointer;

import com.tonic.services.PickCamera;
import com.tonic.vitavr.hud.HudPicture;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.RoomToWorld;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.xr.Controllers;
import com.tonic.vitavr.xr.HudPanel;
import com.tonic.vitavr.xr.RoomPanel;
import com.tonic.vitavr.wrist.WristDisplay;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;

import java.util.Collections;
import java.util.List;

/**
 * The active hand's pointer, driving the game's picking camera and mouse.
 * Trigger is left click and selects the active hand, right grip is right click, left grip is ctrl.
 */
public final class Pointer
{
    private static final double REACH_METRES = 12;
    private static final long CROSS_MILLIS = 350;
    /** Distance outside the menu to move the mouse to close it, in pixels. */
    private static final int CLOSE_OUTSIDE = 40;
    private static final float STICK_PUSHED = 0.7f;
    private static final long SCROLL_STEP_MILLIS = 150;

    private final Client client;
    private final CanvasMouse mouse;
    private final View view = new View();
    @Getter
    private final WorldMenu menu = new WorldMenu();
    private boolean menuHeld;
    @Getter
    private boolean onPanel;
    /** Pointer position on the HUD picture this frame, or null. */
    @Getter
    private int[] panelCursor;
    /** Pointer position on the wrist picture this frame, or null. */
    @Getter
    private int[] wristCursor;
    /** Pointer position on the open menu's picture this frame, or null. */
    @Getter
    private int[] menuCursor;
    private HudPanel panel;
    private List<RoomPanel> ownPanels = Collections.emptyList();
    private boolean onOwnPanel;
    private WristDisplay wrist;
    private boolean onWrist;
    private long nextScroll;
    @Getter
    private int activeHand = Controllers.RIGHT;
    private boolean aimed;
    private boolean leftDown;
    private boolean rightDown;
    private double[] pressedAt;
    private final double[] crossAt = new double[3];
    private long crossFrom;
    private boolean walkCross;
    private boolean crossed;

    /**
     * @param client The client.
     */
    public Pointer(Client client)
    {
        this.client = client;
        this.mouse = new CanvasMouse(client);
    }

    /**
     * Updates the pointer for a frame and forwards presses, drags and scrolls to the game.
     *
     * @param controllers The controllers, read this frame.
     * @param room The room's placement in the world.
     * @param lift The room's lift for the eyes, in metres.
     * @param hit The last world hit, or null if none.
     * @param panel The HUD panel, or null before it is created.
     * @param picture The HUD picture last shown.
     * @param ownPanels VitaVR's own panels, which take the pointer first while shown.
     * @param wrist The left wrist display.
     */
    public void update(Controllers controllers, RoomToWorld room, double lift, double[] hit, HudPanel panel, HudPicture picture, List<RoomPanel> ownPanels,
                       WristDisplay wrist)
    {
        this.panel = panel;
        this.ownPanels = ownPanels;
        this.wrist = wrist;
        panelCursor = null;
        menuCursor = null;
        wristCursor = null;
        for (int hand = Controllers.LEFT; hand <= Controllers.RIGHT; hand++)
        {
            if (controllers.triggerPressed(hand) || hand == Controllers.RIGHT && controllers.gripPressed(hand))
            {
                activeHand = hand;
            }
        }
        Pose held = controllers.aim(activeHand);
        aimed = held != null;
        if (!aimed)
        {
            PickCamera.clear();
            onPanel = false;
            return;
        }
        room.view(held.lifted(lift), view);
        double[] forward = new double[3];
        view.forward(forward);
        PickCamera.set((float) view.eyeX(), (float) view.eyeY(), (float) view.eyeZ(), (float) forward[0], (float) forward[1], (float) forward[2]);

        onOwnPanel = false;
        for (RoomPanel own : ownPanels)
        {
            if (!onOwnPanel && own.point(held))
            {
                onOwnPanel = true;
                if (controllers.triggerPressed(activeHand))
                {
                    own.press(held);
                    controllers.tick(activeHand);
                }
            }
            else if (onOwnPanel)
            {
                own.point(null);
            }
        }
        if (onOwnPanel)
        {
            onPanel = false;
            return;
        }

        // Forward wrist orb presses to their HUD button
        onWrist = activeHand == Controllers.RIGHT && wrist.takes(held);
        if (onWrist)
        {
            onPanel = false;
            wristCursor = wrist.crossing(held);
            int[] button = controllers.triggerPressed(activeHand) ? wrist.pressAt(held) : null;
            if (button != null)
            {
                mouse.moveTo(button[0], button[1]);
                mouse.press(false, false);
                mouse.release(false, false);
                controllers.tick(activeHand);
            }
            return;
        }

        boolean ctrl = controllers.gripHeld(Controllers.LEFT);
        menuHeld = menu.follow(client.isMenuOpen());
        if (menuHeld)
        {
            onPanel = false;
            followMenu(controllers, held, ctrl);
            return;
        }

        // Keep the pressed surface while a button is held
        int[] panelPoint = panel == null ? null : panel.crossing(held);
        if (!leftDown && !rightDown)
        {
            onPanel = panelPoint != null && picture.covers(panelPoint[0], panelPoint[1]);
        }
        if (onPanel)
        {
            if (panelPoint != null)
            {
                mouse.moveTo(panelPoint[0], panelPoint[1]);
                panelCursor = panelPoint;
            }
            scroll(controllers);
        }
        else
        {
            // The game only picks the world while the mouse is in the viewport
            mouse.moveToViewport();
        }
        press(controllers, ctrl, held, onPanel ? null : hit, room, lift);
    }

    /**
     * Presses and releases the mouse buttons from the trigger and right grip.
     */
    private void press(Controllers controllers, boolean ctrl, Pose held, double[] hit, RoomToWorld room, double lift)
    {
        if (controllers.triggerPressed(activeHand))
        {
            pressedAt = hit == null ? null : hit.clone();
            mouse.press(false, ctrl);
            leftDown = true;
            controllers.tick(activeHand);
        }
        else if (leftDown && controllers.triggerReleased(activeHand))
        {
            mouse.release(false, ctrl);
            leftDown = false;
        }
        if (activeHand == Controllers.RIGHT && controllers.gripPressed(Controllers.RIGHT))
        {
            pressedAt = hit == null ? null : hit.clone();
            mouse.press(true, ctrl);
            rightDown = true;
            controllers.tick(Controllers.RIGHT);
            if (!onPanel)
            {
                menu.opening(held, hit == null ? null : inRoom(room, lift, hit));
            }
        }
        else if (rightDown && controllers.gripReleased(Controllers.RIGHT))
        {
            mouse.release(true, ctrl);
            rightDown = false;
        }
    }

    /**
     * Scrolls the wheel while the right stick is held up or down.
     */
    private void scroll(Controllers controllers)
    {
        float up = controllers.stickY(Controllers.RIGHT);
        long now = System.currentTimeMillis();
        if (Math.abs(up) > STICK_PUSHED && now >= nextScroll)
        {
            mouse.wheel(up > 0 ? -1 : 1);
            nextScroll = now + SCROLL_STEP_MILLIS;
        }
    }

    /**
     * Drives an open menu: hovers and picks entries, and closes it when the pointer leaves.
     */
    private void followMenu(Controllers controllers, Pose held, boolean ctrl)
    {
        if (rightDown && controllers.gripReleased(Controllers.RIGHT))
        {
            mouse.release(true, ctrl);
            rightDown = false;
        }
        if (!menu.open())
        {
            return;
        }
        int[] at = menu.crossing(held);
        menuCursor = at;
        if (at != null)
        {
            mouse.moveTo(client.getMenuX() + at[0], client.getMenuY() + at[1]);
            if (controllers.triggerPressed(activeHand))
            {
                mouse.press(false, ctrl);
                leftDown = true;
                controllers.tick(activeHand);
            }
            else if (leftDown && controllers.triggerReleased(activeHand))
            {
                mouse.release(false, ctrl);
                leftDown = false;
            }
            return;
        }
        if (!rightDown && !leftDown || controllers.triggerPressed(activeHand))
        {
            closeMenu();
        }
    }

    /**
     * Closes the game's menu by moving the mouse off it.
     */
    private void closeMenu()
    {
        int left = client.getMenuX();
        int x = left >= CLOSE_OUTSIDE ? left - CLOSE_OUTSIDE : left + client.getMenuWidth() + CLOSE_OUTSIDE;
        mouse.moveTo(x, client.getMenuY() + client.getMenuHeight() / 2);
        menu.end();
    }

    /**
     * @return Whether the active pointer is tracked and on the world.
     */
    public boolean isOnWorld()
    {
        return aimed && !menuHeld && !onPanel && !onOwnPanel && !onWrist;
    }

    /**
     * Converts a world point to the unlifted room.
     */
    private static double[] inRoom(RoomToWorld room, double lift, double[] point)
    {
        double[] at = new double[3];
        room.room(point[0], point[1], point[2], at);
        at[1] -= lift;
        return at;
    }

    /**
     * @return The active pointer's view in the world, or null while not tracked.
     */
    public View view()
    {
        return aimed ? view : null;
    }

    /**
     * Gets a hand's beam start and end in the world.
     *
     * @param controllers The controllers, read this frame.
     * @param hand LEFT or RIGHT.
     * @param room The room's placement in the world.
     * @param lift The room's lift for the eyes, in metres.
     * @param from Receives the start point.
     * @param to Receives the end point, at max reach or on the nearest panel or menu.
     * @return Whether the hand is tracked.
     */
    public boolean beam(Controllers controllers, int hand, RoomToWorld room, double lift, double[] from, double[] to)
    {
        Pose held = controllers.aim(hand);
        if (held == null)
        {
            return false;
        }
        double reach = Math.min(REACH_METRES, Math.min(menu.reach(held), panel == null ? Double.POSITIVE_INFINITY : panel.reach(held)));
        for (RoomPanel own : ownPanels)
        {
            reach = Math.min(reach, own.reach(held));
        }
        if (hand == Controllers.RIGHT && wrist != null)
        {
            reach = Math.min(reach, wrist.reach(held));
        }
        Pose aim = held.lifted(lift);
        double[] direction = new double[3];
        aim.rotate(0, 0, -1, direction);
        room.point(aim.x(), aim.y(), aim.z(), from);
        room.point(aim.x() + direction[0] * reach, aim.y() + direction[1] * reach, aim.z() + direction[2] * reach, to);
        return true;
    }

    /**
     * Shows the click cross where the world was pressed, yellow for a walk and red otherwise.
     *
     * @param action The performed menu action.
     */
    public void performed(MenuAction action)
    {
        boolean walk = action == MenuAction.WALK;
        if (pressedAt == null || !walk && !onWorld(action))
        {
            return;
        }
        System.arraycopy(pressedAt, 0, crossAt, 0, 3);
        crossFrom = System.currentTimeMillis();
        walkCross = walk;
        crossed = true;
        pressedAt = null;
    }

    /**
     * Gets the click cross as it shows now, fading out over time.
     *
     * @param at Receives the cross position.
     * @return The ARGB colour, or 0 when none shows.
     */
    public int cross(double[] at)
    {
        long elapsed = System.currentTimeMillis() - crossFrom;
        if (!crossed || elapsed < 0 || elapsed >= CROSS_MILLIS)
        {
            return 0;
        }
        System.arraycopy(crossAt, 0, at, 0, 3);
        double progress = elapsed / (double) CROSS_MILLIS;
        int alpha = progress < 0.6 ? 255 : (int) (255 * (1.0 - (progress - 0.6) / 0.4));
        return Math.max(0, Math.min(255, alpha)) << 24 | (walkCross ? 0xFFFF00 : 0xFF0000);
    }

    /**
     * @return The click cross's grow-in scale, 0 to 1.
     */
    public double crossScale()
    {
        double progress = (System.currentTimeMillis() - crossFrom) / (double) CROSS_MILLIS;
        return progress < 0.25 ? progress / 0.25 : 1.0;
    }

    /**
     * Releases held buttons and returns picking to the mouse.
     */
    public void close()
    {
        if (leftDown)
        {
            mouse.release(false, false);
        }
        if (rightDown)
        {
            mouse.release(true, false);
        }
        leftDown = false;
        rightDown = false;
        PickCamera.clear();
    }

    private static boolean onWorld(MenuAction action)
    {
        String name = action.name();
        return name.startsWith("NPC_") || name.startsWith("GAME_OBJECT_") || name.startsWith("GROUND_ITEM_") || name.startsWith("PLAYER_")
                || name.startsWith("WIDGET_TARGET_ON_") && action != MenuAction.WIDGET_TARGET_ON_WIDGET || name.startsWith("EXAMINE_");
    }

}
