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

package cn.webank.defibus.client.impl.factory;

import cn.webank.defibus.client.DeFiBusClientManager;
import cn.webank.defibus.client.impl.DeFiBusClientAPIImpl;
import cn.webank.defibus.client.impl.hook.DeFiBusClientHookFactory;
import cn.webank.defibus.common.util.ReflectUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeFiBusClientInstanceTest {
    private DeFiBusClientInstance deFiBusClientInstance = DeFiBusClientManager.getInstance().getAndCreateDeFiBusClientInstance(new ClientConfig(), DeFiBusClientHookFactory.createRPCHook(null));
    private String topic = "FooBar";
    private String group = "FooBarGroup";

    @Mock
    private DeFiBusClientAPIImpl deFiBusClientAPI;

    @Test
    public void testFindConsumerIdList() throws InterruptedException, MQBrokerException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        List<String> cidList = new ArrayList<>();
        cidList.add("client-1");
        cidList.add("client-2");
        cidList.add("client-3");

        ReflectUtil.setSimpleProperty(MQClientInstance.class, deFiBusClientInstance, "mQClientAPIImpl", deFiBusClientAPI);
        ReflectUtil.setSimpleProperty(DeFiBusClientInstance.class, deFiBusClientInstance, "deFiClientAPI", deFiBusClientAPI);

        deFiBusClientInstance.getTopicRouteTable().put(topic, createRouteData());

        when(deFiBusClientAPI.getConsumerIdListByGroupAndTopic(anyString(), anyString(), anyString(), anyLong())).thenReturn(cidList);
        assertThat(cidList).isEqualTo(deFiBusClientInstance.findConsumerIdList(topic, group));
    }

    @Test
    public void testFindConsumerIdList_retry() throws InterruptedException, MQBrokerException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        List<String> cidList = new ArrayList<>();
        cidList.add("client-1");
        cidList.add("client-2");
        cidList.add("client-3");

        ReflectUtil.setSimpleProperty(MQClientInstance.class, deFiBusClientInstance, "mQClientAPIImpl", deFiBusClientAPI);
        ReflectUtil.setSimpleProperty(DeFiBusClientInstance.class, deFiBusClientInstance, "deFiClientAPI", deFiBusClientAPI);

        deFiBusClientInstance.getTopicRouteTable().put(topic, createRouteData());
        when(deFiBusClientAPI.getConsumerIdListByGroupAndTopic(anyString(), anyString(), anyString(), anyLong())).thenReturn(null).thenReturn(cidList);
        assertThat(cidList).isEqualTo(deFiBusClientInstance.findConsumerIdList(topic, group));
    }

    public static TopicRouteData createRouteData() {
        TopicRouteData topicRouteData = new TopicRouteData();
        List<BrokerData> brokerDataList = new ArrayList<>();

        BrokerData brokerDataA = new BrokerData();
        brokerDataA.setBrokerName("Broker-A");
        brokerDataA.setCluster("Cluster-A");
        HashMap<Long, String> addr = new HashMap<>();
        addr.put(0L, "127.0.0.1:10911");
        brokerDataA.setBrokerAddrs(addr);
        brokerDataList.add(brokerDataA);

        BrokerData brokerDataB = new BrokerData();
        brokerDataB.setBrokerName("Broker-B");
        brokerDataB.setCluster("Cluster-B");
        HashMap<Long, String> addrB = new HashMap<>();
        addrB.put(0L, "127.0.0.2:10911");
        brokerDataB.setBrokerAddrs(addrB);
        brokerDataList.add(brokerDataB);

        topicRouteData.setBrokerDatas(brokerDataList);

        QueueData queueData = new QueueData();
        queueData.setBrokerName("Broker-A");
        queueData.setReadQueueNums(3);
        queueData.setWriteQueueNums(3);
        queueData.setPerm(6);

        QueueData queueDataB = new QueueData();
        queueDataB.setBrokerName("Broker-B");
        queueDataB.setReadQueueNums(3);
        queueDataB.setWriteQueueNums(3);
        queueDataB.setPerm(6);

        List<QueueData> queueDataList = new ArrayList<>();
        queueDataList.add(queueData);
        queueDataList.add(queueDataB);
        topicRouteData.setQueueDatas(queueDataList);

        return topicRouteData;
    }
}
