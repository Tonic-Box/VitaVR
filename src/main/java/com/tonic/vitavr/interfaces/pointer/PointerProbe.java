package com.tonic.vitavr.interfaces.pointer;

import com.tonic.vitavr.render.EyesTarget;
import com.tonic.vitavr.render.Shaders;
import com.tonic.vitavr.space.View;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Finds where a pointer meets the world by walking its ray through the left eye's depth, read back a frame later.
 */
public final class PointerProbe implements AutoCloseable
{
    /** How far down the ray the walk goes, in game units. */
    private static final double REACH = 1 << 15;
    private static final int CLIP_DEPTH_MODE = 0x935D;
    private static final int ZERO_TO_ONE = 0x935F;

    private final int width;
    private final int height;
    private final int program;
    private final int startAt;
    private final int endAt;
    private final int spanAt;
    private final int sizeAt;
    private final int depthScaleAt;
    private final int storedToProjectedAt;
    private final boolean clipControl;
    private final int vertexArray;
    private final int depthTexture;
    private final int depthFramebuffer;
    private final int foundTexture;
    private final int foundFramebuffer;
    private final int[] readBuffers = new int[2];
    private final double[][] aims = new double[2][];
    private final double[] span = new double[2];
    private final float[] start = new float[4];
    private final float[] end = new float[4];
    private int next;
    private double[] aim;
    private boolean found;
    private final double[] hit = new double[3];
    private boolean closed;

    /**
     * Creates the probe's GL objects in the current OpenGL context, leaving none bound for drawing.
     *
     * @param width The width of an eye in pixels.
     * @param height The height of an eye in pixels.
     */
    public PointerProbe(int width, int height)
    {
        this.width = width;
        this.height = height;
        program = Shaders.program("shade.vert", "probe.frag");
        startAt = GL20.glGetUniformLocation(program, "start");
        endAt = GL20.glGetUniformLocation(program, "end");
        spanAt = GL20.glGetUniformLocation(program, "span");
        sizeAt = GL20.glGetUniformLocation(program, "size");
        depthScaleAt = GL20.glGetUniformLocation(program, "depthScale");
        storedToProjectedAt = GL20.glGetUniformLocation(program, "storedToProjected");
        GLCapabilities capabilities = GL.getCapabilities();
        clipControl = capabilities.OpenGL45 || capabilities.GL_ARB_clip_control;
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "depths"), 0);
        GL20.glUseProgram(0);
        vertexArray = GL30.glGenVertexArrays();

        depthTexture = texture(GL30.GL_DEPTH_COMPONENT32F, width, height, GL11.GL_DEPTH_COMPONENT);
        depthFramebuffer = framebuffer(GL30.GL_DEPTH_ATTACHMENT, depthTexture);
        foundTexture = texture(GL30.GL_R32F, 1, 1, GL11.GL_RED);
        foundFramebuffer = framebuffer(GL30.GL_COLOR_ATTACHMENT0, foundTexture);
        for (int i = 0; i < readBuffers.length; i++)
        {
            readBuffers[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, Float.BYTES, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    private static int texture(int format, int width, int height, int pixels)
    {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, pixels, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int framebuffer(int attachment, int texture)
    {
        int framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
        if (attachment == GL30.GL_DEPTH_ATTACHMENT)
        {
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return framebuffer;
    }

    /**
     * Aims the probe down a pointer for this frame.
     *
     * @param pointer The pointer's origin and direction, or null for none.
     */
    public void aim(View pointer)
    {
        if (pointer == null)
        {
            aim = null;
            return;
        }
        double[] forward = new double[3];
        pointer.forward(forward);
        aim = new double[]{pointer.eyeX(), pointer.eyeY(), pointer.eyeZ(), forward[0], forward[1], forward[2]};
    }

    /**
     * Starts this frame's walk through the left eye's depth and takes in last frame's.
     *
     * @param eyes The eyes, with the scene drawn and nothing over it yet.
     * @param leftWorldToClip The left eye's matrix.
     */
    public void read(EyesTarget eyes, float[] leftWorldToClip)
    {
        int previous = readBuffers[next ^ 1];
        double[] previousAim = aims[next ^ 1];
        if (previousAim != null)
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previous);
            ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);
            float distance = mapped == null ? -1 : mapped.getFloat(0);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            found = distance >= 0;
            if (found)
            {
                for (int axis = 0; axis < 3; axis++)
                {
                    hit[axis] = previousAim[axis] + previousAim[3 + axis] * distance;
                }
            }
            aims[next ^ 1] = null;
        }

        aims[next] = aim != null && span(leftWorldToClip, aim, View.nearPlane(), REACH, span) ? aim : null;
        if (aims[next] != null)
        {
            walk(eyes, leftWorldToClip);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        next ^= 1;
    }

    private void walk(EyesTarget eyes, float[] leftWorldToClip)
    {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, eyes.getFramebuffer());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthFramebuffer);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        clip(leftWorldToClip, aim, span[0], start);
        clip(leftWorldToClip, aim, span[1], end);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, foundFramebuffer);
        GL11.glViewport(0, 0, 1, 1);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL20.glUseProgram(program);
        GL20.glUniform4f(startAt, start[0], start[1], start[2], start[3]);
        GL20.glUniform4f(endAt, end[0], end[1], end[2], end[3]);
        GL20.glUniform2f(spanAt, (float) span[0], (float) span[1]);
        GL20.glUniform2f(sizeAt, width, height);
        GL20.glUniform1f(depthScaleAt, (float) View.distanceAt(1));
        // The GPU plugin stores depth zero to one; 117 HD keeps OpenGL's default of minus one to one
        boolean zeroToOne = clipControl && GL11.glGetInteger(CLIP_DEPTH_MODE) == ZERO_TO_ONE;
        GL20.glUniform2f(storedToProjectedAt, zeroToOne ? 1 : 2, zeroToOne ? 0 : -1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, foundFramebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[next]);
        GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RED, GL11.GL_FLOAT, 0L);
    }

    /**
     * Gets the stretch of a ray inside an eye's view, ahead of the near plane.
     *
     * @param worldToClip The eye's matrix.
     * @param aim The ray's origin and unit direction.
     * @param from The nearest distance along the ray considered.
     * @param reach The farthest distance along the ray considered.
     * @param out Receives the nearest and farthest distances along the ray inside the view.
     * @return Whether any of the ray is inside the view; out is only set if so.
     */
    static boolean span(float[] worldToClip, double[] aim, double from, double reach, double[] out)
    {
        double low = from;
        double high = reach;
        for (int bound = 0; bound < 5; bound++)
        {
            // Inside means w + x, w - x, w + y, w - y and w - z all stay positive
            int row = bound < 2 ? 0 : bound < 4 ? 1 : 2;
            double sign = bound == 4 || bound % 2 == 1 ? -1 : 1;
            double at = clipRow(worldToClip, 3, aim, 1) + sign * clipRow(worldToClip, row, aim, 1);
            double towards = clipRow(worldToClip, 3, aim, 0) + sign * clipRow(worldToClip, row, aim, 0);
            if (Math.abs(towards) < 1e-12)
            {
                if (at < 0)
                {
                    return false;
                }
                continue;
            }
            double edge = -at / towards;
            if (towards > 0)
            {
                low = Math.max(low, edge);
            }
            else
            {
                high = Math.min(high, edge);
            }
        }
        if (low >= high)
        {
            return false;
        }
        out[0] = low;
        out[1] = high;
        return true;
    }

    /**
     * Gets one clip row of the ray's origin, or of its direction alone.
     */
    private static double clipRow(float[] worldToClip, int row, double[] aim, int origin)
    {
        int offset = origin == 1 ? 0 : 3;
        return worldToClip[row] * aim[offset] + worldToClip[4 + row] * aim[offset + 1] + worldToClip[8 + row] * aim[offset + 2] + worldToClip[12 + row] * origin;
    }

    private static void clip(float[] worldToClip, double[] aim, double distance, float[] out)
    {
        for (int row = 0; row < 4; row++)
        {
            out[row] = (float) (clipRow(worldToClip, row, aim, 1) + clipRow(worldToClip, row, aim, 0) * distance);
        }
    }

    /**
     * Gets where the pointer last met the world.
     *
     * @param out Receives the point, across, down and along.
     * @return Whether anything was hit; out is unchanged if not.
     */
    public boolean hit(double[] out)
    {
        if (found && aim != null)
        {
            System.arraycopy(hit, 0, out, 0, 3);
            return true;
        }
        return false;
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
        GL30.glDeleteFramebuffers(depthFramebuffer);
        GL30.glDeleteFramebuffers(foundFramebuffer);
        GL11.glDeleteTextures(depthTexture);
        GL11.glDeleteTextures(foundTexture);
        GL30.glDeleteVertexArrays(vertexArray);
        GL20.glDeleteProgram(program);
        GL15.glDeleteBuffers(readBuffers[0]);
        GL15.glDeleteBuffers(readBuffers[1]);
    }
}
