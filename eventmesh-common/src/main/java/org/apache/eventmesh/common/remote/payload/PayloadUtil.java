/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
