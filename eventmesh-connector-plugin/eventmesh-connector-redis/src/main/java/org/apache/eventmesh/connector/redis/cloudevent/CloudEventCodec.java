package org.apache.eventmesh.connector.redis.cloudevent;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;


public class CloudEventCodec extends BaseCodec {

    public static final CloudEventCodec INSTANCE = new CloudEventCodec();

    private static final JsonFormat jsonFormat = new JsonFormat(Boolean.FALSE, Boolean.TRUE);

    private final Encoder encoder = in -> {
        ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
        if (in instanceof CloudEvent) {
            out.writeBytes(jsonFormat.serialize((CloudEvent) in));
            return out;
        }
        throw new IllegalStateException("Illegal object type: " + in.getClass().getSimpleName());
    };

    private final Decoder<Object> decoder = (buf, state) -> {
        final byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return jsonFormat.deserialize(bytes);
    };

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }
}
