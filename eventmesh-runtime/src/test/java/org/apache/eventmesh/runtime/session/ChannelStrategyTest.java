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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure-logic tests for {@link ChannelStrategy}/{@link AgentAnchoredStrategy}/{@link ChannelStrategy.Address}. */
class ChannelStrategyTest {

    @Test
    void agentAnchoredAddresses() {
        AgentAnchoredStrategy s = new AgentAnchoredStrategy("client-parent");

        ChannelStrategy.Address req = s.reqAddress("agentX:s1", "agentX", "agent-parent-2");
        assertThat(req.parent()).isEqualTo("agent-parent-2");
        assertThat(req.lite()).isEqualTo("agent.agentX");

        ChannelStrategy.Address reply = s.replyAddress("agentX:s1", "c1");
        assertThat(reply.parent()).isEqualTo("client-parent");
        assertThat(reply.lite()).isEqualTo("client.c1");
    }

    @Test
    void addressEncodedRoundTrip() {
        ChannelStrategy.Address a = new ChannelStrategy.Address("client-parent", "client.c7");
        assertThat(a.encoded()).isEqualTo("client-parent#client.c7");
        assertThat(ChannelStrategy.Address.parse(a.encoded())).isEqualTo(a);
    }

    @Test
    void addressParseRejectsMalformed() {
        assertThatThrownBy(() -> ChannelStrategy.Address.parse("no-hash"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
