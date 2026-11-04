package com.tonic.vitavr.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Draws the controller pointers as depth-tested lines into an eye.
 */
final class Beams implements AutoCloseable
{
    private final int program;
    private final int matrixAt;
    private final int colourAt;
    private final int vertexArray;
    private final int buffer;
    private boolean closed;

    /**
     * Creates the program in the current OpenGL context.
     */
    Beams()
    {
        program = Shaders.program("beam.vert", "beam.frag");
        matrixAt = GL20.glGetUniformLocation(program, "worldToClip");
        colourAt = GL20.glGetUniformLocation(program, "colour");
        vertexArray = GL30.glGenVertexArrays();
        buffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6L * Float.BYTES, GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Draws one pointer into the bound eye.
     *
     * @param worldToClip The eye's matrix.
     * @param from The start point, across, down and along.
     * @param to The end point.
     * @param rgba The colour, red in the high byte.
     */
    void draw(float[] worldToClip, double[] from, double[] to, int rgba)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer points = stack.floats((float) from[0], (float) from[1], (float) from[2], (float) to[0], (float) to[1], (float) to[2]);
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(matrixAt, false, worldToClip);
            GL20.glUniform4f(colourAt, (rgba >>> 24) / 255f, (rgba >> 16 & 0xFF) / 255f, (rgba >> 8 & 0xFF) / 255f, (rgba & 0xFF) / 255f);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, points);

            // Reverse depth, nearer is greater
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_GREATER);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDrawArrays(GL11.GL_LINES, 0, 2);
            GL11.glDepthMask(true);
        }
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
        GL15.glDeleteBuffers(buffer);
        GL30.glDeleteVertexArrays(vertexArray);
        GL20.glDeleteProgram(program);
    }
}
