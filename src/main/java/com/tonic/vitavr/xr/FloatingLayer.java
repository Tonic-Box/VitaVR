package com.tonic.vitavr.xr;

import com.tonic.vitavr.space.Billboard;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrCompositionLayerQuad;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/**
 * A fixed-size picture shown as its own quad layer, placed by a billboard.
 * It is sent every frame, cleared when empty, because SteamVR mixed up layers that came and went.
 */
public final class FloatingLayer implements AutoCloseable
{
    private final Images images;
    private final XrCompositionLayerQuad layer;
    private final IntBuffer flipped;
    private final int width;
    private final int height;
    private boolean cleared;
    private boolean closed;

    /**
     * Creates the layer's images in the OpenGL context current on the calling thread.
     *
     * @param headset The headset.
     * @param width The picture width, in pixels.
     * @param height The picture height, in pixels.
     */
    public FloatingLayer(Headset headset, int width, int height)
    {
        this.images = Images.create(headset.getSession(), width, height);
        this.width = width;
        this.height = height;
        this.flipped = MemoryUtil.memAllocInt(width * height);
        this.layer = XrCompositionLayerQuad.calloc().type$Default()
                .layerFlags(XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                .space(headset.getRoom())
                .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
        layer.subImage().swapchain(images.getSwapchain()).imageArrayIndex(0);
        layer.subImage().imageRect().offset().set(0, 0);
        layer.subImage().imageRect().extent().set(width, height);
        layer.pose().orientation().set(0, 0, 0, 1);
        layer.size().set(0.01f, 0.01f);
        clear();
    }

    /**
     * Clears the layer until the next upload.
     */
    public void clear()
    {
        if (cleared)
        {
            return;
        }
        for (int i = 0; i < width * height; i++)
        {
            flipped.put(i, 0);
        }
        send();
        cleared = true;
    }

    /**
     * Uploads a picture to the layer.
     *
     * @param pixels The picture, ARGB, rows from the top, the size of the layer.
     */
    public void upload(int[] pixels)
    {
        for (int row = 0; row < height; row++)
        {
            flipped.position((height - 1 - row) * width);
            flipped.put(pixels, row * width, width);
        }
        flipped.clear();
        send();
        cleared = false;
    }

    private void send()
    {
        images.acquire();
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, images.acquiredTexture());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, flipped);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        images.release();
    }

    /**
     * @return Whether the layer has been cleared since the last upload.
     */
    public boolean isCleared()
    {
        return cleared;
    }

    /**
     * Places the layer at a billboard's position, orientation and size.
     *
     * @param where The billboard.
     */
    public void place(Billboard where)
    {
        double[] centre = where.centre();
        double[] turn = where.orientation();
        layer.pose().position$().set((float) centre[0], (float) centre[1], (float) centre[2]);
        layer.pose().orientation().set((float) turn[0], (float) turn[1], (float) turn[2], (float) turn[3]);
        layer.size().set((float) where.metresWide(), (float) where.metresTall());
    }

    /**
     * @return The layer's address, for the frame's layer list.
     */
    public long address()
    {
        return layer.address();
    }

    /**
     * Frees the layer's images, in the context they were created in. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        images.close();
        layer.free();
        MemoryUtil.memFree(flipped);
    }
}
