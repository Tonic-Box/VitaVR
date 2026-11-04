package com.tonic.vitavr.render;

import net.runelite.api.Constants;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.plugins.gpu.GpuPluginConfig;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reflective access to the GPU plugin internals needed to draw both eyes from one scene walk.
 */
final class GpuInternals
{
    private static final int SCENE_OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
    private static final int ALPHA_ZSORT_CLOSE = 2048;

    private final Object gpu;
    private final Field renderThreads;
    private final Field opaqueList;
    private final Field uploader;
    private final Method unmap;
    private final Field vaos;
    private final Method vaoDraw;
    private final Method vaoReset;
    private final Method context;
    private final Field zones;
    private final Field cameraX;
    private final Field cameraY;
    private final Field cameraZ;
    private final Field minLevel;
    private final Field level;
    private final Field maxLevel;
    private final Field hideRoofIds;
    private final Field initialized;
    private final Method renderAlpha;
    private final Field cameraYaw;
    private final Field cameraPitch;
    private final List<Object> opaque = new ArrayList<>();

    /**
     * Looks up the GPU plugin internals.
     * @param gpu The GPU plugin, as the client's draw callbacks.
     * @throws IllegalStateException If the GPU plugin is not the version VitaVR was built for.
     */
    GpuInternals(DrawCallbacks gpu)
    {
        this.gpu = gpu;
        try
        {
            Class<?> plugin = gpu.getClass();
            ClassLoader loader = plugin.getClassLoader();
            String gpuPackage = plugin.getPackage().getName() + ".";
            Class<?> renderThread = Class.forName(gpuPackage + "GpuPlugin$RenderThread", false, loader);
            Class<?> vaoList = Class.forName(gpuPackage + "VAOList", false, loader);
            Class<?> vao = Class.forName(gpuPackage + "VAO", false, loader);
            Class<?> sceneContext = Class.forName(gpuPackage + "GpuPlugin$SceneContext", false, loader);
            Class<?> zone = Class.forName(gpuPackage + "Zone", false, loader);
            Class<?> modelUploader = Class.forName(gpuPackage + "ModelUploader", false, loader);

            renderThreads = field(plugin, "rts");
            opaqueList = field(renderThread, "vaoO");
            uploader = field(renderThread, "modelUploader");
            unmap = method(vaoList, "unmap");
            vaos = field(vaoList, "vaos");
            vaoDraw = method(vao, "draw");
            vaoReset = method(vao, "reset");
            context = method(plugin, "context", Scene.class);
            zones = field(sceneContext, "zones");
            cameraX = field(sceneContext, "cameraX");
            cameraY = field(sceneContext, "cameraY");
            cameraZ = field(sceneContext, "cameraZ");
            minLevel = field(sceneContext, "minLevel");
            level = field(sceneContext, "level");
            maxLevel = field(sceneContext, "maxLevel");
            hideRoofIds = field(sceneContext, "hideRoofIds");
            initialized = field(zone, "initialized");
            renderAlpha = method(zone, "renderAlpha", modelUploader, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, Set.class, boolean.class);
            cameraYaw = field(plugin, "cameraYaw");
            cameraPitch = field(plugin, "cameraPitch");
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            throw new IllegalStateException("This GPU plugin is not one VitaVR can draw with: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the GPU plugin's GPU flags, optionally drawing every zone regardless of camera facing.
     *
     * @param config The GPU plugin config.
     * @param allZones Whether zones behind the client camera are drawn too.
     * @return The flags for the client.
     */
    static int flags(GpuPluginConfig config, boolean allZones)
    {
        int threads = Math.min(Runtime.getRuntime().availableProcessors() - 1, config.numThreads());
        return DrawCallbacks.GPU
                | (config.removeVertexSnapping() ? DrawCallbacks.NO_VERTEX_SNAPPING : 0)
                | DrawCallbacks.ZBUF
                | DrawCallbacks.RENDER_THREADS(threads)
                | (allZones ? DrawCallbacks.ZBUF_ZONE_FRUSTUM_CHECK : 0);
    }

    /**
     * Takes the frame's dynamic opaque models from the GPU plugin so they can be drawn for each eye.
     */
    void takeOpaque()
    {
        opaque.clear();
        Object threads = get(renderThreads, gpu);
        for (int i = 0; i < Array.getLength(threads); i++)
        {
            Object list = get(opaqueList, Array.get(threads, i));
            int mapped = (int) invoke(unmap, list);
            List<?> all = (List<?>) get(vaos, list);
            opaque.addAll(all.subList(0, mapped));
        }
    }

    /**
     * Draws the taken dynamic opaque models into the bound eye.
     */
    void drawOpaque()
    {
        for (Object vao : opaque)
        {
            invoke(vaoDraw, vao);
        }
    }

    /**
     * Clears the taken models.
     */
    void resetOpaque()
    {
        for (Object vao : opaque)
        {
            invoke(vaoReset, vao);
        }
        opaque.clear();
    }

    /**
     * Makes the GPU plugin sort transparent models from the head instead of the client camera.
     *
     * @param scene The top level scene for this frame.
     * @param x The head position, across.
     * @param y The head position, down.
     * @param z The head position, along.
     * @param yaw The head yaw in radians, as the GPU plugin's camera yaw.
     * @param pitch The head pitch in radians, down positive.
     */
    void sortFrom(Scene scene, double x, double y, double z, double yaw, double pitch)
    {
        Object sceneContext = invoke(context, gpu, scene);
        if (sceneContext == null)
        {
            return;
        }
        set(cameraX, sceneContext, (int) Math.round(x));
        set(cameraY, sceneContext, (int) Math.round(y));
        set(cameraZ, sceneContext, (int) Math.round(z));
        set(cameraYaw, gpu, Math.floorMod((int) Math.round(yaw * 2048 / (2 * Math.PI)), 2048));
        set(cameraPitch, gpu, Math.floorMod((int) Math.round(pitch * 2048 / (2 * Math.PI)), 2048));
    }

    /**
     * Gets the levels a scene's zones are drawn from this frame.
     *
     * @param scene The scene.
     * @return The lowest then the highest level, or null before the scene is drawn.
     */
    int[] levels(Scene scene)
    {
        Object sceneContext = invoke(context, gpu, scene);
        return sceneContext == null ? null : new int[]{(int) get(minLevel, sceneContext), (int) get(maxLevel, sceneContext)};
    }

    /**
     * Sets the levels a scene's zones are drawn from.
     *
     * @param scene The scene.
     * @param lowest The lowest level drawn.
     * @param highest The highest level drawn.
     */
    void levels(Scene scene, int lowest, int highest)
    {
        Object sceneContext = invoke(context, gpu, scene);
        if (sceneContext != null)
        {
            set(minLevel, sceneContext, lowest);
            set(maxLevel, sceneContext, highest);
        }
    }

    /**
     * Draws a zone's alpha models again like drawZoneAlpha, without re-sorting them into zones.
     *
     * @param scene The scene the zone is in.
     * @param zoneLevel The level drawn.
     * @param zoneX The zone x.
     * @param zoneZ The zone z.
     */
    void drawZoneAlphaAgain(Scene scene, int zoneLevel, int zoneX, int zoneZ)
    {
        Object sceneContext = invoke(context, gpu, scene);
        if (sceneContext == null)
        {
            return;
        }
        Object zone = Array.get(Array.get(get(zones, sceneContext), zoneX), zoneZ);
        if (!(boolean) get(initialized, zone))
        {
            return;
        }

        int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
        int dx = (int) get(cameraX, sceneContext) - ((zoneX - offset) << 10);
        int dz = (int) get(cameraZ, sceneContext) - ((zoneZ - offset) << 10);
        boolean close = dx * dx + dz * dz < ALPHA_ZSORT_CLOSE * ALPHA_ZSORT_CLOSE;
        Object firstThread = Array.get(get(renderThreads, gpu), 0);
        invoke(renderAlpha, zone, get(uploader, firstThread), zoneX - offset, zoneZ - offset, get(cameraYaw, gpu), get(cameraPitch, gpu),
                get(minLevel, sceneContext), get(level, sceneContext), get(maxLevel, sceneContext), zoneLevel, get(hideRoofIds, sceneContext),
                !close || scene.getOverrideAmount() > 0);
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

    private static void set(Field field, Object owner, int value)
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

    private static Object invoke(Method method, Object owner, Object... arguments)
    {
        try
        {
            return method.invoke(owner, arguments);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        }
    }
}
