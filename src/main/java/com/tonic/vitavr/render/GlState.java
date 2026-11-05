package com.tonic.vitavr.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * A snapshot of the OpenGL state VitaVR changes, restored before the renderer draws again.
 */
public final class GlState
{
    private final int[] value = new int[4];
    private int drawFramebuffer;
    private int readFramebuffer;
    private int program;
    private int vertexArray;
    private int activeTexture;
    private int texture;
    private boolean blend;
    private int blendSourceRgb;
    private int blendDestinationRgb;
    private int blendSourceAlpha;
    private int blendDestinationAlpha;
    private boolean depthTest;
    private boolean cullFace;
    private boolean scissorTest;
    private final int[] viewport = new int[4];

    /**
     * Saves the current state.
     */
    public void save()
    {
        drawFramebuffer = get(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = get(GL30.GL_READ_FRAMEBUFFER_BINDING);
        program = get(GL20.GL_CURRENT_PROGRAM);
        vertexArray = get(GL30.GL_VERTEX_ARRAY_BINDING);
        activeTexture = get(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texture = get(GL11.GL_TEXTURE_BINDING_2D);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        blendSourceRgb = get(GL14.GL_BLEND_SRC_RGB);
        blendDestinationRgb = get(GL14.GL_BLEND_DST_RGB);
        blendSourceAlpha = get(GL14.GL_BLEND_SRC_ALPHA);
        blendDestinationAlpha = get(GL14.GL_BLEND_DST_ALPHA);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
    }

    /**
     * Restores the last saved state.
     */
    public void restore()
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL13.glActiveTexture(activeTexture);
        enable(GL11.GL_BLEND, blend);
        GL14.glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb, blendSourceAlpha, blendDestinationAlpha);
        enable(GL11.GL_DEPTH_TEST, depthTest);
        enable(GL11.GL_CULL_FACE, cullFace);
        enable(GL11.GL_SCISSOR_TEST, scissorTest);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    private int get(int name)
    {
        GL11.glGetIntegerv(name, value);
        return value[0];
    }

    private static void enable(int capability, boolean enabled)
    {
        if (enabled)
        {
            GL11.glEnable(capability);
        }
        else
        {
            GL11.glDisable(capability);
        }
    }
}
