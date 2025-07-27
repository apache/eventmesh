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

package cn.webank.eventmesh.client.http.producer;

import cn.webank.eventmesh.client.http.ProxyRetObj;
import cn.webank.eventmesh.common.Constants;
import cn.webank.eventmesh.common.LiteMessage;
import cn.webank.eventmesh.common.ProxyException;
import cn.webank.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import cn.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;

public class RRCallbackResponseHandlerAdapter implements ResponseHandler<String> {

    public Logger logger = LoggerFactory.getLogger(RRCallbackResponseHandlerAdapter.class);

    private long createTime;

    private LiteMessage liteMessage;

    private RRCallback rrCallback;

    private long timeout;

    public RRCallbackResponseHandlerAdapter(LiteMessage liteMessage, RRCallback rrCallback, long timeout) {
        this.liteMessage = liteMessage;
        this.rrCallback = rrCallback;
        this.timeout = timeout;
        this.createTime = System.currentTimeMillis();
    }

    @Override
    public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            rrCallback.onException(new ProxyException(response.toString()));
            return response.toString();
        }

        if (System.currentTimeMillis() - createTime > timeout) {
            String err = String.format("response too late, bizSeqNo=%s, uniqueId=%s, createTime=%s, ttl=%s, cost=%sms",
                    liteMessage.getBizSeqNo(),
                    liteMessage.getUniqueId(),
                    DateFormatUtils.format(createTime, Constants.DATE_FORMAT),
                    timeout,
                    System.currentTimeMillis() - createTime);
            logger.warn(err);
            rrCallback.onException(new ProxyException(err));
            return err;
        }

        String res = EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
        ProxyRetObj ret = JSON.parseObject(res, ProxyRetObj.class);
        if (ret.getRetCode() != ProxyRetCode.SUCCESS.getRetCode()) {
            rrCallback.onException(new ProxyException(ret.getRetCode(), ret.getRetMsg()));
            return res;
        }

        LiteMessage liteMessage = new LiteMessage();
        try {
            SendMessageResponseBody.ReplyMessage replyMessage =
                    JSON.parseObject(ret.getRetMsg(), SendMessageResponseBody.ReplyMessage.class);
            liteMessage.setContent(replyMessage.body).setProp(replyMessage.properties)
                    .setTopic(replyMessage.topic);
            rrCallback.onSuccess(liteMessage);
        } catch (Exception ex) {
            rrCallback.onException(new ProxyException(ex));
            return ex.toString();
        }

        return liteMessage.toString();
    }
}
