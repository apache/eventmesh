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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch;

import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FreePriorityDispatchStrategy implements DownstreamDispatchStrategy {

    @Override
    public Session select(final String group, final String topic, final Set<Session> groupConsumerSessions) {
        if (CollectionUtils.isEmpty(groupConsumerSessions)
            || StringUtils.isBlank(topic)
            || StringUtils.isBlank(group)) {
            return null;
        }

        final List<Session> filtered = new ArrayList<>();
        final List<Session> isolatedSessions = new ArrayList<>();
        for (final Session session : groupConsumerSessions) {
            if (!session.isAvailable(topic)) {
                continue;
            }

            if (session.isIsolated()) {
                isolatedSessions.add(session);
                LogUtils.info(log, "session is not available because session is isolated,isolateTime:{},client:{}",
                    session.getIsolateTime(), session.getClient());
                continue;
            }

            filtered.add(session);
        }

        if (CollectionUtils.isEmpty(filtered)) {
            if (CollectionUtils.isEmpty(isolatedSessions)) {
                LogUtils.warn(log, "all sessions can't downstream msg");
                return null;
            } else {
                LogUtils.warn(log, "all sessions are isolated,group:{},topic:{}", group, topic);
                filtered.addAll(isolatedSessions);
            }
        }

        Collections.shuffle(filtered);
        return filtered.get(0);
    }
}
