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

package cn.webank.defibus.client.common;

import cn.webank.defibus.common.DeFiBusConstant;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeFiBusCLientUtilTest {
    @Test
    public void createReplyMessage_Success() {
        MessageExt msg = new MessageExt();
        msg.setTopic("Test");
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_CLUSTER, "ClusterName");
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, "reply_to");
        msg.putUserProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID, "w/request_id");
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_BROKER, "BrokerName");

        byte[] replyContent = "reply content".getBytes();
        Message replyMsg = DeFiBusClientUtil.createReplyMessage(msg, replyContent);

        assertThat(replyMsg).isNotNull();
        assertThat(replyContent).isEqualTo(replyMsg.getBody());
    }

    @Test
    public void createReplyMessage_Fail() {
        MessageExt msg = new MessageExt();
        msg.setTopic("Test");
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, "reply_to");
        msg.putUserProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID, "w/request_id");

        byte[] replyContent = "reply content".getBytes();
        Message replyMsg = DeFiBusClientUtil.createReplyMessage(msg, replyContent);

        assertThat(replyMsg).isNull();
    }
}
