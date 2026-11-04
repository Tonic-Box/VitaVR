package com.tonic.vitavr.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Draws a texture, or part of it, over the whole of the bound viewport.
 */
final class PictureQuad implements AutoCloseable
{
    private final int program;
    private final int vertexArray;
    private final int pictureUniform;
    private final int areaUniform;
    private boolean closed;

    /**
     * Creates the GL objects in the current OpenGL context.
     */
    PictureQuad()
    {
        program = Shaders.program("picture.vert", "picture.frag");
        vertexArray = GL30.glGenVertexArrays();
        pictureUniform = GL20.glGetUniformLocation(program, "picture");
        areaUniform = GL20.glGetUniformLocation(program, "area");
    }

    /**
     * Draws part of a texture over the viewport with blending, depth, culling and scissor off, leaving that state changed.
     *
     * @param texture The texture.
     * @param left The left edge, 0 to 1.
     * @param bottom The bottom edge, 0 to 1.
     * @param width The width, 0 to 1.
     * @param height The height, 0 to 1, negative to flip.
     */
    void draw(int texture, float left, float bottom, float width, float height)
    {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL20.glUseProgram(program);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL20.glUniform1i(pictureUniform, 0);
        GL20.glUniform4f(areaUniform, left, bottom, width, height);
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }

    /**
     * Deletes the GL objects. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        GL30.glDeleteVertexArrays(vertexArray);
        GL20.glDeleteProgram(program);
    }
}
