package io.qiro.codec;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class UTF8Codec implements Codec<String, String> {
    public static UTF8Codec INSTANCE = new UTF8Codec();

    @Override
    public ByteBuffer encode(String str) {
        return ByteBuffer.wrap(str.getBytes(UTF_8));
    }

    @Override
    public String decode(ByteBuffer serializedData) {
        byte[] buffer = new byte[serializedData.capacity()];
        serializedData.get(buffer);
        return new String(buffer, UTF_8);
    }
}
