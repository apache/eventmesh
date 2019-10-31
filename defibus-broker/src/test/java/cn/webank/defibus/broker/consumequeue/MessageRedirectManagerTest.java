package cn.webank.defibus.broker.consumequeue;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import cn.webank.defibus.common.DeFiBusConstant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

public class MessageRedirectManagerTest {
    private DeFiBusBrokerConfig deFiBusBrokerConfig = new DeFiBusBrokerConfig();
    private MessageRedirectManager messageRedirectManager;
    private DeFiBrokerController deFiBrokerController;
    private String topic = "TestTopic";
    private String group = "TestGroup";
    private String clientId = UUID.randomUUID().toString();


    @Before
    public void init() {
        deFiBrokerController = spy(new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(), deFiBusBrokerConfig));
        messageRedirectManager=new MessageRedirectManager(deFiBrokerController);
        messageRedirectManager.updateConfigs(createRedirectConfItem());
        deFiBrokerController.getClientRebalanceResultManager().updateListenMap(group,topic,1,"127.0.0.1");
    }
    @Test
    public void testRedirectMessageToWhichQueue() throws Exception {
        MessageRedirectManager.RedirectResult result = messageRedirectManager.redirectMessageToWhichQueue(createSendMsgRequestHeader(),"flag");
        assertThat(result.getStates()).isEqualTo(MessageRedirectManager.RedirectStates.REDIRECT_OK);
        assertThat(result.getRedirectQueueId()).isEqualTo(1);
    }


    private List<MessageRedirectManager.RedirectConfItem> createRedirectConfItem(){
        List<MessageRedirectManager.RedirectConfItem> list = new ArrayList<MessageRedirectManager.RedirectConfItem>();
        MessageRedirectManager.RedirectConfItem item = new MessageRedirectManager.RedirectConfItem();
        item.setConsumerGroup(group);
        item.setTopic(topic);
        Set<String> ips = new HashSet<>();
        ips.add("127.0.0.1");
        item.setIps(ips);
        item.setRedirectFlag("flag");
        list.add(item);
        return  list ;
    }
    private SendMessageRequestHeader createSendMsgRequestHeader() {
        SendMessageRequestHeader requestHeader = new SendMessageRequestHeader();
        requestHeader.setProducerGroup(group);
        requestHeader.setTopic(topic);
        requestHeader.setDefaultTopic(MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC);
        requestHeader.setDefaultTopicQueueNums(3);
        requestHeader.setQueueId(1);
        requestHeader.setSysFlag(0);
        requestHeader.setBornTimestamp(System.currentTimeMillis());
        requestHeader.setFlag(124);
        Message msg = new Message();
        msg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.REPLY);
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, clientId);
        msg.setBody(new String("abcd").getBytes());
        requestHeader.setProperties(MessageDecoder.messageProperties2String(msg.getProperties()));
        return requestHeader;
    }

}
