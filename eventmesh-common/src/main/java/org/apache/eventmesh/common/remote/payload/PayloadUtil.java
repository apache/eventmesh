package org.apache.eventmesh.common.remote.payload;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.exception.PayloadFormatException;
import org.apache.eventmesh.common.utils.JsonUtils;

public class PayloadUtil {
    public static Payload from(IPayload payload) {
        byte[] payloadBytes = JsonUtils.toJSONBytes(payload);
        Metadata.Builder metadata = Metadata.newBuilder().setType(payload.getClass().getSimpleName());
        return Payload.newBuilder().setMetadata(metadata).setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(payloadBytes))).build();
    }

    public static IPayload parse(Payload payload) {
        Class<?> targetClass = PayloadFactory.getInstance().getClassByType(payload.getMetadata().getType());
        if (targetClass == null) {
            throw new PayloadFormatException(ErrorCode.BAD_REQUEST,
                    "unknown payload type:" + payload.getMetadata().getType());
        }
        return (IPayload)JsonUtils.parseObject(new ByteBufferBackedInputStream(payload.getBody().getValue().asReadOnlyByteBuffer()), targetClass);
    }
}
