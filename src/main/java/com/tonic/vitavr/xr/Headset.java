package com.tonic.vitavr.xr;

import com.tonic.Logger;
import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.Pose;
import lombok.Getter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.KHRCompositionLayerCylinder;
import org.lwjgl.openxr.KHROpenGLEnable;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrApplicationInfo;
import org.lwjgl.openxr.XrEventDataBaseHeader;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataReferenceSpaceChangePending;
import org.lwjgl.openxr.XrEventDataSessionStateChanged;
import org.lwjgl.openxr.XrExtensionProperties;
import org.lwjgl.openxr.XrFovf;
import org.lwjgl.openxr.XrFrameBeginInfo;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.openxr.XrFrameState;
import org.lwjgl.openxr.XrFrameWaitInfo;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLWin32KHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrInstanceCreateInfo;
import org.lwjgl.openxr.XrInstanceProperties;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrReferenceSpaceCreateInfo;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSessionBeginInfo;
import org.lwjgl.openxr.XrSessionCreateInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSpaceLocation;
import org.lwjgl.openxr.XrSystemGetInfo;
import org.lwjgl.openxr.XrView;
import org.lwjgl.openxr.XrViewConfigurationView;
import org.lwjgl.openxr.XrViewLocateInfo;
import org.lwjgl.openxr.XrViewState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static com.tonic.vitavr.xr.XrChecks.check;

/**
 * The OpenXR headset session, drawn through the client's OpenGL context, with the local space as the room.
 */
public final class Headset implements AutoCloseable
{
    private static final int EYES = 2;

    private final XrInstance instance;
    private final long system;
    @Getter
    private final XrSession session;
    @Getter
    private final XrSpace room;
    private final XrSpace floor;
    @Getter
    private final boolean cylinders;
    private final int[] eyeWidth = new int[EYES];
    private final int[] eyeHeight = new int[EYES];
    private final XrView.Buffer views;
    private final XrEventDataBuffer event;
    private final Pose[] eyePoses = new Pose[EYES];
    private final Fov[] eyeFovs = new Fov[EYES];
    @Getter
    private boolean running;
    @Getter
    private boolean over;
    @Getter
    private boolean focused;
    @Getter
    private long displayTime;
    @Getter
    private long displayPeriod;
    private boolean shouldRender;
    private long recentreAt = -1;
    private boolean closed;

    private Headset(XrInstance instance, long system, XrSession session, XrSpace room, XrSpace floor, boolean cylinders)
    {
        this.instance = instance;
        this.system = system;
        this.session = session;
        this.room = room;
        this.floor = floor;
        this.cylinders = cylinders;
        this.views = XrView.calloc(EYES);
        for (int eye = 0; eye < EYES; eye++)
        {
            views.get(eye).type$Default();
        }
        this.event = XrEventDataBuffer.calloc();
    }

    /**
     * Opens the headset using the OpenGL context current on the calling thread.
     *
     * @param deviceContext The window device context (HDC).
     * @param glContext The OpenGL context (HGLRC).
     * @return The opened headset.
     * @throws IllegalStateException if there is no runtime or headset, or the runtime refuses the context
     */
    public static Headset open(long deviceContext, long glContext)
    {
        boolean cylinders = offered(KHRCompositionLayerCylinder.XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME);
        XrInstance instance = createInstance(cylinders);
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrInstanceProperties properties = XrInstanceProperties.calloc(stack).type$Default();
            if (XR10.xrGetInstanceProperties(instance, properties) >= 0)
            {
                Logger.info("[VitaVR] Runtime " + properties.runtimeNameString() + (cylinders ? ", curved layers" : ", no curved layers"));
            }
            long system = systemOf(instance);

            // Required before creating a session
            XrGraphicsRequirementsOpenGLKHR requirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack).type$Default();
            check(KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(instance, system, requirements), instance, "Reading the OpenGL the headset needs");

            XrGraphicsBindingOpenGLWin32KHR binding = XrGraphicsBindingOpenGLWin32KHR.calloc(stack).type$Default().hDC(deviceContext).hGLRC(glContext);
            XrSessionCreateInfo sessionInfo = XrSessionCreateInfo.calloc(stack).type$Default().next(binding.address()).systemId(system);
            PointerBuffer made = stack.mallocPointer(1);
            check(XR10.xrCreateSession(instance, sessionInfo, made), instance, "Starting the headset's session");
            XrSession session = new XrSession(made.get(0), instance);

            XrSpace room = space(session, XR10.XR_REFERENCE_SPACE_TYPE_LOCAL, stack);
            XrSpace floor = offers(session, XR10.XR_REFERENCE_SPACE_TYPE_STAGE, stack) ? space(session, XR10.XR_REFERENCE_SPACE_TYPE_STAGE, stack) : null;
            Headset headset = new Headset(instance, system, session, room, floor, cylinders);
            headset.readEyeSizes(stack);
            return headset;
        }
        catch (RuntimeException e)
        {
            XR10.xrDestroyInstance(instance);
            throw e;
        }
    }

    private static boolean offered(String name)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer count = stack.mallocInt(1);
            if (XR10.xrEnumerateInstanceExtensionProperties((CharSequence) null, count, null) < 0)
            {
                return false;
            }
            XrExtensionProperties.Buffer properties = XrExtensionProperties.calloc(count.get(0), stack);
            for (int i = 0; i < count.get(0); i++)
            {
                properties.get(i).type$Default();
            }
            if (XR10.xrEnumerateInstanceExtensionProperties((CharSequence) null, count, properties) < 0)
            {
                return false;
            }
            for (int i = 0; i < count.get(0); i++)
            {
                if (name.equals(properties.get(i).extensionNameString()))
                {
                    return true;
                }
            }
            return false;
        }
    }

    private static XrInstance createInstance(boolean cylinders)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer extensions = stack.mallocPointer(cylinders ? 2 : 1);
            extensions.put(stack.UTF8(KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME));
            if (cylinders)
            {
                extensions.put(stack.UTF8(KHRCompositionLayerCylinder.XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME));
            }
            extensions.flip();
            XrApplicationInfo application = XrApplicationInfo.calloc(stack)
                    .applicationName(stack.UTF8("VitaVR"))
                    .applicationVersion(1)
                    .engineName(stack.UTF8("VitaLite"))
                    .engineVersion(1)
                    .apiVersion(XR10.XR_MAKE_VERSION(1, 0, 0));
            XrInstanceCreateInfo info = XrInstanceCreateInfo.calloc(stack).type$Default().applicationInfo(application).enabledExtensionNames(extensions);
            PointerBuffer made = stack.mallocPointer(1);
            check(XR10.xrCreateInstance(info, made), null, "Starting OpenXR (is SteamVR or another OpenXR runtime installed and active?)");
            return new XrInstance(made.get(0), info);
        }
    }

    private static long systemOf(XrInstance instance)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrSystemGetInfo info = XrSystemGetInfo.calloc(stack).type$Default().formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);
            LongBuffer system = stack.mallocLong(1);
            check(XR10.xrGetSystem(instance, info, system), instance, "Finding the headset (is it connected and showing in the runtime?)");
            return system.get(0);
        }
    }

    private static boolean offers(XrSession session, int type, MemoryStack stack)
    {
        IntBuffer count = stack.mallocInt(1);
        check(XR10.xrEnumerateReferenceSpaces(session, count, null), session.getInstance(), "Listing the room's spaces");
        IntBuffer types = stack.mallocInt(count.get(0));
        check(XR10.xrEnumerateReferenceSpaces(session, count, types), session.getInstance(), "Listing the room's spaces");
        for (int i = 0; i < count.get(0); i++)
        {
            if (types.get(i) == type)
            {
                return true;
            }
        }
        return false;
    }

    private static XrSpace space(XrSession session, int type, MemoryStack stack)
    {
        XrPosef identity = XrPosef.calloc(stack);
        identity.orientation().w(1);
        XrReferenceSpaceCreateInfo info = XrReferenceSpaceCreateInfo.calloc(stack).type$Default().referenceSpaceType(type).poseInReferenceSpace(identity);
        PointerBuffer made = stack.mallocPointer(1);
        check(XR10.xrCreateReferenceSpace(session, info, made), session.getInstance(), "Making the room's space");
        return new XrSpace(made.get(0), session);
    }

    private void readEyeSizes(MemoryStack stack)
    {
        IntBuffer count = stack.mallocInt(1);
        check(XR10.xrEnumerateViewConfigurationViews(instance, system, XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, null), instance, "Reading the eyes' sizes");
        XrViewConfigurationView.Buffer configured = XrViewConfigurationView.calloc(count.get(0), stack);
        for (int i = 0; i < count.get(0); i++)
        {
            configured.get(i).type$Default();
        }
        check(XR10.xrEnumerateViewConfigurationViews(instance, system, XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, configured), instance, "Reading the eyes' sizes");
        for (int eye = 0; eye < EYES; eye++)
        {
            eyeWidth[eye] = configured.get(eye).recommendedImageRectWidth();
            eyeHeight[eye] = configured.get(eye).recommendedImageRectHeight();
        }
    }

    /**
     * Polls runtime events, beginning and ending the session as asked.
     */
    public void pollEvents()
    {
        while (true)
        {
            event.type$Default();
            event.next(MemoryUtil.NULL);
            int result = XR10.xrPollEvent(instance, event);
            if (result == XR10.XR_EVENT_UNAVAILABLE)
            {
                return;
            }
            check(result, instance, "Reading the headset's events");
            XrEventDataBaseHeader header = XrEventDataBaseHeader.create(event.address());
            if (header.type() == XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING)
            {
                over = true;
                return;
            }
            if (header.type() == XR10.XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING)
            {
                XrEventDataReferenceSpaceChangePending change = XrEventDataReferenceSpaceChangePending.create(event.address());
                if (change.referenceSpaceType() == XR10.XR_REFERENCE_SPACE_TYPE_LOCAL)
                {
                    recentreAt = change.changeTime();
                }
            }
            if (header.type() == XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED)
            {
                changeState(XrEventDataSessionStateChanged.create(event.address()).state());
            }
        }
    }

    private void changeState(int state)
    {
        focused = state == XR10.XR_SESSION_STATE_FOCUSED;
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            switch (state)
            {
                case XR10.XR_SESSION_STATE_READY:
                    XrSessionBeginInfo info = XrSessionBeginInfo.calloc(stack).type$Default().primaryViewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
                    check(XR10.xrBeginSession(session, info), instance, "Beginning the headset's session");
                    running = true;
                    break;
                case XR10.XR_SESSION_STATE_STOPPING:
                    check(XR10.xrEndSession(session), instance, "Ending the headset's session");
                    running = false;
                    break;
                case XR10.XR_SESSION_STATE_EXITING:
                case XR10.XR_SESSION_STATE_LOSS_PENDING:
                    over = true;
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Waits for and begins the next frame, then locates the eyes. Blocks to pace the client to the headset.
     *
     * @return Whether to draw the frame. Call endFrame either way.
     */
    public boolean beginFrame()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrFrameState state = XrFrameState.calloc(stack).type$Default();
            check(XR10.xrWaitFrame(session, XrFrameWaitInfo.calloc(stack).type$Default(), state), instance, "Waiting for the headset's frame");
            check(XR10.xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type$Default()), instance, "Beginning the headset's frame");
            displayTime = state.predictedDisplayTime();
            displayPeriod = state.predictedDisplayPeriod();
            shouldRender = state.shouldRender();
            if (!shouldRender)
            {
                return false;
            }

            XrViewState viewState = XrViewState.calloc(stack).type$Default();
            XrViewLocateInfo locate = XrViewLocateInfo.calloc(stack).type$Default()
                    .viewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
                    .displayTime(displayTime)
                    .space(room);
            IntBuffer count = stack.mallocInt(1);
            check(XR10.xrLocateViews(session, locate, viewState, count, views), instance, "Finding the eyes");
            long valid = XR10.XR_VIEW_STATE_ORIENTATION_VALID_BIT | XR10.XR_VIEW_STATE_POSITION_VALID_BIT;
            if ((viewState.viewStateFlags() & valid) != valid)
            {
                shouldRender = false;
                return false;
            }
            for (int eye = 0; eye < EYES; eye++)
            {
                XrView view = views.get(eye);
                XrPosef pose = view.pose();
                eyePoses[eye] = new Pose(pose.position$().x(), pose.position$().y(), pose.position$().z(),
                        pose.orientation().x(), pose.orientation().y(), pose.orientation().z(), pose.orientation().w());
                XrFovf fov = view.fov();
                eyeFovs[eye] = new Fov(fov.angleLeft(), fov.angleRight(), fov.angleUp(), fov.angleDown());
            }
            return true;
        }
    }

    /**
     * Ends the current frame with the given layers.
     *
     * @param layers The layer addresses, bottom first, or null for none.
     */
    public void endFrame(PointerBuffer layers)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrFrameEndInfo info = XrFrameEndInfo.calloc(stack).type$Default()
                    .displayTime(displayTime)
                    .environmentBlendMode(XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
                    .layers(shouldRender ? layers : null);
            check(XR10.xrEndFrame(session, info), instance, "Showing the headset's frame");
        }
    }

    /**
     * Gets the floor height in the room from the stage space.
     *
     * @return The floor height in metres, or NaN if unknown.
     */
    public double floorHeight()
    {
        if (floor == null)
        {
            return Double.NaN;
        }
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrSpaceLocation location = XrSpaceLocation.calloc(stack).type$Default();
            if (XR10.xrLocateSpace(floor, room, displayTime, location) < 0 || (location.locationFlags() & XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT) == 0)
            {
                return Double.NaN;
            }
            return location.pose().position$().y();
        }
    }

    /**
     * Checks whether the room was recentred since last asked, once the change applies to the current frame.
     *
     * @return Whether the room was recentred.
     */
    public boolean takeRecentred()
    {
        if (recentreAt < 0 || displayTime < recentreAt)
        {
            return false;
        }
        recentreAt = -1;
        return true;
    }

    /**
     * @param eye 0 for the left, 1 for the right.
     * @return The recommended eye width, in pixels.
     */
    public int eyeWidth(int eye)
    {
        return eyeWidth[eye];
    }

    /**
     * @param eye 0 for the left, 1 for the right.
     * @return The recommended eye height, in pixels.
     */
    public int eyeHeight(int eye)
    {
        return eyeHeight[eye];
    }

    /**
     * @param eye 0 for the left, 1 for the right.
     * @return The eye pose in the room for the current frame.
     */
    public Pose eyePose(int eye)
    {
        return eyePoses[eye];
    }

    /**
     * @param eye 0 for the left, 1 for the right.
     * @return The eye's field of view for the current frame.
     */
    public Fov eyeFov(int eye)
    {
        return eyeFovs[eye];
    }

    /**
     * @param eye 0 for the left, 1 for the right.
     * @return The eye's raw OpenXR view.
     */
    public XrView view(int eye)
    {
        return views.get(eye);
    }

    /**
     * Destroys the session and instance. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        XR10.xrDestroySpace(room);
        if (floor != null)
        {
            XR10.xrDestroySpace(floor);
        }
        XR10.xrDestroySession(session);
        XR10.xrDestroyInstance(instance);
        views.free();
        event.free();
    }
}
