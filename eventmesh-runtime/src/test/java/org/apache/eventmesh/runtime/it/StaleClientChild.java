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

package org.apache.eventmesh.runtime.it;

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;

import java.util.concurrent.TimeUnit;

/**
 * Child-JVM {@code main} used by the abnormal-client e2e scenario: subscribes to a topic with the
 * Java SDK and then blocks until the parent test SIGKILLs it — no {@code /events/unsubscribe}, no
 * shutdown hook. That is the "client crashed" shape the runtime's stale-client reaper must cope
 * with: the cluster-wide subscription it registered must be evicted, not left as a ghost in the
 * shared MetaStore.
 *
 * <p>Args: {@code <runtimeBaseUrl> <topic> <clientId>}.</p>
 */
public final class StaleClientChild {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(StaleClientChild.class);

    private StaleClientChild() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            LOG.error("usage: StaleClientChild <baseUrl> <topic> <clientId>");
            System.exit(2);
        }
        String baseUrl = args[0];
        String topic = args[1];
        String clientId = args[2];

        CloudEventsClient client = CloudEventsClient.builder()
            .runtimeUrl(baseUrl).clientId(clientId).pollIntervalMs(500L).build();
        client.subscribe(topic, "BROADCAST", e -> {
            // discard — this client exists to die, not to consume
        });
        LOG.info("[stale-client] subscribed {} to {} on {}", clientId, topic, baseUrl);

        // Park forever; the test kills the process (SIGKILL) while it is "connected".
        Thread.sleep(TimeUnit.MINUTES.toMillis(30));
    }
}
