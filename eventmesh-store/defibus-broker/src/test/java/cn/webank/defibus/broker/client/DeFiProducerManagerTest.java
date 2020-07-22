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
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class DeFiProducerManagerTest {
    private DeFiProducerManager deFiProducerManager;
    private String group = "FooBar";
    private ClientChannelInfo clientInfo;

    @Mock
    private Channel channel;

    @Before
    public void init() {
        deFiProducerManager = new DeFiProducerManager();
        clientInfo = new ClientChannelInfo(channel, "ClientIdTest", LanguageCode.JAVA, 1);

    }

    @Test
    public void doChannelCloseEvent() throws Exception {
        deFiProducerManager.registerProducer(group, clientInfo);
        assertThat(deFiProducerManager.getGroupChannelTable().get(group).get(channel)).isNotNull();

        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isNotNull();
        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isEqualTo(clientInfo);

        deFiProducerManager.doChannelCloseEvent("127.0.0.1", channel);
        assertThat(deFiProducerManager.getGroupChannelTable().get(group).get(channel)).isNull();

        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isNull();
    }

    @Test
    public void testRegisterProducer() throws Exception {
        deFiProducerManager.registerProducer(group, clientInfo);
        HashMap<Channel, ClientChannelInfo> channelMap = deFiProducerManager.getGroupChannelTable().get(group);
        assertThat(channelMap).isNotNull();
        assertThat(channelMap.get(channel)).isEqualTo(clientInfo);

        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isNotNull();
        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isEqualTo(clientInfo);
    }

    @Test
    public void unregisterProducer() throws Exception {
        deFiProducerManager.registerProducer(group, clientInfo);
        HashMap<Channel, ClientChannelInfo> channelMap = deFiProducerManager.getGroupChannelTable().get(group);
        assertThat(channelMap).isNotNull();
        assertThat(channelMap.get(channel)).isEqualTo(clientInfo);

        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isNotNull();
        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isEqualTo(clientInfo);

        deFiProducerManager.unregisterProducer(group, clientInfo);
        channelMap = deFiProducerManager.getGroupChannelTable().get(group);
        assertThat(channelMap).isNull();

        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isNull();
        assertThat(deFiProducerManager.getProducerChannelTable().get(clientInfo.getClientId())).isNull();
    }

}
