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

package com.webank.emesher.core.protocol.http.producer;

import com.webank.emesher.boot.ProxyHTTPServer;
import com.webank.emesher.core.protocol.http.retry.RetryContext;
import com.webank.eventmesh.common.Constants;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SendMessageContext extends RetryContext {

    public static Logger logger = LoggerFactory.getLogger("retry");

    private Message msg;

    private String bizSeqNo;

    private ProxyProducer proxyProducer;

    private long createTime = System.currentTimeMillis();

    private Map<String, String> props;

    public ProxyHTTPServer proxyHTTPServer;

    public SendMessageContext(String bizSeqNo, Message msg, ProxyProducer proxyProducer, ProxyHTTPServer proxyHTTPServer) {
        this.bizSeqNo = bizSeqNo;
        this.msg = msg;
        this.proxyProducer = proxyProducer;
        this.proxyHTTPServer = proxyHTTPServer;
    }

    public void addProp(String key, String val) {
        if (props == null) {
            props = new HashMap<>();
        }
        props.put(key, val);
    }

    public String getProp(String key) {
        return props.get(key);
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public Message getMsg() {
        return msg;
    }

    public void setMsg(Message msg) {
        this.msg = msg;
    }

    public ProxyProducer getProxyProducer() {
        return proxyProducer;
    }

    public void setProxyProducer(ProxyProducer proxyProducer) {
        this.proxyProducer = proxyProducer;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sendMessageContext={")
                .append("bizSeqNo=").append(bizSeqNo)
                .append(",retryTimes=").append(retryTimes)
                .append(",producer=").append(proxyProducer != null ? proxyProducer.producerGroupConfig.getGroupName() : null)
                .append(",executeTime=").append(DateFormatUtils.format(executeTime, Constants.DATE_FORMAT))
                .append(",createTime=").append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT)).append("}");
        return sb.toString();
    }

    @Override
    public boolean retry() throws Exception {
        if (proxyProducer == null) {
            return false;
        }

        if (retryTimes > 0) { //retry once
            return false;
        }

        retryTimes++;
        proxyProducer.send(this, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {

            }

            @Override
            public void onException(Throwable e) {
                logger.warn("", e);
                proxyHTTPServer.metrics.summaryMetrics.recordSendBatchMsgFailed(1);
            }
        });

        return true;
    }
}
