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

package org.apache.eventmesh.client.http.producer;

import java.io.IOException;
import java.nio.charset.Charset;


import com.alibaba.fastjson.JSON;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            rrCallback.onException(new EventMeshException(response.toString()));
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
            rrCallback.onException(new EventMeshException(err));
            return err;
        }

        String res = EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
        EventMeshRetObj ret = JSON.parseObject(res, EventMeshRetObj.class);
        if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
            rrCallback.onException(new EventMeshException(ret.getRetCode(), ret.getRetMsg()));
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
            rrCallback.onException(new EventMeshException(ex));
            return ex.toString();
        }

        return liteMessage.toString();
    }
}
