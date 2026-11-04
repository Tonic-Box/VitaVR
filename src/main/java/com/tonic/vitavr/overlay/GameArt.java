package com.tonic.vitavr.overlay;

import com.tonic.Logger;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.SpritePixels;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches hitsplat types, health bar types, sprites and fonts. Used on the client thread.
 */
final class GameArt
{
    private static final int CONFIG_HITSPLAT = 32;
    private static final int CONFIG_HEALTH_BAR = 33;
    private static final int INDEX_FONTS = 13;

    private final Client client;
    private final Map<Integer, HitsplatType> hitsplats = new HashMap<>();
    private final Map<Integer, HealthBarType> healthBars = new HashMap<>();
    private final Map<Long, BufferedImage> images = new HashMap<>();
    private final Map<Long, SpritePixels> sprites = new HashMap<>();
    private final Map<Integer, GameFont> fonts = new HashMap<>();

    GameArt(Client client)
    {
        this.client = client;
    }

    /**
     * Gets a hitsplat type, transformed by its varbit or varp if it has one.
     * @param id The hitsplat type id.
     * @return The type, or null if missing or currently hidden.
     */
    HitsplatType hitsplat(int id)
    {
        HitsplatType type = hitsplats.computeIfAbsent(id, this::loadHitsplat);
        if (type == null || type.getTransforms() == null)
        {
            return type;
        }
        int[] transforms = type.getTransforms();
        int value = type.getVarbit() >= 0 ? client.getVarbitValue(type.getVarbit()) : type.getVarp() >= 0 ? client.getVarpValue(type.getVarp()) : -1;
        int turned = value >= 0 && value < transforms.length - 1 ? transforms[value] : transforms[transforms.length - 1];
        return turned < 0 ? null : hitsplats.computeIfAbsent(turned, this::loadHitsplat);
    }

    private HitsplatType loadHitsplat(int id)
    {
        byte[] data = client.getIndexConfig().loadData(CONFIG_HITSPLAT, id);
        try
        {
            return data == null ? null : HitsplatType.decode(data);
        }
        catch (IllegalStateException | ArrayIndexOutOfBoundsException e)
        {
            Logger.warn("[VitaVR] Hitsplat " + id + " could not be read: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets a health bar type.
     * @param id The health bar type id.
     * @return The type, or the default bar if the cache has none.
     */
    HealthBarType healthBar(int id)
    {
        return healthBars.computeIfAbsent(id, key ->
        {
            byte[] data = client.getIndexConfig().loadData(CONFIG_HEALTH_BAR, key);
            try
            {
                return data == null ? HealthBarType.plain() : HealthBarType.decode(data);
            }
            catch (IllegalStateException | ArrayIndexOutOfBoundsException e)
            {
                Logger.warn("[VitaVR] Health bar " + key + " could not be read: " + e.getMessage());
                return HealthBarType.plain();
            }
        });
    }

    /**
     * Gets a frame of a sprite sheet.
     * @param id The sprite sheet id.
     * @param frame The frame index.
     * @return The frame, or null if the cache has none.
     */
    SpritePixels sprite(int id, int frame)
    {
        if (id < 0 || frame < 0)
        {
            return null;
        }
        long key = (long) id << 32 | frame;
        if (sprites.containsKey(key))
        {
            return sprites.get(key);
        }
        SpritePixels[] frames = client.getSprites(client.getIndexSprites(), id, 0);
        SpritePixels found = frames == null || frame >= frames.length ? null : frames[frame];
        sprites.put(key, found);
        return found;
    }

    /**
     * Gets a sprite frame as an image, without its offsets.
     * @param id The sprite sheet id.
     * @param frame The frame index.
     * @return The image, or null if the cache has none.
     */
    BufferedImage image(int id, int frame)
    {
        long key = (long) id << 32 | frame;
        if (images.containsKey(key))
        {
            return images.get(key);
        }
        SpritePixels sprite = sprite(id, frame);
        BufferedImage image = sprite == null || sprite.getWidth() <= 0 ? null : sprite.toBufferedImage();
        images.put(key, image);
        return image;
    }

    /**
     * Gets a sprite frame drawn on its full canvas with offsets applied.
     * @param id The sprite sheet id.
     * @param frame The frame index.
     * @return The image, or null if the cache has none.
     */
    BufferedImage canvas(int id, int frame)
    {
        long key = ((long) id << 32 | frame) ^ Long.MIN_VALUE;
        if (images.containsKey(key))
        {
            return images.get(key);
        }
        SpritePixels sprite = sprite(id, frame);
        BufferedImage canvas = null;
        if (sprite != null && sprite.getMaxWidth() > 0 && sprite.getMaxHeight() > 0)
        {
            canvas = new BufferedImage(sprite.getMaxWidth(), sprite.getMaxHeight(), BufferedImage.TYPE_INT_ARGB);
            BufferedImage own = image(id, frame);
            if (own != null)
            {
                canvas.getGraphics().drawImage(own, sprite.getOffsetX(), sprite.getOffsetY(), null);
            }
        }
        images.put(key, canvas);
        return canvas;
    }

    /**
     * Gets a font.
     * @param id The font id, also its glyph sprite sheet id.
     * @return The font, or null if the cache has none.
     */
    GameFont font(int id)
    {
        return fonts.computeIfAbsent(id, key ->
        {
            IndexDataBase index = client.getIndex(INDEX_FONTS);
            byte[] metrics = index == null ? null : index.loadData(key, 0);
            SpritePixels[] glyphs = client.getSprites(client.getIndexSprites(), key, 0);
            return metrics == null || glyphs == null ? null : GameFont.of(metrics, glyphs);
        });
    }
}
