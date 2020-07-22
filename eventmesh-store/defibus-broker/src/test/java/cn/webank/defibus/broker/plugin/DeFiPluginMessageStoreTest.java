package cn.webank.defibus.broker.plugin;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import java.lang.reflect.Field;
import org.apache.rocketmq.broker.plugin.MessageStorePluginContext;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.AppendMessageResult;
import org.apache.rocketmq.store.AppendMessageStatus;
import org.apache.rocketmq.store.GetMessageResult;
import org.apache.rocketmq.store.GetMessageStatus;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.PutMessageResult;
import org.apache.rocketmq.store.PutMessageStatus;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
@RunWith(MockitoJUnitRunner.class)
public class DeFiPluginMessageStoreTest {

    @Mock
    private MessageStorePluginContext messageStorePluginContext;
    @Spy
    private DeFiBrokerController deFiBrokerController = new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(),new DeFiBusBrokerConfig());
    @Mock
    private MessageStore messageStore;
    @Spy
    private DeFiPluginMessageStore deFiPluginMessageStore =new DeFiPluginMessageStore(messageStorePluginContext,messageStore);;

    private String topic = "FooBar";
    private String group = "FooBarGroup";

    @Before
    public void init()throws Exception{
        Field field = DeFiPluginMessageStore.class.getDeclaredField("next");
        field.setAccessible(true);
        field.set(deFiPluginMessageStore,messageStore);
        deFiBrokerController.setMessageStore(messageStore);
        deFiBrokerController.getConsumeQueueManager().getBrokerController().setMessageStore(messageStore);
        deFiBrokerController.getTopicConfigManager().createTopicInSendMessageBackMethod(topic,2,6,0);
        deFiPluginMessageStore.start();
    }

    @Test
    public void testPutMessage(){
        MessageExtBrokerInner inner = new MessageExtBrokerInner();
        when(messageStore.putMessage(inner)).thenReturn(new PutMessageResult(PutMessageStatus.PUT_OK, new AppendMessageResult(AppendMessageStatus.PUT_OK)));
        PutMessageResult result = deFiPluginMessageStore.putMessage(inner);
        assertThat(result.getPutMessageStatus()).isEqualTo(PutMessageStatus.PUT_OK);
        assertTrue(result.getAppendMessageResult().isOk());
    }

    @Test
    public void testGetMessage(){
        GetMessageResult getMessageResult = new GetMessageResult();
        getMessageResult.setStatus(GetMessageStatus.FOUND);
        getMessageResult.setNextBeginOffset(1);
        when(messageStore.getMessage(group,topic,1,0,0,null)).thenReturn(getMessageResult);
        GetMessageResult result = deFiPluginMessageStore.getMessage(group,topic,1,0,0,null);
        assertThat(result.getStatus()).isEqualTo(GetMessageStatus.FOUND);
        assertThat(result.getNextBeginOffset()).isEqualTo(1);
    }

    @After
    public void shutdown(){
        deFiPluginMessageStore.shutdown();
    }

}
