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

package org.apache.eventmesh.runtime.transport.http;

import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.List;

import io.cloudevents.CloudEvent;

/**
 * Parses the <em>legacy</em> EventMesh HTTP wire format ({@code HttpCommand}/{@code HttpEventWrapper}
 * carrying {@code EventMeshMessage}) into the request shapes the {@link LegacyHttpBridge} needs.
 *
 * <p>Production implementation reuses {@code HttpRequestProtocolResolver} +
 * {@code HttpProtocolAdaptor} to turn the legacy body into a CloudEvent, plus the legacy
 * subscribe-command fields ({@code url}, {@code topics}, {@code consumerGroup}). Tests inject a
 * deterministic stub.</p>
 */
public interface LegacyHttpCodec {

    LegacyPublishRequest parsePublish(byte[] body);

    LegacySubscribeRequest parseSubscribe(byte[] body);

    /** Decoded legacy publish: the target topic + the event in canonical CloudEvents form. */
    final class LegacyPublishRequest {

        private final String topic;
        private final CloudEvent event;

        public LegacyPublishRequest(String topic, CloudEvent event) {
            this.topic = topic;
            this.event = event;
        }

        public String getTopic() {
            return topic;
        }

        public CloudEvent getEvent() {
            return event;
        }
    }

    /**
     * Decoded legacy subscribe: the legacy webhook-push model — the client registers a {@code url}
     * and EventMesh POSTs each matching message to it. {@code consumerGroup} becomes the clientId.
     */
    final class LegacySubscribeRequest {

        private final String clientId;
        private final String url;
        private final String secret;
        private final List<String> topics;
        private final DistributionMode mode;

        public LegacySubscribeRequest(String clientId, String url, String secret, List<String> topics,
            DistributionMode mode) {
            this.clientId = clientId;
            this.url = url;
            this.secret = secret;
            this.topics = topics;
            this.mode = mode;
        }

        public String getClientId() {
            return clientId;
        }

        public String getUrl() {
            return url;
        }

        public String getSecret() {
            return secret;
        }

        public List<String> getTopics() {
            return topics;
        }

        public DistributionMode getMode() {
            return mode;
        }
    }
}
