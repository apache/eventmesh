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

package org.apache.eventmesh.runtime.tcp.internal;

import org.apache.eventmesh.common.wire.EventMeshFrame;

/**
 * Encodes a delivered {@link EventMeshFrame} (plus its delivery id) into the TCP {@code Package}
 * wire bytes the legacy client expects, and decodes a client ACK frame back into the delivery id it
 * acknowledges.
 *
 * <p>Production implementation reuses the existing {@code Codec} + the {@code meshmessage}
 * {@code FrameAdaptor} (reverse direction) and carries the delivery id in a Package header/extension
 * so the client's ACK frame echoes it. Tests inject a deterministic stub.</p>
 */
public interface TcpFrameCodec {

    /**
     * Encode a push frame for {@code frame}, tagged with {@code deliveryId} so the client can ACK it.
     */
    byte[] encodePush(String deliveryId, EventMeshFrame frame);

    /**
     * Extract the delivery id from a client ACK frame, or {@code null} if the frame isn't an ACK.
     */
    String extractDeliveryIdFromAck(byte[] ackFrame);
}
