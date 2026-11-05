package com.tonic.vitavr;

import com.tonic.vitavr.config.HeadFollow;
import com.tonic.vitavr.config.HeldItems;
import com.tonic.vitavr.config.OneEyeCamera;
import com.tonic.vitavr.config.RecordMode;
import com.tonic.vitavr.config.TurnMode;
import com.tonic.vitavr.config.ViewMode;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * VitaVR's settings, shared with the headset settings panel.
 */
@ConfigGroup(VitaVRConfig.GROUP)
public interface VitaVRConfig extends Config
{
    String GROUP = "vitavr";

    String VIEW_MODE = "viewMode";
    String TURN_MODE = "turnMode";
    String SNAP_DEGREES = "snapDegrees";
    String SMOOTH_DEGREES = "smoothDegreesPerSecond";
    String UNITS_PER_METRE = "unitsPerMetre";
    String TABLETOP_SCALE = "tabletopScale";
    String TABLE_HEIGHT = "tableHeight";
    String TABLE_TILT = "tableTiltDegrees";
    String MIRROR = "mirror";
    String RENDER_SCALE = "renderScale";
    String SAMPLES = "samples";
    String GHOST_HANDS = "ghostHands";
    String HEAD_CLEARANCE = "headClearanceCm";
    String HELD_ITEMS = "heldItems";
    String HELD_ITEM_PERCENT = "heldItemPercent";
    String FADE_NEAR_EYES = "fadeNearEyes";
    String HIDE_OWN_HEAD = "hideOwnHead";
    String HEAD_FOLLOW = "headFollow";
    String KEEP_ROOFS_INDOORS = "keepRoofsIndoors";
    String CEILING_LIGHT = "ceilingLightPercent";
    String HUD_SIZE = "hudSizePercent";
    String HUD_DISTANCE = "hudDistance";
    String CHAT_SIDE = "chatSidePercent";
    String INVENTORY_SIDE = "inventorySidePercent";
    String SIDES_APART = "sidesApartDegrees";
    String HUD_CANVAS_WIDTH = "hudCanvasWidth";
    String HUD_CANVAS_HEIGHT = "hudCanvasHeight";
    String OUTLINE_HOVERED = "outlineHoveredTile";
    String OUTLINE_LOCAL = "outlineLocalTile";
    String OUTLINE_DESTINATION = "outlineDestinationTile";
    String POINTER_LABEL = "pointerLabel";
    String VIGNETTE = "vignette";
    String WALL_FADE = "wallFade";
    String VIBRATION = "vibrationPercent";
    String FEEL_HITS_TAKEN = "feelHitsTaken";
    String FEEL_HITS_DEALT = "feelHitsDealt";
    String FEEL_LEVEL_UPS = "feelLevelUps";
    String HEARTBEAT = "heartbeat";
    String HEARTBEAT_BELOW = "heartbeatBelowPercent";
    String RECORD_SESSION = "recordSession";
    String RECORD_MODE = "recordMode";
    String ONE_EYE_CAMERA = "oneEyeCamera";
    String RECORD_HIDE_CHAT = "recordHideChat";
    String SETTINGS_TAB = "settingsTab";

    @ConfigSection(name = "View", description = "How the world is seen and turned", position = 0)
    String VIEW = "view";

    @ConfigSection(name = "Body", description = "Your body in first person", position = 1)
    String BODY = "body";

    @ConfigSection(name = "Interfaces", description = "The interfaces around you", position = 2)
    String INTERFACES = "interfaces";

    @ConfigSection(name = "Comfort", description = "Comfort and vibration", position = 3)
    String COMFORT = "comfort";

    @ConfigSection(name = "Recording", description = "Recording a session to video", position = 4)
    String RECORDING = "recording";

    @ConfigItem(keyName = VIEW_MODE, name = "View", description = "First person, or the world as a table in front of you", position = 0, section = VIEW)
    default ViewMode viewMode()
    {
        return ViewMode.FIRST_PERSON;
    }

    @ConfigItem(keyName = VIEW_MODE, name = "", description = "")
    void viewMode(ViewMode mode);

    @ConfigItem(keyName = TURN_MODE, name = "Turning", description = "How the right thumbstick turns you", position = 1, section = VIEW)
    default TurnMode turnMode()
    {
        return TurnMode.SNAP;
    }

    @Range(min = 5, max = 180)
    @ConfigItem(keyName = SNAP_DEGREES, name = "Snap turn", description = "Degrees turned by each snap", position = 2, section = VIEW)
    default int snapDegrees()
    {
        return 30;
    }

    @Range(min = 10, max = 360)
    @ConfigItem(keyName = SMOOTH_DEGREES, name = "Smooth turn", description = "Degrees a second turned with the stick held", position = 3, section = VIEW)
    default int smoothDegreesPerSecond()
    {
        return 90;
    }

    @ConfigItem(keyName = UNITS_PER_METRE, name = "World size", description = "Game units to a metre in first person (40 to 300; 110 is life size)", position = 5, section = VIEW)
    default double unitsPerMetre()
    {
        return 110;
    }

    @ConfigItem(keyName = TABLETOP_SCALE, name = "Table scale", description = "The table's size against first person (0.005 to 0.5)", position = 6, section = VIEW)
    default double tabletopScale()
    {
        return 0.04;
    }

    @ConfigItem(keyName = TABLE_HEIGHT, name = "Table height", description = "The table's height in metres (0.3 to 1.5)", position = 7, section = VIEW)
    default double tableHeight()
    {
        return 0.8;
    }

    @ConfigItem(keyName = MIRROR, name = "Mirror window", description = "Show one eye's view in the client window", position = 8, section = VIEW)
    default boolean mirror()
    {
        return true;
    }

    @ConfigItem(keyName = RENDER_SCALE, name = "Render scale", description = "Eye resolution against the headset's own (0.5 to 2), from the next launch", position = 9, section = VIEW)
    default double renderScale()
    {
        return 1.0;
    }

    @Range(min = 1, max = 8)
    @ConfigItem(keyName = SAMPLES, name = "Antialiasing", description = "Samples per pixel (1, 2, 4 or 8), from the next launch", position = 10, section = VIEW)
    default int samples()
    {
        return 4;
    }

    @Range(max = 60)
    @ConfigItem(keyName = TABLE_TILT, name = "", description = "", hidden = true)
    default int tableTiltDegrees()
    {
        return 0;
    }

    @ConfigItem(keyName = TABLE_TILT, name = "", description = "")
    void tableTiltDegrees(int degrees);

    @ConfigItem(keyName = GHOST_HANDS, name = "Hands when animating", description = "Show your hands while the game poses your arms", position = 0, section = BODY)
    default boolean ghostHands()
    {
        return true;
    }

    @Range(max = 40)
    @ConfigItem(keyName = HEAD_CLEARANCE, name = "Head clearance", description = "Centimetres the eyes sit ahead of your head", position = 1, section = BODY)
    default int headClearanceCm()
    {
        return 6;
    }

    @ConfigItem(keyName = HELD_ITEMS, name = "Held items", description = "When your weapon and shield are shown", position = 2, section = BODY)
    default HeldItems heldItems()
    {
        return HeldItems.WHEN_ACTING;
    }

    @Range(min = 30, max = 100)
    @ConfigItem(keyName = HELD_ITEM_PERCENT, name = "Held item size", description = "Held items' size in percent", position = 3, section = BODY)
    default int heldItemPercent()
    {
        return 60;
    }

    @ConfigItem(keyName = FADE_NEAR_EYES, name = "Fade near eyes", description = "Fade your body where it comes close to your eyes", position = 4, section = BODY)
    default boolean fadeNearEyes()
    {
        return true;
    }

    @ConfigItem(keyName = HIDE_OWN_HEAD, name = "Hide your head", description = "Hide your own head in first person", position = 5, section = BODY)
    default boolean hideOwnHead()
    {
        return true;
    }

    @ConfigItem(keyName = HEAD_FOLLOW, name = "View follows body", description = "When the view follows your head as the game moves it", position = 6, section = BODY)
    default HeadFollow headFollow()
    {
        return HeadFollow.ALWAYS;
    }

    @ConfigItem(keyName = KEEP_ROOFS_INDOORS, name = "Roofs indoors", description = "Keep the floors and roof over you indoors in first person, while the game's Hide roofs is off", position = 7, section = BODY)
    default boolean keepRoofsIndoors()
    {
        return true;
    }

    @Range(min = 10, max = 100)
    @ConfigItem(keyName = CEILING_LIGHT, name = "Ceiling light", description = "How light a kept roof is seen from under it, in percent of its lit colour", position = 8, section = BODY)
    default int ceilingLightPercent()
    {
        return 45;
    }

    @Range(min = 50, max = 250)
    @ConfigItem(keyName = HUD_SIZE, name = "Interfaces size", description = "The interfaces' size in percent", position = 0, section = INTERFACES)
    default int hudSizePercent()
    {
        return 160;
    }

    @ConfigItem(keyName = HUD_DISTANCE, name = "Interfaces distance", description = "Metres from you to the interfaces (0.5 to 4)", position = 1, section = INTERFACES)
    default double hudDistance()
    {
        return 2.2;
    }

    @Range(min = 50, max = 250)
    @ConfigItem(keyName = CHAT_SIDE, name = "Chat side size", description = "The chat column's size in percent", position = 2, section = INTERFACES)
    default int chatSidePercent()
    {
        return 100;
    }

    @Range(min = 50, max = 250)
    @ConfigItem(keyName = INVENTORY_SIDE, name = "Inventory side size", description = "The inventory column's size in percent", position = 3, section = INTERFACES)
    default int inventorySidePercent()
    {
        return 140;
    }

    @Range(max = 40)
    @ConfigItem(keyName = SIDES_APART, name = "Sides apart", description = "Degrees between the middle and the side columns", position = 4, section = INTERFACES)
    default int sidesApartDegrees()
    {
        return 0;
    }

    @ConfigItem(keyName = OUTLINE_HOVERED, name = "Hovered tile", description = "Outline the tile you point at", position = 5, section = INTERFACES)
    default boolean outlineHoveredTile()
    {
        return true;
    }

    @ConfigItem(keyName = OUTLINE_LOCAL, name = "Your tile", description = "Outline the tile you stand on", position = 6, section = INTERFACES)
    default boolean outlineLocalTile()
    {
        return true;
    }

    @ConfigItem(keyName = OUTLINE_DESTINATION, name = "Destination tile", description = "Outline where you are walking to", position = 7, section = INTERFACES)
    default boolean outlineDestinationTile()
    {
        return true;
    }

    @ConfigItem(keyName = POINTER_LABEL, name = "Pointer label", description = "Show what a click would do beside the pointer", position = 8, section = INTERFACES)
    default boolean pointerLabel()
    {
        return false;
    }

    @Range(min = 765, max = 2560)
    @ConfigItem(keyName = HUD_CANVAS_WIDTH, name = "", description = "", hidden = true)
    default int hudCanvasWidth()
    {
        return 1760;
    }

    @Range(min = 503, max = 1440)
    @ConfigItem(keyName = HUD_CANVAS_HEIGHT, name = "", description = "", hidden = true)
    default int hudCanvasHeight()
    {
        return 880;
    }

    @ConfigItem(keyName = VIGNETTE, name = "Vignette", description = "How dark the edges get as you turn or move (0 to 1)", position = 0, section = COMFORT)
    default double vignette()
    {
        return 0.5;
    }

    @ConfigItem(keyName = WALL_FADE, name = "Wall fade", description = "Fade to black when your head goes into a wall", position = 1, section = COMFORT)
    default boolean wallFade()
    {
        return true;
    }

    @Range(max = 100)
    @ConfigItem(keyName = VIBRATION, name = "Vibration", description = "Controller vibration in percent, 0 for none", position = 2, section = COMFORT)
    default int vibrationPercent()
    {
        return 60;
    }

    @ConfigItem(keyName = FEEL_HITS_TAKEN, name = "Feel hits taken", description = "Vibrate both hands when you are hit", position = 3, section = COMFORT)
    default boolean feelHitsTaken()
    {
        return true;
    }

    @ConfigItem(keyName = FEEL_HITS_DEALT, name = "Feel hits dealt", description = "Vibrate your right hand when you hit something", position = 4, section = COMFORT)
    default boolean feelHitsDealt()
    {
        return true;
    }

    @ConfigItem(keyName = FEEL_LEVEL_UPS, name = "Feel level ups", description = "Vibrate both hands on a level up", position = 5, section = COMFORT)
    default boolean feelLevelUps()
    {
        return true;
    }

    @ConfigItem(keyName = HEARTBEAT, name = "Low HP heartbeat", description = "A heartbeat in both hands while your hitpoints are low", position = 6, section = COMFORT)
    default boolean heartbeat()
    {
        return true;
    }

    @Range(min = 5, max = 75)
    @ConfigItem(keyName = HEARTBEAT_BELOW, name = "Heartbeat below", description = "Hitpoints percent the heartbeat starts below", position = 7, section = COMFORT)
    default int heartbeatBelowPercent()
    {
        return 25;
    }

    @ConfigItem(keyName = RECORD_SESSION, name = "Record session", description = "Record the next VR session to a video", position = 0, section = RECORDING)
    default boolean recordSession()
    {
        return false;
    }

    @ConfigItem(keyName = RECORD_MODE, name = "Record", description = "One eye's view, or both side by side", position = 1, section = RECORDING)
    default RecordMode recordMode()
    {
        return RecordMode.ONE_EYE;
    }

    @ConfigItem(keyName = ONE_EYE_CAMERA, name = "One eye camera", description = "A camera steadied on your head, or the left eye exactly", position = 2, section = RECORDING)
    default OneEyeCamera oneEyeCamera()
    {
        return OneEyeCamera.STEADIED;
    }

    @ConfigItem(keyName = RECORD_HIDE_CHAT, name = "Hide chat", description = "Leave the chat column out of the video", position = 3, section = RECORDING)
    default boolean recordHideChat()
    {
        return false;
    }

    @ConfigItem(keyName = SETTINGS_TAB, name = "", description = "", hidden = true)
    default String settingsTab()
    {
        return "";
    }
}
