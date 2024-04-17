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

package org.apache.eventmesh.connector.rocketmq.source.connector;

import org.apache.eventmesh.connector.rocketmq.source.config.RocketMQSourceConfig;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReader;

import org.apache.rocketmq.client.consumer.AllocateMessageQueueStrategy;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.body.Connection;
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RocketMQSourceConnector implements Source, ConnectorCreateService<Source> {

    private RocketMQSourceConfig sourceConfig;

    private OffsetStorageReader offsetStorageReader;

    private DefaultMQAdminExt srcMQAdminExt;

    // message queue divided strategy
    private final AllocateMessageQueueStrategy allocateMessageQueueStrategy = new AllocateMessageQueueAveragely();

    private final DefaultLitePullConsumer consumer = new DefaultLitePullConsumer();

    private final ScheduledExecutorService commitOffsetScheduleService = Executors.newSingleThreadScheduledExecutor();

    private final ConcurrentHashMap<MessageQueue, List<AtomicLong>> prepareCommitOffset = new ConcurrentHashMap<>();

    private ConcurrentHashMap<MessageQueue, TreeMap<Long/* offset */, MessageExt/* can commit */>> queue2Offsets = new ConcurrentHashMap<>();

    private final AtomicInteger unAckCounter = new AtomicInteger();

    @Override
    public Class<? extends Config> configClass() {
        return RocketMQSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for rocketmq source connector
        this.sourceConfig = (RocketMQSourceConfig) config;
        consumer.setConsumerGroup(sourceConfig.getPubSubConfig().getGroup());
        consumer.setNamesrvAddr(sourceConfig.getConnectorConfig().getNameserver());
        consumer.setAutoCommit(false);
        consumer.setPullBatchSize(32);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        initAdmin();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (RocketMQSourceConfig) sourceConnectorContext.getSourceConfig();
        this.offsetStorageReader = sourceConnectorContext.getOffsetStorageReader();
        consumer.setConsumerGroup(sourceConfig.getPubSubConfig().getGroup());
        consumer.setNamesrvAddr(sourceConfig.getConnectorConfig().getNameserver());
        consumer.setAutoCommit(false);
        consumer.setPullBatchSize(32);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setAllocateMessageQueueStrategy(allocateMessageQueueStrategy);
        initAdmin();
    }

    private synchronized void initAdmin() throws MQClientException {
        if (srcMQAdminExt == null) {
            srcMQAdminExt = new DefaultMQAdminExt();
            srcMQAdminExt.setNamesrvAddr(sourceConfig.getConnectorConfig().getNameserver());
            srcMQAdminExt.setAdminExtGroup("RocketMQ-Admin");
        }
    }

    @Override
    public void start() throws Exception {

        consumer.start();
        srcMQAdminExt.start();

        // commit offset with schedule task
        execScheduleTask();
        // todo: we need more elegant way instead of sleep
        // for rocketmq client, will delay 1 second to send heartbeat to broker, so here sleep few seconds
        Thread.sleep(1500);

        List<MessageQueue> allocated = getAllocatedMessageQueue(sourceConfig.getConnectorConfig().getTopic(),
            sourceConfig.getPubSubConfig().getGroup());

        consumer.assign(allocated);

        consumer.setMessageQueueListener((topic, mqAll, mqDivided) -> {

            for (MessageQueue messageQueue : mqDivided) {
                try {
                    Map<String, String> partitionMap = new HashMap<>();
                    partitionMap.put("topic", messageQueue.getTopic());
                    partitionMap.put("brokerName", messageQueue.getBrokerName());
                    partitionMap.put("queueId", messageQueue.getQueueId() + "");
                    RecordPartition recordPartition = new RecordPartition(partitionMap);
                    RecordOffset recordOffset = offsetStorageReader.readOffset(recordPartition);
                    log.info("assigned messageQueue {}, recordOffset {}", messageQueue, recordOffset);
                    if (recordOffset != null) {
                        long pollOffset = (Long) recordOffset.getOffset().get("queueOffset");
                        if (pollOffset != 0) {
                            consumer.seek(messageQueue, pollOffset);
                        }
                    }
                } catch (MQClientException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private List<MessageQueue> getAllocatedMessageQueue(String topic, String group)
        throws MQBrokerException, RemotingException, InterruptedException, MQClientException {
        List<MessageQueue> mqAll = getMessageQueueList(topic);
        List<String> cidAll = getCidList(group);
        if (cidAll != null) {
            Collections.sort(mqAll);
            Collections.sort(cidAll);
            return allocateMessageQueueStrategy.allocate(group, consumer.buildMQClientId(), mqAll, cidAll);
        }
        return new ArrayList<>();
    }

    private List<String> getCidList(String group) throws MQBrokerException, RemotingException, InterruptedException, MQClientException {
        ConsumerConnection consumerConnection = srcMQAdminExt.examineConsumerConnectionInfo(group);
        return consumerConnection.getConnectionSet().stream().map(Connection::getClientId).collect(Collectors.toList());
    }

    private List<MessageQueue> getMessageQueueList(String topic) throws MQClientException {
        Collection<MessageQueue> messageQueueCollection = consumer.fetchMessageQueues(topic);
        return new ArrayList<>(messageQueueCollection);
    }

    @Override
    public void commit(ConnectRecord record) {
        // send success, commit offset
        Map<String, ?> map = record.getPosition().getPartition().getPartition();
        String brokerName = (String) map.get("brokerName");
        String topic = (String) map.get("topic");
        int queueId = Integer.parseInt((String) map.get("queueId"));
        MessageQueue mq = new MessageQueue(topic, brokerName, queueId);
        Map<String, ?> offsetMap = record.getPosition().getOffset().getOffset();
        long offset = Long.parseLong((String) offsetMap.get("queueOffset"));
        long canCommitOffset = removeMessage(mq, offset);
        log.info("commit record {}|mq {}|canCommitOffset {}", record, mq, canCommitOffset);
        // commit offset to prepareCommitOffset
        commitOffset(mq, canCommitOffset);
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        consumer.unsubscribe(sourceConfig.getConnectorConfig().getTopic());
        consumer.shutdown();
    }

    @Override
    public List<ConnectRecord> poll() {
        List<MessageExt> messageExts = consumer.poll();
        List<ConnectRecord> connectRecords = new ArrayList<>(messageExts.size());
        for (MessageExt messageExt : messageExts) {
            log.info("poll message {} from mq", messageExt);
            Long timestamp = System.currentTimeMillis();
            byte[] body = messageExt.getBody();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            RecordPartition recordPartition = convertToRecordPartition(messageExt.getTopic(),
                messageExt.getBrokerName(), messageExt.getQueueId());
            RecordOffset recordOffset = convertToRecordOffset(messageExt.getQueueOffset());
            ConnectRecord connectRecord = new ConnectRecord(recordPartition, recordOffset, timestamp, bodyStr);
            connectRecord.addExtension("topic", messageExt.getTopic());
            connectRecords.add(connectRecord);
            // put to unAckMessage Map
            putPulledQueueOffset(messageExt);
        }
        return connectRecords;
    }

    public static RecordOffset convertToRecordOffset(Long offset) {
        Map<String, String> offsetMap = new HashMap<>();
        offsetMap.put("queueOffset", offset + "");
        return new RecordOffset(offsetMap);
    }

    public static RecordPartition convertToRecordPartition(String topic, String brokerName, int queueId) {
        Map<String, String> map = new HashMap<>();
        map.put("topic", topic);
        map.put("brokerName", brokerName);
        map.put("queueId", queueId + "");
        return new RecordPartition(map);
    }

    private void putPulledQueueOffset(MessageExt messageExt) {
        MessageQueue mq = new MessageQueue(messageExt.getTopic(), messageExt.getBrokerName(), messageExt.getQueueId());
        TreeMap<Long, MessageExt> offsets = queue2Offsets.get(mq);
        if (offsets == null) {
            TreeMap<Long, MessageExt> newOffsets = new TreeMap<>();
            offsets = queue2Offsets.putIfAbsent(mq, newOffsets);
            if (offsets == null) {
                offsets = newOffsets;
            }
        }
        // add to unAckMessage
        offsets.put(messageExt.getQueueOffset(), messageExt);
        unAckCounter.incrementAndGet();
    }

    private long removeMessage(MessageQueue mq, long offset) {
        TreeMap<Long, MessageExt> offsets = queue2Offsets.get(mq);
        if (offsets != null && !offsets.isEmpty()) {
            MessageExt prev = offsets.remove(offset);
            if (prev != null) {
                unAckCounter.decrementAndGet();
            }
        }
        return offset;
    }

    private void execScheduleTask() {
        commitOffsetScheduleService.scheduleAtFixedRate(this::commitOffsetSchedule, sourceConfig.connectorConfig.getCommitOffsetIntervalMs(),
            sourceConfig.connectorConfig.getCommitOffsetIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void commitOffsetSchedule() {

        prepareCommitOffset.forEach((messageQueue, list) -> {
            Iterator<AtomicLong> offsetIterator = list.iterator();
            while (offsetIterator.hasNext()) {
                Map<MessageQueue, Long> commitOffsetTable = new HashMap<>();
                commitOffsetTable.put(messageQueue, offsetIterator.next().get());
                consumer.commitSync(commitOffsetTable, false);
                offsetIterator.remove();
            }
        });
    }

    public void commitOffset(MessageQueue mq, long canCommitOffset) {
        if (canCommitOffset == -1) {
            return;
        }
        long nextBeginOffset = canCommitOffset + 1;
        List<AtomicLong> commitOffset = prepareCommitOffset.get(mq);
        if (commitOffset == null || commitOffset.isEmpty()) {
            commitOffset = new ArrayList<>();
        }
        commitOffset.add(new AtomicLong(nextBeginOffset));
        prepareCommitOffset.put(mq, commitOffset);
    }

    @Override
    public Source create() {
        return new RocketMQSourceConnector();
    }
}
