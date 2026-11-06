package com.tonic.vitavr.render.scene.hd;

import com.tonic.vitavr.render.EyesTarget;
import com.tonic.vitavr.render.GlState;
import com.tonic.vitavr.render.scene.SceneRenderer;
import com.tonic.vitavr.render.scene.SceneViews;
import com.tonic.vitavr.space.View;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Draws the views through 117 HD: it records the whole scene once, all the way round, then draws it once per view.
 */
public final class HdScene implements SceneRenderer
{
    /** Settings overridden while in VR, as group, key and VR value; the headset paces the client, so no vsync or frame cap. */
    public static final String[][] VR_SETTINGS = {
            {"hd", "unlockFps", "true"},
            {"hd", "vsyncMode", "OFF"},
            {"hd", "fpsTarget", "0"},
    };

    private final DrawCallbacks hd;
    private final HdInternals internals;
    private final GlState state = new GlState();
    private final float[] clipToWorld = new float[16];
    private final double[] inverting = new double[16];
    private final float[] eye = new float[3];
    private SceneViews views;
    private EyesTarget target;
    private int mirrorFramebuffer;
    private int mirrorTexture;
    private int mirrorWidth;
    private int mirrorHeight;
    private boolean mirrored;
    private boolean closed;

    /**
     * Wraps a running 117 HD.
     *
     * @param plugin The 117 HD plugin.
     * @throws IllegalStateException If 117 HD is not a version VitaVR can draw with.
     */
    public HdScene(Object plugin)
    {
        this.internals = new HdInternals(plugin);
        this.hd = (DrawCallbacks) internals.renderer();
    }

    @Override
    public DrawCallbacks plugin()
    {
        return hd;
    }

    @Override
    public void applyFlags()
    {
    }

    @Override
    public void restoreFlags()
    {
    }

    @Override
    public void preSceneDraw(Scene scene, Projection projection, float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw, int minLevel, int level,
                             int maxLevel, Set<Integer> hideRoofIds, View head)
    {
        if (head == null || scene.getWorldViewId() != WorldView.TOPLEVEL)
        {
            hd.preSceneDraw(scene, projection, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, maxLevel, hideRoofIds);
            return;
        }

        // The head as the camera, so 117 HD sorts, fogs and fits shadows from it
        hd.preSceneDraw(scene, projection, (float) head.eyeX(), (float) head.eyeY(), (float) head.eyeZ(), (float) head.pitch(), (float) head.yaw(), minLevel, level,
                maxLevel, hideRoofIds);
        internals.openFrustum();
    }

    @Override
    public boolean startViews(SceneViews views, double fogX, double fogZ)
    {
        this.views = views;
        EyesTarget eyes = views.getEyes();
        if (target == null)
        {
            target = new EyesTarget(eyes.getEyeWidth(), eyes.getEyeHeight(), eyes.getSamples(), 1);
        }
        return true;
    }

    @Override
    public boolean zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY)
    {
        return hd.zoneInFrustum(zoneX, zoneZ, maxY, minY);
    }

    @Override
    public void drawZoneOpaque(Projection projection, Scene scene, int zoneX, int zoneZ)
    {
        hd.drawZoneOpaque(projection, scene, zoneX, zoneZ);
    }

    @Override
    public void drawZoneAlpha(Projection projection, Scene scene, int level, int zoneX, int zoneZ)
    {
        hd.drawZoneAlpha(projection, scene, level, zoneX, zoneZ);
    }

    @Override
    public void drawPass(Projection projection, Scene scene, int pass)
    {
        hd.drawPass(projection, scene, pass);
    }

    @Override
    public boolean hasFacesFrom(Scene scene, int zoneX, int zoneZ, int level)
    {
        return false;
    }

    @Override
    public void ceilings(Projection projection, Scene scene, int zoneX, int zoneZ, int fromLevel, int toLevel, float light)
    {
    }

    @Override
    public void postSceneDraw(Scene scene, Runnable finishViews)
    {
        hd.postSceneDraw(scene);
        if (finishViews == null)
        {
            return;
        }
        state.save();
        try
        {
            drawViews();
        }
        finally
        {
            state.restore();
        }
        finishViews.run();
    }

    /**
     * Draws the recorded scene into each eye, then the recording's steadied camera when it is due, sharing one shadow pass.
     */
    private void drawViews()
    {
        internals.drawShadows();
        EyesTarget eyes = views.getEyes();
        for (int view = 0; view < views.count(); view++)
        {
            float[] worldToClip = views.worldToClip(view);
            invert(worldToClip, inverting, clipToWorld);
            View at = views.view(view);
            eye[0] = (float) at.eyeX();
            eye[1] = (float) at.eyeY();
            eye[2] = (float) at.eyeZ();
            if (view >= SceneViews.EYES)
            {
                EyesTarget spectator = views.spectatorTarget();
                internals.drawView(spectator.getFramebuffer(), spectator.getEyeWidth(), spectator.getEyeHeight(), spectator.getSamples(), worldToClip, clipToWorld, eye);
                continue;
            }
            internals.drawView(target.getFramebuffer(), target.getEyeWidth(), target.getEyeHeight(), target.getSamples(), worldToClip, clipToWorld, eye);
            eyes.copyIn(view, target);
        }
        internals.finishViews();
    }

    @Override
    public void draw(int overlayColor)
    {
        int[] resolution = internals.sceneResolution();
        if (resolution == null)
        {
            hd.draw(overlayColor);
            return;
        }
        if (!mirrored)
        {
            sizeMirror(resolution);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mirrorFramebuffer);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(0, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        mirrored = false;

        // 117 HD shows the mirror picture where its own scene would be
        internals.showScene(mirrorFramebuffer);
        try
        {
            hd.draw(overlayColor);
        }
        finally
        {
            internals.restoreScene();
        }
    }

    @Override
    public void leaveViews()
    {
    }

    @Override
    public boolean bindMirror(int[] viewport)
    {
        int[] resolution = internals.sceneResolution();
        if (resolution == null)
        {
            return false;
        }
        sizeMirror(resolution);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mirrorFramebuffer);
        GL11.glViewport(0, 0, mirrorWidth, mirrorHeight);
        viewport[0] = 0;
        viewport[1] = 0;
        viewport[2] = mirrorWidth;
        viewport[3] = mirrorHeight;
        mirrored = true;
        return true;
    }

    /**
     * Makes the mirror picture the size of 117 HD's scene, if not already.
     */
    private void sizeMirror(int[] resolution)
    {
        if (mirrorFramebuffer != 0 && mirrorWidth == resolution[0] && mirrorHeight == resolution[1])
        {
            return;
        }
        deleteMirror();
        mirrorWidth = resolution[0];
        mirrorHeight = resolution[1];
        mirrorTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mirrorTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, mirrorWidth, mirrorHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        mirrorFramebuffer = GL30.glGenFramebuffers();
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mirrorFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mirrorTexture, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bound);
    }

    private void deleteMirror()
    {
        if (mirrorFramebuffer != 0)
        {
            GL30.glDeleteFramebuffers(mirrorFramebuffer);
            GL11.glDeleteTextures(mirrorTexture);
            mirrorFramebuffer = 0;
            mirrorTexture = 0;
        }
    }

    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        deleteMirror();
        if (target != null)
        {
            target.close();
            target = null;
        }
    }

    /**
     * Inverts a column-major 4 by 4 matrix.
     *
     * @param m The matrix.
     * @param inverse Room to work the inverse out in, 16 long.
     * @param out Receives the inverse.
     */
    static void invert(float[] m, double[] inverse, float[] out)
    {
        inverse[0] = (double) m[5] * m[10] * m[15] - (double) m[5] * m[11] * m[14] - (double) m[9] * m[6] * m[15] + (double) m[9] * m[7] * m[14]
                + (double) m[13] * m[6] * m[11] - (double) m[13] * m[7] * m[10];
        inverse[4] = -(double) m[4] * m[10] * m[15] + (double) m[4] * m[11] * m[14] + (double) m[8] * m[6] * m[15] - (double) m[8] * m[7] * m[14]
                - (double) m[12] * m[6] * m[11] + (double) m[12] * m[7] * m[10];
        inverse[8] = (double) m[4] * m[9] * m[15] - (double) m[4] * m[11] * m[13] - (double) m[8] * m[5] * m[15] + (double) m[8] * m[7] * m[13]
                + (double) m[12] * m[5] * m[11] - (double) m[12] * m[7] * m[9];
        inverse[12] = -(double) m[4] * m[9] * m[14] + (double) m[4] * m[10] * m[13] + (double) m[8] * m[5] * m[14] - (double) m[8] * m[6] * m[13]
                - (double) m[12] * m[5] * m[10] + (double) m[12] * m[6] * m[9];
        inverse[1] = -(double) m[1] * m[10] * m[15] + (double) m[1] * m[11] * m[14] + (double) m[9] * m[2] * m[15] - (double) m[9] * m[3] * m[14]
                - (double) m[13] * m[2] * m[11] + (double) m[13] * m[3] * m[10];
        inverse[5] = (double) m[0] * m[10] * m[15] - (double) m[0] * m[11] * m[14] - (double) m[8] * m[2] * m[15] + (double) m[8] * m[3] * m[14]
                + (double) m[12] * m[2] * m[11] - (double) m[12] * m[3] * m[10];
        inverse[9] = -(double) m[0] * m[9] * m[15] + (double) m[0] * m[11] * m[13] + (double) m[8] * m[1] * m[15] - (double) m[8] * m[3] * m[13]
                - (double) m[12] * m[1] * m[11] + (double) m[12] * m[3] * m[9];
        inverse[13] = (double) m[0] * m[9] * m[14] - (double) m[0] * m[10] * m[13] - (double) m[8] * m[1] * m[14] + (double) m[8] * m[2] * m[13]
                + (double) m[12] * m[1] * m[10] - (double) m[12] * m[2] * m[9];
        inverse[2] = (double) m[1] * m[6] * m[15] - (double) m[1] * m[7] * m[14] - (double) m[5] * m[2] * m[15] + (double) m[5] * m[3] * m[14]
                + (double) m[13] * m[2] * m[7] - (double) m[13] * m[3] * m[6];
        inverse[6] = -(double) m[0] * m[6] * m[15] + (double) m[0] * m[7] * m[14] + (double) m[4] * m[2] * m[15] - (double) m[4] * m[3] * m[14]
                - (double) m[12] * m[2] * m[7] + (double) m[12] * m[3] * m[6];
        inverse[10] = (double) m[0] * m[5] * m[15] - (double) m[0] * m[7] * m[13] - (double) m[4] * m[1] * m[15] + (double) m[4] * m[3] * m[13]
                + (double) m[12] * m[1] * m[7] - (double) m[12] * m[3] * m[5];
        inverse[14] = -(double) m[0] * m[5] * m[14] + (double) m[0] * m[6] * m[13] + (double) m[4] * m[1] * m[14] - (double) m[4] * m[2] * m[13]
                - (double) m[12] * m[1] * m[6] + (double) m[12] * m[2] * m[5];
        inverse[3] = -(double) m[1] * m[6] * m[11] + (double) m[1] * m[7] * m[10] + (double) m[5] * m[2] * m[11] - (double) m[5] * m[3] * m[10]
                - (double) m[9] * m[2] * m[7] + (double) m[9] * m[3] * m[6];
        inverse[7] = (double) m[0] * m[6] * m[11] - (double) m[0] * m[7] * m[10] - (double) m[4] * m[2] * m[11] + (double) m[4] * m[3] * m[10]
                + (double) m[8] * m[2] * m[7] - (double) m[8] * m[3] * m[6];
        inverse[11] = -(double) m[0] * m[5] * m[11] + (double) m[0] * m[7] * m[9] + (double) m[4] * m[1] * m[11] - (double) m[4] * m[3] * m[9]
                - (double) m[8] * m[1] * m[7] + (double) m[8] * m[3] * m[5];
        inverse[15] = (double) m[0] * m[5] * m[10] - (double) m[0] * m[6] * m[9] - (double) m[4] * m[1] * m[10] + (double) m[4] * m[2] * m[9]
                + (double) m[8] * m[1] * m[6] - (double) m[8] * m[2] * m[5];
        double determinant = m[0] * inverse[0] + m[1] * inverse[4] + m[2] * inverse[8] + m[3] * inverse[12];
        for (int i = 0; i < 16; i++)
        {
            out[i] = (float) (inverse[i] / determinant);
        }
    }
}
