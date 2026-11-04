package com.tonic.vitavr.xr;

import com.tonic.Logger;
import com.tonic.vitavr.space.Pose;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrAction;
import org.lwjgl.openxr.XrActionCreateInfo;
import org.lwjgl.openxr.XrActionSet;
import org.lwjgl.openxr.XrActionSetCreateInfo;
import org.lwjgl.openxr.XrActionSpaceCreateInfo;
import org.lwjgl.openxr.XrActionStateBoolean;
import org.lwjgl.openxr.XrActionStateFloat;
import org.lwjgl.openxr.XrActionStateGetInfo;
import org.lwjgl.openxr.XrActionStateVector2f;
import org.lwjgl.openxr.XrActionSuggestedBinding;
import org.lwjgl.openxr.XrActionsSyncInfo;
import org.lwjgl.openxr.XrActiveActionSet;
import org.lwjgl.openxr.XrHapticActionInfo;
import org.lwjgl.openxr.XrHapticBaseHeader;
import org.lwjgl.openxr.XrHapticVibration;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrInteractionProfileSuggestedBinding;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSessionActionSetsAttachInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSpaceLocation;
import org.lwjgl.system.MemoryStack;

import java.nio.LongBuffer;

import static com.tonic.vitavr.xr.XrChecks.check;

/**
 * Reads both controllers through OpenXR actions, with bindings for Touch, Index and simple controllers.
 */
public final class Controllers implements AutoCloseable
{
    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    private static final int HANDS = 2;
    private static final long TICK_NANOS = 15_000_000L;
    private static final float TICK_AMPLITUDE = 0.4f;
    private static final float PRESSED = 0.6f;
    private static final float LET_GO = 0.4f;

    private final XrInstance instance;
    private final XrSession session;
    private final XrActionSet set;
    private final long[] handPaths;
    private final XrAction aim;
    private final XrAction trigger;
    private final XrAction grip;
    private final XrAction stick;
    private final XrAction hud;
    private final XrAction keyboard;
    private final XrAction menu;
    private final XrAction view;
    private final XrAction haptic;
    private final XrSpace[] aimSpaces = new XrSpace[HANDS];

    private final Pose[] aims = new Pose[HANDS];
    private final boolean[] triggerDown = new boolean[HANDS];
    private final boolean[] triggerWasDown = new boolean[HANDS];
    private final boolean[] gripDown = new boolean[HANDS];
    private final boolean[] gripWasDown = new boolean[HANDS];
    private final float[] stickX = new float[HANDS];
    private final float[] stickY = new float[HANDS];
    private boolean hudPressed;
    private boolean keyboardPressed;
    private boolean menuPressed;
    private boolean viewPressed;
    private boolean closed;

    /**
     * Creates both controllers' actions on the session.
     *
     * @param session The headset's session.
     * @throws IllegalStateException if the runtime refuses the actions
     */
    public Controllers(XrSession session)
    {
        this.session = session;
        this.instance = session.getInstance();
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            handPaths = new long[]{path("/user/hand/left"), path("/user/hand/right")};
            XrActionSetCreateInfo setInfo = XrActionSetCreateInfo.calloc(stack).type$Default().actionSetName(stack.UTF8("gameplay"))
                    .localizedActionSetName(stack.UTF8("Gameplay")).priority(0);
            PointerBuffer made = stack.mallocPointer(1);
            check(XR10.xrCreateActionSet(instance, setInfo, made), instance, "Making the controllers' actions");
            set = new XrActionSet(made.get(0), instance);
        }
        aim = action("aim", "Aim", XR10.XR_ACTION_TYPE_POSE_INPUT, true);
        trigger = action("trigger", "Click", XR10.XR_ACTION_TYPE_FLOAT_INPUT, true);
        grip = action("grip", "Right click and ctrl", XR10.XR_ACTION_TYPE_FLOAT_INPUT, true);
        stick = action("stick", "Turn and scroll", XR10.XR_ACTION_TYPE_VECTOR2F_INPUT, true);
        hud = action("hud", "Show or hide the interfaces", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT, false);
        keyboard = action("keyboard", "Show or hide the keyboard", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT, false);
        menu = action("settings", "Show or hide the settings", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT, false);
        view = action("view", "First person or tabletop", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT, false);
        haptic = action("haptic", "Tick", XR10.XR_ACTION_TYPE_VIBRATION_OUTPUT, true);
        suggest("/interaction_profiles/oculus/touch_controller", new String[][]{
                {"aim", "/user/hand/left/input/aim/pose"}, {"aim", "/user/hand/right/input/aim/pose"},
                {"trigger", "/user/hand/left/input/trigger/value"}, {"trigger", "/user/hand/right/input/trigger/value"},
                {"grip", "/user/hand/left/input/squeeze/value"}, {"grip", "/user/hand/right/input/squeeze/value"},
                {"stick", "/user/hand/left/input/thumbstick"}, {"stick", "/user/hand/right/input/thumbstick"},
                {"hud", "/user/hand/right/input/b/click"}, {"keyboard", "/user/hand/left/input/y/click"},
                {"settings", "/user/hand/left/input/x/click"}, {"settings", "/user/hand/left/input/menu/click"}, {"view", "/user/hand/right/input/a/click"},
                {"haptic", "/user/hand/left/output/haptic"}, {"haptic", "/user/hand/right/output/haptic"}});
        suggest("/interaction_profiles/valve/index_controller", new String[][]{
                {"aim", "/user/hand/left/input/aim/pose"}, {"aim", "/user/hand/right/input/aim/pose"},
                {"trigger", "/user/hand/left/input/trigger/value"}, {"trigger", "/user/hand/right/input/trigger/value"},
                {"grip", "/user/hand/left/input/squeeze/value"}, {"grip", "/user/hand/right/input/squeeze/value"},
                {"stick", "/user/hand/left/input/thumbstick"}, {"stick", "/user/hand/right/input/thumbstick"},
                {"hud", "/user/hand/right/input/b/click"}, {"keyboard", "/user/hand/left/input/b/click"},
                {"settings", "/user/hand/left/input/a/click"}, {"view", "/user/hand/right/input/a/click"},
                {"haptic", "/user/hand/left/output/haptic"}, {"haptic", "/user/hand/right/output/haptic"}});
        suggest("/interaction_profiles/khr/simple_controller", new String[][]{
                {"aim", "/user/hand/left/input/aim/pose"}, {"aim", "/user/hand/right/input/aim/pose"},
                {"trigger", "/user/hand/left/input/select/click"}, {"trigger", "/user/hand/right/input/select/click"},
                {"settings", "/user/hand/left/input/menu/click"}, {"hud", "/user/hand/right/input/menu/click"},
                {"haptic", "/user/hand/left/output/haptic"}, {"haptic", "/user/hand/right/output/haptic"}});
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrSessionActionSetsAttachInfo attach = XrSessionActionSetsAttachInfo.calloc(stack).type$Default().actionSets(stack.pointers(set));
            check(XR10.xrAttachSessionActionSets(session, attach), instance, "Giving the session the controllers' actions");
            for (int hand = 0; hand < HANDS; hand++)
            {
                XrPosef identity = XrPosef.calloc(stack);
                identity.orientation().w(1);
                XrActionSpaceCreateInfo info = XrActionSpaceCreateInfo.calloc(stack).type$Default().action(aim).subactionPath(handPaths[hand]).poseInActionSpace(identity);
                PointerBuffer made = stack.mallocPointer(1);
                check(XR10.xrCreateActionSpace(session, info, made), instance, "Making a hand's aim");
                aimSpaces[hand] = new XrSpace(made.get(0), session);
            }
        }
    }

    private long path(String text)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            LongBuffer path = stack.mallocLong(1);
            check(XR10.xrStringToPath(instance, stack.UTF8(text), path), instance, "Naming " + text);
            return path.get(0);
        }
    }

    private XrAction action(String name, String shown, int type, boolean perHand)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrActionCreateInfo info = XrActionCreateInfo.calloc(stack).type$Default().actionName(stack.UTF8(name)).localizedActionName(stack.UTF8(shown)).actionType(type);
            if (perHand)
            {
                info.subactionPaths(stack.longs(handPaths));
            }
            PointerBuffer made = stack.mallocPointer(1);
            check(XR10.xrCreateAction(set, info, made), instance, "Making the " + name + " action");
            return new XrAction(made.get(0), set);
        }
    }

    private void suggest(String profile, String[][] bindings)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrActionSuggestedBinding.Buffer suggested = XrActionSuggestedBinding.calloc(bindings.length, stack);
            for (int i = 0; i < bindings.length; i++)
            {
                suggested.get(i).action(actionNamed(bindings[i][0])).binding(path(bindings[i][1]));
            }
            XrInteractionProfileSuggestedBinding info = XrInteractionProfileSuggestedBinding.calloc(stack).type$Default().interactionProfile(path(profile))
                    .suggestedBindings(suggested);
            int result = XR10.xrSuggestInteractionProfileBindings(instance, info);
            if (result < 0)
            {
                Logger.warn("[VitaVR] The runtime took no bindings for " + profile + ": " + result);
            }
        }
    }

    private XrAction actionNamed(String name)
    {
        switch (name)
        {
            case "aim":
                return aim;
            case "trigger":
                return trigger;
            case "grip":
                return grip;
            case "stick":
                return stick;
            case "hud":
                return hud;
            case "keyboard":
                return keyboard;
            case "settings":
                return menu;
            case "view":
                return view;
            case "haptic":
                return haptic;
            default:
                throw new IllegalArgumentException("No action " + name);
        }
    }

    /**
     * Reads the controllers for a frame.
     *
     * @param room The space the aims are located in.
     * @param time The frame's display time.
     */
    public void sync(XrSpace room, long time)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrActiveActionSet.Buffer active = XrActiveActionSet.calloc(1, stack);
            active.get(0).actionSet(set);
            check(XR10.xrSyncActions(session, XrActionsSyncInfo.calloc(stack).type$Default().activeActionSets(active)), instance, "Reading the controllers");
            for (int hand = 0; hand < HANDS; hand++)
            {
                triggerWasDown[hand] = triggerDown[hand];
                gripWasDown[hand] = gripDown[hand];
                triggerDown[hand] = held(floatState(trigger, hand, stack), triggerDown[hand]);
                gripDown[hand] = held(floatState(grip, hand, stack), gripDown[hand]);
                XrActionStateVector2f vector = XrActionStateVector2f.calloc(stack).type$Default();
                check(XR10.xrGetActionStateVector2f(session, info(stick, handPaths[hand], stack), vector), instance, "Reading a thumbstick");
                stickX[hand] = vector.isActive() ? vector.currentState().x() : 0;
                stickY[hand] = vector.isActive() ? vector.currentState().y() : 0;
                XrSpaceLocation location = XrSpaceLocation.calloc(stack).type$Default();
                check(XR10.xrLocateSpace(aimSpaces[hand], room, time, location), instance, "Finding a hand");
                long valid = XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT | XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT;
                if ((location.locationFlags() & valid) == valid)
                {
                    XrPosef pose = location.pose();
                    aims[hand] = new Pose(pose.position$().x(), pose.position$().y(), pose.position$().z(), pose.orientation().x(), pose.orientation().y(),
                            pose.orientation().z(), pose.orientation().w());
                }
                else
                {
                    aims[hand] = null;
                }
            }
            hudPressed = pressed(hud, stack);
            keyboardPressed = pressed(keyboard, stack);
            menuPressed = pressed(menu, stack);
            viewPressed = pressed(view, stack);
        }
    }

    private float floatState(XrAction action, int hand, MemoryStack stack)
    {
        XrActionStateFloat state = XrActionStateFloat.calloc(stack).type$Default();
        check(XR10.xrGetActionStateFloat(session, info(action, handPaths[hand], stack), state), instance, "Reading a button");
        return state.isActive() ? state.currentState() : 0;
    }

    private boolean pressed(XrAction action, MemoryStack stack)
    {
        XrActionStateBoolean state = XrActionStateBoolean.calloc(stack).type$Default();
        check(XR10.xrGetActionStateBoolean(session, info(action, XR10.XR_NULL_PATH, stack), state), instance, "Reading a button");
        return state.isActive() && state.changedSinceLastSync() && state.currentState();
    }

    private static XrActionStateGetInfo info(XrAction action, long subaction, MemoryStack stack)
    {
        return XrActionStateGetInfo.calloc(stack).type$Default().action(action).subactionPath(subaction);
    }

    private static boolean held(float value, boolean wasHeld)
    {
        return wasHeld ? value > LET_GO : value > PRESSED;
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return The hand's aim pose in the room, or null while not tracked.
     */
    public Pose aim(int hand)
    {
        return aims[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return Whether the trigger was pressed this frame.
     */
    public boolean triggerPressed(int hand)
    {
        return triggerDown[hand] && !triggerWasDown[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return Whether the trigger was released this frame.
     */
    public boolean triggerReleased(int hand)
    {
        return !triggerDown[hand] && triggerWasDown[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return Whether the grip was pressed this frame.
     */
    public boolean gripPressed(int hand)
    {
        return gripDown[hand] && !gripWasDown[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return Whether the grip was released this frame.
     */
    public boolean gripReleased(int hand)
    {
        return !gripDown[hand] && gripWasDown[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return Whether the grip is held.
     */
    public boolean gripHeld(int hand)
    {
        return gripDown[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return The thumbstick x, -1 left to 1 right.
     */
    public float stickX(int hand)
    {
        return stickX[hand];
    }

    /**
     * @param hand LEFT or RIGHT.
     * @return The thumbstick y, -1 down to 1 up.
     */
    public float stickY(int hand)
    {
        return stickY[hand];
    }

    /**
     * @return Whether the HUD button was pressed this frame.
     */
    public boolean hudPressed()
    {
        return hudPressed;
    }

    /**
     * @return Whether the keyboard button was pressed this frame.
     */
    public boolean keyboardPressed()
    {
        return keyboardPressed;
    }

    /**
     * @return Whether the settings button was pressed this frame.
     */
    public boolean settingsPressed()
    {
        return menuPressed;
    }

    /**
     * @return Whether the view button was pressed this frame.
     */
    public boolean viewPressed()
    {
        return viewPressed;
    }

    /**
     * Plays a short click vibration on a hand.
     *
     * @param hand LEFT or RIGHT.
     */
    public void tick(int hand)
    {
        pulse(hand, TICK_NANOS, TICK_AMPLITUDE);
    }

    /**
     * Vibrates a hand.
     *
     * @param hand LEFT or RIGHT.
     * @param durationNanos The duration, in nanoseconds.
     * @param amplitude The strength, 0 to 1.
     */
    public void pulse(int hand, long durationNanos, float amplitude)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrHapticVibration vibration = XrHapticVibration.calloc(stack).type$Default().duration(durationNanos).frequency(XR10.XR_FREQUENCY_UNSPECIFIED)
                    .amplitude(Math.max(0, Math.min(1, amplitude)));
            XrHapticActionInfo info = XrHapticActionInfo.calloc(stack).type$Default().action(haptic).subactionPath(handPaths[hand]);
            XR10.xrApplyHapticFeedback(session, info, XrHapticBaseHeader.create(vibration.address()));
        }
    }

    /**
     * Destroys the actions and aim spaces. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        for (XrSpace space : aimSpaces)
        {
            if (space != null)
            {
                XR10.xrDestroySpace(space);
            }
        }
        XR10.xrDestroyActionSet(set);
    }
}
