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

package cn.webank.defibus.broker.monitor;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import java.lang.reflect.Field;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class QueueListeningMonitorTest {
    private DeFiBrokerController deFiBrokerController;

    @Mock
    QueueListeningMonitor queueListeningMonitor;

    @Before
    public void init() throws Exception {
        deFiBrokerController = new DeFiBrokerController(
            new BrokerConfig(),
            new NettyServerConfig(),
            new NettyClientConfig(),
            new MessageStoreConfig(),
            new DeFiBusBrokerConfig());
        assertThat(deFiBrokerController.initialize());

        Field field = DeFiBrokerController.class.getDeclaredField("queueListeningMonitor");
        field.setAccessible(true);
        field.set(deFiBrokerController, queueListeningMonitor);
    }

    @Test
    public void testQueueListeningMonitorStart() throws Exception {
        deFiBrokerController.start();
        verify(queueListeningMonitor).start();
    }

    @After
    public void shutdown() {
        deFiBrokerController.shutdown();
    }
}
