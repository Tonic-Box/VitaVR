package com.tonic.vitavr.xr;

import com.tonic.vitavr.hud.ColumnSplit;
import com.tonic.vitavr.space.HudShape;
import com.tonic.vitavr.space.Pose;
import lombok.Getter;
import org.lwjgl.openxr.KHRCompositionLayerCylinder;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrCompositionLayerCylinderKHR;
import org.lwjgl.openxr.XrCompositionLayerQuad;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSwapchainSubImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * The HUD panel, split into chat, middle and inventory columns, each its own curved or flat layer.
 * Cuts follow clear pixel columns; the columns join at middle size while an interface spans a cut.
 * When hidden, its layers are still sent, cleared.
 */
public final class HudPanel implements AutoCloseable
{
    private static final int STRIPS_PER_COLUMN = 12;
    private static final int CUT_EVERY_PICTURES = 20;
    private static final int CUT_MOVES = 2;

    private final Images images;
    private final XrSwapchainSubImage[] pictures = new XrSwapchainSubImage[HudShape.COLUMNS];
    private final int[] cutBounds;
    private int leftCut;
    private int rightCut;
    private int picturesSinceCut;
    private boolean joined;
    private double leftScale = 1;
    private double rightScale = 1;
    private double apartRadians;
    private final HudShape shape;
    private final XrCompositionLayerCylinderKHR[] arcs;
    private final XrCompositionLayerQuad[] chords;
    private final IntBuffer flipped;
    @Getter
    private final int pixelWidth;
    @Getter
    private final int pixelHeight;
    private boolean shown;
    private boolean cleared;
    private boolean closed;

    /**
     * Creates the panel's images in the OpenGL context current on the calling thread.
     *
     * @param session The headset's session.
     * @param room The space the panel is placed in.
     * @param width The interface width, in pixels.
     * @param height The interface height, in pixels.
     * @param leftPixels The chat side width, in pixels.
     * @param rightPixels The inventory side width, in pixels.
     * @param curved Whether the runtime supports curved layers.
     */
    public HudPanel(XrSession session, XrSpace room, int width, int height, int leftPixels, int rightPixels, boolean curved)
    {
        this.leftCut = leftPixels;
        this.rightCut = width - rightPixels;
        this.cutBounds = new int[] {leftPixels / 2, Math.min(width / 2, leftPixels * 3 / 2), width - rightPixels * 5 / 2, width - rightPixels / 2};
        this.images = Images.create(session, width, height);
        this.pixelWidth = width;
        this.pixelHeight = height;
        this.flipped = MemoryUtil.memAllocInt(width * height);
        this.shape = new HudShape(width, height, leftPixels, rightPixels, curved);
        this.arcs = curved ? new XrCompositionLayerCylinderKHR[HudShape.COLUMNS] : null;
        this.chords = curved ? null : new XrCompositionLayerQuad[HudShape.COLUMNS];
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            XrSwapchainSubImage picture;
            if (curved)
            {
                arcs[column] = XrCompositionLayerCylinderKHR.calloc()
                        .type(KHRCompositionLayerCylinder.XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR)
                        .layerFlags(XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                        .space(room)
                        .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
                picture = arcs[column].subImage();
            }
            else
            {
                chords[column] = XrCompositionLayerQuad.calloc().type$Default()
                        .layerFlags(XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                        .space(room)
                        .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
                picture = chords[column].subImage();
            }
            picture.swapchain(images.getSwapchain()).imageArrayIndex(0);
            pictures[column] = picture;
        }
        frame();
    }

    /** Sets each column's image rect. */
    private void frame()
    {
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            pictures[column].imageRect().offset().set(shape.fromPixel(column), 0);
            pictures[column].imageRect().extent().set(shape.width(column), pixelHeight);
        }
    }

    /**
     * Periodically moves the column cuts to clear pixel columns.
     */
    private void recut(int[] pixels)
    {
        if (++picturesSinceCut < CUT_EVERY_PICTURES)
        {
            return;
        }
        picturesSinceCut = 0;
        boolean covered = ColumnSplit.covered(pixels, pixelWidth, pixelHeight, cutBounds[0], cutBounds[1], cutBounds[2], cutBounds[3]);
        if (covered != joined)
        {
            joined = covered;
            sides();
            if (shape.placed())
            {
                stand();
            }
        }
        int[] cuts = ColumnSplit.find(pixels, pixelWidth, pixelHeight, cutBounds[0], cutBounds[1], cutBounds[2], cutBounds[3], leftCut, rightCut);
        if (Math.abs(cuts[0] - leftCut) < CUT_MOVES && Math.abs(cuts[1] - rightCut) < CUT_MOVES)
        {
            return;
        }
        leftCut = cuts[0];
        rightCut = cuts[1];
        shape.columns(leftCut, pixelWidth - rightCut);
        frame();
        if (shape.placed())
        {
            stand();
        }
    }

    /**
     * Sizes the panel. Must be called once before the first frame.
     *
     * @param radius The distance from the head, in metres.
     * @param pixelRadians The angle of one middle pixel, in radians.
     * @param leftScale The chat side scale relative to the middle.
     * @param rightScale The inventory side scale relative to the middle.
     * @param apartRadians The gap between each side and the middle, in radians.
     */
    public void shape(double radius, double pixelRadians, double leftScale, double rightScale, double apartRadians)
    {
        boolean first = shape.radius() == 0;
        this.leftScale = leftScale;
        this.rightScale = rightScale;
        this.apartRadians = apartRadians;
        shape.size(radius, pixelRadians, leftScale, rightScale, apartRadians);
        sides();
        if (first)
        {
            shape.place(Pose.ORIGIN);
            stand();
            shape.clear();
            clearPicture();
        }
    }

    /** Applies the side sizes, or middle size with no gap while joined. */
    private void sides()
    {
        shape.sides(joined ? 1 : leftScale, joined ? 1 : rightScale, joined ? 0 : apartRadians);
    }

    /**
     * Uploads the interfaces' picture while the panel is shown.
     *
     * @param pixels The picture, premultiplied ARGB, rows from the top.
     */
    public void upload(int[] pixels)
    {
        if (!shown)
        {
            return;
        }
        recut(pixels);
        for (int row = 0; row < pixelHeight; row++)
        {
            flipped.position((pixelHeight - 1 - row) * pixelWidth);
            flipped.put(pixels, row * pixelWidth, pixelWidth);
        }
        flipped.clear();
        send();
        cleared = false;
    }

    private void clearPicture()
    {
        for (int i = 0; i < pixelWidth * pixelHeight; i++)
        {
            flipped.put(i, 0);
        }
        send();
        cleared = true;
    }

    private void send()
    {
        images.acquire();
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, images.acquiredTexture());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, pixelWidth, pixelHeight, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, flipped);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        images.release();
    }

    /**
     * Places the panel around the head on the first frame after it is shown or reshaped.
     *
     * @param head The head pose, in the room.
     */
    public void standBefore(Pose head)
    {
        if (!shown || shape.placed())
        {
            return;
        }
        shape.place(head);
        stand();
    }

    /** Positions each column's layer from the shape. */
    private void stand()
    {
        double[] centre = shape.centre();
        double[][] corners = new double[4][3];
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            if (arcs != null)
            {
                double yaw = shape.yawOf(column);
                arcs[column].pose().position$().set((float) centre[0], (float) centre[1], (float) centre[2]);
                arcs[column].pose().orientation().set(0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2));
                arcs[column].radius((float) shape.radius()).centralAngle((float) shape.angle(column)).aspectRatio((float) shape.width(column) / pixelHeight);
                continue;
            }
            shape.chordCorners(column, corners);
            double acrossX = corners[1][0] - corners[0][0];
            double acrossZ = corners[1][2] - corners[0][2];
            double yaw = Math.atan2(-acrossZ, acrossX);
            chords[column].pose().position$().set((float) ((corners[0][0] + corners[2][0]) / 2), (float) ((corners[0][1] + corners[2][1]) / 2),
                    (float) ((corners[0][2] + corners[2][2]) / 2));
            chords[column].pose().orientation().set(0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2));
            chords[column].size().set((float) Math.hypot(acrossX, acrossZ), (float) shape.tall(column));
        }
    }

    /**
     * Shows or hides the panel. Showing places it anew; hiding clears it.
     *
     * @param show Whether to show it.
     */
    public void show(boolean show)
    {
        if (show && !shown)
        {
            shape.clear();
        }
        if (!show && !cleared)
        {
            clearPicture();
        }
        shown = show;
    }

    /** @return Whether the panel is shown. */
    public boolean shown()
    {
        return shown;
    }

    /**
     * Gets where a pointer crosses the panel, including past its outer edges for drags.
     *
     * @param aim The pointer pose, in the room.
     * @return The picture pixel x and y, possibly off the edge, or null if hidden or missed.
     */
    public int[] crossing(Pose aim)
    {
        double[] hit = shown ? shape.crossing(aim) : null;
        return hit == null ? null : new int[] {(int) hit[1], (int) hit[2]};
    }

    /**
     * Gets the distance from a pointer to the panel.
     *
     * @param aim The pointer pose, in the room.
     * @return The distance in metres, or infinity if it misses.
     */
    public double reach(Pose aim)
    {
        double[] hit = shown ? shape.crossing(aim) : null;
        return hit == null || hit[3] == 0 ? Double.POSITIVE_INFINITY : hit[0];
    }

    /** @return The panel as flat strips, each strip's corners in the room, left to right. */
    public double[][][] strips()
    {
        return shape.strips(STRIPS_PER_COLUMN);
    }

    /** @return Each strip's span across the picture, 0 to 1. */
    public double[][] stripsAcross()
    {
        return shape.stripsAcross(STRIPS_PER_COLUMN);
    }

    /**
     * Gets the strips that start at or after a point across the picture.
     *
     * @param across The start point, 0 to 1 across the picture.
     * @return The kept strip indices, left to right.
     */
    public int[] stripsFrom(double across)
    {
        double[][] all = stripsAcross();
        int[] kept = new int[all.length];
        int count = 0;
        for (int strip = 0; strip < all.length; strip++)
        {
            if (all[strip][0] >= across - 1e-9)
            {
                kept[count++] = strip;
            }
        }
        return Arrays.copyOf(kept, count);
    }

    /**
     * @return Where the middle starts across the picture, 0 to 1, or 0 while the columns are joined.
     */
    public double chatSideEnd()
    {
        return joined ? 0 : (double) shape.fromPixel(HudShape.MIDDLE) / pixelWidth;
    }

    /** @return The panel's layer addresses, sent every frame. */
    public long[] layers()
    {
        long[] addresses = new long[HudShape.COLUMNS];
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            addresses[column] = arcs != null ? arcs[column].address() : chords[column].address();
        }
        return addresses;
    }

    /** Frees the panel's images, in the context they were created in. Safe to call more than once. */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        images.close();
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            if (arcs != null)
            {
                arcs[column].free();
            }
            else
            {
                chords[column].free();
            }
        }
        MemoryUtil.memFree(flipped);
    }
}
