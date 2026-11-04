package com.tonic.vitavr.overlay;

import lombok.Getter;

/**
 * Represents a hitsplat definition from cache config group 32.
 */
@Getter
final class HitsplatType
{
    private static final int NONE = 65535;

    private int fontId = -1;
    private int textColour = 0xFFFFFF;
    private int leftSpriteId = -1;
    private int leftSprite2Id = -1;
    private int backgroundSpriteId = -1;
    private int rightSpriteId = -1;
    private int scrollToOffsetX;
    private int scrollToOffsetY;
    private String format = "";
    private int displayCycles = 70;
    private int fadeCycles = -1;
    private int textOffsetY;
    private int varbit = -1;
    private int varp = -1;
    private int[] transforms;

    /**
     * Decodes a hitsplat type.
     * @param data The file contents.
     * @return The decoded type.
     * @throws IllegalStateException If the data holds an unknown opcode.
     */
    static HitsplatType decode(byte[] data)
    {
        HitsplatType type = new HitsplatType();
        CacheBuffer in = new CacheBuffer(data);
        while (true)
        {
            int opcode = in.unsignedByte();
            switch (opcode)
            {
                case 0:
                    return type;
                case 1:
                    type.fontId = nullable(in.unsignedShort());
                    break;
                case 2:
                    type.textColour = in.unsignedMedium();
                    break;
                case 3:
                    type.leftSpriteId = nullable(in.unsignedShort());
                    break;
                case 4:
                    type.leftSprite2Id = nullable(in.unsignedShort());
                    break;
                case 5:
                    type.backgroundSpriteId = nullable(in.unsignedShort());
                    break;
                case 6:
                    type.rightSpriteId = nullable(in.unsignedShort());
                    break;
                case 7:
                    type.scrollToOffsetX = in.signedShort();
                    break;
                case 8:
                    in.unsignedByte();
                    type.format = in.string();
                    break;
                case 9:
                    type.displayCycles = in.unsignedShort();
                    break;
                case 10:
                    type.scrollToOffsetY = in.signedShort();
                    break;
                case 11:
                    type.fadeCycles = 0;
                    break;
                case 12:
                    in.unsignedByte();
                    break;
                case 13:
                    type.textOffsetY = in.signedShort();
                    break;
                case 14:
                    type.fadeCycles = in.unsignedShort();
                    break;
                case 17:
                case 18:
                    type.varbit = nullable(in.unsignedShort());
                    type.varp = nullable(in.unsignedShort());
                    type.transforms = transforms(in, opcode == 18);
                    break;
                default:
                    throw new IllegalStateException("Unknown hitsplat opcode " + opcode);
            }
        }
    }

    /**
     * Reads the transform ids, the last entry being the fallback.
     * @param in The buffer to read from.
     * @param withDefault Whether a fallback id is stored.
     * @return The transform ids.
     */
    static int[] transforms(CacheBuffer in, boolean withDefault)
    {
        int fallback = withDefault ? nullable(in.unsignedShort()) : -1;
        int count = in.unsignedByte();
        int[] transforms = new int[count + 2];
        for (int i = 0; i <= count; i++)
        {
            transforms[i] = nullable(in.unsignedShort());
        }
        transforms[count + 1] = fallback;
        return transforms;
    }

    /**
     * Gets the text shown for an amount.
     * @param amount The hitsplat amount.
     * @return The formatted text.
     */
    String text(int amount)
    {
        return format.replace("%1", Integer.toString(amount));
    }

    private static int nullable(int value)
    {
        return value == NONE ? -1 : value;
    }
}
