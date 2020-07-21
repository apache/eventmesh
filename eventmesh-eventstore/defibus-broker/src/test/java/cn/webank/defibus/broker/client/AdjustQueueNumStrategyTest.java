package cn.webank.defibus.broker.client;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdjustQueueNumStrategyTest {

    private String topic = "AdjustQueue";
    private AdjustQueueNumStrategy adjustQueueNumStrategy;
    @Spy
    private DeFiBrokerController deFiBrokerController = new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(), new DeFiBusBrokerConfig());
    @Mock
    private MessageStore messageStore;

    @Before
    public void init() {
        deFiBrokerController.setMessageStore(messageStore);
        adjustQueueNumStrategy = new AdjustQueueNumStrategy(deFiBrokerController);
    }

    @Test
    public void testIsCanAdjustReadQueueSizeByFalse() {
        deFiBrokerController.getTopicConfigManager().updateTopicConfig(createTopic());
        when(messageStore.getMaxOffsetInQueue(anyString(), anyInt())).thenReturn(1000L);
        when(messageStore.getMessageStoreTimeStamp(anyString(), anyInt(), anyLong())).thenReturn(System.currentTimeMillis());
        boolean flag = adjustQueueNumStrategy.isCanAdjustReadQueueSize(topic, 3);
        assertThat(flag).isFalse();
    }

    @Test
    public void testIsCanAdjustReadQueueSizeByTrue() {
        deFiBrokerController.getTopicConfigManager().updateTopicConfig(createTopic());
        boolean flag = adjustQueueNumStrategy.isCanAdjustReadQueueSize(topic, 4);
        assertThat(flag).isTrue();
    }

    private TopicConfig createTopic() {
        TopicConfig topicConfig = new TopicConfig();
        topicConfig.setTopicName(topic);
        topicConfig.setWriteQueueNums(4);
        topicConfig.setReadQueueNums(4);
        return topicConfig;
    }

}
