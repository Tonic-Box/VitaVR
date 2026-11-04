package com.tonic.vitavr.render;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/**
 * A multisampled framebuffer holding the views side by side, left eye first, resolved into a texture after the scene.
 */
final class EyesTarget implements AutoCloseable
{
    @Getter
    private final int eyeWidth;
    @Getter
    private final int eyeHeight;
    private final int views;
    @Getter
    private final int framebuffer;
    private final int colourBuffer;
    private final int depthBuffer;
    private final int resolveFramebuffer;
    @Getter
    private final int texture;
    private boolean closed;

    /**
     * Creates the framebuffers in the current OpenGL context, leaving none bound for drawing.
     *
     * @param eyeWidth The width of each eye in pixels.
     * @param eyeHeight The height of each eye in pixels.
     * @param samples The samples per pixel, 1 for none.
     * @param views The number of side by side views.
     */
    EyesTarget(int eyeWidth, int eyeHeight, int samples, int views)
    {
        this.eyeWidth = eyeWidth;
        this.eyeHeight = eyeHeight;
        this.views = views;
        int width = eyeWidth * views;
        int sampled = samples <= 1 ? 0 : Math.min(samples, GL11.glGetInteger(GL30.GL_MAX_SAMPLES));

        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        colourBuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colourBuffer);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, sampled, GL11.GL_RGBA8, width, eyeHeight);
        GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, colourBuffer);
        depthBuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthBuffer);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, sampled, GL30.GL_DEPTH_COMPONENT32F, width, eyeHeight);
        GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthBuffer);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);

        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, eyeHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        resolveFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
        {
            close();
            throw new IllegalStateException("The eyes' framebuffer is incomplete: " + status);
        }
    }

    /**
     * Clears all views to a colour and reverse depth to 0, leaving the target bound for drawing.
     *
     * @param rgb The colour as 0xRRGGBB.
     */
    void clear(int rgb)
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        GL11.glClearDepth(0d);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Binds the target with the viewport on one eye.
     *
     * @param eye 0 for left, 1 for right.
     */
    void use(int eye)
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL11.glViewport(eye * eyeWidth, 0, eyeWidth, eyeHeight);
    }

    /**
     * Resolves the framebuffer into the texture.
     */
    void resolve()
    {
        int width = eyeWidth * views;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
        GL30.glBlitFramebuffer(0, 0, width, eyeHeight, 0, 0, width, eyeHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /**
     * Copies a resolved eye into a framebuffer of one eye's size.
     *
     * @param eye 0 for left, 1 for right.
     * @param target The framebuffer to copy into.
     */
    void copyEye(int eye, int target)
    {
        int left = eye * eyeWidth;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, resolveFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
        GL30.glBlitFramebuffer(left, 0, left + eyeWidth, eyeHeight, 0, 0, eyeWidth, eyeHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /**
     * Copies part of a resolved view into part of another framebuffer, scaled to fit.
     *
     * @param eye The view index.
     * @param from The source rect as x, y, width and height from the bottom left.
     * @param target The framebuffer to copy into.
     * @param into The destination rect as x, y, width and height from the bottom left.
     */
    void copyEye(int eye, int[] from, int target, int[] into)
    {
        int left = eye * eyeWidth + from[0];
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, resolveFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
        GL30.glBlitFramebuffer(left, from[1], left + from[2], from[1] + from[3], into[0], into[1], into[0] + into[2], into[1] + into[3], GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_LINEAR);
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
        GL30.glDeleteFramebuffers(resolveFramebuffer);
        GL30.glDeleteRenderbuffers(colourBuffer);
        GL30.glDeleteRenderbuffers(depthBuffer);
        GL11.glDeleteTextures(texture);
    }
}
