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

package org.apache.eventmesh.runtime.session;

/**
 * Resolves the MQ channel addresses for a mode-1 streaming call (§4.4). The protocol
 * (open/stream/close + sessionId framing) is fixed; the lite topology is captured here.
 *
 * <p>The runtime calls {@link #reqAddress} to publish {@code STREAM_REQ} and {@link #replyAddress} to
 * consume {@code CHUNK}s; it serializes the reply address into {@code StreamRequest.replyTo}
 * ({@code parent#lite}) so the agent publishes without computing it. Mode 1 is multiplexed: requests
 * land on {@code agent.<agentId>} (one subscribe per agent) and replies on {@code client.<clientId>}
 * (one subscribe per client, demuxed by sessionId).</p>
 *
 * <p>Mode 2 (publish/subscribe a session stream onto a lite topic) is a separate path — it does not
 * involve an agent or matchmaking, so it bypasses {@code ChannelStrategy} entirely.</p>
 */
public interface ChannelStrategy {

    /** Where the runtime publishes STREAM_REQ for this session (agent consumes). */
    Address reqAddress(String sessionId, String agentId, String parent);

    /** Where CHUNKs for this session land (runtime consumes, = the value carried in {@code replyTo}). */
    Address replyAddress(String sessionId, String clientId);

    /** A (parent, lite) pair; {@link #encoded()} is the wire form used for {@code replyTo}. */
    record Address(String parent, String lite) {
        public String encoded() {
            return parent + "#" + lite;
        }

        public static Address parse(String encoded) {
            int i = encoded.indexOf('#');
            if (i < 0) {
                throw new IllegalArgumentException("malformed channel address (no '#'): " + encoded);
            }
            return new Address(encoded.substring(0, i), encoded.substring(i + 1));
        }
    }
}