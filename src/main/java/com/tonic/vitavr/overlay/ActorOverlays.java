package com.tonic.vitavr.overlay;

import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws actor overheads, health bars, hitsplats and the hint arrow into an atlas of world-placed cells.
 * Shows up to eight nearest actors, the hinted one first, repainted at most once a game cycle. Used on the
 * client thread.
 */
public final class ActorOverlays
{
    public static final int ATLAS_WIDTH = 1024;
    public static final int ATLAS_HEIGHT = 800;
    private static final int OVER_WIDTH = 512;
    private static final int OVER_HEIGHT = 160;
    private static final int OVER_ANCHOR_X = OVER_WIDTH / 2;
    private static final int OVER_ANCHOR_Y = 140;
    private static final int OVER_COLUMNS = ATLAS_WIDTH / OVER_WIDTH;
    private static final int SPLAT_SIZE = 128;
    private static final int SPLAT_TOP = 4 * OVER_HEIGHT;
    private static final int SPLAT_COLUMNS = ATLAS_WIDTH / SPLAT_SIZE;
    private static final int HINT_SIZE = 32;
    private static final int HINT_TOP = SPLAT_TOP + SPLAT_SIZE;
    private static final int HINT_ANCHOR_X = HINT_SIZE / 2;
    /** Height of a tile hint arrow above its tile, in atlas pixels. */
    private static final int HINT_ANCHOR_Y = 28;
    private static final int MOST = 8;
    /** Angle one cell pixel covers from the eye, in radians. */
    private static final double PIXEL_RADIANS = 0.0012;

    /** An atlas region placed in the world at its anchor pixel. */
    @Getter
    public static final class Cell
    {
        private final double x;
        private final double y;
        private final double z;
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final int anchorX;
        private final int anchorY;
        private final double away;

        Cell(double x, double y, double z, int left, int top, int width, int height, int anchorX, int anchorY, double away)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.away = away;
        }
    }

    /** An actor shown this frame with its overhead and hitsplat positions. */
    private static final class Hung
    {
        final Actor actor;
        final double x;
        final double overY;
        final double splatY;
        final double z;
        final double away;
        final boolean hinted;

        Hung(Actor actor, double x, double overY, double splatY, double z, double away, boolean hinted)
        {
            this.actor = actor;
            this.x = x;
            this.overY = overY;
            this.splatY = splatY;
            this.z = z;
            this.away = away;
            this.hinted = hinted;
        }
    }

    private final Client client;
    private final Hitsplats hitsplats = new Hitsplats();
    private final GameArt art;
    private final OverheadPainter painter;
    private final BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB_PRE);
    @Getter
    private final List<Cell> cells = new ArrayList<>();
    private int paintedCycle = -1;
    @Getter
    private boolean repainted;

    /**
     * Creates the overlays for a client.
     * @param client The client whose actors are drawn.
     */
    public ActorOverlays(Client client)
    {
        this.client = client;
        this.art = new GameArt(client);
        this.painter = new OverheadPainter(client, art, hitsplats);
    }

    /**
     * Notes a hitsplat the game applied.
     * @param actor The actor it landed on.
     * @param hitsplat The hitsplat.
     */
    public void applied(Actor actor, Hitsplat hitsplat)
    {
        hitsplats.applied(actor, hitsplat);
    }

    /**
     * Gets how long until a hitsplat shows, after any delay.
     * @param hitsplat The hitsplat.
     * @return The game cycles until it shows, 0 for now.
     */
    public int showsIn(Hitsplat hitsplat)
    {
        HitsplatType type = art.hitsplat(hitsplat.getHitsplatType());
        int remaining = hitsplat.getDisappearsOnGameCycle() - client.getGameCycle();
        return type == null ? 0 : Math.max(0, remaining - type.getDisplayCycles());
    }

    /**
     * Builds this frame's cells, repainting the atlas if a game cycle has passed.
     * @param eye The head position in the world, used to pick the nearest actors.
     * @param ownOver The player's own overhead position, or null to place it over their head.
     * @param ownDrop How far below ownOver the player's hitsplats sit, in game units.
     */
    public void prepare(double[] eye, double[] ownOver, double ownDrop)
    {
        cells.clear();
        WorldView world = client.getTopLevelWorldView();
        List<Hung> found = new ArrayList<>();
        for (Actor actor : world.players())
        {
            hang(actor, eye, ownOver, ownDrop, found);
        }
        for (Actor actor : world.npcs())
        {
            hang(actor, eye, ownOver, ownDrop, found);
        }
        found.sort(Comparator.comparing((Hung near) -> !near.hinted).thenComparingDouble(near -> near.away));
        List<Hung> shown = found.subList(0, Math.min(MOST, found.size()));

        int cycle = client.getGameCycle();
        repainted = cycle != paintedCycle;
        if (repainted)
        {
            paintedCycle = cycle;
            paint(shown);
        }
        for (int cell = 0; cell < shown.size(); cell++)
        {
            Hung near = shown.get(cell);
            int overX = cell % OVER_COLUMNS * OVER_WIDTH;
            int overY = cell / OVER_COLUMNS * OVER_HEIGHT;
            cells.add(new Cell(near.x, near.overY, near.z, overX, overY, OVER_WIDTH, OVER_HEIGHT, OVER_ANCHOR_X, OVER_ANCHOR_Y, near.away));
            int splatX = cell % SPLAT_COLUMNS * SPLAT_SIZE;
            int splatY = SPLAT_TOP + cell / SPLAT_COLUMNS * SPLAT_SIZE;
            cells.add(new Cell(near.x, near.splatY, near.z, splatX, splatY, SPLAT_SIZE, SPLAT_SIZE, SPLAT_SIZE / 2, SPLAT_SIZE / 2, near.away));
        }
        double[] tile = tileArrow();
        if (tile != null)
        {
            cells.add(new Cell(tile[0], tile[1], tile[2], 0, HINT_TOP, HINT_SIZE, HINT_SIZE, HINT_ANCHOR_X, HINT_ANCHOR_Y, distance(tile, eye)));
        }
        cells.sort(Comparator.comparingDouble(Cell::getAway).reversed());
    }

    private void hang(Actor actor, double[] eye, double[] ownOver, double ownDrop, List<Hung> found)
    {
        boolean hinted = OverheadPainter.hinted(client, actor);
        LocalPoint at = actor == null ? null : actor.getLocalLocation();
        if (at == null || at.getWorldView() != WorldView.TOPLEVEL || !painter.hangsAnything(actor, hinted))
        {
            return;
        }
        double ground = Perspective.getTileHeight(client, at, client.getTopLevelWorldView().getPlane());
        double x = at.getX();
        double z = at.getY();
        double overY = ground - actor.getLogicalHeight();
        double splatY = ground - actor.getLogicalHeight() / 2.0;
        if (actor == client.getLocalPlayer() && ownOver != null)
        {
            x = ownOver[0];
            overY = ownOver[1];
            splatY = ownOver[1] + ownDrop;
            z = ownOver[2];
        }
        found.add(new Hung(actor, x, overY, splatY, z, distance(new double[]{x, overY, z}, eye), hinted));
    }

    private void paint(List<Hung> shown)
    {
        Graphics2D g = atlas.createGraphics();
        try
        {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);
            g.setComposite(AlphaComposite.SrcOver);
            for (int cell = 0; cell < shown.size(); cell++)
            {
                Hung near = shown.get(cell);
                Graphics2D over = (Graphics2D) g.create(cell % OVER_COLUMNS * OVER_WIDTH, cell / OVER_COLUMNS * OVER_HEIGHT, OVER_WIDTH, OVER_HEIGHT);
                painter.drawOver(over, near.actor, OVER_ANCHOR_X, OVER_ANCHOR_Y, near.hinted);
                over.dispose();
                Graphics2D splats = (Graphics2D) g.create(cell % SPLAT_COLUMNS * SPLAT_SIZE, SPLAT_TOP + cell / SPLAT_COLUMNS * SPLAT_SIZE, SPLAT_SIZE, SPLAT_SIZE);
                painter.drawHitsplats(splats, near.actor, SPLAT_SIZE / 2, SPLAT_SIZE / 2);
                splats.dispose();
            }
            BufferedImage arrow = tileArrow() == null ? null : painter.hintArrow(OverheadPainter.NPC_ARROW);
            if (arrow != null)
            {
                g.drawImage(arrow, HINT_ANCHOR_X - arrow.getWidth() / 2, HINT_TOP, null);
            }
        }
        finally
        {
            g.dispose();
        }
    }

    /**
     * Gets the ground point of a tile hint arrow, or null if there is none.
     */
    private double[] tileArrow()
    {
        if (client.getHintArrowNpc() != null || client.getHintArrowPlayer() != null || !client.hasHintArrow())
        {
            return null;
        }
        WorldPoint point = client.getHintArrowPoint();
        LocalPoint at = point == null ? null : LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
        if (at == null)
        {
            return null;
        }
        return new double[]{at.getX(), Perspective.getTileHeight(client, at, point.getPlane()), at.getY()};
    }

    /**
     * @return The atlas pixels as premultiplied ARGB, top row first.
     */
    public int[] atlasPixels()
    {
        return ((DataBufferInt) atlas.getRaster().getDataBuffer()).getData();
    }

    /**
     * Gets an upright cell's corners, sized to a fixed angle from the eye.
     * @param cell The cell.
     * @param eye The eye position.
     * @param acrossX The x of the level direction across.
     * @param acrossZ The z of the level direction across.
     * @param out Receives the corners.
     * @return The out array: top left, top right, bottom right and bottom left as x, y, z.
     */
    public static double[][] corners(Cell cell, double[] eye, double acrossX, double acrossZ, double[][] out)
    {
        double dx = cell.x - eye[0];
        double dy = cell.y - eye[1];
        double dz = cell.z - eye[2];
        double pixel = Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz)) * PIXEL_RADIANS;
        double left = -cell.anchorX * pixel;
        double right = (cell.width - cell.anchorX) * pixel;
        double top = -cell.anchorY * pixel;
        double bottom = (cell.height - cell.anchorY) * pixel;
        corner(out[0], cell.x + acrossX * left, cell.y + top, cell.z + acrossZ * left);
        corner(out[1], cell.x + acrossX * right, cell.y + top, cell.z + acrossZ * right);
        corner(out[2], cell.x + acrossX * right, cell.y + bottom, cell.z + acrossZ * right);
        corner(out[3], cell.x + acrossX * left, cell.y + bottom, cell.z + acrossZ * left);
        return out;
    }

    private static void corner(double[] out, double x, double y, double z)
    {
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    /**
     * Clears the noted hitsplats.
     */
    public void clear()
    {
        hitsplats.clear();
    }

    private static double distance(double[] a, double[] b)
    {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
