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

package cn.webank.defibus.client.impl.rebalance;

import cn.webank.defibus.common.DeFiBusConstant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AllocateMessageQueueByIDCTest {
    private String topic = "FooBar";
    private String currentCid = "client#0#A";
    private AllocateMessageQueueByIDC allocateMessageQueueByIDC = new AllocateMessageQueueByIDC();

    @Mock
    private MQClientInstance mqClientInstance;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void test_CurrentCIDisNull() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("currentCID is empty");
        allocateMessageQueueByIDC.allocate("group", null, null, null);
    }

    @Test
    public void test_mqAllIsNull() {
        List<MessageQueue> result = allocateMessageQueueByIDC.allocate("group", "currentCid", null, new ArrayList<>());
        assertThat(result).isEmpty();
    }

    @Test
    public void test_cidAllIsNull() {
        List<MessageQueue> mqAll = new ArrayList<>();
        MessageQueue mq = new MessageQueue();
        mqAll.add(mq);
        List<MessageQueue> result = allocateMessageQueueByIDC.allocate("group", "currentCid", mqAll, null);
        assertThat(result).isEmpty();
    }

    @Test
    public void test_allocateSuccess() {
        List<String> cidAll = prepareCidList("A", 4);
        cidAll.addAll(prepareCidList("B", 5));
        cidAll.add(currentCid);
        List<MessageQueue> mqAll = prepareMqList(topic, "A", 4);
        mqAll.addAll(prepareMqList(topic, "B", 5));

        ConcurrentHashMap<String, TopicRouteData> topicRouteTable = new ConcurrentHashMap<>();
        TopicRouteData topicRouteData = prepareRouteData();
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("A", 5));
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("B", 5));
        topicRouteTable.put(topic, topicRouteData);
        allocateMessageQueueByIDC.setMqClientInstance(mqClientInstance);
        when(mqClientInstance.getTopicRouteTable()).thenReturn(new ConcurrentHashMap<>()).thenReturn(topicRouteTable);
        when(mqClientInstance.updateTopicRouteInfoFromNameServer(anyString())).thenReturn(true);

        List<MessageQueue> result = allocateMessageQueueByIDC.allocate("group", currentCid, mqAll, cidAll);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0)).isEqualTo(mqAll.get(0));
    }

    @Test
    public void test_allocateWithUnknownIdc() {
        List<String> cidAll = prepareCidList("A", 4);
        cidAll.addAll(prepareCidList("B", 5));
        cidAll.add(currentCid);
        List<MessageQueue> mqAll = prepareMqList(topic, "A", 4);
        mqAll.addAll(prepareMqList(topic, "B", 5));
        List<MessageQueue> mqInC = prepareMqList(topic, "C", 5);
        mqAll.addAll(mqInC);

        ConcurrentHashMap<String, TopicRouteData> topicRouteTable = new ConcurrentHashMap<>();
        TopicRouteData topicRouteData = prepareRouteData();
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("A", 5));
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("B", 5));
        topicRouteTable.put(topic, topicRouteData);
        allocateMessageQueueByIDC.setMqClientInstance(mqClientInstance);
        when(mqClientInstance.getTopicRouteTable()).thenReturn(new ConcurrentHashMap<>()).thenReturn(topicRouteTable);
        when(mqClientInstance.updateTopicRouteInfoFromNameServer(anyString())).thenReturn(true);

        List<MessageQueue> result = allocateMessageQueueByIDC.allocate("group", currentCid, mqAll, cidAll);

        assertThat(result.size()).isEqualTo(6);
        assertThat(result.get(0)).isEqualTo(mqAll.get(0));
        assertThat(result.containsAll(mqInC)).isTrue();
    }

    @Test
    public void test_allocateOtherIdc() {
        List<String> cidAll = prepareCidList("A", 4);
        cidAll.add(currentCid);
        List<MessageQueue> mqAll = prepareMqList(topic, "A", 4);
        List<MessageQueue> mqInB = prepareMqList(topic, "B", 5);
        mqAll.addAll(mqInB);

        ConcurrentHashMap<String, TopicRouteData> topicRouteTable = new ConcurrentHashMap<>();
        TopicRouteData topicRouteData = prepareRouteData();
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("A", 5));
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("B", 5));
        topicRouteTable.put(topic, topicRouteData);
        allocateMessageQueueByIDC.setMqClientInstance(mqClientInstance);
        when(mqClientInstance.getTopicRouteTable()).thenReturn(new ConcurrentHashMap<>()).thenReturn(topicRouteTable);
        when(mqClientInstance.updateTopicRouteInfoFromNameServer(anyString())).thenReturn(true);

        List<MessageQueue> result = allocateMessageQueueByIDC.allocate("group", currentCid, mqAll, cidAll);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0)).isEqualTo(mqAll.get(0));
        assertThat(result.get(1)).isEqualTo(mqInB.get(0));
    }

    @Test
    public void test_allocate_notContainCurrentCID() {
        List<String> cidAll = prepareCidList("A", 4);
        cidAll.addAll(prepareCidList("B", 5));
        List<MessageQueue> mqAll = prepareMqList(topic, "A", 4);
        mqAll.addAll(prepareMqList(topic, "B", 5));

        ConcurrentHashMap<String, TopicRouteData> topicRouteTable = new ConcurrentHashMap<>();
        TopicRouteData topicRouteData = prepareRouteData();
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("A", 5));
        topicRouteData.getBrokerDatas().addAll(prepareBrokerData("B", 5));
        topicRouteTable.put(topic, topicRouteData);
        allocateMessageQueueByIDC.setMqClientInstance(mqClientInstance);
        List<MessageQueue> result = allocateMessageQueueByIDC.allocate("group", currentCid, mqAll, cidAll);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    public List<String> prepareCidList(String idc, int size) {
        List<String> cids = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            cids.add("client" + DeFiBusConstant.INSTANCE_NAME_SEPERATER + i + DeFiBusConstant.INSTANCE_NAME_SEPERATER + idc);
        }
        return cids;
    }

    public List<MessageQueue> prepareMqList(String topic, String idc, int size) {
        List<MessageQueue> mqs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            MessageQueue mq = new MessageQueue();
            mq.setTopic(topic);
            mq.setBrokerName(idc + "-Broker-" + i);
            mq.setQueueId(i);
            mqs.add(mq);
        }
        return mqs;
    }

    public TopicRouteData prepareRouteData() {
        TopicRouteData topicRouteData = new TopicRouteData();
        List<BrokerData> brokerDataList = new ArrayList<>();
        topicRouteData.setBrokerDatas(brokerDataList);
        List<QueueData> queueDataList = new ArrayList<>();
        topicRouteData.setQueueDatas(queueDataList);
        return topicRouteData;
    }

    public List<BrokerData> prepareBrokerData(String idc, int size) {
        List<BrokerData> brokerDataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            BrokerData brokerData = new BrokerData();
            brokerData.setBrokerName(idc + "-Broker-" + i);
            brokerData.setCluster(idc + DeFiBusConstant.IDC_SEPERATER + "Cluster");
            HashMap<Long, String> addr = new HashMap<>();
            addr.put(0L, "127.0.0.1:10911");
            brokerData.setBrokerAddrs(addr);
            brokerDataList.add(brokerData);
        }
        return brokerDataList;
    }
}
