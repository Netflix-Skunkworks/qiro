package io.qiro.codec;

import java.nio.ByteBuffer;

public interface Codec<Req, Resp> {
    ByteBuffer encode(Req request);
    Resp decode(ByteBuffer serializedData);
}
