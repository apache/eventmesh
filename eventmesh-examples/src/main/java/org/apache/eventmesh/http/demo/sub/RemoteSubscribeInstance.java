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

package org.apache.eventmesh.http.demo.sub;

import static org.apache.eventmesh.common.ExampleConstants.EVENTMESH_HTTP_PORT;
import static org.apache.eventmesh.common.ExampleConstants.SERVER_PORT;
import static org.apache.eventmesh.util.Utils.getURL;

import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.client.http.util.HttpUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.util.Utils;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;

import io.netty.handler.codec.http.HttpMethod;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteSubscribeInstance {

    static final CloseableHttpClient httpClient = HttpClients.createDefault();

    Properties properties;

    {
        try {
            properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        } catch (IOException e) {
            log.error("Failed to read the file.", e);
        }
    }

    final String localPort = properties.getProperty(SERVER_PORT);
    final String httServerPort = properties.getProperty(EVENTMESH_HTTP_PORT);
    final String testURL = getURL(localPort, "/sub/test");
    final String localURL = getURL(httServerPort, "/eventmesh/subscribe/local");
    final String remoteURL = getURL(httServerPort, "/eventmesh/subscribe/remote");

    public static void main(String[] args) {
        RemoteSubscribeInstance remoteSubscribeInstance = new RemoteSubscribeInstance();
        remoteSubscribeInstance.subscribeRemote();
        // remoteSubscribeInstance.unsubscribeRemote();
    }

    private void subscribeRemote() {
        SubscriptionItem subscriptionItem = new SubscriptionItem();
        subscriptionItem.setTopic(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC);
        subscriptionItem.setMode(SubscriptionMode.CLUSTERING);
        subscriptionItem.setType(SubscriptionType.ASYNC);

        final RequestParam subscribeParam = buildCommonRequestParam()
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.toJSONString(Collections.singletonList(subscriptionItem)))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
            .addBody(SubscribeRequestBody.URL, testURL)
            .addBody("remoteMesh", localURL);

        postMsg(subscribeParam);
    }

    private void unsubscribeRemote() {
        final RequestParam subscribeParam = buildCommonRequestParam()
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.toJSONString(Collections.singletonList(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC)))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
            .addBody(SubscribeRequestBody.URL, testURL);

        postMsg(subscribeParam);
    }

    private void postMsg(RequestParam subscribeParam) {
        // cluster2 ip
        try {
            final String res = HttpUtils.post(httpClient, remoteURL, subscribeParam);
            final EventMeshRetObj ret = JsonUtils.parseObject(res, EventMeshRetObj.class);
            if (Objects.requireNonNull(ret).getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
        } catch (Exception ex) {
            throw new EventMeshException(String.format("Subscribe topic error, target:%s", remoteURL), ex);
        }
    }

    private RequestParam buildCommonRequestParam() {
        return new RequestParam(HttpMethod.POST)
            .addHeader(ProtocolKey.ClientInstanceKey.IP.getKey(), IPUtils.getLocalAddress())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT);
    }
}
