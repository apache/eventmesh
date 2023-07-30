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

package org.apache.eventmesh.storage.redis.cloudevent;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

// TODO: Duplicate with org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec
public class CloudEventCodec extends BaseCodec {

    private static final CloudEventCodec INSTANCE = new CloudEventCodec();

    private static final JsonFormat jsonFormat = new JsonFormat(false, true);

    private CloudEventCodec() {
        // To prevent class instantiation
    }

    public static CloudEventCodec getInstance() {
        return INSTANCE;
    }

    private static final Encoder encoder = in -> {
        ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
        if (in instanceof CloudEvent) {
            out.writeBytes(jsonFormat.serialize((CloudEvent) in));
            return out;
        }
        throw new IllegalStateException("Illegal object type: " + in.getClass().getSimpleName());
    };

    private static final Decoder<Object> decoder = (buf, state) -> {
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
