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

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the session-idle reaper: {@link SessionRegistry#expireStaleSessions(long)} and the
 * {@link SessionRegistry#touchSession(String)} refresh that keeps active sessions alive.
 */
class SessionReaperTest {

    private AtomicLong now;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000_000L);
        registry = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);
    }

    @Test
    void idleSessionPastTtlIsExpired() {
        registry.putSession("s1", "c1", "a1");
        assertThat(registry.session("s1")).isNotNull();

        now.addAndGet(600_000L); // idle 10 min → past the 5-min TTL
        List<String> expired = registry.expireStaleSessions(300_000L);

        assertThat(expired).containsExactly("s1");
        assertThat(registry.session("s1")).isNull(); // meta gone
    }

    @Test
    void activeSessionBeforeTtlIsKept() {
        registry.putSession("s1", "c1", "a1");

        now.addAndGet(120_000L); // idle only 2 min → under the 5-min TTL
        List<String> expired = registry.expireStaleSessions(300_000L);

        assertThat(expired).isEmpty();
        assertThat(registry.session("s1")).isNotNull();
    }

    @Test
    void touchRefreshesSoActiveSessionSurvives() {
        registry.putSession("s1", "c1", "a1");

        now.addAndGet(400_000L); // 400s idle
        registry.touchSession("s1"); // client just started a new turn → refresh
        now.addAndGet(200_000L); // 200s more → 200s since touch, under TTL

        List<String> expired = registry.expireStaleSessions(300_000L);

        assertThat(expired).isEmpty();
        assertThat(registry.session("s1")).isNotNull();
    }

    @Test
    void onlyIdleSessionsExpiredActiveOnesKept() {
        registry.putSession("s1", "c1", "a1");
        now.addAndGet(200_000L);
        registry.putSession("s2", "c2", "a1"); // s2 newer
        now.addAndGet(200_000L); // s1 idle 400s, s2 idle 200s

        List<String> expired = registry.expireStaleSessions(300_000L);

        assertThat(expired).containsExactly("s1"); // only s1 past TTL
        assertThat(registry.session("s1")).isNull();
        assertThat(registry.session("s2")).isNotNull();
    }

    @Test
    void ttlZeroOrNegativeDisablesReaping() {
        registry.putSession("s1", "c1", "a1");
        now.addAndGet(999_999_999L);

        assertThat(registry.expireStaleSessions(0)).isEmpty();
        assertThat(registry.expireStaleSessions(-1)).isEmpty();
        assertThat(registry.session("s1")).isNotNull();
    }
}
