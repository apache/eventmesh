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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;


import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StreamTopicConfig extends ConsumerGroupTopicConfig {

    /**
     * Key: IDC
     * Value: list of emitters with Client_IP:port
     */
    private final transient Map<String, Map<String, EventEmitter<SimpleMessage>>> idcEmitterMap = new ConcurrentHashMap<>();

    /**
     * Key: IDC
     * Value: list of emitters
     */
    private transient Map<String, List<EventEmitter<SimpleMessage>>> idcEmitters = new ConcurrentHashMap<>();

    private transient List<EventEmitter<SimpleMessage>> totalEmitters = new ArrayList<>();

    public StreamTopicConfig(final String consumerGroup, final String topic, final SubscriptionMode subscriptionMode) {
        super(consumerGroup, topic, subscriptionMode, GrpcType.STREAM);
    }

    @Override
    public synchronized void registerClient(final ConsumerGroupClient client) {
        Objects.requireNonNull(client, "ConsumerGroupClient can not be null");

        if (client.getGrpcType() != grpcType) {
            if (log.isWarnEnabled()) {
                log.warn("Invalid grpc type: {}, expecting grpc type: {}, can not register client {}",
                        client.getGrpcType(), grpcType, client);
            }
            return;
        }

        idcEmitterMap.computeIfAbsent(client.getIdc(), k -> new HashMap<>())
                .put(client.getIp() + ":" + client.getPid(), client.getEventEmitter());

        idcEmitters = buildIdcEmitter(idcEmitterMap);
        totalEmitters = buildTotalEmitter(idcEmitters);
    }

    @Override
    public void deregisterClient(final ConsumerGroupClient client) {
        final String idc = client.getIdc();
        final String clientIp = client.getIp();
        final String clientPid = client.getPid();

        final Map<String, EventEmitter<SimpleMessage>> emitters = idcEmitterMap.get(idc);
        if (MapUtils.isEmpty(emitters)) {
            return;
        }

        emitters.remove(clientIp + ":" + clientPid);
        if (emitters.isEmpty()) {
            idcEmitterMap.remove(idc);
        }

        idcEmitters = buildIdcEmitter(idcEmitterMap);
        totalEmitters = buildTotalEmitter(idcEmitters);
    }

    @Override
    public int getSize() {
        return totalEmitters.size();
    }

    @Override
    public String toString() {
        return "StreamConsumeTopicConfig={consumerGroup=" + consumerGroup
                + ",grpcType=" + grpcType
                + ",topic=" + topic + "}";
    }

    public Map<String, List<EventEmitter<SimpleMessage>>> getIdcEmitters() {
        return idcEmitters;
    }

    public List<EventEmitter<SimpleMessage>> getTotalEmitters() {
        return totalEmitters;
    }

    private static Map<String, List<EventEmitter<SimpleMessage>>> buildIdcEmitter(
            final Map<String, Map<String, EventEmitter<SimpleMessage>>> idcEmitterMap) {
        final Map<String, List<EventEmitter<SimpleMessage>>> result = new HashMap<>();
        idcEmitterMap.forEach((k, v) -> {
            result.put(k, new LinkedList<EventEmitter<SimpleMessage>>(v.values()));
        });
        return result;
    }

    private static List<EventEmitter<SimpleMessage>> buildTotalEmitter(
            final Map<String, List<EventEmitter<SimpleMessage>>> idcEmitters) {
        final List<EventEmitter<SimpleMessage>> emitterList = new LinkedList<>();
        idcEmitters.values().forEach(emitterList::addAll);
        return emitterList;
    }
}