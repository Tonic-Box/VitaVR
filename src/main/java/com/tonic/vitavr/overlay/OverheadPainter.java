package com.tonic.vitavr.overlay;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Paints an actor's chat, health bar, head icons, hint arrow and hitsplats from the game's own art.
 */
final class OverheadPainter
{
    /** The game's 15 unit bar clearance over the actor, in atlas pixels. */
    private static final int CLEARANCE = 8;
    private static final int PLAIN_HEIGHT = 5;
    private static final int BAR_GAP = 2;
    private static final int NO_BAR_LIFT = PLAIN_HEIGHT + BAR_GAP;
    private static final int TEXT_DROP = 2;
    private static final int TEXT_LINE = 12;
    private static final int OVERHEAD_TEXT_COLOUR = 0xFFFF00;
    private static final int PLAYER_ICON_LEFT = 12;
    private static final int NPC_ICON_GAP = 2;
    private static final int HINT_GAP = 10;
    private static final int STANDARD_BAR = 0;
    private static final Color PLAIN_FILL = new Color(0x00FF00);
    private static final Color PLAIN_REST = new Color(0xFF0000);

    /** Per-slot x/y hitsplat offsets: centre, above, left-up, right-up. */
    private static final int[] SLOT_OFFSETS = {0, 0, 0, -20, -15, -10, 15, -10};
    private static final int SLOTS = SLOT_OFFSETS.length / 2;
    private static final int SPLAT_GAP = 2;
    private static final int SPLAT_TOP_LIFT = 12;
    private static final int SPLAT_TEXT_DROP = 15;

    static final int NPC_ARROW = 0;
    static final int PLAYER_ARROW = 1;
    private static final int BLINK_CYCLES = 20;
    private static final int SHOWN_CYCLES = 10;

    private final Client client;
    private final GameArt art;
    private final Hitsplats hitsplats;

    OverheadPainter(Client client, GameArt art, Hitsplats hitsplats)
    {
        this.client = client;
        this.art = art;
        this.hitsplats = hitsplats;
    }

    /**
     * Checks whether an actor has anything to draw over it.
     * @param actor The actor.
     * @param hinted Whether the hint arrow points at it.
     * @return Whether it has chat, a health bar, head icons, hitsplats or the hint arrow.
     */
    boolean hangsAnything(Actor actor, boolean hinted)
    {
        return hinted || actor.getOverheadCycle() > 0 && actor.getOverheadText() != null || actor.getHealthRatio() >= 0 || hasHeadIcons(actor)
                || !hitsplats.of(actor, client.getGameCycle()).isEmpty();
    }

    /**
     * Draws an actor's overheads stacked upward from an anchor point.
     * @param g The graphics to draw on.
     * @param actor The actor.
     * @param anchorX The anchor x.
     * @param anchorY The anchor y.
     * @param hinted Whether the hint arrow points at it.
     */
    void drawOver(Graphics2D g, Actor actor, int anchorX, int anchorY, boolean hinted)
    {
        int stack = -BAR_GAP;
        if (drawChat(g, actor, anchorX, anchorY))
        {
            stack += TEXT_LINE;
        }
        int base = stack;
        if (actor.getHealthRatio() >= 0 && actor.getHealthScale() > 0)
        {
            stack = drawBar(g, actor.getHealthRatio(), actor.getHealthScale(), anchorX, anchorY - CLEARANCE, stack);
        }
        if (stack == base)
        {
            stack += NO_BAR_LIFT;
        }
        if (hasHeadIcons(actor))
        {
            stack = drawHeadIcons(g, actor, anchorX, anchorY - CLEARANCE, stack);
        }
        if (hinted)
        {
            BufferedImage arrow = hintArrow(actor instanceof NPC ? NPC_ARROW : PLAYER_ARROW);
            if (arrow != null)
            {
                g.drawImage(arrow, anchorX - arrow.getWidth() / 2, anchorY - CLEARANCE - (stack + arrow.getHeight() + HINT_GAP), null);
            }
        }
    }

    /**
     * Gets the hint arrow image while it is blinked on.
     * @param frame NPC_ARROW or PLAYER_ARROW.
     * @return The arrow image, or null while blinked off.
     */
    BufferedImage hintArrow(int frame)
    {
        return client.getGameCycle() % BLINK_CYCLES < SHOWN_CYCLES ? art.canvas(SpriteID.HEADICONS_HINT, frame) : null;
    }

    private boolean drawChat(Graphics2D g, Actor actor, int anchorX, int anchorY)
    {
        String text = actor.getOverheadText();
        GameFont font = art.font(SpriteID.B12_FULL);
        if (text == null || text.isEmpty() || actor.getOverheadCycle() <= 0 || font == null)
        {
            return false;
        }
        font.draw(g, text, anchorX - font.width(text) / 2, anchorY + TEXT_DROP, OVERHEAD_TEXT_COLOUR, true);
        return true;
    }

    /**
     * Draws the health bar on the stack and returns the new stack height.
     */
    private int drawBar(Graphics2D g, int ratio, int healthScale, int anchorX, int anchorY, int stack)
    {
        HealthBarType type = art.healthBar(STANDARD_BAR);
        BufferedImage front = art.image(type.getFrontSpriteId(), 0);
        BufferedImage back = art.image(type.getBackSpriteId(), 0);
        if (front != null && back != null)
        {
            int padding = type.getPadding() < front.getWidth() ? type.getPadding() : 0;
            int width = front.getWidth() - 2 * padding;
            int fill = ratio * width / healthScale;
            if (ratio > 0 && fill < 1)
            {
                fill = 1;
            }
            fill += fill == width ? 2 * padding : padding;
            int height = back.getHeight();
            stack += height;
            int x = anchorX - (width >> 1) - padding;
            int y = anchorY - stack;
            g.drawImage(back, x, y, null);
            Shape clip = g.getClip();
            g.clipRect(x, y, fill, height);
            g.drawImage(front, x, y, null);
            g.setClip(clip);
            return stack + BAR_GAP;
        }
        int width = type.getScale();
        int fill = ratio * width / healthScale;
        stack += PLAIN_HEIGHT;
        int x = anchorX - (width >> 1);
        int y = anchorY - stack;
        g.setColor(PLAIN_FILL);
        g.fillRect(x, y, fill, PLAIN_HEIGHT);
        g.setColor(PLAIN_REST);
        g.fillRect(x + fill, y, width - fill, PLAIN_HEIGHT);
        return stack + BAR_GAP;
    }

    private static boolean hasHeadIcons(Actor actor)
    {
        if (actor instanceof NPC)
        {
            int[] archives = ((NPC) actor).getOverheadArchiveIds();
            return archives != null && archives.length > 0;
        }
        if (actor instanceof Player)
        {
            Player player = (Player) actor;
            return player.getSkullIcon() >= 0 || player.getOverheadIcon() != null;
        }
        return false;
    }

    /**
     * Draws the head icons on the stack: an NPC's slots in order, or a player's skull then prayer.
     */
    private int drawHeadIcons(Graphics2D g, Actor actor, int anchorX, int anchorY, int stack)
    {
        if (actor instanceof NPC)
        {
            int[] archives = ((NPC) actor).getOverheadArchiveIds();
            short[] frames = ((NPC) actor).getOverheadSpriteIds();
            for (int slot = 0; archives != null && frames != null && slot < archives.length && slot < frames.length; slot++)
            {
                BufferedImage image = archives[slot] >= 0 && frames[slot] >= 0 ? art.canvas(archives[slot], frames[slot]) : null;
                if (image != null)
                {
                    stack += image.getHeight();
                    g.drawImage(image, anchorX - image.getWidth() / 2, anchorY - stack, null);
                    stack += NPC_ICON_GAP;
                }
            }
            return stack;
        }
        Player player = (Player) actor;
        if (player.getSkullIcon() >= 0)
        {
            stack = drawPlayerIcon(g, art.canvas(SpriteID.HEADICONS_PK, player.getSkullIcon()), anchorX, anchorY, stack);
        }
        HeadIcon prayer = player.getOverheadIcon();
        if (prayer != null)
        {
            stack = drawPlayerIcon(g, art.canvas(SpriteID.HEADICONS_PRAYER, prayer.ordinal()), anchorX, anchorY, stack);
        }
        return stack;
    }

    private static int drawPlayerIcon(Graphics2D g, BufferedImage image, int anchorX, int anchorY, int stack)
    {
        if (image == null)
        {
            return stack;
        }
        stack += image.getHeight();
        g.drawImage(image, anchorX - PLAYER_ICON_LEFT, anchorY - stack, null);
        return stack;
    }

    /**
     * Draws an actor's active hitsplats in the four slots, in arrival order.
     * @param g The graphics to draw on.
     * @param actor The actor.
     * @param midX The centre x.
     * @param midY The centre y.
     */
    void drawHitsplats(Graphics2D g, Actor actor, int midX, int midY)
    {
        int cycle = client.getGameCycle();
        int slot = 0;
        for (Hitsplats.Splat splat : hitsplats.of(actor, cycle))
        {
            if (slot >= SLOTS)
            {
                break;
            }
            HitsplatType type = art.hitsplat(splat.type);
            if (type == null)
            {
                continue;
            }
            int remaining = splat.goneOnCycle - cycle;
            if (remaining > type.getDisplayCycles())
            {
                continue;
            }
            drawHitsplat(g, type, splat.amount, slot, midX, midY, remaining);
            slot++;
        }
    }

    /**
     * Draws one hitsplat with its sprites, tiled background and text, drifting and fading over time.
     */
    private void drawHitsplat(Graphics2D g, HitsplatType type, int amount, int slot, int anchorX, int anchorY, int remaining)
    {
        GameFont font = art.font(type.getFontId() >= 0 ? type.getFontId() : SpriteID.P11_FULL);
        if (font == null)
        {
            return;
        }
        BufferedImage left = art.image(type.getLeftSpriteId(), 0);
        BufferedImage left2 = art.image(type.getLeftSprite2Id(), 0);
        BufferedImage background = art.image(type.getBackgroundSpriteId(), 0);
        BufferedImage right = art.image(type.getRightSpriteId(), 0);
        String text = type.text(amount);
        int textWidth = font.width(text);

        int x = 0;
        int leftAt = -1;
        int left2At = -1;
        int backgroundAt = -1;
        int tiles = 0;
        if (left != null)
        {
            leftAt = 0;
            x = left.getWidth();
        }
        x += SPLAT_GAP;
        if (left2 != null)
        {
            left2At = x;
            x += left2.getWidth();
        }
        int textAt = x;
        if (background != null && background.getWidth() > 0)
        {
            backgroundAt = x;
            tiles = textWidth / background.getWidth() + 1;
            int span = background.getWidth() * tiles;
            textAt += (span - textWidth) / 2;
            x += span;
        }
        else
        {
            x += textWidth;
        }
        int rightAt = right == null ? -1 : x;
        int width = right == null ? x : x + right.getWidth();

        int display = Math.max(1, type.getDisplayCycles());
        int driftX = type.getScrollToOffsetX() - remaining * type.getScrollToOffsetX() / display;
        int driftY = -type.getScrollToOffsetY() + type.getScrollToOffsetY() * remaining / display;
        int splatX = anchorX + SLOT_OFFSETS[slot * 2] - (width >> 1) + driftX;
        int splatY = anchorY + SLOT_OFFSETS[slot * 2 + 1] - SPLAT_TOP_LIFT + driftY;
        int alpha = type.getFadeCycles() >= 0 ? (remaining << 8) / Math.max(1, display - type.getFadeCycles()) : 255;

        Composite composite = g.getComposite();
        if (alpha < 255)
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha) / 255f));
        }
        drawSprite(g, left, type.getLeftSpriteId(), splatX + leftAt, splatY);
        drawSprite(g, left2, type.getLeftSprite2Id(), splatX + left2At, splatY);
        for (int tile = 0; tile < tiles; tile++)
        {
            drawSprite(g, background, type.getBackgroundSpriteId(), splatX + backgroundAt + tile * background.getWidth(), splatY);
        }
        drawSprite(g, right, type.getRightSpriteId(), splatX + rightAt, splatY);
        font.draw(g, text, splatX + textAt, splatY + SPLAT_TEXT_DROP + type.getTextOffsetY(), type.getTextColour(), true);
        g.setComposite(composite);
    }

    /**
     * Draws a sprite at x minus its own x offset.
     */
    private void drawSprite(Graphics2D g, BufferedImage image, int spriteId, int x, int y)
    {
        SpritePixels sprite = art.sprite(spriteId, 0);
        if (image == null || sprite == null)
        {
            return;
        }
        g.drawImage(image, x - sprite.getOffsetX(), y, null);
    }

    /**
     * Checks whether the hint arrow points at an actor.
     * @param client The client.
     * @param actor The actor.
     * @return Whether the hint arrow is over it.
     */
    static boolean hinted(Client client, Actor actor)
    {
        return actor != null && (actor == client.getHintArrowNpc() || actor == client.getHintArrowPlayer());
    }
}
