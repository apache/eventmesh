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

import org.apache.eventmesh.runtime.delivery.CloudEventSerializer;
import org.apache.eventmesh.runtime.delivery.HttpCaller;
import org.apache.eventmesh.runtime.delivery.WebHookChannel;
import org.apache.eventmesh.runtime.ingress.UniIngressService;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

/**
 * Compatibility bridge for legacy EventMesh HTTP clients ({@code EventMeshHttpClient} speaking the
 * old {@code /eventmesh/publish} / {@code /eventmesh/subscribe} API with {@code EventMeshMessage}).
 *
 * <p>The legacy HTTP subscribe model is webhook-push: the client registers a URL and EventMesh POSTs
 * each matching message to it. That maps directly onto the new architecture's
 * {@link WebHookChannel} — a legacy subscriber is registered as a webhook push target, and the same
 * {@code ReliableDispatcher} drives delivery (retry, DLQ). So legacy HTTP clients run on the new
 * core with zero client-side change.</p>
 *
 * <p>Ingress ({@code /eventmesh/publish}) is a pure translation: legacy body → CloudEvent →
 * {@link UniIngressService#publish}.</p>
 */
@Slf4j
public class LegacyHttpBridge {

    private final UniIngressService ingress;
    private final LegacyHttpCodec codec;
    private final HttpCaller httpCaller;
    private final CloudEventSerializer serializer;
    private final String defaultWebhookSecret;

    public LegacyHttpBridge(UniIngressService ingress, LegacyHttpCodec codec,
        HttpCaller httpCaller, CloudEventSerializer serializer, String defaultWebhookSecret) {
        this.ingress = ingress;
        this.codec = codec;
        this.httpCaller = httpCaller;
        this.serializer = serializer;
        this.defaultWebhookSecret = defaultWebhookSecret;
    }

    /**
     * Legacy {@code POST /eventmesh/publish} — translate and persist via the new core.
     */
    public CompletableFuture<Void> publish(byte[] legacyBody) {
        LegacyHttpCodec.LegacyPublishRequest req = codec.parsePublish(legacyBody);
        return ingress.publish(req.getTopic(), req.getEvent());
    }

    /**
     * Legacy {@code POST /eventmesh/subscribe} — register the client's webhook URL as the push
     * target (a {@link WebHookChannel}) and subscribe it to each requested topic.
     */
    public void subscribe(byte[] legacyBody) {
        LegacyHttpCodec.LegacySubscribeRequest req = codec.parseSubscribe(legacyBody);
        String secret = req.getSecret() != null ? req.getSecret() : defaultWebhookSecret;
        WebHookChannel channel = new WebHookChannel(req.getUrl(), secret, httpCaller, serializer);
        ingress.registerChannel(req.getClientId(), channel);
        for (String topic : req.getTopics()) {
            ingress.subscribe(topic, req.getClientId(), req.getMode(), null);
        }
        log.info("legacy HTTP subscriber registered: clientId={} url={} topics={}",
            req.getClientId(), req.getUrl(), req.getTopics());
    }

    /**
     * Legacy {@code POST /eventmesh/unsubscribe} — drop the client's subscriptions.
     */
    public int unsubscribe(byte[] legacyBody) {
        LegacyHttpCodec.LegacySubscribeRequest req = codec.parseSubscribe(legacyBody);
        return ingress.getSubscriptionManager().unsubscribeByClient(req.getClientId());
    }
}
