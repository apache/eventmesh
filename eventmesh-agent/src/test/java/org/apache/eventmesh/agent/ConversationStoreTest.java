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

package org.apache.eventmesh.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Covers ConversationStore get/append/trim + the null-convId single-turn case. */
class ConversationStoreTest {

    @Test
    void nullConversationIsSingleTurn() {
        ConversationStore store = new ConversationStore(20);
        assertThat(store.get(null)).isEmpty();
        store.appendTurn(null, "p", "a"); // no-op
        assertThat(store.get(null)).isEmpty();
    }

    @Test
    void appendsUserAssistantPairs() {
        ConversationStore store = new ConversationStore(20);
        store.appendTurn("c1", "hello", "hi");
        store.appendTurn("c1", "how are you", "fine");
        List<Map<String, String>> msgs = store.get("c1");
        assertThat(msgs).hasSize(4);
        assertThat(msgs).extracting(m -> m.get("role")).containsExactly("user", "assistant", "user", "assistant");
        assertThat(msgs.get(0).get("content")).isEqualTo("hello");
        assertThat(msgs.get(3).get("content")).isEqualTo("fine");
    }

    @Test
    void getReturnsIndependentSnapshot() {
        ConversationStore store = new ConversationStore(20);
        store.appendTurn("c1", "p", "a");
        List<Map<String, String>> snap = store.get("c1");
        snap.clear(); // mutate the snapshot
        assertThat(store.get("c1")).hasSize(2); // store unaffected
    }

    @Test
    void trimsToSlidingWindow() {
        ConversationStore store = new ConversationStore(4); // 2 turns kept
        store.appendTurn("c1", "p1", "a1");
        store.appendTurn("c1", "p2", "a2");
        store.appendTurn("c1", "p3", "a3"); // 3 turns = 6 msgs → trim oldest
        List<Map<String, String>> msgs = store.get("c1");
        assertThat(msgs).hasSize(4); // trimmed to maxMessages
        // oldest turn (p1/a1) dropped; only a2-turn and a3-turn kept
        assertThat(msgs).extracting(m -> m.get("content"))
            .containsExactly("p2", "a2", "p3", "a3");
    }

    @Test
    void conversationsAreIsolated() {
        ConversationStore store = new ConversationStore(20);
        store.appendTurn("c1", "p", "a");
        store.appendTurn("c2", "x", "y");
        assertThat(store.get("c1")).hasSize(2);
        assertThat(store.get("c2")).hasSize(2);
        assertThat(store.get("c1").get(0).get("content")).isEqualTo("p");
        assertThat(store.get("c2").get(0).get("content")).isEqualTo("x");
    }
}
