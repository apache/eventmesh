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

package org.apache.eventmesh.storage.rocketmq.storage;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.exception.StorageRuntimeException;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ storage plugin using rocketmq-remoting direct RPC (no rocketmq-client).
 * Uses NettyRemotingClient to send RemotingCommand directly to broker/NameServer.
 */
@Slf4j
public class RocketMQRemotingStoragePlugin implements MeshStoragePlugin {

    private static final String CONSUMER_GROUP = "eventmesh-remoting-internal";
    private static final String PRODUCER_GROUP = "eventmesh-remoting-producer";
    private static final long RPC_TIMEOUT_MS = 1000L;
    private static final long SEND_TIMEOUT_MS = 5000L;
    private static final long ROUTE_TIMEOUT_MS = 5000L;
    private static final long PULL_SUSPEND_TIMEOUT_MS = 0L;
    private static final int PULL_MAX_MSGS = 32;

    private org.apache.rocketmq.remoting.netty.NettyRemotingClient remotingClient;
    private String namesrvAddr;

    private final ConcurrentHashMap<String, List<String>> brokerAddrCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> queueCountCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Integer>> assignedQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> pullOffsets = new ConcurrentHashMap<>();
    private final AtomicInteger queueRouter = new AtomicInteger(0);
    private final java.util.Set<String> failedBrokers = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    /** Cached TCP-reachability of each broker data port (addr → reachable). Unreachable brokers are
     *  excluded from the flattened queue space so send/poll never target a broker whose data port is
     *  down — a real resilience concern (brokers do fail), not just a test convenience. Probed once
     *  per address; first route build pays the probe, later builds reuse the cached result. */
    private final ConcurrentHashMap<String, Boolean> brokerReachable = new ConcurrentHashMap<>();
    // Global flattened queueId → (broker address, per-broker local queueId). RocketMQ queueIds are
    // scoped per-broker, not global: a topic spanning N brokers has readQueueNums(b) queues on each
    // broker b, addressed locally 0..readQueueNums(b)-1. Flattening them into one 0..total-1 space
    // requires translating each global id back to its (broker, local id) before the RPC — sending the
    // global id straight as header.queueId hits invalid local ids on the 2nd+ broker (null response).
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, QueueLoc>> queueBrokerCache = new ConcurrentHashMap<>();
    private java.nio.file.Path pullOffsetFile;

    @Override
    public void init(Properties properties) throws Exception {
        if (remotingClient != null) {
            log.info("RocketMQ remoting storage plugin already initialized");
            return;
        }
        namesrvAddr = properties.getProperty("namesrvAddr",
            properties.getProperty("eventmesh.server.rocketmq.namesrvAddr", "localhost:9876"));

        org.apache.rocketmq.remoting.netty.NettyClientConfig config = new org.apache.rocketmq.remoting.netty.NettyClientConfig();
        config.setClientWorkerThreads(4);
        config.setConnectTimeoutMillis(2000);
        remotingClient = new org.apache.rocketmq.remoting.netty.NettyRemotingClient(config);
        remotingClient.start();

        String offsetPath = properties.getProperty("eventmesh.offset.path", "./data/offset");
        pullOffsetFile = java.nio.file.Paths.get(offsetPath, "rocketmq-pull-offsets.properties");
        loadPullOffsets();

        log.info("RocketMQ remoting storage plugin initialized: {}", namesrvAddr);
    }

    @Override
    public void send(String topic, EventMeshFrame frame, SendCallback callback) throws Exception {
        // SPI carries EventMeshFrame (EventMesh's internal wire unit). Encode it to bytes and store —
        // the MQ stores the frame's binary encoding as the message body (no CloudEvents envelope).
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
            sendQueueId = queueId; // route miss → best-effort on a TBW102 broker
        }
        if (brokerAddr == null) {
            throw new StorageRuntimeException("no broker for topic " + topic + " queue " + queueId);
        }

        final byte[] body = frame.encode();
        org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader header =
            new org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader();
        header.setProducerGroup(PRODUCER_GROUP);
        header.setTopic(topic);
        header.setQueueId(sendQueueId);
        header.setBornTimestamp(System.currentTimeMillis());
        header.setFlag(0);
        header.setProperties("");
        header.setDefaultTopic("TBW102");
        header.setDefaultTopicQueueNums(8);
        header.setSysFlag(0);

        org.apache.rocketmq.remoting.protocol.RemotingCommand request =
            org.apache.rocketmq.remoting.protocol.RemotingCommand.createRequestCommand(
                org.apache.rocketmq.common.protocol.RequestCode.SEND_MESSAGE, header);
        request.setBody(body);

        try {
            org.apache.rocketmq.remoting.protocol.RemotingCommand response =
                remotingClient.invokeSync(brokerAddr, request, SEND_TIMEOUT_MS);
            if (response.getCode() == org.apache.rocketmq.common.protocol.ResponseCode.SUCCESS) {
                org.apache.rocketmq.common.protocol.header.SendMessageResponseHeader respHeader =
                    (org.apache.rocketmq.common.protocol.header.SendMessageResponseHeader) response
                        .decodeCommandCustomHeader(org.apache.rocketmq.common.protocol.header.SendMessageResponseHeader.class);
                SendResult result = new SendResult();
                result.setMessageId(respHeader.getMsgId());
                result.setTopic(topic);
                callback.onSuccess(result);
            } else {
                OnExceptionContext ctx = new OnExceptionContext();
                ctx.setTopic(topic);
                ctx.setException(new StorageRuntimeException(
                    new Throwable("send failed: code=" + response.getCode() + " note=" + response.getRemark())));
                callback.onException(ctx);
            }
        } catch (Exception e) {
            OnExceptionContext ctx = new OnExceptionContext();
            ctx.setTopic(topic);
            ctx.setException(new StorageRuntimeException(e));
            callback.onException(ctx);
        }
    }

    @Override
    public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
        int queueCount = getQueueCount(topic);
        if (queueCount <= 0) {
            return Collections.emptyList();
        }
        Set<Integer> owned = assignedQueues.get(topic);
        ConcurrentHashMap<Integer, Long> topicOffsets = pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());

        List<EventMeshFrame> frames = new ArrayList<>();
        for (int q = 0; q < queueCount && frames.size() < maxEvents; q++) {
            if (owned != null && !owned.contains(q)) {
                continue;
            }
            QueueLoc loc = getBrokerForQueue(topic, q);
            String brokerAddr = loc == null ? null : loc.brokerAddr;
            if (brokerAddr == null || failedBrokers.contains(brokerAddr)) {
                continue;
            }
            long offset = topicOffsets.getOrDefault(q, 0L);
            int remaining = maxEvents - frames.size();
            int pullSize = Math.min(remaining, PULL_MAX_MSGS);

            try {
                org.apache.rocketmq.common.protocol.header.PullMessageRequestHeader header =
                    new org.apache.rocketmq.common.protocol.header.PullMessageRequestHeader();
                header.setConsumerGroup(CONSUMER_GROUP);
                header.setTopic(topic);
                header.setQueueId(loc.localQueueId);
                header.setQueueOffset(offset);
                header.setMaxMsgNums(pullSize);
                header.setSubscription("*");
                header.setSubVersion(0L);
                header.setSysFlag(org.apache.rocketmq.common.sysflag.PullSysFlag.buildSysFlag(false, false, true, false));
                header.setCommitOffset(0L);
                header.setSuspendTimeoutMillis(PULL_SUSPEND_TIMEOUT_MS);

                org.apache.rocketmq.remoting.protocol.RemotingCommand request =
                    org.apache.rocketmq.remoting.protocol.RemotingCommand.createRequestCommand(
                        org.apache.rocketmq.common.protocol.RequestCode.PULL_MESSAGE, header);

                org.apache.rocketmq.remoting.protocol.RemotingCommand response =
                    remotingClient.invokeSync(brokerAddr, request, RPC_TIMEOUT_MS);

                org.apache.rocketmq.common.protocol.header.PullMessageResponseHeader respHeader =
                    (org.apache.rocketmq.common.protocol.header.PullMessageResponseHeader) response
                        .decodeCommandCustomHeader(org.apache.rocketmq.common.protocol.header.PullMessageResponseHeader.class);
                Long nextOffset = respHeader.getNextBeginOffset();
                if (nextOffset == null) {
                    // Defensive: a route occasionally lists a broker/queue the broker doesn't host
                    // (stale route, partial TBW102 auto-creation). Skip this queue this cycle rather
                    // than NPE — ConcurrentHashMap forbids null values, and the != long comparison
                    // below would unbox the null Long.
                    continue;
                }
                // Always advance the pull cursor from the broker's nextBeginOffset, regardless of
                // status. Only recording on SUCCESS left the cursor stuck when the broker returned
                // OFFSET_MOVED (requested offset below the queue's min offset — old messages cleared
                // — broker corrects to min offset): the corrected offset was dropped, the next poll
                // re-requested the same illegal offset, and the cursor never advanced, so freshly
                // published messages were never pulled. NO_NEW_MSG returns nextBeginOffset == the
                // requested offset (a no-op); FOUND returns the offset past the pulled batch.
                topicOffsets.put(q, nextOffset);
                if (response.getCode() == org.apache.rocketmq.common.protocol.ResponseCode.SUCCESS
                    && response.getBody() != null && response.getBody().length > 0) {
                    List<org.apache.rocketmq.common.message.MessageExt> msgs = decodeMessages(response.getBody());
                    for (org.apache.rocketmq.common.message.MessageExt msg : msgs) {
                        // The stored body is an EventMeshFrame; decode it back. Legacy CE-JSON bodies
                        // (written before the frame migration) are converted to a frame via the codec.
                        try {
                            EventMeshFrame frame = EventMeshFrame.decode(msg.getBody());
                            // Stamp MQ physical offset/partition for restart-cursor alignment
                            // (Frame-native replacement of develop's OffsetExtensions).
                            frame.attributes().put("emmqoffset", Long.toString(msg.getQueueOffset()));
                            frame.attributes().put("emmqpartition", Integer.toString(msg.getQueueId()));
                            frames.add(frame);
                        } catch (Exception decodeEx) {
                            // fall back: legacy CloudEvents-JSON → CE → frame
                            CloudEvent legacy = deserialize(msg.getBody());
                            if (legacy != null) {
                                EventMeshFrame frame = EventMeshFrame.fromCloudEvent(legacy);
                                frame.attributes().put("emmqoffset", Long.toString(msg.getQueueOffset()));
                                frame.attributes().put("emmqpartition", Integer.toString(msg.getQueueId()));
                                frames.add(frame);
                            }
                        }
                    }
                } else if (response.getCode() != org.apache.rocketmq.common.protocol.ResponseCode.SUCCESS
                    && nextOffset != offset) {
                    // Diagnostic: log only when a non-FOUND status moved the offset (OFFSET_MOVED),
                    // not on every empty NO_NEW_MSG poll (would spam at 200ms cadence).
                    log.info("pull {} q{} offset {}->{} (code {})", topic, q, offset,
                        nextOffset, response.getCode());
                }
            } catch (Exception e) {
                log.warn("pull failed for {} queue {} offset {}: {}", topic, q, offset, e.toString());
            }
        }
        return frames;
    }

    @Override
    public int partitionCount(String topic) {
        return getQueueCount(topic);
    }

    @Override
    public void assignPartitions(String topic, List<Integer> partitions) {
        assignedQueues.put(topic, new TreeSet<>(partitions));
        log.info("assignPartitions {}: queues {}", topic, partitions);
    }

    @Override
    public void commitOffset(String topic, int partition, long offset) {
        // Self-managed via pullOffsets + persisted to file.
    }

    /**
     * Rewind the self-managed pull cursor ({@code pullOffsets}) for {@code (topic, partition)} to
     * {@code ackOffset} (restart recovery, at-least-once). The persisted pull-offsets file may be
     * ahead of the ACK offset (messages pulled but not ACKed before the crash). No seek needed —
     * every poll issues a PULL_MESSAGE with the current pullOffsets value.
     */
    @Override
    public boolean alignPullOffset(String topic, int partition, long ackOffset) {
        if (remotingClient == null || ackOffset < 0) {
            return false;
        }
        ConcurrentHashMap<Integer, Long> topicOffsets = pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
        if (partition >= 0) {
            Long current = topicOffsets.get(partition);
            if (current != null && current <= ackOffset) {
                return false; // no gap
            }
            topicOffsets.put(partition, ackOffset);
            log.info("aligned pull offset for {}#{}: {} -> {}", topic, partition, current, ackOffset);
            return true;
        }
        // partition -1: rewind all queues of this topic
        boolean anyRewound = false;
        for (Map.Entry<Integer, Long> e : topicOffsets.entrySet()) {
            if (e.getValue() > ackOffset) {
                log.info("aligned pull offset for {}#{}: {} -> {}", topic, e.getKey(), e.getValue(), ackOffset);
                e.setValue(ackOffset);
                anyRewound = true;
            }
        }
        return anyRewound;
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

    /** Resolved location of one logical (global flattened) queue: which broker owns it and the
     *  per-broker local queueId to send in the RPC. */
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
        org.apache.rocketmq.common.protocol.route.TopicRouteData route = fetchRoute(topic);
        if (route == null || route.getQueueDatas() == null || route.getBrokerDatas() == null) {
            return null;
        }
        // Only route to brokers whose data port is reachable; an unreachable broker (e.g. a down
        // broker in an 11-broker cluster) would make every send/poll that lands on its queues fail.
        java.util.Map<String, String> brokerNameToAddr = reachableBrokerNameToAddr(route);
        java.util.List<org.apache.rocketmq.common.protocol.route.QueueData> qds = new java.util.ArrayList<>(route.getQueueDatas());
        qds.sort(java.util.Comparator.comparing(org.apache.rocketmq.common.protocol.route.QueueData::getBrokerName));
        // Flatten by readQueueNums (matches getQueueCount, which bounds the poll loop as 0..count-1).
        // The local queueId restarts at 0 within each broker — that is what the broker expects in
        // header.queueId, not the global flattened index (which would be out of range on the 2nd+
        // broker and yield a null pull response).
        ConcurrentHashMap<Integer, QueueLoc> newCache = new ConcurrentHashMap<>();
        int globalQ = 0;
        for (org.apache.rocketmq.common.protocol.route.QueueData qd : qds) {
            String addr = brokerNameToAddr.get(qd.getBrokerName());
            if (addr == null) {
                continue; // broker unreachable (or no master) — its queues are not routable
            }
            int localQ = 0;
            for (int i = 0; i < qd.getReadQueueNums(); i++) {
                newCache.put(globalQ++, new QueueLoc(addr, localQ++));
            }
        }
        queueBrokerCache.put(topic, newCache);
        return newCache.get(queueId);
    }

    /** Create a topic on all brokers via UPDATE_AND_CREATE_TOPIC RPC (code 17). */
    public void createTopic(String topic, int queueNums) {
        List<String> brokers = getBrokers("TBW102");
        if (brokers.isEmpty()) {
            log.warn("createTopic: no brokers for TBW102");
            return;
        }
        for (String brokerAddr : brokers.subList(0, Math.min(1, brokers.size()))) {
            try {
                org.apache.rocketmq.common.protocol.header.CreateTopicRequestHeader header =
                    new org.apache.rocketmq.common.protocol.header.CreateTopicRequestHeader();
                header.setTopic(topic);
                header.setDefaultTopic("TBW102");
                header.setReadQueueNums(queueNums);
                header.setWriteQueueNums(queueNums);
                header.setPerm(6); /* read+write */
                header.setTopicFilterType("SINGLE_TAG");
                org.apache.rocketmq.remoting.protocol.RemotingCommand request =
                    org.apache.rocketmq.remoting.protocol.RemotingCommand.createRequestCommand(
                        org.apache.rocketmq.common.protocol.RequestCode.UPDATE_AND_CREATE_TOPIC, header);
                org.apache.rocketmq.remoting.protocol.RemotingCommand response =
                    remotingClient.invokeSync(brokerAddr, request, ROUTE_TIMEOUT_MS);
            } catch (Exception e) {
                log.warn("createTopic {} on {} failed: {}", topic, brokerAddr, e.toString());
            }
        }
        /* Clear route cache so the next fetchRoute picks up the new topic */
        queueCountCache.remove(topic);
        brokerAddrCache.remove(topic);
        queueBrokerCache.remove(topic);
    }

    // ---- route discovery ----

    private org.apache.rocketmq.common.protocol.route.TopicRouteData fetchRoute(String topic) {
        try {
            org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader header =
                new org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader();
            header.setTopic(topic);
            org.apache.rocketmq.remoting.protocol.RemotingCommand request =
                org.apache.rocketmq.remoting.protocol.RemotingCommand.createRequestCommand(
                    org.apache.rocketmq.common.protocol.RequestCode.GET_ROUTEINFO_BY_TOPIC, header);

            org.apache.rocketmq.remoting.protocol.RemotingCommand response =
                remotingClient.invokeSync(namesrvAddr, request, ROUTE_TIMEOUT_MS);

            if (response.getCode() == org.apache.rocketmq.common.protocol.ResponseCode.SUCCESS
                && response.getBody() != null) {
                return org.apache.rocketmq.remoting.protocol.RemotingSerializable.decode(
                    response.getBody(), org.apache.rocketmq.common.protocol.route.TopicRouteData.class);
            }
        } catch (Exception e) {
            log.warn("fetchRoute failed for {}: {}", topic, e.toString());
        }
        return null;
    }

    private List<String> getBrokers(String topic) {
        return brokerAddrCache.computeIfAbsent(topic, t -> {
            org.apache.rocketmq.common.protocol.route.TopicRouteData route = fetchRoute(t);
            if (route == null || route.getBrokerDatas() == null) {
                return Collections.emptyList();
            }
            List<String> addrs = new ArrayList<>();
            for (org.apache.rocketmq.common.protocol.route.BrokerData broker : route.getBrokerDatas()) {
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
            org.apache.rocketmq.common.protocol.route.TopicRouteData route = fetchRoute(t);
            if (route == null || route.getQueueDatas() == null || route.getQueueDatas().isEmpty()) {
                return -1;
            }
            // Count only queues on reachable brokers, matching getBrokerForQueue's flatten space.
            java.util.Map<String, String> reachable = reachableBrokerNameToAddr(route);
            int total = 0;
            for (org.apache.rocketmq.common.protocol.route.QueueData qd : route.getQueueDatas()) {
                if (reachable.containsKey(qd.getBrokerName())) {
                    total += qd.getReadQueueNums();
                }
            }
            return total == 0 ? -1 : total;
        });
    }

    /** brokerName → master address, restricted to brokers whose data port is reachable. */
    private java.util.Map<String, String> reachableBrokerNameToAddr(
        org.apache.rocketmq.common.protocol.route.TopicRouteData route) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (route.getBrokerDatas() == null) {
            return m;
        }
        for (org.apache.rocketmq.common.protocol.route.BrokerData bd : route.getBrokerDatas()) {
            String addr = bd.getBrokerAddrs() != null ? bd.getBrokerAddrs().get(0L) : null;
            if (addr != null && isBrokerReachable(addr)) {
                m.put(bd.getBrokerName(), addr);
            }
        }
        return m;
    }

    /** Cached TCP reachability of a broker data port. */
    private boolean isBrokerReachable(String addr) {
        return brokerReachable.computeIfAbsent(addr, a -> {
            String[] hp = a.split(":");
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(hp[0], Integer.parseInt(hp[1])), 1000);
                return true;
            } catch (Exception e) {
                log.warn("broker {} data port unreachable — excluding from routing", a);
                return false;
            }
        });
    }

    // ---- message decode ----

    private List<org.apache.rocketmq.common.message.MessageExt> decodeMessages(byte[] body) {
        return org.apache.rocketmq.common.message.MessageDecoder.decodes(java.nio.ByteBuffer.wrap(body));
    }

    // ---- pull offset persistence ----

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
                String[] parts = key.split("#", 2);
                if (parts.length == 2) {
                    pullOffsets.computeIfAbsent(parts[0], k -> new ConcurrentHashMap<>())
                        .put(Integer.parseInt(parts[1]), Long.parseLong(props.getProperty(key)));
                }
            }
            log.info("loaded pull offsets: {} topics from {}", pullOffsets.size(), pullOffsetFile);
        } catch (Exception e) {
            log.warn("failed to load pull offsets: {}", e.toString());
        }
    }

    private void persistPullOffsets() {
        if (pullOffsetFile == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(pullOffsetFile.getParent());
            Properties props = new Properties();
            for (Map.Entry<String, ConcurrentHashMap<Integer, Long>> topicEntry : pullOffsets.entrySet()) {
                for (Map.Entry<Integer, Long> queueEntry : topicEntry.getValue().entrySet()) {
                    props.setProperty(topicEntry.getKey() + "#" + queueEntry.getKey(),
                        String.valueOf(queueEntry.getValue()));
                }
            }
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(pullOffsetFile)) {
                props.store(w, "RocketMQ pull offsets (nextBeginOffset per topic#queueId)");
            }
            log.info("persisted pull offsets: {} topics to {}", pullOffsets.size(), pullOffsetFile);
        } catch (Exception e) {
            log.warn("failed to persist pull offsets: {}", e.toString());
        }
    }

    // ---- CloudEvent deserialize — legacy fallback for pre-frame bodies ----

    private static final io.cloudevents.core.format.EventFormat FORMAT =
        io.cloudevents.core.provider.EventFormatProvider.getInstance().resolveFormat(io.cloudevents.jackson.JsonFormat.CONTENT_TYPE);

    private CloudEvent deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return FORMAT.deserialize(bytes);
        } catch (Exception e) {
            log.warn("failed to deserialize CloudEvent from RocketMQ: {}", e.toString());
            return null;
        }
    }
}
