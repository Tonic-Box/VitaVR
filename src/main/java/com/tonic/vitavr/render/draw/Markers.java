package com.tonic.vitavr.render.draw;

import com.tonic.vitavr.render.Shaders;
import com.tonic.vitavr.space.View;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Draws eye-facing cursor markers over the world: a dot where a pointer ends and a cross where it clicked.
 */
public final class Markers implements AutoCloseable
{
    public static final int DOT = 0;
    public static final int CROSS = 1;

    private final int program;
    private final int matrixAt;
    private final int colourAt;
    private final int shapeAt;
    private final int vertexArray;
    private final int buffer;
    private final double[] right = new double[3];
    private final double[] down = new double[3];
    private boolean closed;

    /**
     * Creates the program in the current OpenGL context.
     */
    public Markers()
    {
        program = Shaders.program("marker.vert", "marker.frag");
        matrixAt = GL20.glGetUniformLocation(program, "worldToClip");
        colourAt = GL20.glGetUniformLocation(program, "colour");
        shapeAt = GL20.glGetUniformLocation(program, "shape");
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
    }

    /**
     * Draws a shape into the bound eye, facing it.
     *
     * @param eye The eye it faces.
     * @param worldToClip The eye's matrix.
     * @param centre The centre, across, down and along.
     * @param radians The angular half width, so it looks the same size at any distance.
     * @param shape DOT or CROSS.
     * @param argb The colour, alpha in the high byte.
     */
    public void draw(View eye, float[] worldToClip, double[] centre, double radians, int shape, int argb)
    {
        double dx = centre[0] - eye.eyeX();
        double dy = centre[1] - eye.eyeY();
        double dz = centre[2] - eye.eyeZ();
        double half = Math.sqrt(dx * dx + dy * dy + dz * dz) * Math.tan(radians);
        eye.right(right);
        eye.down(down);
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer corners = stack.mallocFloat(6 * 5);
            corner(corners, centre, -1, -1, half);
            corner(corners, centre, 1, -1, half);
            corner(corners, centre, 1, 1, half);
            corner(corners, centre, -1, -1, half);
            corner(corners, centre, 1, 1, half);
            corner(corners, centre, -1, 1, half);
            corners.flip();
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(matrixAt, false, worldToClip);
            GL20.glUniform4f(colourAt, (argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
            GL20.glUniform1i(shapeAt, shape);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, corners);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        }
    }

    private void corner(FloatBuffer corners, double[] centre, int across, int up, double half)
    {
        for (int axis = 0; axis < 3; axis++)
        {
            corners.put((float) (centre[axis] + right[axis] * across * half - down[axis] * up * half));
        }
        corners.put(across).put(up);
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
