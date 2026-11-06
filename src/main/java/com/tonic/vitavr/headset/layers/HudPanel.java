package com.tonic.vitavr.headset.layers;

import com.tonic.vitavr.headset.Headset;
import com.tonic.vitavr.headset.Images;
import com.tonic.vitavr.interfaces.hud.ColumnSplit;
import com.tonic.vitavr.interfaces.hud.HudLayout;
import com.tonic.vitavr.interfaces.hud.HudPiece;
import com.tonic.vitavr.interfaces.hud.HudShape;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;
import lombok.Getter;
import lombok.Value;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.openxr.KHRCompositionLayerCylinder;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrCompositionLayerCylinderKHR;
import org.lwjgl.openxr.XrCompositionLayerQuad;
import org.lwjgl.openxr.XrSwapchainSubImage;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The HUD panel, split into chat, middle and inventory columns, each its own curved or flat layer.
 * Cuts follow clear pixel columns; the columns join at middle size while an interface spans a cut.
 * Moved pieces are cut out of the columns into a quad each, until the columns join. Every layer is made with the panel
 * and sent every frame, parked or cleared when unused, because SteamVR mixes up layers that come and go.
 */
public final class HudPanel implements AutoCloseable
{
    private static final int STRIPS_PER_COLUMN = 12;
    private static final int CUT_EVERY_PICTURES = 20;
    private static final int CUT_MOVES = 2;
    /** The outline drawn round a piece the pointer would move, premultiplied ARGB, and its width in pixels. */
    private static final int OUTLINE = 0xFFFFFFFF;
    private static final int OUTLINE_PIXELS = 2;
    /** The most of the picture a piece may cover; anything bigger is a layer of the whole interfaces, not a piece. */
    private static final double MOST_OF_PICTURE = 0.5;
    /** The alpha a column pixel needs to hide a moved piece behind it from the pointer. */
    private static final int OPAQUE_ENOUGH = 24;

    private final Headset headset;
    @Getter
    private final HudLayout layout;
    private final Map<HudPiece, int[]> found = new EnumMap<>(HudPiece.class);
    private final Map<HudPiece, Cutout> cutouts = new EnumMap<>(HudPiece.class);
    private double pixelRadians;
    /** The moved pieces side by side, each the full picture height with the piece in the middle; the last column stays clear. */
    private final Images pieceImages;
    private final IntBuffer pieceFlipped;
    private final int pieceWidth;
    private final int[] blankRow;
    private boolean piecesCleared;
    private int[] lastPixels;

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
     * @param headset The headset, whose room the panel is placed in.
     * @param width The interface width, in pixels.
     * @param height The interface height, in pixels.
     * @param leftPixels The chat side width, in pixels.
     * @param rightPixels The inventory side width, in pixels.
     * @param layout Where moved pieces stand.
     */
    public HudPanel(Headset headset, int width, int height, int leftPixels, int rightPixels, HudLayout layout)
    {
        this.headset = headset;
        this.layout = layout;
        boolean curved = headset.isCylinders();
        this.leftCut = leftPixels;
        this.rightCut = width - rightPixels;
        this.cutBounds = new int[] {leftPixels / 2, Math.min(width / 2, leftPixels * 3 / 2), width - rightPixels * 5 / 2, width - rightPixels / 2};
        this.images = Images.create(headset.getSession(), width, height);
        this.pixelWidth = width;
        this.pixelHeight = height;
        this.flipped = MemoryUtil.memAllocInt(width * height);
        this.pieceWidth = width + 1;
        this.pieceImages = Images.create(headset.getSession(), pieceWidth, height);
        this.pieceFlipped = MemoryUtil.memAllocInt(pieceWidth * height);
        this.blankRow = new int[width];
        for (HudPiece piece : HudPiece.values())
        {
            XrCompositionLayerQuad quad = XrCompositionLayerQuad.calloc().type$Default()
                    .layerFlags(XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                    .space(headset.getRoom())
                    .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
            quad.subImage().swapchain(pieceImages.getSwapchain()).imageArrayIndex(0);
            Cutout cutout = new Cutout(quad);
            park(cutout);
            cutouts.put(piece, cutout);
        }
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
                        .space(headset.getRoom())
                        .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
                picture = arcs[column].subImage();
            }
            else
            {
                chords[column] = XrCompositionLayerQuad.calloc().type$Default()
                        .layerFlags(XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                        .space(headset.getRoom())
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
        this.pixelRadians = pixelRadians;
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
            clearPieces();
        }
    }

    /** Applies the side sizes, or middle size with no gap while joined. */
    private void sides()
    {
        shape.sides(joined ? 1 : leftScale, joined ? 1 : rightScale, joined ? 0 : apartRadians);
    }

    /**
     * Uploads the interfaces' picture while the panel is shown, cutting moved pieces out into their own layers.
     *
     * @param pixels The picture, premultiplied ARGB, rows from the top.
     * @param boxes Each shown piece's box on the picture as x, y, width and height; pieces not shown are left out.
     * @param outlined The piece to outline, or null.
     */
    public void upload(int[] pixels, Map<HudPiece, int[]> boxes, HudPiece outlined)
    {
        if (!shown)
        {
            return;
        }
        lastPixels = pixels;
        recut(pixels);
        for (int row = 0; row < pixelHeight; row++)
        {
            flipped.position((pixelHeight - 1 - row) * pixelWidth);
            flipped.put(pixels, row * pixelWidth, pixelWidth);
        }
        flipped.clear();
        found.clear();
        int slot = 0;
        for (HudPiece piece : HudPiece.values())
        {
            int[] box = boxes.get(piece) == null ? null : clipped(boxes.get(piece));
            if (box != null && (double) box[2] * box[3] > MOST_OF_PICTURE * pixelWidth * pixelHeight)
            {
                box = null;
            }
            if (box != null)
            {
                found.put(piece, box);
            }
            if (box != null && !joined && layout.moved(piece) && slot + box[2] <= pixelWidth)
            {
                if (slot == 0)
                {
                    wipePieces();
                }
                cutOut(cutouts.get(piece), box, pixels, slot, piece == outlined);
                slot += box[2];
                continue;
            }
            hideCutout(piece);
            if (box != null && !joined && piece == outlined)
            {
                outlineFlipped(box);
            }
        }
        send();
        cleared = false;
        if (slot > 0)
        {
            sendPieces();
            piecesCleared = false;
        }
        else
        {
            clearPieces();
        }
    }

    /**
     * Gets a box kept inside the picture, or null if none of it is.
     */
    private int[] clipped(int[] box)
    {
        int left = Math.max(0, box[0]);
        int top = Math.max(0, box[1]);
        int right = Math.min(pixelWidth, box[0] + box[2]);
        int bottom = Math.min(pixelHeight, box[1] + box[3]);
        return right <= left || bottom <= top ? null : new int[]{left, top, right - left, bottom - top};
    }

    /**
     * Moves a piece's pixels from the columns into its slot of the pieces' picture, centred on the picture height.
     */
    private void cutOut(Cutout cutout, int[] box, int[] pixels, int slot, boolean outline)
    {
        int top = (pixelHeight - box[3]) / 2;
        for (int row = 0; row < box[3]; row++)
        {
            pieceFlipped.position((pixelHeight - 1 - top - row) * pieceWidth + slot);
            pieceFlipped.put(pixels, (box[1] + row) * pixelWidth + box[0], box[2]);
            flipped.position((pixelHeight - 1 - box[1] - row) * pixelWidth + box[0]);
            flipped.put(blankRow, 0, box[2]);
        }
        pieceFlipped.clear();
        flipped.clear();
        if (outline)
        {
            outlinePiece(slot, top, box[2], box[3]);
        }
        cutout.quad.subImage().imageRect().offset().set(slot, 0);
        cutout.quad.subImage().imageRect().extent().set(box[2], pixelHeight);
        cutout.box = box;
        cutout.shown = true;
    }

    private void hideCutout(HudPiece piece)
    {
        Cutout cutout = cutouts.get(piece);
        if (cutout.shown)
        {
            cutout.shown = false;
            park(cutout);
        }
    }

    /**
     * Points an unused piece's quad at the clear last column and shrinks it out of sight.
     */
    private void park(Cutout cutout)
    {
        cutout.quad.subImage().imageRect().offset().set(pixelWidth, 0);
        cutout.quad.subImage().imageRect().extent().set(1, pixelHeight);
        cutout.quad.pose().orientation().set(0, 0, 0, 1);
        cutout.quad.pose().position$().set(0, -100, 0);
        cutout.quad.size().set(0.001f, 0.001f);
    }

    /**
     * Draws the outline round a piece in the pieces' picture.
     */
    private void outlinePiece(int slot, int top, int width, int height)
    {
        for (int y = 0; y < height; y++)
        {
            int row = (pixelHeight - 1 - top - y) * pieceWidth + slot;
            for (int x = 0; x < width; x++)
            {
                if (x < OUTLINE_PIXELS || y < OUTLINE_PIXELS || x >= width - OUTLINE_PIXELS || y >= height - OUTLINE_PIXELS)
                {
                    pieceFlipped.put(row + x, OUTLINE);
                }
            }
        }
    }

    private void wipePieces()
    {
        MemoryUtil.memSet(MemoryUtil.memAddress(pieceFlipped), 0, (long) pieceWidth * pixelHeight * Integer.BYTES);
    }

    private void clearPieces()
    {
        if (piecesCleared)
        {
            return;
        }
        wipePieces();
        sendPieces();
        piecesCleared = true;
    }

    private void sendPieces()
    {
        pieceImages.acquire();
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pieceImages.acquiredTexture());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, pieceWidth, pixelHeight, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pieceFlipped);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        pieceImages.release();
    }


    /**
     * Draws the outline round a box of the columns' picture.
     */
    private void outlineFlipped(int[] box)
    {
        for (int y = box[1]; y < box[1] + box[3]; y++)
        {
            int row = (pixelHeight - 1 - y) * pixelWidth;
            for (int x = box[0]; x < box[0] + box[2]; x++)
            {
                if (x < box[0] + OUTLINE_PIXELS || y < box[1] + OUTLINE_PIXELS || x >= box[0] + box[2] - OUTLINE_PIXELS || y >= box[1] + box[3] - OUTLINE_PIXELS)
                {
                    flipped.put(row + x, OUTLINE);
                }
            }
        }
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
        if (!show)
        {
            for (HudPiece piece : HudPiece.values())
            {
                hideCutout(piece);
            }
            clearPieces();
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
        if (!shown)
        {
            return null;
        }
        double[] hit = columnsCrossing(aim);
        HudPiece piece = nearestCutout(aim, blocks(hit) ? hit[0] : Double.POSITIVE_INFINITY);
        if (piece != null)
        {
            Cutout cutout = cutouts.get(piece);
            int[] at = cutout.board.crossing(aim);
            return new int[] {cutout.box[0] + at[0], cutout.box[1] + at[1]};
        }
        return hit == null ? null : new int[] {(int) hit[1], (int) hit[2]};
    }

    /**
     * Gets where a pointer crosses the columns, treating the holes of moved pieces as misses.
     */
    private double[] columnsCrossing(Pose aim)
    {
        double[] hit = shape.crossing(aim);
        if (hit == null || hit[3] == 0)
        {
            return hit;
        }
        for (Cutout cutout : cutouts.values())
        {
            if (cutout.shown && inside(cutout.box, hit[1], hit[2]))
            {
                return null;
            }
        }
        return hit;
    }

    /**
     * Gets the moved piece a pointer hits nearer than a distance, or null.
     */
    private HudPiece nearestCutout(Pose aim, double nearerThan)
    {
        standCutouts();
        HudPiece nearest = null;
        double best = nearerThan;
        for (Map.Entry<HudPiece, Cutout> entry : cutouts.entrySet())
        {
            double reach = entry.getValue().shown ? entry.getValue().board.reach(aim) : Double.POSITIVE_INFINITY;
            if (reach < best)
            {
                best = reach;
                nearest = entry.getKey();
            }
        }
        return nearest;
    }

    /**
     * Gets whether a columns hit lands on a drawn pixel, so it hides any moved piece behind it.
     */
    private boolean blocks(double[] hit)
    {
        if (hit == null || hit[3] == 0 || lastPixels == null)
        {
            return false;
        }
        int x = (int) hit[1];
        int y = (int) hit[2];
        return x >= 0 && y >= 0 && x < pixelWidth && y < pixelHeight && lastPixels[y * pixelWidth + x] >>> 24 >= OPAQUE_ENOUGH;
    }

    private static boolean inside(int[] box, double x, double y)
    {
        return x >= box[0] && y >= box[1] && x < box[0] + box[2] && y < box[1] + box[3];
    }

    /**
     * Gets the piece a pointer would grab and the pixel of it grabbed, or null; nothing can be grabbed while the columns are joined.
     *
     * @param aim The pointer pose, in the room.
     * @return The grab, or null.
     */
    public Grab grabbing(Pose aim)
    {
        if (!shown || joined)
        {
            return null;
        }
        double[] hit = columnsCrossing(aim);
        boolean onColumns = hit != null && hit[3] != 0;
        HudPiece piece = nearestCutout(aim, blocks(hit) ? hit[0] : Double.POSITIVE_INFINITY);
        if (piece != null)
        {
            int[] at = cutouts.get(piece).board.crossing(aim);
            return new Grab(piece, at[0], at[1]);
        }
        if (!onColumns)
        {
            return null;
        }
        for (Map.Entry<HudPiece, int[]> entry : found.entrySet())
        {
            int[] box = entry.getValue();
            if (!layout.moved(entry.getKey()) && inside(box, hit[1], hit[2]))
            {
                return new Grab(entry.getKey(), hit[1] - box[0], hit[2] - box[1]);
            }
        }
        return null;
    }

    /**
     * Lifts a grabbed piece out of the default layout where it stands, if it is not moved already.
     *
     * @param piece The piece.
     */
    public void lift(HudPiece piece)
    {
        int[] box = found.get(piece);
        if (layout.moved(piece) || box == null)
        {
            return;
        }
        frameLayout();
        double middleX = box[0] + box[2] / 2.0;
        layout.standAt(piece, shape.pointAt(middleX, box[1] + box[3] / 2.0), shape.metresPerPixelAt(middleX));
    }

    /**
     * Moves a held piece so the pixel it was grabbed by stays on the pointer.
     *
     * @param grab The grab.
     * @param aim The pointer pose, in the room.
     */
    public void hold(Grab grab, Pose aim)
    {
        int[] box = found.get(grab.getPiece());
        if (box == null || !layout.moved(grab.getPiece()))
        {
            return;
        }
        frameLayout();
        layout.drag(grab.getPiece(), aim, box[2], box[3], grab.getX(), grab.getY());
    }

    /**
     * Lays every shown moved piece out from the layout.
     */
    private void standCutouts()
    {
        frameLayout();
        for (Map.Entry<HudPiece, Cutout> entry : cutouts.entrySet())
        {
            Cutout cutout = entry.getValue();
            if (cutout.shown && layout.moved(entry.getKey()))
            {
                layout.lay(entry.getKey(), cutout.box[2], cutout.box[3], cutout.board);
                layout.lay(entry.getKey(), cutout.box[2], pixelHeight, cutout.drawn);
                double[] centre = cutout.drawn.centre();
                double[] turn = cutout.drawn.orientation();
                cutout.quad.pose().position$().set((float) centre[0], (float) centre[1], (float) centre[2]);
                cutout.quad.pose().orientation().set((float) turn[0], (float) turn[1], (float) turn[2], (float) turn[3]);
                cutout.quad.size().set((float) cutout.drawn.metresWide(), (float) cutout.drawn.metresTall());
            }
        }
    }

    private void frameLayout()
    {
        layout.frame(shape.eye(), shape.yaw(), pixelRadians);
    }

    /**
     * Gets the moved pieces shown, for drawing them elsewhere.
     *
     * @return Each piece's corners in the room and its box on the picture.
     */
    public List<Moved> moved()
    {
        standCutouts();
        List<Moved> shownPieces = new ArrayList<>();
        for (Map.Entry<HudPiece, Cutout> entry : cutouts.entrySet())
        {
            Cutout cutout = entry.getValue();
            if (cutout.shown)
            {
                double[][] corners = new double[4][3];
                cutout.board.corners(corners);
                shownPieces.add(new Moved(entry.getKey(), corners, cutout.box));
            }
        }
        return shownPieces;
    }

    /**
     * Gets the distance from a pointer to the panel.
     *
     * @param aim The pointer pose, in the room.
     * @return The distance in metres, or infinity if it misses.
     */
    public double reach(Pose aim)
    {
        if (!shown)
        {
            return Double.POSITIVE_INFINITY;
        }
        double[] hit = columnsCrossing(aim);
        double columns = hit == null || hit[3] == 0 ? Double.POSITIVE_INFINITY : hit[0];
        HudPiece piece = nearestCutout(aim, blocks(hit) ? columns : Double.POSITIVE_INFINITY);
        return piece == null ? columns : cutouts.get(piece).board.reach(aim);
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

    /** @return The panel's layer addresses, the columns then one per piece, the same every frame. */
    public long[] layers()
    {
        standCutouts();
        long[] addresses = new long[HudShape.COLUMNS + cutouts.size()];
        for (int column = 0; column < HudShape.COLUMNS; column++)
        {
            addresses[column] = arcs != null ? arcs[column].address() : chords[column].address();
        }
        int next = HudShape.COLUMNS;
        for (Cutout cutout : cutouts.values())
        {
            addresses[next++] = cutout.quad.address();
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
        pieceImages.close();
        for (Cutout cutout : cutouts.values())
        {
            cutout.quad.free();
        }
        MemoryUtil.memFree(pieceFlipped);
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

    /**
     * A piece the pointer would grab and the pixel of it grabbed.
     */
    @Value
    public static class Grab
    {
        HudPiece piece;
        double x;
        double y;
    }

    /**
     * A moved piece as shown: its corners in the room and its box on the picture.
     */
    @Value
    public static class Moved
    {
        HudPiece piece;
        double[][] corners;
        int[] box;
    }

    /**
     * A piece's quad, its board in the room and where it was cut from.
     */
    private static final class Cutout
    {
        private final XrCompositionLayerQuad quad;
        private final Billboard board = new Billboard();
        private final Billboard drawn = new Billboard();
        private int[] box;
        private boolean shown;

        private Cutout(XrCompositionLayerQuad quad)
        {
            this.quad = quad;
        }
    }
}
