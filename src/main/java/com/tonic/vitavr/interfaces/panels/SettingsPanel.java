package com.tonic.vitavr.interfaces.panels;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.TurnMode;
import com.tonic.vitavr.config.ViewMode;
import com.tonic.vitavr.gestures.vr.GestureSession;
import com.tonic.vitavr.interfaces.hud.HudLayout;
import net.runelite.client.config.ConfigManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The in-headset settings panel, with tabbed rows stepped by arrows and saved immediately.
 */
public final class SettingsPanel extends AbstractPanel
{
    public static final int WIDTH = 600;

    private static final int PAD = 18;
    private static final int TITLE = 48;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_GAP = 10;
    private static final int TAB_SPACE = 4;
    private static final int ROWS_TOP = TITLE + TAB_HEIGHT + TAB_GAP;
    private static final int ROW = 33;
    private static final int MOST_ROWS = 11;
    private static final int ARROW = 30;
    private static final int VALUE = 150;
    private static final int DONE_WIDTH = 140;
    private static final int DONE_HEIGHT = 36;

    /** Panel height, fitting the tab with the most rows. */
    public static final int HEIGHT = ROWS_TOP + MOST_ROWS * ROW + PAD + DONE_HEIGHT + PAD;

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
    private static final Font NOTE_FONT = new Font(Font.SANS_SERIF, Font.ITALIC, 13);
    private static final Color BACKGROUND = new Color(24, 22, 18, 235);
    private static final Color EDGE = new Color(120, 104, 72);
    private static final Color TEXT = new Color(235, 226, 200);
    private static final Color NOTE = new Color(160, 150, 125);
    private static final Color BUTTON = new Color(62, 56, 44);
    private static final Color LIT = new Color(110, 96, 62);
    private static final Color CHOSEN = new Color(146, 124, 74);
    private static final double FIRST_PERSON_UNITS = 110;
    private static final String NEXT_SESSION_NOTE = "* from the next launch";

    /** The settings tabs. */
    enum Tab
    {
        VIEW("View"),
        BODY("Body"),
        INTERFACES("Interfaces"),
        COMFORT("Comfort"),
        GESTURES("Gestures");

        private final String label;

        Tab(String label)
        {
            this.label = label;
        }
    }

    /** A setting row with its tab, name, shown value and a step of -1 or 1; a button row shows one button labelled with the value instead. */
    private static final class Row
    {
        final Tab tab;
        final String name;
        final Supplier<String> value;
        final IntConsumer step;
        final boolean nextLaunch;
        final boolean button;

        Row(Tab tab, String name, Supplier<String> value, IntConsumer step, boolean nextLaunch)
        {
            this(tab, name, value, step, nextLaunch, false);
        }

        Row(Tab tab, String name, Supplier<String> value, IntConsumer step, boolean nextLaunch, boolean button)
        {
            this.tab = tab;
            this.name = name;
            this.value = value;
            this.step = step;
            this.nextLaunch = nextLaunch;
            this.button = button;
        }
    }

    private final VitaVRConfig config;
    private final ConfigManager configManager;
    private final List<Row> rows;
    private final GesturesPage gestures;
    private Object gesturesShown;
    private Tab tab;
    private boolean closeAsked;

    /**
     * Creates the settings panel.
     * @param config The settings to read.
     * @param configManager The config manager to write to.
     */
    public SettingsPanel(VitaVRConfig config, ConfigManager configManager)
    {
        super(WIDTH, HEIGHT);
        this.config = config;
        this.configManager = configManager;
        this.tab = tabNamed(config.settingsTab());
        this.gestures = new GesturesPage(config, configManager, PAD, ROWS_TOP, WIDTH - 2 * PAD, ROW);
        this.rows = Arrays.asList(
                new Row(Tab.VIEW, "View", () -> config.viewMode().toString(),
                        direction -> set(VitaVRConfig.VIEW_MODE, config.viewMode() == ViewMode.TABLETOP ? ViewMode.FIRST_PERSON : ViewMode.TABLETOP), false),
                new Row(Tab.VIEW, "Turning", () -> config.turnMode().toString(),
                        direction -> set(VitaVRConfig.TURN_MODE, config.turnMode() == TurnMode.SMOOTH ? TurnMode.SNAP : TurnMode.SMOOTH), false),
                new Row(Tab.VIEW, "Snap turn", () -> config.snapDegrees() + "°",
                        direction -> set(VitaVRConfig.SNAP_DEGREES, (int) stepped(config.snapDegrees(), 15, direction, 15, 90)), false),
                new Row(Tab.VIEW, "Smooth turn", () -> config.smoothDegreesPerSecond() + "°/s",
                        direction -> set(VitaVRConfig.SMOOTH_DEGREES, (int) stepped(config.smoothDegreesPerSecond(), 30, direction, 30, 360)), false),
                new Row(Tab.VIEW, "World size", () -> Math.round(FIRST_PERSON_UNITS / config.unitsPerMetre() * 100) + "%",
                        direction -> set(VitaVRConfig.UNITS_PER_METRE, worldSize(config.unitsPerMetre(), direction)), false),
                new Row(Tab.VIEW, "Table scale", () -> "1:" + Math.round(1 / config.tabletopScale()),
                        direction -> set(VitaVRConfig.TABLETOP_SCALE, 1.0 / stepped(Math.round(1 / config.tabletopScale()), 5, -direction, 5, 100)), false),
                new Row(Tab.VIEW, "Table height", () -> String.format("%.2f m", config.tableHeight()),
                        direction -> set(VitaVRConfig.TABLE_HEIGHT, stepped(config.tableHeight(), 0.05, direction, 0.3, 1.5)), false),
                new Row(Tab.VIEW, "Mirror window", () -> onOff(config.mirror()), direction -> set(VitaVRConfig.MIRROR, !config.mirror()), false),
                new Row(Tab.VIEW, "Render scale", () -> Math.round(config.renderScale() * 100) + "%",
                        direction -> set(VitaVRConfig.RENDER_SCALE, stepped(config.renderScale(), 0.1, direction, 0.5, 1.5)), true),
                new Row(Tab.VIEW, "Antialiasing", () -> config.samples() == 1 ? "Off" : config.samples() + "x",
                        direction -> set(VitaVRConfig.SAMPLES, Math.max(1, Math.min(8, direction > 0 ? config.samples() * 2 : config.samples() / 2))), true),
                new Row(Tab.BODY, "Hands when animating", () -> onOff(config.ghostHands()), direction -> set(VitaVRConfig.GHOST_HANDS, !config.ghostHands()), false),
                new Row(Tab.BODY, "Head clearance", () -> config.headClearanceCm() + " cm",
                        direction -> set(VitaVRConfig.HEAD_CLEARANCE, (int) stepped(config.headClearanceCm(), 2, direction, 0, 40)), false),
                new Row(Tab.BODY, "Held items", () -> config.heldItems().toString(), direction -> set(VitaVRConfig.HELD_ITEMS, next(config.heldItems(), direction)), false),
                new Row(Tab.BODY, "Held item size", () -> config.heldItemPercent() + "%",
                        direction -> set(VitaVRConfig.HELD_ITEM_PERCENT, (int) stepped(config.heldItemPercent(), 10, direction, 30, 100)), false),
                new Row(Tab.BODY, "Fade near eyes", () -> onOff(config.fadeNearEyes()), direction -> set(VitaVRConfig.FADE_NEAR_EYES, !config.fadeNearEyes()), false),
                new Row(Tab.BODY, "Hide your head", () -> onOff(config.hideOwnHead()), direction -> set(VitaVRConfig.HIDE_OWN_HEAD, !config.hideOwnHead()), false),
                new Row(Tab.BODY, "View follows body", () -> config.headFollow().toString(), direction -> set(VitaVRConfig.HEAD_FOLLOW, next(config.headFollow(), direction)), false),
                new Row(Tab.BODY, "Roofs indoors", () -> config.keepRoofsIndoors() ? "Keep" : "Hide", direction -> set(VitaVRConfig.KEEP_ROOFS_INDOORS, !config.keepRoofsIndoors()), false),
                new Row(Tab.BODY, "Ceiling light", () -> config.ceilingLightPercent() + "%",
                        direction -> set(VitaVRConfig.CEILING_LIGHT, (int) stepped(config.ceilingLightPercent(), 5, direction, 10, 100)), false),
                new Row(Tab.INTERFACES, "Interfaces size", () -> config.hudSizePercent() + "%",
                        direction -> set(VitaVRConfig.HUD_SIZE, (int) stepped(config.hudSizePercent(), 10, direction, 50, 250)), false),
                new Row(Tab.INTERFACES, "Interfaces distance", () -> String.format("%.1f m", config.hudDistance()),
                        direction -> set(VitaVRConfig.HUD_DISTANCE, stepped(config.hudDistance(), 0.1, direction, 0.6, 2.5)), false),
                new Row(Tab.INTERFACES, "Chat side size", () -> config.chatSidePercent() + "%",
                        direction -> set(VitaVRConfig.CHAT_SIDE, (int) stepped(config.chatSidePercent(), 10, direction, 50, 250)), false),
                new Row(Tab.INTERFACES, "Inventory side size", () -> config.inventorySidePercent() + "%",
                        direction -> set(VitaVRConfig.INVENTORY_SIDE, (int) stepped(config.inventorySidePercent(), 10, direction, 50, 250)), false),
                new Row(Tab.INTERFACES, "Sides apart", () -> config.sidesApartDegrees() + "°",
                        direction -> set(VitaVRConfig.SIDES_APART, (int) stepped(config.sidesApartDegrees(), 2, direction, 0, 40)), false),
                new Row(Tab.INTERFACES, "Moved interfaces", this::movedInterfaces, direction -> set(VitaVRConfig.HUD_LAYOUT, ""), false, true),
                new Row(Tab.INTERFACES, "Hovered tile", () -> onOff(config.outlineHoveredTile()), direction -> set(VitaVRConfig.OUTLINE_HOVERED, !config.outlineHoveredTile()), false),
                new Row(Tab.INTERFACES, "Your tile", () -> onOff(config.outlineLocalTile()), direction -> set(VitaVRConfig.OUTLINE_LOCAL, !config.outlineLocalTile()), false),
                new Row(Tab.INTERFACES, "Destination tile", () -> onOff(config.outlineDestinationTile()),
                        direction -> set(VitaVRConfig.OUTLINE_DESTINATION, !config.outlineDestinationTile()), false),
                new Row(Tab.INTERFACES, "Pointer label", () -> onOff(config.pointerLabel()), direction -> set(VitaVRConfig.POINTER_LABEL, !config.pointerLabel()), false),
                new Row(Tab.COMFORT, "Vignette", () -> Math.round(config.vignette() * 100) + "%",
                        direction -> set(VitaVRConfig.VIGNETTE, stepped(config.vignette(), 0.1, direction, 0, 1)), false),
                new Row(Tab.COMFORT, "Wall fade", () -> onOff(config.wallFade()), direction -> set(VitaVRConfig.WALL_FADE, !config.wallFade()), false),
                new Row(Tab.COMFORT, "Vibration", () -> config.vibrationPercent() == 0 ? "Off" : config.vibrationPercent() + "%",
                        direction -> set(VitaVRConfig.VIBRATION, (int) stepped(config.vibrationPercent(), 10, direction, 0, 100)), false),
                new Row(Tab.COMFORT, "Feel hits taken", () -> onOff(config.feelHitsTaken()), direction -> set(VitaVRConfig.FEEL_HITS_TAKEN, !config.feelHitsTaken()), false),
                new Row(Tab.COMFORT, "Feel hits dealt", () -> onOff(config.feelHitsDealt()), direction -> set(VitaVRConfig.FEEL_HITS_DEALT, !config.feelHitsDealt()), false),
                new Row(Tab.COMFORT, "Feel level ups", () -> onOff(config.feelLevelUps()), direction -> set(VitaVRConfig.FEEL_LEVEL_UPS, !config.feelLevelUps()), false),
                new Row(Tab.COMFORT, "Low HP heartbeat", () -> onOff(config.heartbeat()), direction -> set(VitaVRConfig.HEARTBEAT, !config.heartbeat()), false),
                new Row(Tab.COMFORT, "Heartbeat below", () -> config.heartbeatBelowPercent() + "% HP",
                        direction -> set(VitaVRConfig.HEARTBEAT_BELOW, (int) stepped(config.heartbeatBelowPercent(), 5, direction, 5, 75)), false));
    }

    /**
     * Gets the moved interfaces row's button label: how many pieces are moved, or that none are.
     */
    private String movedInterfaces()
    {
        HudLayout layout = new HudLayout();
        layout.load(config.hudLayout());
        return layout.movedCount() == 0 ? "Default" : "Reset " + layout.movedCount();
    }

    private void set(String key, Object value)
    {
        configManager.setConfiguration(VitaVRConfig.GROUP, key, value);
    }

    private static Tab tabNamed(String name)
    {
        for (Tab each : Tab.values())
        {
            if (each.name().equals(name))
            {
                return each;
            }
        }
        return Tab.VIEW;
    }

    /**
     * Gets the next enum choice in a direction, wrapping round.
     */
    private static <E extends Enum<E>> E next(E chosen, int direction)
    {
        E[] all = chosen.getDeclaringClass().getEnumConstants();
        return all[Math.floorMod(chosen.ordinal() + direction, all.length)];
    }

    private static String onOff(boolean on)
    {
        return on ? "On" : "Off";
    }

    /**
     * Steps a value onto the step grid, clamped between least and most.
     */
    static double stepped(double value, double step, int direction, double least, double most)
    {
        double moved = Math.round(value / step + direction) * step;
        return Math.max(least, Math.min(most, Math.round(moved * 1000) / 1000.0));
    }

    /**
     * Steps the world size by 5% and returns the matching units per metre.
     */
    private static double worldSize(double unitsPerMetre, int direction)
    {
        double percent = stepped(FIRST_PERSON_UNITS / unitsPerMetre * 100, 5, direction, 40, 250);
        return Math.max(40, Math.min(300, FIRST_PERSON_UNITS * 100 / percent));
    }

    /**
     * Checks and clears whether Done was pressed since the last call.
     * @return Whether Done was pressed.
     */
    public boolean takeCloseAsked()
    {
        boolean asked = closeAsked;
        closeAsked = false;
        return asked;
    }

    /**
     * Gives the Gestures tab the session's gestures.
     *
     * @param session       The session's gestures.
     * @param openKeyboard  Opens the keyboard, for naming a gesture.
     * @param closeKeyboard Puts the keyboard away once named.
     */
    public void gestures(GestureSession session, Runnable openKeyboard, Runnable closeKeyboard)
    {
        gestures.session(session, openKeyboard, closeKeyboard);
        changed();
    }

    /**
     * Marks the panel for repainting when the Gestures tab is shown and what it shows has moved on, as while recording.
     */
    public void followGestures()
    {
        if (tab != Tab.GESTURES)
        {
            return;
        }
        Object now = gestures.state();
        if (!Objects.equals(now, gesturesShown))
        {
            gesturesShown = now;
            changed();
        }
    }

    /**
     * @return Whether the Gestures tab is the one shown.
     */
    public boolean onGestures()
    {
        return tab == Tab.GESTURES;
    }

    /**
     * Marks the panel for repainting after a setting changed elsewhere.
     */
    public void settingsChanged()
    {
        changed();
    }

    private int tabs()
    {
        return Tab.values().length;
    }

    private int doneTarget()
    {
        return tabs() + rows.size() * 2;
    }

    /**
     * Gets a row's position within its tab.
     */
    private int placeInTab(int index)
    {
        int place = 0;
        for (int before = 0; before < index; before++)
        {
            if (rows.get(before).tab == rows.get(index).tab)
            {
                place++;
            }
        }
        return place;
    }

    @Override
    protected int targets()
    {
        return doneTarget() + 1 + (tab == Tab.GESTURES ? gestures.targets() : 0);
    }

    @Override
    protected Rectangle bounds(int target)
    {
        if (target > doneTarget())
        {
            return tab == Tab.GESTURES ? gestures.bounds(target - doneTarget() - 1) : null;
        }
        boolean row = target != doneTarget() && target >= tabs();
        if (!row)
        {
            return rectangle(target);
        }
        Row shown = rows.get((target - tabs()) / 2);
        // A button row's one button is its first target
        boolean spare = shown.button && (target - tabs()) % 2 == 1;
        return shown.tab != tab || spare ? null : rectangle(target);
    }

    private Rectangle rectangle(int target)
    {
        if (target == doneTarget())
        {
            return new Rectangle((WIDTH - DONE_WIDTH) / 2, HEIGHT - PAD - DONE_HEIGHT, DONE_WIDTH, DONE_HEIGHT);
        }
        if (target < tabs())
        {
            int width = (WIDTH - 2 * PAD + TAB_SPACE) / tabs();
            return new Rectangle(PAD + target * width, TITLE, width - TAB_SPACE, TAB_HEIGHT);
        }
        int index = (target - tabs()) / 2;
        int top = ROWS_TOP + placeInTab(index) * ROW + (ROW - ARROW) / 2;
        int right = WIDTH - PAD;
        if (rows.get(index).button)
        {
            return new Rectangle(right - ARROW - VALUE - ARROW, top, ARROW + VALUE + ARROW, ARROW);
        }
        int x = (target - tabs()) % 2 == 0 ? right - ARROW - VALUE - ARROW : right - ARROW;
        return new Rectangle(x, top, ARROW, ARROW);
    }

    @Override
    protected void pressed(int target)
    {
        if (target > doneTarget())
        {
            gestures.pressed(target - doneTarget() - 1);
            return;
        }
        if (target == doneTarget())
        {
            closeAsked = true;
            return;
        }
        if (target < tabs())
        {
            tab = Tab.values()[target];
            set(VitaVRConfig.SETTINGS_TAB, tab.name());
            return;
        }
        rows.get((target - tabs()) / 2).step.accept((target - tabs()) % 2 == 0 ? -1 : 1);
    }

    @Override
    protected void paint(Graphics2D g)
    {
        g.setColor(BACKGROUND);
        g.fillRoundRect(0, 0, WIDTH, HEIGHT, 18, 18);
        g.setColor(EDGE);
        g.drawRoundRect(1, 1, WIDTH - 3, HEIGHT - 3, 18, 18);
        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        g.drawString("VR settings", PAD, 34);
        boolean nextLaunch = false;
        for (Row row : rows)
        {
            nextLaunch |= row.tab == tab && row.nextLaunch;
        }
        if (nextLaunch)
        {
            g.setFont(NOTE_FONT);
            g.setColor(NOTE);
            g.drawString(NEXT_SESSION_NOTE, WIDTH - PAD - g.getFontMetrics().stringWidth(NEXT_SESSION_NOTE), 34);
        }
        for (Tab each : Tab.values())
        {
            Rectangle bounds = rectangle(each.ordinal());
            g.setColor(each == tab ? CHOSEN : hovered() == each.ordinal() ? LIT : BUTTON);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            g.setFont(FONT);
            g.setColor(TEXT);
            centred(g, each.label, bounds.x, bounds.width, bounds.y + bounds.height / 2);
        }
        for (int index = 0; index < rows.size(); index++)
        {
            if (rows.get(index).tab == tab)
            {
                paintRow(g, index, rows.get(index));
            }
        }
        if (tab == Tab.GESTURES)
        {
            gestures.paint(g, hovered() - doneTarget() - 1, TEXT, NOTE, BUTTON, LIT, CHOSEN, FONT);
        }
        Rectangle done = rectangle(doneTarget());
        button(g, done, hovered() == doneTarget());
        g.setFont(FONT);
        g.setColor(TEXT);
        centred(g, "Done", done.x, done.width, done.y + done.height / 2);
    }

    private void paintRow(Graphics2D g, int index, Row row)
    {
        int middle = ROWS_TOP + placeInTab(index) * ROW + ROW / 2;
        g.setFont(FONT);
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(TEXT);
        g.drawString(row.name + (row.nextLaunch ? " *" : ""), PAD, middle + metrics.getAscent() / 2 - 2);
        int down = tabs() + index * 2;
        if (row.button)
        {
            Rectangle only = rectangle(down);
            button(g, only, hovered() == down);
            g.setColor(TEXT);
            centred(g, row.value.get(), only.x, only.width, middle);
            return;
        }
        Rectangle lower = rectangle(down);
        Rectangle upper = rectangle(down + 1);
        button(g, lower, hovered() == down);
        button(g, upper, hovered() == down + 1);
        g.setColor(TEXT);
        centred(g, "<", lower.x, lower.width, middle);
        centred(g, ">", upper.x, upper.width, middle);
        centred(g, row.value.get(), lower.x + lower.width, VALUE, middle);
    }

    private static void button(Graphics2D g, Rectangle bounds, boolean lit)
    {
        g.setColor(lit ? LIT : BUTTON);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
    }

    private static void centred(Graphics2D g, String text, int x, int width, int middle)
    {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, middle + metrics.getAscent() / 2 - 2);
    }
}
