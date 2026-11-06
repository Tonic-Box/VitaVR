package com.tonic.vitavr.interfaces.hud;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.headset.Headset;
import com.tonic.vitavr.headset.input.Controllers;
import com.tonic.vitavr.headset.layers.FloatingLayer;
import com.tonic.vitavr.headset.layers.HudPanel;
import com.tonic.vitavr.headset.layers.RoomPanel;
import com.tonic.vitavr.interfaces.panels.CanvasKeys;
import com.tonic.vitavr.interfaces.panels.KeyboardPanel;
import com.tonic.vitavr.interfaces.panels.SettingsPanel;
import com.tonic.vitavr.interfaces.pointer.Pointer;
import com.tonic.vitavr.interfaces.pointer.PointerLabel;
import com.tonic.vitavr.interfaces.pointer.WorldMenu;
import com.tonic.vitavr.interfaces.wrist.WristDisplay;
import com.tonic.vitavr.interfaces.wrist.WristPicture;
import com.tonic.vitavr.render.draw.AtlasQuad;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.placement.Placement;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.lwjgl.PointerBuffer;

import java.awt.Rectangle;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The headset layers drawn over the eyes: the interface panel, world menu, pointer label, settings and keyboard panels, and the wrist picture.
 */
public final class Layers implements AutoCloseable
{
    /** The canvas width the chat and inventory side widths are measured against. */
    private static final int HUD_CANVAS_WIDTH = 1760;
    private static final int CHAT_SIDE_PIXELS = 520;
    private static final int INVENTORY_SIDE_PIXELS = 260;
    /** The angle one interface pixel covers at 100% size, in radians. */
    private static final double HUD_PIXEL_RADIANS = 0.00065;
    public static final int LABEL_WIDTH = 512;
    public static final int LABEL_HEIGHT = 96;
    /** The furthest the label stands from the head, in metres. */
    private static final double LABEL_METRES = 1.0;
    private static final double NEARER_THAN_WORLD = 0.95;
    /** The label's offset from the pointer end, in label pixels. */
    private static final double LABEL_RIGHT = -14;
    private static final double LABEL_BELOW = -16;
    private static final double LABEL_ENLARGED = 1.8;
    private static final double SETTINGS_METRES = 0.85;
    private static final double SETTINGS_BELOW_EYES = 0.12;
    private static final double KEYBOARD_METRES = 0.6;
    private static final double KEYBOARD_BELOW_EYES = 0.4;
    /** How often typing input is checked for, in milliseconds. */
    private static final long TYPING_CHECK_MILLIS = 200;

    private final Client client;
    private final VitaVRConfig config;
    private final Headset headset;
    private final Pointer pointer;
    @Getter
    private final HudPicture picture = new HudPicture();
    private final CursorDot cursor = new CursorDot();
    @Getter
    private final PointerLabel label = new PointerLabel(LABEL_WIDTH, LABEL_HEIGHT);
    @Getter
    private final Billboard labelBoard = new Billboard();
    /** The interface panel, or null before the interfaces are first drawn. */
    @Getter
    private HudPanel panel;
    @Getter
    private final FloatingLayer menuLayer;
    @Getter
    private final FloatingLayer labelLayer;
    @Getter
    private final RoomPanel settingsPanel;
    @Getter
    private final RoomPanel keyboardPanel;
    /** VitaVR's own panels, which take the pointer first while shown. */
    @Getter
    private final List<RoomPanel> ownPanels;
    @Getter
    private final AtlasQuad wristPicture;
    private final HudLayout layout = new HudLayout();
    private final Map<HudPiece, int[]> boxes = new EnumMap<>(HudPiece.class);
    private volatile boolean hudShapeStale = true;
    private volatile boolean layoutStale = true;
    private long nextTypingCheck;
    private boolean typingWanted;
    private boolean keyboardOpenedForTyping;
    private boolean closed;

    /**
     * Creates the layers in the current OpenGL context.
     *
     * @param client The client.
     * @param config The plugin config.
     * @param configManager The config manager the settings panel writes to.
     * @param keys The keys the keyboard panel types.
     * @param headset The headset.
     * @param pointer The pointer whose menu and cursors are shown.
     */
    public Layers(Client client, VitaVRConfig config, ConfigManager configManager, CanvasKeys keys, Headset headset, Pointer pointer)
    {
        this.client = client;
        this.config = config;
        this.headset = headset;
        this.pointer = pointer;
        menuLayer = new FloatingLayer(headset, WorldMenu.WIDTH, WorldMenu.HEIGHT);
        labelLayer = new FloatingLayer(headset, LABEL_WIDTH, LABEL_HEIGHT);
        settingsPanel = new RoomPanel(headset, new SettingsPanel(config, configManager), SETTINGS_METRES, SETTINGS_BELOW_EYES);
        keyboardPanel = new RoomPanel(headset, new KeyboardPanel(keys), KEYBOARD_METRES, KEYBOARD_BELOW_EYES);
        ownPanels = Arrays.asList(settingsPanel, keyboardPanel);
        wristPicture = new AtlasQuad(WristPicture.WIDTH, WristPicture.HEIGHT);
    }

    /**
     * Prepares the layers for a frame, toggling and placing the interfaces and VitaVR's panels.
     *
     * @param controllers The controllers, read for this frame's presses.
     * @param head The head pose in the room.
     */
    public void frame(Controllers controllers, Pose head)
    {
        if (panel != null)
        {
            if (controllers.hudPressed())
            {
                panel.show(!panel.shown());
            }
            panel.standBefore(head);
        }
        openPanels(controllers, head);
    }

    /**
     * Opens and closes the settings and keyboard panels; the keyboard also opens while the game wants typing.
     */
    private void openPanels(Controllers controllers, Pose head)
    {
        double pixelAtOneMetre = HUD_PIXEL_RADIANS * config.hudSizePercent() / 100.0;
        if (controllers.settingsPressed() || ((SettingsPanel) settingsPanel.getPanel()).takeCloseAsked())
        {
            toggle(settingsPanel, head, pixelAtOneMetre);
        }
        boolean wasWanted = typingWanted;
        long now = System.currentTimeMillis();
        if (now >= nextTypingCheck)
        {
            nextTypingCheck = now + TYPING_CHECK_MILLIS;
            Widget input = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
            typingWanted = client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0 && input != null && !input.isHidden();
        }
        if (controllers.keyboardPressed() || ((KeyboardPanel) keyboardPanel.getPanel()).takeHideAsked())
        {
            keyboardOpenedForTyping = false;
            toggle(keyboardPanel, head, pixelAtOneMetre);
        }
        else if (typingWanted && !wasWanted && !keyboardPanel.isShown())
        {
            keyboardOpenedForTyping = true;
            keyboardPanel.open(head, pixelAtOneMetre);
        }
        else if (!typingWanted && wasWanted && keyboardOpenedForTyping)
        {
            keyboardOpenedForTyping = false;
            keyboardPanel.hide();
        }
    }

    private static void toggle(RoomPanel own, Pose head, double pixelAtOneMetre)
    {
        if (own.isShown())
        {
            own.hide();
        }
        else
        {
            own.open(head, pixelAtOneMetre);
        }
    }

    /**
     * Marks the settings panel for repaint, and the interfaces for reshaping or the moved pieces for reloading. Safe on any thread.
     *
     * @param key The changed setting's key.
     */
    public void settingsChanged(String key)
    {
        if (VitaVRConfig.HUD_LAYOUT.equals(key))
        {
            layoutStale = true;
        }
        // VitaVR's own saved state changes nothing about the interfaces' shape
        else if (!VitaVRConfig.SETTINGS_TAB.equals(key) && !VitaVRConfig.TABLE_TILT.equals(key) && !VitaVRConfig.RECORD_HIDE_CHAT.equals(key)
                && !VitaVRConfig.STREAM_WINDOW.equals(key))
        {
            hudShapeStale = true;
        }
        ((SettingsPanel) settingsPanel.getPanel()).settingsChanged();
    }

    /**
     * Updates all layers from the interfaces the game just drew.
     *
     * @param placement The room's placement in the world.
     * @param wrist The wrist display.
     * @param pointerHit Where the pointer met the world.
     * @param pointerMet Whether the pointer met the world.
     * @param head The head pose in the room.
     */
    public void show(Placement placement, WristDisplay wrist, double[] pointerHit, boolean pointerMet, Pose head)
    {
        picture.copy(client.getBufferProvider());
        readyPanel(picture.getWidth(), picture.getHeight());

        WorldMenu menu = pointer.getMenu();
        Menu shown = client.getMenu();
        if (client.isMenuOpen() && menu.lift(picture.getPixels(), picture.getWidth(), picture.getHeight(), shown.getMenuX(), shown.getMenuY(),
                shown.getMenuWidth(), shown.getMenuHeight(), head))
        {
            picture.clear(shown.getMenuX(), shown.getMenuY(), shown.getMenuWidth(), shown.getMenuHeight());
            int[] onMenu = pointer.getMenuCursor();
            if (onMenu != null)
            {
                cursor.paint(menu.getCrop().getPixels(), WorldMenu.WIDTH, WorldMenu.HEIGHT, onMenu[0], onMenu[1]);
            }
            menuLayer.upload(menu.getCrop().getPixels());
            menuLayer.place(menu.getBoard());
        }
        else
        {
            menuLayer.clear();
        }

        showLabel(placement, pointerHit, pointerMet, head);
        paintWrist(wrist);
        settingsPanel.refresh();
        keyboardPanel.refresh();

        // Paint the dot for the upload only, so pointer hit tests never see it
        int[] onPanel = pointer.getPanelCursor();
        if (onPanel != null)
        {
            cursor.paint(picture.getPixels(), picture.getWidth(), picture.getHeight(), onPanel[0], onPanel[1]);
        }
        panel.upload(picture.getPixels(), pieceBoxes(), pointer.getOutlined());
        if (onPanel != null)
        {
            cursor.restore(picture.getPixels());
        }
    }

    /**
     * Finds the movable pieces on the interfaces; there are none outside the resizable layouts.
     */
    private Map<HudPiece, int[]> pieceBoxes()
    {
        boxes.clear();
        if (client.isResized())
        {
            for (HudPiece piece : HudPiece.values())
            {
                int[] box = piece.box(this::canvasBox);
                if (box != null)
                {
                    boxes.put(piece, box);
                }
            }
        }
        return boxes;
    }

    /**
     * Creates the interface panel on first use or canvas resize, reshapes it when settings change, and loads or saves the moved pieces.
     */
    private void readyPanel(int width, int height)
    {
        if (layoutStale)
        {
            layoutStale = false;
            layout.load(config.hudLayout());
        }
        if (layout.takeDropped())
        {
            config.hudLayout(layout.format());
        }
        if (panel != null && (panel.getPixelWidth() != width || panel.getPixelHeight() != height))
        {
            panel.close();
            panel = null;
        }
        if (panel == null)
        {
            int chat = CHAT_SIDE_PIXELS * Math.min(width, HUD_CANVAS_WIDTH) / HUD_CANVAS_WIDTH;
            int inventory = INVENTORY_SIDE_PIXELS * Math.min(width, HUD_CANVAS_WIDTH) / HUD_CANVAS_WIDTH;
            panel = new HudPanel(headset, width, height, chat, inventory, layout);
            hudShapeStale = true;
        }
        if (hudShapeStale)
        {
            hudShapeStale = false;
            panel.shape(config.hudDistance(), HUD_PIXEL_RADIANS * config.hudSizePercent() / 100.0, config.chatSidePercent() / 100.0,
                    config.inventorySidePercent() / 100.0, Math.toRadians(config.sidesApartDegrees()));
        }
    }

    /**
     * Places the action label beside the pointer end while it is on the world with no menu open.
     */
    private void showLabel(Placement placement, double[] pointerHit, boolean pointerMet, Pose head)
    {
        boolean changed = label.update(client.getMenu().getMenuEntries());
        if (!config.pointerLabel() || !pointer.isOnWorld() || !pointerMet || client.isMenuOpen() || label.isEmpty())
        {
            labelLayer.clear();
            return;
        }
        double[] at = new double[3];
        placement.toRoom(pointerHit[0], pointerHit[1], pointerHit[2], at);
        double dx = at[0] - head.x();
        double dy = at[1] - head.y();
        double dz = at[2] - head.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6)
        {
            labelLayer.clear();
            return;
        }
        double out = Math.min(LABEL_METRES, distance * NEARER_THAN_WORLD);
        double scale = out / distance;
        double metresPerPixel = HUD_PIXEL_RADIANS * config.hudSizePercent() / 100.0 * LABEL_ENLARGED * out;
        labelBoard.stand(head.x() + dx * scale, head.y() + dy * scale, head.z() + dz * scale, head, LABEL_WIDTH, LABEL_HEIGHT, metresPerPixel, LABEL_RIGHT,
                LABEL_BELOW);
        labelLayer.place(labelBoard);
        if (changed || labelLayer.isCleared())
        {
            labelLayer.upload(label.getPixels());
        }
    }

    /**
     * Repaints the wrist picture from the interfaces while it is in view.
     */
    private void paintWrist(WristDisplay wrist)
    {
        if (wrist.getShown() <= 0)
        {
            return;
        }
        int[] pixels = wrist.paint(picture.getPixels(), picture.getWidth(), picture.getHeight(), this::canvasBox, LocalTime.now());
        int[] onWrist = pointer.getWristCursor();
        if (onWrist != null)
        {
            cursor.paint(pixels, WristPicture.WIDTH, WristPicture.HEIGHT, onWrist[0], onWrist[1]);
        }
        wristPicture.upload(pixels);
    }

    /**
     * Gets a component's box on the interfaces, or null if not shown.
     */
    private int[] canvasBox(int component)
    {
        Widget widget = client.getWidget(component);
        if (widget == null || widget.isHidden())
        {
            return null;
        }
        Rectangle bounds = widget.getBounds();
        return bounds == null || bounds.width <= 0 ? null : new int[]{bounds.x, bounds.y, bounds.width, bounds.height};
    }

    public WorldMenu getMenu()
    {
        return pointer.getMenu();
    }

    /**
     * Gets how many layers put adds.
     * @return The layer count.
     */
    public int count()
    {
        return 4 + (panel == null ? 0 : panel.layers().length);
    }

    /**
     * Adds the layers to the frame in order: interfaces, menu, label, then VitaVR's panels.
     *
     * @param layers The frame's layers.
     */
    public void put(PointerBuffer layers)
    {
        if (panel != null)
        {
            for (long column : panel.layers())
            {
                layers.put(column);
            }
        }
        layers.put(menuLayer.address());
        layers.put(labelLayer.address());
        layers.put(settingsPanel.address());
        layers.put(keyboardPanel.address());
    }

    /**
     * Releases the layers' images and GL objects. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        menuLayer.close();
        labelLayer.close();
        settingsPanel.close();
        keyboardPanel.close();
        wristPicture.close();
        if (panel != null)
        {
            panel.close();
            panel = null;
        }
    }
}
