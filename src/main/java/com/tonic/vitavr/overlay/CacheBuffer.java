package com.tonic.vitavr.overlay;

import java.nio.charset.Charset;

/**
 * Reads big-endian values from a cache file.
 */
final class CacheBuffer
{
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private final byte[] data;
    private int at;

    CacheBuffer(byte[] data)
    {
        this.data = data;
    }

    int unsignedByte()
    {
        return data[at++] & 0xFF;
    }

    int unsignedShort()
    {
        return unsignedByte() << 8 | unsignedByte();
    }

    int signedShort()
    {
        return (short) unsignedShort();
    }

    int unsignedMedium()
    {
        return unsignedByte() << 16 | unsignedShort();
    }

    /**
     * Reads a zero-terminated string in the game's character set.
     */
    String string()
    {
        int from = at;
        while (data[at] != 0)
        {
            at++;
        }
        String text = new String(data, from, at - from, CP1252);
        at++;
        return text;
    }
}
