package com.tonic.vitavr.render.scene.gpu;

import com.tonic.vitavr.render.scene.SceneRenderer;
import com.tonic.vitavr.render.scene.SceneViews;
import com.tonic.vitavr.space.View;
import net.runelite.api.Client;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.plugins.gpu.GpuPluginConfig;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.util.Set;

/**
 * Draws the views through the GPU plugin by replaying each of its zone draws once per view as the client walks the scene.
 */
public final class GpuScene implements SceneRenderer
{
    /** Settings overridden while in VR, as group, key and VR value; the headset paces the client, so no vsync or frame cap. */
    public static final String[][] VR_SETTINGS = {
            {"gpu", "unlockFps", "true"},
            {"gpu", "syncMode", "OFF"},
            {"gpu", "fpsTarget", "0"},
    };
    /** How many tiles the table's edge fades over. */
    private static final int TABLE_FOG_TILES = 2;

    private final DrawCallbacks gpu;
    private final Client client;
    private final GpuPluginConfig config;
    private final GpuInternals internals;
    private final float[] gpuWorldProj = new float[16];
    private final int[] sceneViewport = new int[4];
    private final ZoneDraw zoneDraw = new ZoneDraw();
    private SceneViews views;
    private int sceneFramebuffer;
    private int worldProjProgram;
    private int worldProj = -1;
    private int useFogUniform = -1;
    private int fogDepthUniform = -1;
    private int drawDistanceUniform = -1;
    private int lowestLevel;
    private boolean drawing;

    /**
     * Wraps the GPU plugin.
     *
     * @param gpu The GPU plugin, as the client's draw callbacks.
     * @param client The client.
     * @param config The GPU plugin config.
     * @throws IllegalStateException If the GPU plugin is not the version VitaVR was built for.
     */
    public GpuScene(DrawCallbacks gpu, Client client, GpuPluginConfig config)
    {
        this.gpu = gpu;
        this.client = client;
        this.config = config;
        this.internals = new GpuInternals(gpu);
    }

    @Override
    public DrawCallbacks plugin()
    {
        return gpu;
    }

    @Override
    public void applyFlags()
    {
        client.setGpuFlags(GpuInternals.flags(config, true));
    }

    @Override
    public void restoreFlags()
    {
        client.setGpuFlags(GpuInternals.flags(config, false));
    }

    @Override
    public void preSceneDraw(Scene scene, Projection projection, float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw, int minLevel, int level,
                             int maxLevel, Set<Integer> hideRoofIds, View head)
    {
        gpu.preSceneDraw(scene, projection, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, maxLevel, hideRoofIds);
        internals.prepare(scene);
        if (scene.getWorldViewId() == WorldView.TOPLEVEL)
        {
            lowestLevel = minLevel;
        }
        if (head != null)
        {
            internals.sortFrom(scene, head.eyeX(), head.eyeY(), head.eyeZ(), head.yaw(), head.pitch());
        }
    }

    @Override
    public boolean startViews(SceneViews views, double fogX, double fogZ, double tableReach)
    {
        this.views = views;
        sceneFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, sceneViewport);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program != worldProjProgram)
        {
            worldProjProgram = program;
            worldProj = GL20.glGetUniformLocation(program, "worldProj");
            useFogUniform = GL20.glGetUniformLocation(program, "useFog");
            fogDepthUniform = GL20.glGetUniformLocation(program, "fogDepth");
            drawDistanceUniform = GL20.glGetUniformLocation(program, "drawDistance");
        }
        if (worldProj < 0)
        {
            return false;
        }
        GL20.glGetUniformfv(program, worldProj, gpuWorldProj);
        centreFog(fogX, fogZ, tableReach);
        drawing = true;
        return true;
    }

    /**
     * Centres the GPU plugin's fog, fading at the table's edge on the tabletop.
     */
    private void centreFog(double fogX, double fogZ, double tableReach)
    {
        // Uniform block starts cameraYaw, cameraPitch, cameraX, cameraY, cameraZ as std140 floats
        int block = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, 0);
        if (block != 0)
        {
            int bound = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, block);
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 8, new float[]{(float) fogX});
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 16, new float[]{(float) fogZ});
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, bound);
        }
        if (tableReach > 0 && drawDistanceUniform >= 0)
        {
            GL20.glUniform1i(useFogUniform, 1);
            GL20.glUniform1i(fogDepthUniform, TABLE_FOG_TILES);
            GL20.glUniform1i(drawDistanceUniform, (int) Math.round(tableReach));
        }
    }

    @Override
    public boolean zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY)
    {
        return true;
    }

    @Override
    public void drawZoneOpaque(Projection projection, Scene scene, int zoneX, int zoneZ)
    {
        zoneDraw.set(projection, scene, zoneX, zoneZ);
        forEachView(zoneDraw);
    }

    @Override
    public void drawZoneAlpha(Projection projection, Scene scene, int level, int zoneX, int zoneZ)
    {
        // Plugin call for the first view, since its zone sorting must run only once
        use(0);
        gpu.drawZoneAlpha(projection, scene, level, zoneX, zoneZ);
        for (int view = 1; view < views.count(); view++)
        {
            use(view);
            internals.drawZoneAlphaAgain(scene, level, zoneX, zoneZ);
        }
    }

    @Override
    public void drawPass(Projection projection, Scene scene, int pass)
    {
        if (drawing && pass == DrawCallbacks.PASS_OPAQUE && scene.getWorldViewId() == WorldView.TOPLEVEL)
        {
            internals.takeOpaque();
            forEachView(internals::drawOpaque);
            internals.resetOpaque();
            return;
        }
        gpu.drawPass(projection, scene, pass);
    }

    @Override
    public boolean hasFacesFrom(Scene scene, int zoneX, int zoneZ, int level)
    {
        return internals.hasFacesFrom(scene, zoneX, zoneZ, level);
    }

    @Override
    public void ceilings(Projection projection, Scene scene, int zoneX, int zoneZ, int fromLevel, int toLevel, float light)
    {
        internals.levels(scene, fromLevel, toLevel);
        GL11.glCullFace(GL11.GL_FRONT);
        GL14.glBlendColor(light, light, light, 1f);
        GL14.glBlendFuncSeparate(GL14.GL_CONSTANT_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE);
        try
        {
            drawZoneOpaque(projection, scene, zoneX, zoneZ);
        }
        finally
        {
            GL11.glCullFace(GL11.GL_BACK);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
            internals.levels(scene, lowestLevel, toLevel);
        }
    }

    @Override
    public void postSceneDraw(Scene scene, Runnable finishViews)
    {
        if (finishViews != null)
        {
            finishViews.run();
        }
        gpu.postSceneDraw(scene);
    }

    @Override
    public void draw(int overlayColor)
    {
        gpu.draw(overlayColor);
    }

    @Override
    public void leaveViews()
    {
        if (!drawing)
        {
            return;
        }
        drawing = false;
        GL20.glUniformMatrix4fv(worldProj, false, gpuWorldProj);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFramebuffer);
        GL11.glViewport(sceneViewport[0], sceneViewport[1], sceneViewport[2], sceneViewport[3]);
    }

    @Override
    public boolean bindMirror(int[] viewport)
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFramebuffer);
        GL11.glViewport(sceneViewport[0], sceneViewport[1], sceneViewport[2], sceneViewport[3]);
        System.arraycopy(sceneViewport, 0, viewport, 0, 4);
        return true;
    }

    @Override
    public void close()
    {
    }

    /**
     * Binds a view and gives the GPU plugin its matrix.
     */
    private void use(int view)
    {
        views.bind(view);
        GL20.glUniformMatrix4fv(worldProj, false, views.worldToClip(view));
    }

    /**
     * Runs a GPU plugin draw once for each view.
     */
    private void forEachView(Runnable draw)
    {
        for (int view = 0; view < views.count(); view++)
        {
            use(view);
            draw.run();
        }
    }

    /**
     * A reusable opaque zone draw, run once per view.
     */
    private final class ZoneDraw implements Runnable
    {
        private Projection projection;
        private Scene scene;
        private int zoneX;
        private int zoneZ;

        private void set(Projection projection, Scene scene, int zoneX, int zoneZ)
        {
            this.projection = projection;
            this.scene = scene;
            this.zoneX = zoneX;
            this.zoneZ = zoneZ;
        }

        @Override
        public void run()
        {
            gpu.drawZoneOpaque(projection, scene, zoneX, zoneZ);
        }
    }
}
