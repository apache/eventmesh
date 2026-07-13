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

package org.apache.eventmesh.runtime.push;

import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link Connection} backed by a held-open netty WebSocket channel (§7.2 / §15.6 default push
 * transport). Each delivered event is written as one {@code TextWebSocketFrame} carrying the JSON
 * {@code {deliveryId, event}} pair, mirroring {@link SseConnection}'s SSE frame so the subscriber
 * can {@code POST /events/ack} (or send a WS control frame) after processing.
 *
 * <p>Control frames (ack / unsubscribe) travel the other direction and are parsed by the server's
 * frame handler, not here — this class only owns the server→client push path.</p>
 */
@Slf4j
public class WsConnection implements Connection {

    private final Channel channel;
    private volatile boolean open = true;

    public WsConnection(Channel channel) {
        this.channel = channel;
    }

    @Override
    public boolean isOpen() {
        return open && channel.isActive();
    }

    @Override
    public void send(String deliveryId, CloudEvent event) {
        if (!isOpen()) {
            return;
        }
        try {
            byte[] eventJson = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);
            // One JSON object per frame: {"deliveryId":"...", "event":{...}}
            String frame = "{\"deliveryId\":\"" + deliveryId + "\",\"event\":"
                + new String(eventJson, StandardCharsets.UTF_8) + "}";
            channel.writeAndFlush(new TextWebSocketFrame(frame));
        } catch (Exception e) {
            open = false;
            log.debug("ws client disconnected: {}", e.toString());
        }
    }

    public void close() {
        open = false;
        if (channel.isActive()) {
            channel.close();
        }
    }
}
