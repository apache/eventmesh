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

package org.apache.eventmesh.storage.rocketmq5.storage;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.exception.StorageRuntimeException;
import org.apache.eventmesh.api.storage.LiteTopicCapable;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;

import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.header.AckMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.CreateTopicRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetLiteTopicInfoRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.PopMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.PullMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.SendMessageResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.namesrv.GetRouteInfoRequestHeader;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 5.x storage plugin on rocketmq-remoting direct RPC (NOT the 5.x gRPC
 * {@code rocketmq-client-java}). Mirrors the 4.9 remoting plugin's route/send/createTopic model on
 * the 5.5.0 package layout ({@code remoting.protocol.*}), and consumes normal topics with the 5.x
 * <b>POP</b> mode ({@code POP_MESSAGE} + {@code ACK_MESSAGE}) — broker-side assignment, so no
 * client-side partition ownership ({@code partitionCount} returns -1 → EventMesh poll-all).
 *
 * <p>Also implements {@link LiteTopicCapable} for RocketMQ 5.5 Lite Topic (RIP-83): {@code sendLite}
 * sends to the parent topic with the {@code __LITE_TOPIC} message property (broker routes into the
 * LMQ consume queue, auto-materializing the lite topic); {@code pullLite} subscribes via
 * {@code LITE_SUBSCRIPTION_CTL} then pops via {@code POP_LITE_MESSAGE} + {@code ACK_LITE_MESSAGE}.</p>
 */
@Slf4j
public class RocketMQ5RemotingStoragePlugin implements MeshStoragePlugin, LiteTopicCapable {

    private static final String CONSUMER_GROUP = "eventmesh-rocketmq5-pop";
    /** Separate group for lite (POP_LITE) consumption — a group bound by normal POP_MESSAGE and by
     *  LITE_SUBSCRIPTION_CTL must not collide. Unique per plugin instance: the broker-side lite
     *  subscription binding persists, so a fixed group reused across instances (each with a different
     *  topic) would stay bound to a stale topic and POP_LITE would reject with "subscription bind
     *  topic not match". */
    private String liteConsumerGroup;
    private static final String PRODUCER_GROUP = "eventmesh-rocketmq5-producer";
    private static final long RPC_TIMEOUT_MS = 1000L;
    private static final long SEND_TIMEOUT_MS = 5000L;
    private static final long ROUTE_TIMEOUT_MS = 5000L;
    private static final long POP_TIMEOUT_MS = 2000L;
    private static final int PULL_MAX_MSGS = 32;
    /** POP invisibleTime: how long a popped message stays hidden (prevents redelivery) before ack.
     *  Long enough to process + ack within a poll cycle. */
    private static final long POP_INVISIBLE_TIME_MS = 30_000L;
    /** POP pollTime: how long the broker holds the pop RPC open (long-poll) when no message. */
    private static final long POP_POLL_TIME_MS = 0L;
    private static final int POP_INIT_MODE_LATEST = 0;

    private NettyRemotingClient remotingClient;
    private String namesrvAddr;

    private final ConcurrentHashMap<String, List<String>> brokerAddrCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> queueCountCache = new ConcurrentHashMap<>();
    private final AtomicInteger queueRouter = new AtomicInteger(0);
    /** Global flattened queueId → (broker address, per-broker local queueId) — used by send routing. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, QueueLoc>> queueBrokerCache = new ConcurrentHashMap<>();
    /** Cached TCP-reachability of each broker data port; unreachable brokers are excluded from routing. */
    private final ConcurrentHashMap<String, Boolean> brokerReachable = new ConcurrentHashMap<>();
    /** parent#lite → per-queueId pull offset, for classic-pull lite consumption. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> litePullOffsets = new ConcurrentHashMap<>();
    private java.nio.file.Path pullOffsetFile;

    @Override
    public void init(Properties properties) throws Exception {
        if (remotingClient != null) {
            log.info("RocketMQ5 remoting storage plugin already initialized");
            return;
        }
        namesrvAddr = properties.getProperty("namesrvAddr",
            properties.getProperty("eventmesh.server.rocketmq5.namesrvAddr", "localhost:9876"));

        NettyClientConfig config = new NettyClientConfig();
        config.setClientWorkerThreads(4);
        config.setConnectTimeoutMillis(2000);
        remotingClient = new NettyRemotingClient(config);
        remotingClient.start();
        liteConsumerGroup = "eventmesh-rocketmq5-lite-" + System.nanoTime();

        String offsetPath = properties.getProperty("eventmesh.offset.path", "./data/offset");
        pullOffsetFile = java.nio.file.Paths.get(offsetPath, "rocketmq5-pull-offsets.properties");
        loadPullOffsets();

        log.info("RocketMQ5 remoting storage plugin initialized: {}", namesrvAddr);
    }

    // ===================== MeshStoragePlugin =====================

    @Override
    public void send(String topic, CloudEvent event, SendCallback callback) throws Exception {
        sendToParent(topic, event, null, callback);
    }

    /** Send to {@code topic} (optionally a lite topic via {@code liteTopic}). */
    private void sendToParent(String topic, CloudEvent event, String liteTopic, SendCallback callback) {
        int qc = getQueueCount(topic);
        int queueId = Math.floorMod(queueRouter.getAndIncrement(), Math.max(1, qc > 0 ? qc : 1));
        QueueLoc loc0 = getBrokerForQueue(topic, queueId);
        final String brokerAddr;
        final int sendQueueId;
        if (loc0 != null && loc0.brokerAddr != null) {
            brokerAddr = loc0.brokerAddr;
            sendQueueId = loc0.localQueueId;
        } else {
            List<String> tbw = getBrokers("TBW102");
            brokerAddr = tbw.isEmpty() ? null : tbw.get(0);
            sendQueueId = queueId;
        }
        if (brokerAddr == null) {
            fail(callback, topic, "no broker for topic " + topic + " queue " + queueId);
            return;
        }

        byte[] body = serialize(event);
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.setProducerGroup(PRODUCER_GROUP);
        header.setTopic(topic);
        header.setQueueId(sendQueueId);
        header.setBornTimestamp(System.currentTimeMillis());
        header.setFlag(0);
        // __LITE_TOPIC message property routes the message into the lite topic's LMQ (RIP-83).
        Map<String, String> props = new HashMap<>();
        if (liteTopic != null && !liteTopic.isEmpty()) {
            props.put(MessageConst.PROPERTY_LITE_TOPIC, liteTopic);
        }
        header.setProperties(MessageDecoder.messageProperties2String(props));
        header.setDefaultTopic("TBW102");
        header.setDefaultTopicQueueNums(8);
        header.setSysFlag(0);

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, header);
        request.setBody(body);

        try {
            RemotingCommand response = remotingClient.invokeSync(brokerAddr, request, SEND_TIMEOUT_MS);
            if (response.getCode() == ResponseCode.SUCCESS) {
                SendMessageResponseHeader respHeader =
                    (SendMessageResponseHeader) response.decodeCommandCustomHeader(SendMessageResponseHeader.class);
                SendResult result = new SendResult();
                result.setMessageId(respHeader.getMsgId());
                result.setTopic(topic);
                callback.onSuccess(result);
            } else {
                fail(callback, topic, "send failed: code=" + response.getCode() + " note=" + response.getRemark());
            }
        } catch (Exception e) {
            fail(callback, topic, e);
        }
    }

    /**
     * Pop a batch from {@code topic} using 5.x POP mode ({@code POP_MESSAGE}). The broker assigns
     * messages across queues; each popped message is best-effort acked ({@code ACK_MESSAGE}) so it
     * does not redeliver after {@code invisibleTime}. {@code partition}/{@code startOffset} are
     * ignored (broker-managed) — consistent with {@link #partitionCount} returning -1 (poll-all).
     */
    @Override
    public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
        List<CloudEvent> events = new ArrayList<>();
        List<String> brokers = reachableBrokerAddrs(topic);
        if (brokers.isEmpty()) {
            return events;
        }
        for (String brokerAddr : brokers) {
            if (events.size() >= maxEvents) {
                break;
            }
            try {
                PopMessageRequestHeader header = new PopMessageRequestHeader();
                header.setConsumerGroup(CONSUMER_GROUP);
                header.setTopic(topic);
                header.setQueueId(-1); // -1 → broker pops across all queues it hosts for the topic
                header.setMaxMsgNums(Math.min(maxEvents - events.size(), PULL_MAX_MSGS));
                header.setInvisibleTime(POP_INVISIBLE_TIME_MS);
                header.setPollTime(POP_POLL_TIME_MS);
                header.setBornTime(System.currentTimeMillis());
                header.setInitMode(POP_INIT_MODE_LATEST);

                RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.POP_MESSAGE, header);
                RemotingCommand response = remotingClient.invokeSync(brokerAddr, request, POP_TIMEOUT_MS);

                if (response.getBody() != null && response.getBody().length > 0) {
                    for (MessageExt msg : decodeMessages(response.getBody())) {
                        if (msg.getTopic() == null || !msg.getTopic().equals(topic)) {
                            // Pop may surface a message from a different (auto-created) topic — skip.
                            continue;
                        }
                        ackNormal(brokerAddr, msg);
                        CloudEvent event = deserialize(msg.getBody());
                        if (event != null) {
                            events.add(event);
                            if (events.size() >= maxEvents) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("pop {} from {} failed: {}", topic, brokerAddr, e.toString());
            }
        }
        return events;
    }

    /** POP mode is broker-managed → no client-side partition ownership. */
    @Override
    public int partitionCount(String topic) {
        return -1;
    }

    @Override
    public void assignPartitions(String topic, List<Integer> partitions) {
        // No-op: POP assigns at the broker.
    }

    @Override
    public void commitOffset(String topic, int partition, long offset) {
        // No-op: POP acks via ACK_MESSAGE in poll().
    }

    /**
     * Create a NORMAL topic on all reachable brokers via UPDATE_AND_CREATE_TOPIC (code 17).
     *
     * <p>Note: RocketMQ 5.5 Lite Topic (RIP-83) requires the <b>parent</b> topic's message type to be
     * {@code MIXED} (or {@code LITE}); the broker rejects lite ops on a NORMAL topic with
     * "message type not match". This remoting createTopic creates a NORMAL topic — setting the
     * {@code messageType} attribute over remoting is broker-version-specific (some builds reject the
     * attribute wire-format), so creating a lite-capable (MIXED) topic is done via the admin tools
     * path ({@code DefaultMQAdminExt.createAndUpdateTopicConfig} with a {@code messageType=MIXED}
     * {@link org.apache.rocketmq.common.TopicConfig}) — see {@code RocketMQ5BrokerIntegrationTest}.
     * The plugin's message path (send / POP poll / sendLite / pullLite) is fully remoting-based.</p>
     */
    public void createTopic(String topic, int queueNums) {
        List<String> brokers = getBrokers("TBW102");
        if (brokers.isEmpty()) {
            log.warn("createTopic: no brokers for TBW102");
            return;
        }
        for (String brokerAddr : brokers) {
            try {
                CreateTopicRequestHeader header = new CreateTopicRequestHeader();
                header.setTopic(topic);
                header.setDefaultTopic("TBW102");
                header.setReadQueueNums(queueNums);
                header.setWriteQueueNums(queueNums);
                header.setPerm(6); /* read+write */
                header.setTopicFilterType("SINGLE_TAG");
                RemotingCommand request = RemotingCommand.createRequestCommand(
                    RequestCode.UPDATE_AND_CREATE_TOPIC, header);
                remotingClient.invokeSync(brokerAddr, request, ROUTE_TIMEOUT_MS);
            } catch (Exception e) {
                log.warn("createTopic {} on {} failed: {}", topic, brokerAddr, e.toString());
            }
        }
        queueCountCache.remove(topic);
        brokerAddrCache.remove(topic);
        queueBrokerCache.remove(topic);
    }

    /**
     * Create the parent topic with {@code messageType=MIXED} (lite-capable) on all brokers, via the
     * same remoting RPC but with the broker's native attribute format. Exposed for callers that need
     * a lite-capable topic when the broker supports attribute creation over remoting.
     */
    public void createLiteCapableTopic(String topic, int queueNums) {
        List<String> brokers = getBrokers("TBW102");
        for (String brokerAddr : brokers) {
            try {
                CreateTopicRequestHeader header = new CreateTopicRequestHeader();
                header.setTopic(topic);
                header.setDefaultTopic("TBW102");
                header.setReadQueueNums(queueNums);
                header.setWriteQueueNums(queueNums);
                header.setPerm(6);
                header.setTopicFilterType("SINGLE_TAG");
                header.setAttributes("+message.type=LITE");
                RemotingCommand request = RemotingCommand.createRequestCommand(
                    RequestCode.UPDATE_AND_CREATE_TOPIC, header);
                RemotingCommand resp = remotingClient.invokeSync(brokerAddr, request, ROUTE_TIMEOUT_MS);
                log.info("createLiteCapableTopic {} on {} -> code={} note={}", topic, brokerAddr,
                    resp.getCode(), resp.getRemark());
            } catch (Exception e) {
                log.warn("createLiteCapableTopic {} on {} failed: {}", topic, brokerAddr, e.toString());
            }
        }
        queueCountCache.remove(topic);
        brokerAddrCache.remove(topic);
        queueBrokerCache.remove(topic);
    }

    @Override
    public boolean isStarted() {
        return remotingClient != null;
    }

    @Override
    public boolean isClosed() {
        return remotingClient == null;
    }

    @Override
    public void start() {
        // remotingClient started in init()
    }

    @Override
    public void shutdown() {
        persistPullOffsets();
        if (remotingClient != null) {
            remotingClient.shutdown();
        }
    }

    // ===================== LiteTopicCapable =====================

    @Override
    public void createLiteTopic(String parentTopic, String liteTopic) throws Exception {
        // Ensure the parent is lite-capable (messageType=LITE) — 5.5 brokers reject lite ops on a
        // NORMAL parent with "message type not match". Idempotent (UPDATE_AND_CREATE_TOPIC).
        createLiteCapableTopic(parentTopic, 4);
        // Probe/record the lite sub-topic (auto-materializes on first send regardless).
        List<String> brokers = reachableBrokerAddrs(parentTopic);
        for (String brokerAddr : brokers) {
            try {
                GetLiteTopicInfoRequestHeader header = new GetLiteTopicInfoRequestHeader();
                header.setParentTopic(parentTopic);
                header.setLiteTopic(liteTopic);
                RemotingCommand request = RemotingCommand.createRequestCommand(
                    RequestCode.GET_LITE_TOPIC_INFO, header);
                RemotingCommand resp = remotingClient.invokeSync(brokerAddr, request, ROUTE_TIMEOUT_MS);
                log.info("getLiteTopicInfo {} {} on {} -> code={} body={} note={}", parentTopic, liteTopic, brokerAddr,
                    resp.getCode(), resp.getBody() == null ? 0 : resp.getBody().length, resp.getRemark());
                return;
            } catch (Exception e) {
                log.warn("getLiteTopicInfo {} {} on {} : {}", parentTopic, liteTopic, brokerAddr, e.toString());
            }
        }
    }

    @Override
    public void sendLite(String parentTopic, String liteTopic, CloudEvent event, SendCallback callback) throws Exception {
        sendToParent(parentTopic, event, liteTopic, callback);
    }

    /**
     * Pull from a lite topic via classic {@code PULL_MESSAGE} with the {@code liteTopic} header field
     * — pulls from the lite topic's LMQ consume queue without needing a {@code LITE_SUBSCRIPTION_CTL}
     * binding (the POP_LITE path requires a subscription whose binding semantics differ across
     * broker builds). Offset is self-managed per (parent#lite).
     */
    @Override
    public List<CloudEvent> pullLite(String parentTopic, String liteTopic, int maxEvents, long timeoutMs) {
        List<CloudEvent> events = new ArrayList<>();
        int qc = getQueueCount(parentTopic);
        if (qc <= 0) {
            return events;
        }
        String key = parentTopic + "#" + liteTopic;
        ConcurrentHashMap<Integer, Long> offsets = litePullOffsets.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        // Iterate every parent queue: a lite message is routed into the LMQ of the parent queue it was
        // sent to, and sendLite round-robins the queue — so pulling only queue 0 misses it.
        for (int gq = 0; gq < qc && events.size() < maxEvents; gq++) {
            QueueLoc loc = getBrokerForQueue(parentTopic, gq);
            if (loc == null || loc.brokerAddr == null) {
                continue;
            }
            try {
                long offset = offsets.getOrDefault(gq, 0L);
                PullMessageRequestHeader header = new PullMessageRequestHeader();
                header.setConsumerGroup(liteConsumerGroup);
                header.setTopic(parentTopic);
                header.setLiteTopic(liteTopic);
                header.setQueueId(loc.localQueueId);
                header.setQueueOffset(offset);
                header.setMaxMsgNums(Math.min(maxEvents - events.size(), PULL_MAX_MSGS));
                header.setSubscription("*");
                header.setSubVersion(0L);
                header.setSysFlag(org.apache.rocketmq.common.sysflag.PullSysFlag.buildSysFlag(false, false, true, false));
                header.setCommitOffset(0L);
                header.setSuspendTimeoutMillis(0L);

                RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, header);
                RemotingCommand response = remotingClient.invokeSync(loc.brokerAddr, request, RPC_TIMEOUT_MS);
                org.apache.rocketmq.remoting.protocol.header.PullMessageResponseHeader respHeader =
                    (org.apache.rocketmq.remoting.protocol.header.PullMessageResponseHeader) response
                        .decodeCommandCustomHeader(org.apache.rocketmq.remoting.protocol.header.PullMessageResponseHeader.class);
                Long next = respHeader.getNextBeginOffset();
                log.info("pullLite {} {} gq{} (q{}@{}) off{} -> code={} body={} next={} note={}", parentTopic, liteTopic,
                    gq, loc.localQueueId, loc.brokerAddr, offset, response.getCode(),
                    response.getBody() == null ? 0 : response.getBody().length, next, response.getRemark());
                if (next != null) {
                    offsets.put(gq, next);
                }
                if (response.getCode() == ResponseCode.SUCCESS
                    && response.getBody() != null && response.getBody().length > 0) {
                    for (MessageExt msg : decodeMessages(response.getBody())) {
                        CloudEvent event = deserialize(msg.getBody());
                        if (event != null) {
                            events.add(event);
                            if (events.size() >= maxEvents) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("pullLite {} {} gq{} from {} failed: {}", parentTopic, liteTopic, gq, loc.brokerAddr, e.toString());
            }
        }
        return events;
    }

    // ===================== send/poll ack helpers =====================

    private void ackNormal(String brokerAddr, MessageExt msg) {
        ack(brokerAddr, CONSUMER_GROUP, msg.getTopic(), msg, null, RequestCode.ACK_MESSAGE);
    }

    private void ack(String brokerAddr, String group, String topic, MessageExt msg, String liteTopic, int requestCode) {
        try {
            AckMessageRequestHeader h = new AckMessageRequestHeader();
            h.setConsumerGroup(group);
            h.setTopic(topic);
            h.setQueueId(msg.getQueueId());
            h.setOffset(msg.getQueueOffset());
            h.setExtraInfo(msg.getProperty(MessageConst.PROPERTY_POP_CK));
            if (liteTopic != null) {
                // 5.5.0 AckMessageRequestHeader carries a liteTopic field for lite acks.
                try {
                    h.getClass().getMethod("setLiteTopic", String.class).invoke(h, liteTopic);
                } catch (NoSuchMethodException ignored) {
                    // older signature — skip (best-effort ack)
                }
            }
            RemotingCommand request = RemotingCommand.createRequestCommand(requestCode, h);
            remotingClient.invokeSync(brokerAddr, request, RPC_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("ack failed for {} offset {}: {}", topic, msg.getQueueOffset(), e.toString());
        }
    }

    // ===================== routing helpers (ported from 4.9, 5.5.0 packages) =====================

    private static final class QueueLoc {

        final String brokerAddr;
        final int localQueueId;

        QueueLoc(String brokerAddr, int localQueueId) {
            this.brokerAddr = brokerAddr;
            this.localQueueId = localQueueId;
        }
    }

    private QueueLoc getBrokerForQueue(String topic, int queueId) {
        ConcurrentHashMap<Integer, QueueLoc> cache = queueBrokerCache.get(topic);
        if (cache != null) {
            QueueLoc loc = cache.get(queueId);
            if (loc != null) {
                return loc;
            }
        }
        TopicRouteData route = fetchRoute(topic);
        if (route == null || route.getQueueDatas() == null || route.getBrokerDatas() == null) {
            return null;
        }
        Map<String, String> brokerNameToAddr = reachableBrokerNameToAddr(route);
        List<QueueData> qds = new ArrayList<>(route.getQueueDatas());
        qds.sort(java.util.Comparator.comparing(QueueData::getBrokerName));
        ConcurrentHashMap<Integer, QueueLoc> newCache = new ConcurrentHashMap<>();
        int globalQ = 0;
        for (QueueData qd : qds) {
            String addr = brokerNameToAddr.get(qd.getBrokerName());
            if (addr == null) {
                continue; // broker unreachable — its queues are not routable
            }
            int localQ = 0;
            for (int i = 0; i < qd.getReadQueueNums(); i++) {
                newCache.put(globalQ++, new QueueLoc(addr, localQ++));
            }
        }
        queueBrokerCache.put(topic, newCache);
        return newCache.get(queueId);
    }

    private List<String> reachableBrokerAddrs(String topic) {
        TopicRouteData route = fetchRoute(topic);
        if (route == null || route.getBrokerDatas() == null) {
            return Collections.emptyList();
        }
        List<String> addrs = new ArrayList<>();
        for (String addr : reachableBrokerNameToAddr(route).values()) {
            addrs.add(addr);
        }
        return addrs;
    }

    private TopicRouteData fetchRoute(String topic) {
        try {
            GetRouteInfoRequestHeader header = new GetRouteInfoRequestHeader();
            header.setTopic(topic);
            RemotingCommand request = RemotingCommand.createRequestCommand(
                RequestCode.GET_ROUTEINFO_BY_TOPIC, header);
            RemotingCommand response = remotingClient.invokeSync(namesrvAddr, request, ROUTE_TIMEOUT_MS);
            if (response.getCode() == ResponseCode.SUCCESS && response.getBody() != null) {
                return org.apache.rocketmq.remoting.protocol.RemotingSerializable.decode(
                    response.getBody(), TopicRouteData.class);
            }
        } catch (Exception e) {
            log.warn("fetchRoute failed for {}: {}", topic, e.toString());
        }
        return null;
    }

    private List<String> getBrokers(String topic) {
        return brokerAddrCache.computeIfAbsent(topic, t -> {
            TopicRouteData route = fetchRoute(t);
            if (route == null || route.getBrokerDatas() == null) {
                return Collections.emptyList();
            }
            List<String> addrs = new ArrayList<>();
            for (BrokerData broker : route.getBrokerDatas()) {
                String addr = broker.getBrokerAddrs() != null ? broker.getBrokerAddrs().get(0L) : null;
                if (addr != null) {
                    addrs.add(addr);
                }
            }
            return addrs;
        });
    }

    private int getQueueCount(String topic) {
        return queueCountCache.computeIfAbsent(topic, t -> {
            TopicRouteData route = fetchRoute(t);
            if (route == null || route.getQueueDatas() == null || route.getQueueDatas().isEmpty()) {
                return -1;
            }
            Map<String, String> reachable = reachableBrokerNameToAddr(route);
            int total = 0;
            for (QueueData qd : route.getQueueDatas()) {
                if (reachable.containsKey(qd.getBrokerName())) {
                    total += qd.getReadQueueNums();
                }
            }
            return total == 0 ? -1 : total;
        });
    }

    /** brokerName → master address, restricted to brokers whose data port is reachable. */
    private Map<String, String> reachableBrokerNameToAddr(TopicRouteData route) {
        Map<String, String> m = new HashMap<>();
        if (route.getBrokerDatas() == null) {
            return m;
        }
        for (BrokerData bd : route.getBrokerDatas()) {
            String addr = bd.getBrokerAddrs() != null ? bd.getBrokerAddrs().get(0L) : null;
            if (addr != null && isBrokerReachable(addr)) {
                m.put(bd.getBrokerName(), addr);
            }
        }
        return m;
    }

    /** Cached TCP reachability of a broker data port. Only caches reachable=true (so a transiently-
     *  unreachable broker is re-probed next time, not permanently excluded). */
    private boolean isBrokerReachable(String addr) {
        if (Boolean.TRUE.equals(brokerReachable.get(addr))) {
            return true; // cached reachable — skip re-probe
        }
        String[] hp = addr.split(":");
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(hp[0], Integer.parseInt(hp[1])), 1000);
            brokerReachable.put(addr, true);
            return true;
        } catch (Exception e) {
            log.warn("broker {} data port unreachable — excluding from routing (will re-probe)", addr);
            return false; // not cached — will re-probe next cycle
        }
    }

    // ===================== decode / offsets / serialize =====================

    private List<MessageExt> decodeMessages(byte[] body) {
        return MessageDecoder.decodes(ByteBuffer.wrap(body));
    }

    private void loadPullOffsets() {
        if (pullOffsetFile == null || !java.nio.file.Files.exists(pullOffsetFile)) {
            return;
        }
        try {
            Properties props = new Properties();
            try (java.io.Reader r = java.nio.file.Files.newBufferedReader(pullOffsetFile)) {
                props.load(r);
            }
            for (String key : props.stringPropertyNames()) {
                String[] parts = key.split("@", 2);
                if (parts.length == 2) {
                    litePullOffsets.computeIfAbsent(parts[0], k -> new ConcurrentHashMap<>())
                        .put(Integer.parseInt(parts[1]), Long.parseLong(props.getProperty(key)));
                }
            }
        } catch (Exception e) {
            log.warn("failed to load lite pull offsets: {}", e.toString());
        }
    }

    private void persistPullOffsets() {
        if (pullOffsetFile == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(pullOffsetFile.getParent());
            Properties props = new Properties();
            for (Map.Entry<String, ConcurrentHashMap<Integer, Long>> topicEntry : litePullOffsets.entrySet()) {
                for (Map.Entry<Integer, Long> qEntry : topicEntry.getValue().entrySet()) {
                    props.setProperty(topicEntry.getKey() + "@" + qEntry.getKey(),
                        String.valueOf(qEntry.getValue()));
                }
            }
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(pullOffsetFile)) {
                props.store(w, "RocketMQ5 lite pull offsets (parent#lite → queueId → offset)");
            }
        } catch (Exception e) {
            log.warn("failed to persist lite pull offsets: {}", e.toString());
        }
    }

    private static final io.cloudevents.core.format.EventFormat FORMAT =
        io.cloudevents.core.provider.EventFormatProvider.getInstance().resolveFormat(io.cloudevents.jackson.JsonFormat.CONTENT_TYPE);

    private byte[] serialize(CloudEvent event) {
        return FORMAT.serialize(event);
    }

    private CloudEvent deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return FORMAT.deserialize(bytes);
        } catch (Exception e) {
            log.warn("failed to deserialize CloudEvent: {}", e.toString());
            return null;
        }
    }

    private void fail(SendCallback callback, String topic, String msg) {
        OnExceptionContext ctx = new OnExceptionContext();
        ctx.setTopic(topic);
        ctx.setException(new StorageRuntimeException(new Throwable(msg)));
        callback.onException(ctx);
    }

    private void fail(SendCallback callback, String topic, Exception e) {
        OnExceptionContext ctx = new OnExceptionContext();
        ctx.setTopic(topic);
        ctx.setException(new StorageRuntimeException(e));
        callback.onException(ctx);
    }

    @SuppressWarnings("unused")
    private static void touchTreeSet() {
        // keep the TreeSet import meaningful for future partition-assignment parity with the 4.9 plugin.
        new TreeSet<Integer>();
    }
}
