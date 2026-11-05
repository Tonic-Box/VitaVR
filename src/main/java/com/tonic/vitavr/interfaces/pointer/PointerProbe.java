package com.tonic.vitavr.interfaces.pointer;

import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.View;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Finds where a pointer meets the world by drawing a small depth view down it and reading the centre back a frame later.
 */
public final class PointerProbe implements AutoCloseable
{
    private static final int SIZE = 32;
    /** The probe's field of view, five degrees across. */
    private static final Fov NARROW = new Fov(-Math.toRadians(2.5), Math.toRadians(2.5), Math.toRadians(2.5), -Math.toRadians(2.5));

    private final int framebuffer;
    private final int depthBuffer;
    private final int[] readBuffers = new int[2];
    private final double[][] aims = new double[2][];
    private int next;
    @Getter
    private float[] worldToClip;
    private double[] aim;
    private boolean found;
    private final double[] hit = new double[3];
    private boolean closed;

    /**
     * Creates the framebuffer in the current OpenGL context, leaving none bound for drawing.
     */
    public PointerProbe()
    {
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        depthBuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthBuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT32F, SIZE, SIZE);
        GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthBuffer);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        for (int i = 0; i < readBuffers.length; i++)
        {
            readBuffers[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, Float.BYTES, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
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
            worldToClip = null;
            return;
        }
        double[] forward = new double[3];
        pointer.forward(forward);
        aim = new double[]{pointer.eyeX(), pointer.eyeY(), pointer.eyeZ(), forward[0], forward[1], forward[2]};
        worldToClip = pointer.worldToClip(NARROW);
    }

    public boolean aimed()
    {
        return aim != null;
    }

    /**
     * Clears the probe's depth.
     */
    public void clear()
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearDepth(0d);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Binds the probe for drawing.
     */
    public void use()
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, SIZE, SIZE);
    }

    /**
     * Starts reading back the centre depth and takes in last frame's read.
     */
    public void read()
    {
        int previous = readBuffers[next ^ 1];
        double[] previousAim = aims[next ^ 1];
        if (previousAim != null)
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previous);
            ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);
            float depth = mapped == null ? 0 : mapped.getFloat(0);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            found = depth > 0;
            if (found)
            {
                double distance = View.distanceAt(depth);
                for (int axis = 0; axis < 3; axis++)
                {
                    hit[axis] = previousAim[axis] + previousAim[3 + axis] * distance;
                }
            }
            aims[next ^ 1] = null;
        }

        aims[next] = aim;
        if (aim != null)
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readBuffers[next]);
            GL11.glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0L);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        next ^= 1;
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
        GL30.glDeleteFramebuffers(framebuffer);
        GL30.glDeleteRenderbuffers(depthBuffer);
        GL15.glDeleteBuffers(readBuffers[0]);
        GL15.glDeleteBuffers(readBuffers[1]);
    }
}
