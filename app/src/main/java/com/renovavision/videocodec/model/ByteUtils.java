package com.renovavision.videocodec.model;

import java.nio.ByteBuffer;

/**
 * Created by Alexandr Golovach on 27.06.16.
 */
public class ByteUtils {

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / 8);
        buffer.putLong(0, x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / 8);
        buffer.put(bytes, 0, bytes.length);
        return buffer.getLong();
    }
}

