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

package org.apache.eventmesh.client.cloudevents.stream;

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;

/**
 * Default {@link StreamingOperations}: a thin facade over {@link CloudEventsClient}'s session
 * methods. Obtain via {@code client.streaming()}.
 */
public class DefaultStreamingOperations implements StreamingOperations {

    private final CloudEventsClient client;

    DefaultStreamingOperations(CloudEventsClient client) {
        this.client = client;
    }

    /**
     * Factory used by {@link CloudEventsClient#streaming()}; the constructor is package-private so
     * only this factory (and tests in the same package) can build instances.
     */
    public static StreamingOperations forClient(CloudEventsClient client) {
        return new DefaultStreamingOperations(client);
    }

    @Override
    public StreamingSession openSession(OpenSession req) {
        CloudEventsClient.SessionHandle handle = client.openSession(req.getClientId(), req.getModel());
        return new StreamingSession(client, handle.sessionId, handle.agentId, handle.instanceUrl);
    }
}