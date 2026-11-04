package com.tonic.vitavr.xr;

import lombok.Getter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSwapchain;
import org.lwjgl.openxr.XrSwapchainCreateInfo;
import org.lwjgl.openxr.XrSwapchainImageAcquireInfo;
import org.lwjgl.openxr.XrSwapchainImageBaseHeader;
import org.lwjgl.openxr.XrSwapchainImageOpenGLKHR;
import org.lwjgl.openxr.XrSwapchainImageReleaseInfo;
import org.lwjgl.openxr.XrSwapchainImageWaitInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static com.tonic.vitavr.xr.XrChecks.check;

/**
 * An OpenXR swapchain with a framebuffer per image, using sRGB when offered.
 */
public final class Images implements AutoCloseable
{
    @Getter
    private final XrSwapchain swapchain;
    private final XrInstance instance;
    @Getter
    private final int width;
    @Getter
    private final int height;
    private final int[] textures;
    private final int[] framebuffers;
    private int acquired = -1;
    private int picture;
    private int pictureFramebuffer;
    private boolean closed;

    private Images(XrSwapchain swapchain, XrInstance instance, int width, int height, int[] textures)
    {
        this.swapchain = swapchain;
        this.instance = instance;
        this.width = width;
        this.height = height;
        this.textures = textures;
        this.framebuffers = new int[textures.length];

        int[] previous = new int[1];
        GL11.glGetIntegerv(GL30.GL_DRAW_FRAMEBUFFER_BINDING, previous);
        for (int i = 0; i < textures.length; i++)
        {
            framebuffers[i] = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[i]);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textures[i], 0);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous[0]);
    }

    /**
     * Creates a swapchain in the OpenGL context current on the calling thread.
     *
     * @param session The headset's session.
     * @param width The image width, in pixels.
     * @param height The image height, in pixels.
     * @return The new swapchain.
     * @throws IllegalStateException if the runtime refuses it
     */
    public static Images create(XrSession session, int width, int height)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrSwapchainCreateInfo info = XrSwapchainCreateInfo.calloc(stack).type$Default()
                    .usageFlags(XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR10.XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT)
                    .format(format(session, stack))
                    .sampleCount(1)
                    .width(width)
                    .height(height)
                    .faceCount(1)
                    .arraySize(1)
                    .mipCount(1);
            PointerBuffer made = stack.mallocPointer(1);
            check(XR10.xrCreateSwapchain(session, info, made), session.getInstance(), "Making the headset's images");
            XrSwapchain swapchain = new XrSwapchain(made.get(0), session);

            IntBuffer count = stack.mallocInt(1);
            check(XR10.xrEnumerateSwapchainImages(swapchain, count, null), session.getInstance(), "Listing the headset's images");
            XrSwapchainImageOpenGLKHR.Buffer images = XrSwapchainImageOpenGLKHR.calloc(count.get(0), stack);
            for (int i = 0; i < count.get(0); i++)
            {
                images.get(i).type$Default();
            }
            check(XR10.xrEnumerateSwapchainImages(swapchain, count, XrSwapchainImageBaseHeader.create(images.address(), count.get(0))), session.getInstance(), "Listing the headset's images");
            int[] textures = new int[count.get(0)];
            for (int i = 0; i < textures.length; i++)
            {
                textures[i] = images.get(i).image();
            }
            return new Images(swapchain, session.getInstance(), width, height, textures);
        }
    }

    private static long format(XrSession session, MemoryStack stack)
    {
        IntBuffer count = stack.mallocInt(1);
        check(XR10.xrEnumerateSwapchainFormats(session, count, null), session.getInstance(), "Listing the image formats");
        LongBuffer formats = stack.mallocLong(count.get(0));
        check(XR10.xrEnumerateSwapchainFormats(session, count, formats), session.getInstance(), "Listing the image formats");
        for (long wanted : new long[]{GL21.GL_SRGB8_ALPHA8, GL11.GL_RGBA8})
        {
            for (int i = 0; i < count.get(0); i++)
            {
                if (formats.get(i) == wanted)
                {
                    return wanted;
                }
            }
        }
        return formats.get(0);
    }

    /**
     * Shows a picture, flipped into the next image on the card.
     *
     * @param pixels The picture, ARGB, rows from the top.
     */
    public void upload(int[] pixels)
    {
        readyPicture();
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, picture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        show(false);
    }

    /**
     * Clears the picture and the next image.
     */
    public void clear()
    {
        readyPicture();
        show(true);
    }

    /**
     * @return The texture with the last picture, right way up, or 0 before one was shown.
     */
    public int getPicture()
    {
        return picture;
    }

    private void readyPicture()
    {
        if (picture != 0)
        {
            return;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        picture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, picture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        int drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        pictureFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pictureFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, picture, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn);
    }

    /**
     * Flips the picture into the next image, or clears both, with scissor and sRGB conversion off.
     */
    private void show(boolean cleared)
    {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        try
        {
            int image = acquire();
            if (cleared)
            {
                float[] clearColour = new float[4];
                GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColour);
                GL11.glClearColor(0, 0, 0, 0);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pictureFramebuffer);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, image);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glClearColor(clearColour[0], clearColour[1], clearColour[2], clearColour[3]);
            }
            else
            {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, pictureFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, image);
                GL30.glBlitFramebuffer(0, 0, width, height, 0, height, width, 0, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            }
        }
        finally
        {
            release();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn);
            if (scissor)
            {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            if (srgb)
            {
                GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            }
        }
    }

    /**
     * Acquires the next image, waiting until the runtime is done with it.
     *
     * @return The acquired image's framebuffer.
     * @throws IllegalStateException if an image is already taken
     */
    public int acquire()
    {
        if (acquired >= 0)
        {
            throw new IllegalStateException("An image is already taken");
        }
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer index = stack.mallocInt(1);
            check(XR10.xrAcquireSwapchainImage(swapchain, XrSwapchainImageAcquireInfo.calloc(stack).type$Default(), index), instance, "Taking an image");
            XrSwapchainImageWaitInfo wait = XrSwapchainImageWaitInfo.calloc(stack).type$Default().timeout(XR10.XR_INFINITE_DURATION);
            check(XR10.xrWaitSwapchainImage(swapchain, wait), instance, "Waiting for an image");
            acquired = index.get(0);
            return framebuffers[acquired];
        }
    }

    /**
     * @return The acquired image's texture.
     */
    public int acquiredTexture()
    {
        if (acquired < 0)
        {
            throw new IllegalStateException("No image is taken");
        }
        return textures[acquired];
    }

    /**
     * Releases the acquired image to the runtime.
     */
    public void release()
    {
        if (acquired < 0)
        {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            check(XR10.xrReleaseSwapchainImage(swapchain, XrSwapchainImageReleaseInfo.calloc(stack).type$Default()), instance, "Handing an image back");
            acquired = -1;
        }
    }

    /**
     * Destroys the swapchain, in the OpenGL context it was created in. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        for (int framebuffer : framebuffers)
        {
            GL30.glDeleteFramebuffers(framebuffer);
        }
        if (picture != 0)
        {
            GL30.glDeleteFramebuffers(pictureFramebuffer);
            GL11.glDeleteTextures(picture);
        }
        XR10.xrDestroySwapchain(swapchain);
    }
}
