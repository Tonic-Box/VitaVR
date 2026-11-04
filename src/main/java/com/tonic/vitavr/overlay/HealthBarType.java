package com.tonic.vitavr.overlay;

import lombok.Getter;

/**
 * Represents a health bar definition from cache config group 33.
 */
@Getter
final class HealthBarType
{
    private static final int NONE = 65535;

    private int frontSpriteId = -1;
    private int backSpriteId = -1;
    private int scale = 30;
    private int padding;

    /**
     * Gets the default type used when the cache has none.
     * @return The default type.
     */
    static HealthBarType plain()
    {
        return new HealthBarType();
    }

    /**
     * Decodes a health bar type.
     * @param data The file contents.
     * @return The decoded type.
     * @throws IllegalStateException If the data holds an unknown opcode.
     */
    static HealthBarType decode(byte[] data)
    {
        HealthBarType type = new HealthBarType();
        CacheBuffer in = new CacheBuffer(data);
        while (true)
        {
            int opcode = in.unsignedByte();
            switch (opcode)
            {
                case 0:
                    return type;
                case 1:
                case 5:
                case 11:
                    in.unsignedShort();
                    break;
                case 2:
                case 3:
                case 6:
                    in.unsignedByte();
                    break;
                case 4:
                    break;
                case 7:
                    type.frontSpriteId = sprite(in.unsignedShort());
                    break;
                case 8:
                    type.backSpriteId = sprite(in.unsignedShort());
                    break;
                case 14:
                    type.scale = in.unsignedByte();
                    break;
                case 15:
                    type.padding = in.unsignedByte();
                    break;
                default:
                    throw new IllegalStateException("Unknown health bar opcode " + opcode);
            }
        }
    }

    private static int sprite(int value)
    {
        return value == NONE ? -1 : value;
    }
}
