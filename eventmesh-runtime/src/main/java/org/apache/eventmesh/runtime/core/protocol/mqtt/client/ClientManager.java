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

package org.apache.eventmesh.runtime.core.protocol.mqtt.client;

import org.apache.eventmesh.runtime.core.protocol.mqtt.exception.MqttException;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientManager {

    private final Map<String, ClientInfo> clientTable = new ConcurrentHashMap<>();

    private final DelayQueue<ClientInfo> delayQueue = new DelayQueue<>();

    private Thread cleanThread;

    private ReentrantLock lock = new ReentrantLock();

    AtomicBoolean started = new AtomicBoolean(false);

    private ClientManager() {
        this.cleanThread = new Thread(() -> {
            while (true && !Thread.currentThread().isInterrupted()) {
                try {
                    ClientInfo clientInfo = delayQueue.take();
                    if (!Objects.isNull(clientInfo)) {
                        clientTable.remove(clientInfo.getRemoteAddress());
                        log.info("cleanup mqtt client {}", clientInfo.getRemoteAddress());
                    }
                } catch (InterruptedException e) {
                    log.error("cleanup timeout client InterruptedException", e);
                }
            }
        });
        cleanThread.setName("Mtqq-Client-Manager-Thread");
        cleanThread.setDaemon(true);
    }

    public Set<ClientInfo> search(String topic) {
        Set<ClientInfo> clientInfoSet = new HashSet<>();
        clientTable.forEach((addr, clientInfo) -> {
            Collection<TopicAndQos> subscriptionItems = clientInfo.getSubscriptionItems();
            for (TopicAndQos subscriptionItem : subscriptionItems) {
                String subTopic = subscriptionItem.getTopic();
                if (topic.equals(subTopic)) {
                    clientInfoSet.add(clientInfo);
                    break;
                }
                if (topic.split("/").length >= subTopic.split("/").length) {
                    List<String> splitTopics = Arrays.stream(topic.split("/")).collect(Collectors.toList());
                    List<String> spliteTopicFilters = Arrays.stream(subTopic.split("/")).collect(Collectors.toList());
                    String newTopicFilter = "";
                    for (int i = 0; i < spliteTopicFilters.size(); i++) {
                        String value = spliteTopicFilters.get(i);
                        if (value.equals("+")) {
                            newTopicFilter = newTopicFilter + "+/";
                        } else if (value.equals("#")) {
                            newTopicFilter = newTopicFilter + "#/";
                            break;
                        } else {
                            newTopicFilter = newTopicFilter + splitTopics.get(i) + "/";
                        }
                    }
                    if (newTopicFilter.endsWith("/")) {
                        newTopicFilter = newTopicFilter.substring(0, newTopicFilter.length() - 1);
                    }

                    if (subTopic.equals(newTopicFilter)) {
                        clientInfoSet.add(clientInfo);
                    }

                }

            }
        });

        return clientInfoSet;
    }

    public static ClientManager getInstance() {
        return ClientManagerHolder.clientManagerProvider;
    }

    public ClientInfo getOrRegisterClient(ChannelHandlerContext ctx, MqttMessage mqttMessage) throws MqttException {
        ClientInfo temp = ClientInfo.build(ctx, mqttMessage);
        String remoteAddress = temp.getRemoteAddress();
        ClientInfo clientInfo = clientTable.computeIfAbsent(remoteAddress, (addressInner) -> ClientInfo.build(ctx, mqttMessage));
        clientInfo.refresh();
        lock.lock();
        try {
            delayQueue.remove(clientInfo);
            delayQueue.add(clientInfo);
        } finally {
            lock.unlock();
        }
        if (started.compareAndSet(false, true)) {
            cleanThread.start();
        }

        return clientInfo;
    }


    public void unregisterClient(ChannelHandlerContext ctx, MqttMessage mqttMessage) throws MqttException {
        ClientInfo temp = ClientInfo.build(ctx, mqttMessage);
        String remoteAddress = temp.getRemoteAddress();
        clientTable.remove(remoteAddress);
        delayQueue.remove(temp);

    }

    private static class ClientManagerHolder {

        static ClientManager clientManagerProvider = new ClientManager();
    }

    public static class TopicAndQos implements Serializable {


        private String topic;

        private Integer qos;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TopicAndQos that = (TopicAndQos) o;
            return Objects.equals(topic, that.topic) && Objects.equals(qos, that.qos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic, qos);
        }

        public TopicAndQos(String topic, Integer qos) {
            this.topic = topic;
            this.qos = qos;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public Integer getQos() {
            return qos;
        }

        public void setQos(Integer qos) {
            this.qos = qos;
        }
    }


    public static class ClientInfo implements Delayed {

        Channel channel;

        String remoteAddress;

        private Date lastUpTime = new Date();

        Collection<TopicAndQos> subscriptionItems = Collections.synchronizedCollection(new HashSet<>());

        int keepLiveTime;

        private long activeTime;

        public void setLastUpTime(Date lastUpTime) {
            this.lastUpTime = lastUpTime;
        }

        public void subscribe(String topic, int qos) {
            subscriptionItems.add(new TopicAndQos(topic, qos));
        }

        public void subscribes(Collection<TopicAndQos> topics) {
            subscriptionItems.addAll(topics);
        }

        public void unsubscribes(Collection<String> topics) {
            subscriptionItems.removeAll(topics);
        }

        public Collection<TopicAndQos> getSubscriptionItems() {
            return Collections.unmodifiableCollection(subscriptionItems);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClientInfo that = (ClientInfo) o;
            return Objects.equals(channel, that.channel) && Objects.equals(remoteAddress, that.remoteAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, remoteAddress);
        }

        public String getRemoteAddress() {
            return remoteAddress;
        }

        public void setRemoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        public Channel getChannel() {
            return channel;
        }

        public void setChannel(Channel channel) {
            this.channel = channel;
        }

        public int getKeepLiveTime() {
            return keepLiveTime;
        }

        public void setKeepLiveTime(int keepLiveTime) {
            this.keepLiveTime = keepLiveTime;
        }

        public void setActiveTime(long activeTime) {
            this.activeTime = activeTime;
        }

        static ClientInfo build(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setChannel(ctx.channel());
            Object header = mqttMessage.variableHeader();
            if (header instanceof MqttConnectVariableHeader) {
                int keepliveTime = ((MqttConnectVariableHeader) header).keepAliveTimeSeconds();
                clientInfo.setKeepLiveTime(keepliveTime);
                clientInfo.setActiveTime(System.currentTimeMillis() + (1000 * keepliveTime * 2));
            }
            clientInfo.setRemoteAddress(RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            return clientInfo;
        }


        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(activeTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return (int) (this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
        }

        public void refresh() {
            setLastUpTime(new Date());
            setActiveTime(System.currentTimeMillis() + (1000 * getKeepLiveTime() * 2));
        }
    }
}
