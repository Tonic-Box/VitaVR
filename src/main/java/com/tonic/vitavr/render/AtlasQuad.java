package com.tonic.vitavr.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A texture atlas whose cells are drawn as world quads over the scene.
 */
final class AtlasQuad implements AutoCloseable
{
    private final int width;
    private final int height;
    private final int program;
    private final int matrixAt;
    private final int atlasAt;
    private final int fadeAt;
    private final int vertexArray;
    private final int buffer;
    private final int texture;
    private boolean closed;

    /**
     * Creates the atlas texture and program in the current OpenGL context.
     *
     * @param width The atlas width in pixels.
     * @param height The atlas height in pixels.
     */
    AtlasQuad(int width, int height)
    {
        this(width, height, true);
    }

    /**
     * Creates only the program, for drawing pictures held in other textures.
     */
    AtlasQuad()
    {
        this(1, 1, false);
    }

    private AtlasQuad(int width, int height, boolean ownPicture)
    {
        this.width = width;
        this.height = height;
        program = Shaders.program("atlas.vert", "atlas.frag");
        matrixAt = GL20.glGetUniformLocation(program, "worldToClip");
        atlasAt = GL20.glGetUniformLocation(program, "atlas");
        fadeAt = GL20.glGetUniformLocation(program, "fade");
        vertexArray = GL30.glGenVertexArrays();
        buffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6L * 5 * Float.BYTES, GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        GL30.glBindVertexArray(0);

        if (!ownPicture)
        {
            texture = 0;
            return;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
    }

    /**
     * Uploads new atlas pixels.
     *
     * @param pixels Premultiplied ARGB rows from the top, the size of the atlas.
     */
    void upload(int[] pixels)
    {
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
    }

    /**
     * Draws an atlas cell between four world corners into the bound view.
     *
     * @param worldToClip The view's matrix.
     * @param corners The top left, top right, bottom right and bottom left corners, each across, down and along.
     * @param left The cell's left edge in pixels.
     * @param top The cell's top edge in pixels.
     * @param cellWidth The cell width in pixels.
     * @param cellHeight The cell height in pixels.
     * @param fade The opacity, 0 to 1.
     */
    void draw(float[] worldToClip, double[][] corners, int left, int top, int cellWidth, int cellHeight, float fade)
    {
        draw(worldToClip, corners, texture, width, height, left, top, cellWidth, cellHeight, fade);
    }

    /**
     * Draws a cell of another texture's picture between four world corners.
     *
     * @param worldToClip   The view's matrix.
     * @param corners       The top left, top right, bottom right and bottom left corners.
     * @param picture       The texture, premultiplied, first row at the top.
     * @param pictureWidth  The picture width, in pixels.
     * @param pictureHeight The picture height, in pixels.
     * @param left          The cell's left edge, in pixels.
     * @param top           The cell's top edge.
     * @param cellWidth     The cell width.
     * @param cellHeight    The cell height.
     * @param fade          How much shows, 0 to 1.
     */
    void draw(float[] worldToClip, double[][] corners, int picture, int pictureWidth, int pictureHeight, int left, int top, int cellWidth, int cellHeight, float fade)
    {
        float u0 = (float) left / pictureWidth;
        float u1 = (float) (left + cellWidth) / pictureWidth;
        float v0 = (float) top / pictureHeight;
        float v1 = (float) (top + cellHeight) / pictureHeight;
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer vertices = stack.mallocFloat(6 * 5);
            vertex(vertices, corners[0], u0, v0);
            vertex(vertices, corners[1], u1, v0);
            vertex(vertices, corners[2], u1, v1);
            vertex(vertices, corners[0], u0, v0);
            vertex(vertices, corners[2], u1, v1);
            vertex(vertices, corners[3], u0, v1);
            vertices.flip();
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(matrixAt, false, worldToClip);
            GL20.glUniform1i(atlasAt, 0);
            GL20.glUniform1f(fadeAt, fade);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, picture);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        }
    }

    private static void vertex(FloatBuffer vertices, double[] corner, float u, float v)
    {
        vertices.put((float) corner[0]).put((float) corner[1]).put((float) corner[2]).put(u).put(v);
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
        if (texture != 0)
        {
            GL11.glDeleteTextures(texture);
        }
        GL15.glDeleteBuffers(buffer);
        GL30.glDeleteVertexArrays(vertexArray);
        GL20.glDeleteProgram(program);
    }
}
