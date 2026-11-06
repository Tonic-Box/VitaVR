package com.tonic.vitavr.render.recording;

import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.config.OneEyeCamera;
import com.tonic.vitavr.config.RecordMode;
import com.tonic.vitavr.headset.layers.HudPanel;
import com.tonic.vitavr.headset.layers.RoomPanel;
import com.tonic.vitavr.interfaces.hud.HudPicture;
import com.tonic.vitavr.interfaces.hud.Layers;
import com.tonic.vitavr.interfaces.pointer.WorldMenu;
import com.tonic.vitavr.render.EyesTarget;
import com.tonic.vitavr.render.draw.AtlasQuad;
import com.tonic.vitavr.space.Fov;
import com.tonic.vitavr.space.Pose;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.space.placement.Placement;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.List;

/**
 * The 1920 by 1080 picture recordings and the stream show: both eyes, the cropped left eye, or a steadied head camera, with the headset layers drawn in.
 */
final class Composite implements AutoCloseable
{
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    static final int MENU = 0;
    static final int LABEL = 1;
    static final int SETTINGS = 2;
    static final int KEYBOARD = 3;
    static final int FLOATING = 4;
    private static final double SPECTATOR_FOV = Math.toRadians(90);
    private static final int EYES = 2;

    private final RecordMode mode;
    @Getter
    private final boolean steadied;
    private final EyesTarget picture;
    @Getter
    private final EyesTarget spectatorTarget;
    private final AtlasQuad interfaces;
    /** The whole interfaces, which moved pieces are drawn from while interfaces holds the columns with them cut out. */
    private final AtlasQuad pieces;
    private int[] columns = new int[0];
    private final int interfacesWidth;
    private final int interfacesHeight;
    private final AtlasQuad[] floating;
    private final int[][] floatingSizes;
    private final SpectatorView spectator = new SpectatorView();
    @Getter
    private final View spectatorView = new View();
    private final Fov spectatorFov;
    private boolean closed;

    private Composite(VitaVRConfig config, int[] interfacesSize, int[][] floatingSizes)
    {
        this.mode = config.recordMode();
        this.steadied = mode == RecordMode.ONE_EYE && config.oneEyeCamera() == OneEyeCamera.STEADIED;
        this.picture = new EyesTarget(WIDTH, HEIGHT, 1, 1);
        this.spectatorTarget = steadied ? new EyesTarget(WIDTH, HEIGHT, config.samples(), 1) : null;
        this.interfaces = new AtlasQuad(interfacesSize[0], interfacesSize[1]);
        this.pieces = new AtlasQuad(interfacesSize[0], interfacesSize[1]);
        this.interfacesWidth = interfacesSize[0];
        this.interfacesHeight = interfacesSize[1];
        this.floatingSizes = floatingSizes;
        this.floating = new AtlasQuad[floatingSizes.length];
        for (int index = 0; index < floating.length; index++)
        {
            floating[index] = new AtlasQuad(floatingSizes[index][0], floatingSizes[index][1]);
        }
        double halfUp = Math.tan(SPECTATOR_FOV / 2) * HEIGHT / WIDTH;
        this.spectatorFov = new Fov(-SPECTATOR_FOV / 2, SPECTATOR_FOV / 2, Math.atan(halfUp), -Math.atan(halfUp));
    }

    /**
     * Creates the picture's GL objects in the current OpenGL context, sized for the layers as they are now.
     *
     * @param config The plugin config, read for the view.
     * @param layers The headset layers drawn in.
     * @return The composite.
     */
    static Composite create(VitaVRConfig config, Layers layers)
    {
        HudPicture picture = layers.getPicture();
        RoomPanel settings = layers.getSettingsPanel();
        RoomPanel keyboard = layers.getKeyboardPanel();
        return new Composite(config, new int[]{picture.getWidth(), picture.getHeight()}, new int[][]{{WorldMenu.WIDTH, WorldMenu.HEIGHT},
                {Layers.LABEL_WIDTH, Layers.LABEL_HEIGHT}, {settings.getPanel().width(), settings.getPanel().height()},
                {keyboard.getPanel().width(), keyboard.getPanel().height()}});
    }

    /**
     * Uploads the interfaces, and with pieces moved, the columns with those pieces cut out as well.
     */
    void uploadInterfaces(int[] pixels, List<HudPanel.Moved> moved)
    {
        if (moved.isEmpty())
        {
            interfaces.upload(pixels);
            return;
        }
        if (columns.length != pixels.length)
        {
            columns = new int[pixels.length];
        }
        System.arraycopy(pixels, 0, columns, 0, pixels.length);
        for (HudPanel.Moved piece : moved)
        {
            int[] box = piece.getBox();
            for (int row = box[1]; row < box[1] + box[3]; row++)
            {
                Arrays.fill(columns, row * interfacesWidth + box[0], row * interfacesWidth + box[0] + box[2], 0);
            }
        }
        interfaces.upload(columns);
        pieces.upload(pixels);
    }

    void uploadFloating(int index, int[] pixels)
    {
        floating[index].upload(pixels);
    }

    boolean fitsInterfaces(int width, int height)
    {
        return width == interfacesWidth && height == interfacesHeight;
    }

    float[] follow(Pose head, double seconds, Placement placement)
    {
        placement.getRoom().view(spectator.follow(head, seconds), spectatorView);
        return spectatorView.worldToClip(spectatorFov);
    }

    /**
     * Builds the picture from the eyes.
     *
     * @return The picture's framebuffer.
     */
    int drawEyes(EyesTarget eyes, float[][] eyeMatrices, Shown shown)
    {
        picture.clear(0);
        int width = eyes.getEyeWidth();
        int height = eyes.getEyeHeight();
        if (mode == RecordMode.BOTH_EYES)
        {
            for (int eye = 0; eye < EYES; eye++)
            {
                int[] into = fit(width, height, eye * WIDTH / 2, WIDTH / 2, HEIGHT);
                eyes.copyEye(eye, new int[]{0, 0, width, height}, picture.getFramebuffer(), into);
                layersOver(eyeMatrices[eye], shown, into);
            }
        }
        else
        {
            int cropHeight = Math.min(height, width * HEIGHT / WIDTH);
            int cropWidth = cropHeight * WIDTH / HEIGHT;
            int cropX = (width - cropWidth) / 2;
            int cropY = (height - cropHeight) / 2;
            eyes.copyEye(0, new int[]{cropX, cropY, cropWidth, cropHeight}, picture.getFramebuffer(), new int[]{0, 0, WIDTH, HEIGHT});
            double scale = (double) WIDTH / cropWidth;
            layersOver(eyeMatrices[0], shown, new int[]{(int) Math.round(-cropX * scale), (int) Math.round(-cropY * scale), (int) Math.round(width * scale),
                    (int) Math.round(height * scale)});
        }
        return picture.getFramebuffer();
    }

    /**
     * Builds the picture from the steadied camera.
     *
     * @return The picture's framebuffer.
     */
    int drawSteadied(float[] worldToClip, Shown shown)
    {
        spectatorTarget.resolve();
        int[] whole = {0, 0, WIDTH, HEIGHT};
        spectatorTarget.copyEye(0, whole, picture.getFramebuffer(), whole);
        layersOver(worldToClip, shown, whole);
        return picture.getFramebuffer();
    }

    private void layersOver(float[] worldToClip, Shown shown, int[] viewport)
    {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, picture.getFramebuffer());
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (shown.strips != null)
        {
            for (int strip = 0; strip < shown.strips.length; strip++)
            {
                double[] across = shown.stripsAcross[strip];
                int left = (int) Math.round(across[0] * interfacesWidth);
                int right = (int) Math.round(across[1] * interfacesWidth);
                interfaces.draw(worldToClip, shown.strips[strip], left, 0, right - left, interfacesHeight, 1);
            }
        }
        for (int piece = 0; piece < shown.pieceCorners.length; piece++)
        {
            int[] box = shown.pieceBoxes[piece];
            pieces.draw(worldToClip, shown.pieceCorners[piece], box[0], box[1], box[2], box[3], 1);
        }
        for (int index = 0; index < floating.length; index++)
        {
            if (shown.floating[index] != null)
            {
                floating[index].draw(worldToClip, shown.floating[index], 0, 0, floatingSizes[index][0], floatingSizes[index][1], 1);
            }
        }
    }

    /**
     * Fits a picture's shape centred inside a box.
     *
     * @param width The picture width.
     * @param height The picture height.
     * @param boxX The box's left edge.
     * @param boxWidth The box width.
     * @param boxHeight The box height.
     * @return The fitted x, y, width and height.
     */
    static int[] fit(int width, int height, int boxX, int boxWidth, int boxHeight)
    {
        double scale = Math.min((double) boxWidth / width, (double) boxHeight / height);
        int fittedWidth = (int) Math.round(width * scale);
        int fittedHeight = (int) Math.round(height * scale);
        return new int[]{boxX + (boxWidth - fittedWidth) / 2, (boxHeight - fittedHeight) / 2, fittedWidth, fittedHeight};
    }

    /**
     * Releases the GL objects, in the context they were made in. Safe to call more than once.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        picture.close();
        if (spectatorTarget != null)
        {
            spectatorTarget.close();
        }
        interfaces.close();
        pieces.close();
        for (AtlasQuad quad : floating)
        {
            quad.close();
        }
    }

    /**
     * The layers' world corners for a picture, each top left, top right, bottom right, bottom left.
     */
    static final class Shown
    {
        private final double[][][] strips;
        private final double[][] stripsAcross;
        private final double[][][] pieceCorners;
        private final int[][] pieceBoxes;
        private final double[][][] floating;

        Shown(double[][][] strips, double[][] stripsAcross, double[][][] pieceCorners, int[][] pieceBoxes, double[][][] floating)
        {
            this.strips = strips;
            this.stripsAcross = stripsAcross;
            this.pieceCorners = pieceCorners;
            this.pieceBoxes = pieceBoxes;
            this.floating = floating;
        }
    }
}
