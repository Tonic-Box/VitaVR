package com.tonic.vitavr.render.scene.hd;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Reflective access to the 117 HD internals needed to record the scene once and draw it into every view.
 */
final class HdInternals
{
    private static final String ROOT = "rs117.hd.";
    /** A frustum plane every point lies well inside of. */
    private static final float[] OPEN_PLANE = {0, 0, 0, 1e30f};

    private final Object plugin;
    private final Object renderer;
    private final Object sceneCamera;
    private final Object renderState;
    private final float[][] cameraPlanes;
    private final float[][] pluginPlanes;
    private final float[][] planesScratch = new float[6][4];
    private final float[] cameraPosition;
    private final Field fboScene;
    private final Field fboSceneResolve;
    private final Field sceneResolution;
    private final Field sceneViewport;
    private final Field msaaSamples;
    private final Field viewProjMatrix;
    private final Field invViewProjMatrix;
    private final Field uboGlobal;
    private final Field shouldRenderScene;
    private final Field sceneFboValid;
    private final Field projectionMatrix;
    private final Field invProjectionMatrix;
    private final Field cameraPos;
    private final Field viewportSize;
    private final Field sceneResolutionUniform;
    private final MethodHandle getFrustumPlanes;
    private final MethodHandle scenePass;
    private final MethodHandle tiledLightingPass;
    private final MethodHandle directionalShadowPass;
    private final MethodHandle resetState;
    private final MethodHandle upload;
    private final MethodHandle setFloats;
    private final MethodHandle setInts;
    private final int[] viewResolution = new int[2];
    private int desktopFramebuffer;
    private int desktopResolve;
    private int desktopSamples;
    private boolean desktopFboValid;

    /**
     * Looks up the 117 HD internals.
     *
     * @param plugin The running 117 HD plugin.
     * @throws IllegalStateException If 117 HD is not running its zone renderer, or is not a version VitaVR can draw with.
     */
    HdInternals(Object plugin)
    {
        this.plugin = plugin;
        try
        {
            Class<?> pluginClass = plugin.getClass();
            ClassLoader loader = pluginClass.getClassLoader();
            Class<?> zoneRenderer = Class.forName(ROOT + "renderer.zone.ZoneRenderer", false, loader);
            Class<?> camera = Class.forName(ROOT + "utils.Camera", false, loader);
            Class<?> state = Class.forName(ROOT + "utils.RenderState", false, loader);
            Class<?> uniformBuffer = Class.forName(ROOT + "opengl.uniforms.UniformBuffer", false, loader);
            Class<?> property = Class.forName(ROOT + "opengl.uniforms.UniformBuffer$Property", false, loader);
            Class<?> global = Class.forName(ROOT + "opengl.uniforms.UBOGlobal", false, loader);

            renderer = field(pluginClass, "renderer").get(plugin);
            if (!zoneRenderer.isInstance(renderer))
            {
                throw new IllegalStateException("117 HD is not using its zone renderer");
            }
            sceneCamera = field(zoneRenderer, "sceneCamera").get(renderer);
            renderState = field(zoneRenderer, "renderState").get(renderer);
            cameraPlanes = (float[][]) field(camera, "frustumPlanes").get(sceneCamera);
            pluginPlanes = (float[][]) field(pluginClass, "cameraFrustum").get(plugin);
            cameraPosition = (float[]) field(pluginClass, "cameraPosition").get(plugin);
            fboScene = field(pluginClass, "fboScene");
            fboSceneResolve = field(pluginClass, "fboSceneResolve");
            sceneResolution = field(pluginClass, "sceneResolution");
            sceneViewport = field(pluginClass, "sceneViewport");
            msaaSamples = field(pluginClass, "msaaSamples");
            viewProjMatrix = field(pluginClass, "viewProjMatrix");
            invViewProjMatrix = field(pluginClass, "invViewProjMatrix");
            uboGlobal = field(pluginClass, "uboGlobal");
            shouldRenderScene = field(zoneRenderer, "shouldRenderScene");
            sceneFboValid = field(zoneRenderer, "sceneFboValid");
            projectionMatrix = field(global, "projectionMatrix");
            invProjectionMatrix = field(global, "invProjectionMatrix");
            cameraPos = field(global, "cameraPos");
            viewportSize = field(global, "viewportSize");
            sceneResolutionUniform = field(global, "sceneResolution");

            getFrustumPlanes = handle(method(camera, "getFrustumPlanes", float[][].class), MethodType.methodType(void.class, Object.class, float[][].class));
            scenePass = handle(method(zoneRenderer, "scenePass"), MethodType.methodType(void.class, Object.class));
            tiledLightingPass = handle(method(zoneRenderer, "tiledLightingPass"), MethodType.methodType(void.class, Object.class));
            directionalShadowPass = handle(method(zoneRenderer, "directionalShadowPass"), MethodType.methodType(void.class, Object.class));
            resetState = handle(method(state, "reset"), MethodType.methodType(void.class, Object.class));
            upload = handle(method(uniformBuffer, "upload"), MethodType.methodType(void.class, Object.class));
            setFloats = handle(method(property, "set", float[].class), MethodType.methodType(void.class, Object.class, float[].class));
            setInts = handle(method(property, "set", int.class, int.class), MethodType.methodType(void.class, Object.class, int.class, int.class));
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            throw new IllegalStateException("This 117 HD is not one VitaVR can draw with: " + e.getMessage(), e);
        }
    }

    /**
     * @return The zone renderer, as the client's draw callbacks.
     */
    Object renderer()
    {
        return renderer;
    }

    /**
     * Opens the culling frustum all the way round for this frame, after the top level scene is set up.
     */
    void openFrustum()
    {
        try
        {
            // Settles the camera's own planes first so they are not worked out again over ours
            getFrustumPlanes.invokeExact(sceneCamera, planesScratch);
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
        for (int plane = 0; plane < cameraPlanes.length; plane++)
        {
            System.arraycopy(OPEN_PLANE, 0, cameraPlanes[plane], 0, 4);
            System.arraycopy(OPEN_PLANE, 0, pluginPlanes[plane], 0, 4);
        }
    }

    /**
     * Draws the frame's directional shadows once for every view.
     */
    void drawShadows()
    {
        try
        {
            directionalShadowPass.invokeExact(renderer);
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
    }

    /**
     * Draws the recorded scene into a framebuffer as seen from one view, lights included.
     *
     * @param framebuffer The multisampled framebuffer drawn into.
     * @param width Its width in pixels.
     * @param height Its height in pixels.
     * @param samples Its samples per pixel.
     * @param worldToClip The view's world to clip matrix.
     * @param clipToWorld Its inverse.
     * @param eye Where the view is, across, down and along.
     */
    void drawView(int framebuffer, int width, int height, int samples, float[] worldToClip, float[] clipToWorld, float[] eye)
    {
        int[] resolution = (int[]) get(sceneResolution, plugin);
        int desktop = getInt(fboScene, plugin);
        int desktopMsaa = getInt(msaaSamples, plugin);
        viewResolution[0] = width;
        viewResolution[1] = height;
        set(sceneResolution, plugin, viewResolution);
        setInt(fboScene, plugin, framebuffer);
        setInt(msaaSamples, plugin, samples);
        try
        {
            Object global = get(uboGlobal, plugin);
            setFloats.invokeExact(get(projectionMatrix, global), worldToClip);
            setFloats.invokeExact(get(invProjectionMatrix, global), clipToWorld);
            setFloats.invokeExact(get(cameraPos, global), eye);
            setInts.invokeExact(get(viewportSize, global), width, height);
            setInts.invokeExact(get(sceneResolutionUniform, global), width, height);
            upload.invokeExact(global);
            resetState.invokeExact(renderState);
            tiledLightingPass.invokeExact(renderer);
            scenePass.invokeExact(renderer);
            resetState.invokeExact(renderState);
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
        finally
        {
            set(sceneResolution, plugin, resolution);
            setInt(fboScene, plugin, desktop);
            setInt(msaaSamples, plugin, desktopMsaa);
        }
    }

    /**
     * Puts back the camera uniforms 117 HD worked out for the desktop, and stops it drawing the scene again this frame.
     */
    void finishViews()
    {
        int[] resolution = (int[]) get(sceneResolution, plugin);
        int[] viewport = (int[]) get(sceneViewport, plugin);
        try
        {
            Object global = get(uboGlobal, plugin);
            setFloats.invokeExact(get(projectionMatrix, global), (float[]) get(viewProjMatrix, plugin));
            setFloats.invokeExact(get(invProjectionMatrix, global), (float[]) get(invViewProjMatrix, plugin));
            setFloats.invokeExact(get(cameraPos, global), cameraPosition);
            if (viewport != null)
            {
                setInts.invokeExact(get(viewportSize, global), viewport[2], viewport[3]);
            }
            if (resolution != null)
            {
                setInts.invokeExact(get(sceneResolutionUniform, global), resolution[0], resolution[1]);
            }
            upload.invokeExact(global);
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
        setBoolean(shouldRenderScene, renderer, false);
    }

    /**
     * @return 117 HD's desktop scene resolution, or null before it has one.
     */
    int[] sceneResolution()
    {
        int[] resolution = (int[]) get(sceneResolution, plugin);
        return resolution == null ? null : Arrays.copyOf(resolution, 2);
    }

    /**
     * Makes 117 HD show a single sampled framebuffer of its scene resolution as the scene until put back.
     *
     * @param framebuffer The framebuffer shown.
     */
    void showScene(int framebuffer)
    {
        desktopFramebuffer = getInt(fboScene, plugin);
        desktopResolve = getInt(fboSceneResolve, plugin);
        desktopFboValid = getBoolean(sceneFboValid, renderer);
        setInt(fboScene, plugin, framebuffer);
        setInt(fboSceneResolve, plugin, 0);
        setBoolean(sceneFboValid, renderer, true);
    }

    /**
     * Puts back 117 HD's own scene framebuffer after showing another.
     */
    void restoreScene()
    {
        setInt(fboScene, plugin, desktopFramebuffer);
        setInt(fboSceneResolve, plugin, desktopResolve);
        setBoolean(sceneFboValid, renderer, desktopFboValid);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException
    {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    /**
     * Gets a method as a handle adapted to the given Object-based type.
     */
    private static MethodHandle handle(Method method, MethodType as) throws IllegalAccessException
    {
        return MethodHandles.lookup().unreflect(method).asType(as);
    }

    private static Object get(Field field, Object owner)
    {
        try
        {
            return field.get(owner);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field field, Object owner, Object value)
    {
        try
        {
            field.set(owner, value);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static int getInt(Field field, Object owner)
    {
        try
        {
            return field.getInt(owner);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static void setInt(Field field, Object owner, int value)
    {
        try
        {
            field.setInt(owner, value);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static boolean getBoolean(Field field, Object owner)
    {
        try
        {
            return field.getBoolean(owner);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static void setBoolean(Field field, Object owner, boolean value)
    {
        try
        {
            field.setBoolean(owner, value);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static RuntimeException rethrown(Throwable e)
    {
        if (e instanceof RuntimeException)
        {
            return (RuntimeException) e;
        }
        if (e instanceof Error)
        {
            throw (Error) e;
        }
        return new IllegalStateException(e);
    }
}
