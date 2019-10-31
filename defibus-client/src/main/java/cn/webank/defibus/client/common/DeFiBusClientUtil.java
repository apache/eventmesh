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
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusClientUtil {
    public static final Logger LOGGER = LoggerFactory.getLogger(DeFiBusClientUtil.class);

    public static Message createReplyMessage(MessageExt sourceMsg, byte[] content) {
        String cluster = sourceMsg.getUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_CLUSTER);
        String replyTopic = DeFiBusConstant.RR_REPLY_TOPIC;
        if (!StringUtils.isEmpty(cluster)) {
            replyTopic = cluster + "-" + replyTopic;
        } else {
            LOGGER.warn("no cluster info from message, can not reply");
            return null;
        }

        Message msg = new Message();
        msg.setTopic(replyTopic);//回程topic
        msg.setBody(content);//body
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, sourceMsg.getUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO));//回给谁
        msg.putUserProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID, sourceMsg.getUserProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID));//原uniqueId
        String sourceBroker = sourceMsg.getUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_BROKER);
        if (!StringUtils.isEmpty(sourceBroker)) {
            msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_BROKER, sourceBroker);//消息从哪个broker来
        }

        return msg;
    }
}
