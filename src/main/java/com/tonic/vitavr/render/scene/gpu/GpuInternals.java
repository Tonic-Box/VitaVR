package com.tonic.vitavr.render.scene.gpu;

import com.tonic.vitavr.world.Zones;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.plugins.gpu.GpuPluginConfig;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflective access to the GPU plugin internals needed to draw both eyes from one scene walk.
 */
final class GpuInternals
{
    private static final int ALPHA_ZSORT_CLOSE = 2048;

    private final Object gpu;
    private final Field renderThreads;
    private final Field opaqueList;
    private final Field vaos;
    private final MethodHandle unmap;
    private final MethodHandle vaoDraw;
    private final MethodHandle vaoReset;
    private final Method context;
    private final Field zones;
    private final Field cameraX;
    private final Field cameraY;
    private final Field cameraZ;
    private final Field minLevel;
    private final Field level;
    private final Field maxLevel;
    private final Field hideRoofIds;
    private final MethodHandle initialized;
    private final Field levelOffsets;
    private final MethodHandle renderAlpha;
    private final Field cameraYaw;
    private final Field cameraPitch;
    private final Field uploader;
    private Object firstUploader;
    private final List<Object> opaque = new ArrayList<>();
    private final Map<Scene, SceneState> scenes = new IdentityHashMap<>();

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
            vaos = field(vaoList, "vaos");
            unmap = handle(method(vaoList, "unmap"), MethodType.methodType(int.class, Object.class));
            vaoDraw = handle(method(vao, "draw"), MethodType.methodType(void.class, Object.class));
            vaoReset = handle(method(vao, "reset"), MethodType.methodType(void.class, Object.class));
            context = method(plugin, "context", Scene.class);
            zones = field(sceneContext, "zones");
            cameraX = field(sceneContext, "cameraX");
            cameraY = field(sceneContext, "cameraY");
            cameraZ = field(sceneContext, "cameraZ");
            minLevel = field(sceneContext, "minLevel");
            level = field(sceneContext, "level");
            maxLevel = field(sceneContext, "maxLevel");
            hideRoofIds = field(sceneContext, "hideRoofIds");
            initialized = MethodHandles.lookup().unreflectGetter(field(zone, "initialized")).asType(MethodType.methodType(boolean.class, Object.class));
            levelOffsets = field(zone, "levelOffsets");
            renderAlpha = handle(method(zone, "renderAlpha", modelUploader, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, Set.class,
                    boolean.class), MethodType.methodType(void.class, Object.class, Object.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                    int.class, Set.class, boolean.class));
            cameraYaw = field(plugin, "cameraYaw");
            cameraPitch = field(plugin, "cameraPitch");
            uploader = field(renderThread, "modelUploader");
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
     * Reads a scene's draw state for this frame; the top level scene clears all earlier ones.
     *
     * @param scene The scene just set up.
     */
    void prepare(Scene scene)
    {
        if (scene.getWorldViewId() == WorldView.TOPLEVEL)
        {
            scenes.clear();
            firstUploader = get(uploader, Array.get(get(renderThreads, gpu), 0));
        }
        scenes.remove(scene);
        state(scene);
    }

    /**
     * Takes the frame's dynamic opaque models from the GPU plugin so they can be drawn for each eye.
     */
    void takeOpaque()
    {
        opaque.clear();
        Object threads = get(renderThreads, gpu);
        try
        {
            for (int i = 0; i < Array.getLength(threads); i++)
            {
                Object list = get(opaqueList, Array.get(threads, i));
                int mapped = (int) unmap.invokeExact(list);
                List<?> all = (List<?>) get(vaos, list);
                opaque.addAll(all.subList(0, mapped));
            }
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
    }

    /**
     * Draws the taken dynamic opaque models into the bound eye.
     */
    void drawOpaque()
    {
        try
        {
            for (Object vao : opaque)
            {
                vaoDraw.invokeExact(vao);
            }
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
    }

    /**
     * Clears the taken models.
     */
    void resetOpaque()
    {
        try
        {
            for (Object vao : opaque)
            {
                vaoReset.invokeExact(vao);
            }
        }
        catch (Throwable e)
        {
            throw rethrown(e);
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
        SceneState state = state(scene);
        if (state == null)
        {
            return;
        }
        state.cameraX = (int) Math.round(x);
        state.cameraZ = (int) Math.round(z);
        setInt(cameraX, state.context, state.cameraX);
        setInt(cameraY, state.context, (int) Math.round(y));
        setInt(cameraZ, state.context, state.cameraZ);
        setInt(cameraYaw, gpu, Math.floorMod((int) Math.round(yaw * 2048 / (2 * Math.PI)), 2048));
        setInt(cameraPitch, gpu, Math.floorMod((int) Math.round(pitch * 2048 / (2 * Math.PI)), 2048));
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
        SceneState state = state(scene);
        if (state != null)
        {
            state.minLevel = lowest;
            state.maxLevel = highest;
            setInt(minLevel, state.context, lowest);
            setInt(maxLevel, state.context, highest);
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
        SceneState state = state(scene);
        if (state == null)
        {
            return;
        }
        Object zone = state.zones[zoneX][zoneZ];
        if (!isInitialized(zone))
        {
            return;
        }

        int dx = state.cameraX - ((zoneX - state.offset) << 10);
        int dz = state.cameraZ - ((zoneZ - state.offset) << 10);
        boolean close = dx * dx + dz * dz < ALPHA_ZSORT_CLOSE * ALPHA_ZSORT_CLOSE;
        try
        {
            renderAlpha.invokeExact(zone, firstUploader, zoneX - state.offset, zoneZ - state.offset, getInt(cameraYaw, gpu), getInt(cameraPitch, gpu),
                    state.minLevel, state.level, state.maxLevel, zoneLevel, state.roofs, !close || scene.getOverrideAmount() > 0);
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
    }

    /**
     * Gets whether a zone has any faces on a level or above.
     *
     * @param scene The scene the zone is in.
     * @param zoneX The zone x.
     * @param zoneZ The zone z.
     * @param from The lowest level checked.
     * @return Whether the zone has faces on that level or above.
     */
    boolean hasFacesFrom(Scene scene, int zoneX, int zoneZ, int from)
    {
        SceneState state = state(scene);
        if (state == null)
        {
            return false;
        }
        Object zone = state.zones[zoneX][zoneZ];
        if (!isInitialized(zone))
        {
            return false;
        }
        int[] ends = (int[]) get(levelOffsets, zone);
        int start = from == 0 ? 0 : ends[from - 1];
        return ends[ends.length - 1] > start;
    }

    /**
     * Gets a scene's state for this frame, reading it if not seen yet.
     */
    private SceneState state(Scene scene)
    {
        SceneState state = scenes.get(scene);
        if (state == null)
        {
            state = read(scene);
            if (state != null)
            {
                scenes.put(scene, state);
            }
        }
        return state;
    }

    private SceneState read(Scene scene)
    {
        Object sceneContext;
        try
        {
            sceneContext = context.invoke(gpu, scene);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
        catch (InvocationTargetException e)
        {
            throw rethrown(e.getCause());
        }
        if (sceneContext == null)
        {
            return null;
        }
        SceneState state = new SceneState();
        state.context = sceneContext;
        state.zones = (Object[][]) get(zones, sceneContext);
        state.minLevel = getInt(minLevel, sceneContext);
        state.level = getInt(level, sceneContext);
        state.maxLevel = getInt(maxLevel, sceneContext);
        state.roofs = (Set<?>) get(hideRoofIds, sceneContext);
        state.cameraX = getInt(cameraX, sceneContext);
        state.cameraZ = getInt(cameraZ, sceneContext);
        state.offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? Zones.OFFSET : 0;
        return state;
    }

    private boolean isInitialized(Object zone)
    {
        try
        {
            return (boolean) initialized.invokeExact(zone);
        }
        catch (Throwable e)
        {
            throw rethrown(e);
        }
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

    /**
     * A scene's draw state for this frame, read once instead of per zone.
     */
    private static final class SceneState
    {
        private Object context;
        private Object[][] zones;
        private int minLevel;
        private int level;
        private int maxLevel;
        private Set<?> roofs;
        private int cameraX;
        private int cameraZ;
        private int offset;
    }
}
