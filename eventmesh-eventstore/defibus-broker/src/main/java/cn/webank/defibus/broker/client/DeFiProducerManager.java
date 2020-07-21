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

package cn.webank.defibus.broker.client;

import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ProducerManager;
import org.apache.rocketmq.common.constant.LoggerName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiProducerManager extends ProducerManager {
    private static final Logger LOG = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);

    private final ConcurrentHashMap<String/* clientId*/, ClientChannelInfo> producerChannelTable
        = new ConcurrentHashMap<String/* clientId*/, ClientChannelInfo>();
    private final Lock deleteChannelLock = new ReentrantLock();
    private static final long LockTimeoutMillis = 3000;

    @SuppressWarnings("unchecked")
    public DeFiProducerManager() {
        super();
    }

    @Override
    public void scanNotActiveChannel() {
        super.scanNotActiveChannel();
    }

    @Override
    public void doChannelCloseEvent(final String remoteAddr, final Channel channel) {
        super.doChannelCloseEvent(remoteAddr, channel);
        if (channel == null) {
            return;
        }
        String cid = null;
        try {
            try {
                if (this.deleteChannelLock.tryLock(LockTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    for (Map.Entry<String, ClientChannelInfo> entry : producerChannelTable.entrySet()) {
                        Channel producerChannel = entry.getValue().getChannel();
                        if (producerChannel.equals(channel)) {
                            cid = entry.getKey();
                            break;
                        }
                    }
                    if (cid != null) {
                        producerChannelTable.remove(cid);
                    }
                }
            } finally {
                this.deleteChannelLock.unlock();
            }
        } catch (Exception e) {
            LOG.warn("ProducerManager do delete client channel map lock timeout");
        }
    }

    @Override
    public void registerProducer(final String group, final ClientChannelInfo clientChannelInfo) {
        super.registerProducer(group, clientChannelInfo);
        producerChannelTable.put(clientChannelInfo.getClientId(), clientChannelInfo);
    }

    @Override
    public void unregisterProducer(final String group, final ClientChannelInfo clientChannelInfo) {
        super.unregisterProducer(group, clientChannelInfo);
        producerChannelTable.remove(clientChannelInfo.getClientId());
    }

    public ClientChannelInfo getClientChannel(String clientId) {
        return producerChannelTable.get(clientId);
    }

    public HashMap<String, HashMap<Channel, ClientChannelInfo>> getGroupChannelTable() {
        return super.getGroupChannelTable();
    }

    public ConcurrentHashMap<String, ClientChannelInfo> getProducerChannelTable() {
        return producerChannelTable;
    }
}
