package com.tonic.vitavr.render.comfort;

import com.tonic.vitavr.render.Shaders;
import com.tonic.vitavr.space.Fov;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Draws a comfort vignette and blackout over an eye's finished picture.
 */
public final class ComfortShade implements AutoCloseable
{
    private static final double SHOWN = 1e-3;

    private final int program;
    private final int centreAt;
    private final int halfSizeAt;
    private final int vignetteAt;
    private final int blackAt;
    private final int vertexArray;
    private boolean closed;

    /**
     * Creates the program in the current OpenGL context.
     */
    public ComfortShade()
    {
        program = Shaders.program("shade.vert", "shade.frag");
        centreAt = GL20.glGetUniformLocation(program, "centre");
        halfSizeAt = GL20.glGetUniformLocation(program, "halfSize");
        vignetteAt = GL20.glGetUniformLocation(program, "vignette");
        blackAt = GL20.glGetUniformLocation(program, "black");
        vertexArray = GL30.glGenVertexArrays();
    }

    /**
     * Draws the shade over a picture.
     *
     * @param framebuffer The picture's framebuffer.
     * @param width The picture width.
     * @param height The picture height.
     * @param fov The eye's field of view, which places the vignette centre.
     * @param vignette The vignette strength, 0 to 1.
     * @param black The blackout strength, 0 to 1.
     */
    public void draw(int framebuffer, int width, int height, Fov fov, double vignette, double black)
    {
        if (vignette < SHOWN && black < SHOWN)
        {
            return;
        }
        double tanLeft = Math.tan(fov.left());
        double tanRight = Math.tan(fov.right());
        double tanUp = Math.tan(fov.up());
        double tanDown = Math.tan(fov.down());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
        GL20.glUseProgram(program);
        GL20.glUniform2f(centreAt, (float) (-(tanRight + tanLeft) / (tanRight - tanLeft)), (float) (-(tanUp + tanDown) / (tanUp - tanDown)));
        GL20.glUniform2f(halfSizeAt, (float) ((tanRight - tanLeft) / 2), (float) ((tanUp - tanDown) / 2));
        GL20.glUniform1f(vignetteAt, (float) Math.max(0, Math.min(1, vignette)));
        GL20.glUniform1f(blackAt, (float) Math.max(0, Math.min(1, black)));
        GL30.glBindVertexArray(vertexArray);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
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
