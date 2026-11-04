package com.tonic.vitavr.xr;

import org.lwjgl.openxr.XrCompositionLayerProjection;
import org.lwjgl.openxr.XrCompositionLayerProjectionView;
import org.lwjgl.openxr.XrView;

/**
 * The headset's projection layer, with one image chain per eye.
 */
public final class EyeLayer implements AutoCloseable
{
    private static final int EYES = 2;

    private final Images[] images = new Images[EYES];
    private final XrCompositionLayerProjectionView.Buffer views;
    private final XrCompositionLayerProjection layer;
    private boolean closed;

    /**
     * Creates both eyes' image chains in the OpenGL context current on the calling thread.
     *
     * @param headset The headset.
     * @param widths The width of each eye's images, in pixels.
     * @param heights The height of each eye's images, in pixels.
     */
    public EyeLayer(Headset headset, int[] widths, int[] heights)
    {
        views = XrCompositionLayerProjectionView.calloc(EYES);
        layer = XrCompositionLayerProjection.calloc();
        try
        {
            for (int eye = 0; eye < EYES; eye++)
            {
                images[eye] = Images.create(headset.getSession(), widths[eye], heights[eye]);
                XrCompositionLayerProjectionView view = views.get(eye);
                view.type$Default();
                view.subImage().swapchain(images[eye].getSwapchain()).imageArrayIndex(0);
                view.subImage().imageRect().offset().set(0, 0);
                view.subImage().imageRect().extent().set(widths[eye], heights[eye]);
            }
            layer.type$Default().space(headset.getRoom()).views(views);
        }
        catch (RuntimeException e)
        {
            close();
            throw e;
        }
    }

    /**
     * Acquires an eye's next image, waiting until the runtime is done with it.
     *
     * @param eye 0 for the left, 1 for the right.
     * @return The image's framebuffer.
     */
    public int acquire(int eye)
    {
        return images[eye].acquire();
    }

    /**
     * Releases an eye's image and records the view it was drawn from.
     *
     * @param eye 0 for the left, 1 for the right.
     * @param view The eye's view this frame.
     */
    public void release(int eye, XrView view)
    {
        images[eye].release();
        views.get(eye).pose(view.pose()).fov(view.fov());
    }

    /**
     * @return The layer's address, for the frame's layer list.
     */
    public long address()
    {
        return layer.address();
    }

    /**
     * Frees the images. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        for (Images chain : images)
        {
            if (chain != null)
            {
                chain.close();
            }
        }
        views.free();
        layer.free();
    }
}
