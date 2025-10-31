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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.netty.handler.codec.http.HttpMethod;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteSubscribeInstance {

    static final CloseableHttpClient httpClient = HttpClients.createDefault();

    public static void main(String[] args) throws IOException {
        subscribeLocal();
        //subscribeRemote();
        // unsubscribeRemote();
    }

    private static void subscribeLocal() throws IOException {
        SubscriptionItem item = new SubscriptionItem();
        item.setTopic(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC);
        item.setMode(SubscriptionMode.CLUSTERING);
        item.setType(SubscriptionType.ASYNC);

        Map<String, Object> body = new HashMap<>();
        body.put("url", "http://127.0.0.1:8088/sub/test");
        body.put("consumerGroup", "EventMeshTest-consumerGroup");
        body.put("topic", Collections.singletonList(item));

        String json = JsonUtils.toJSONString(body);
        // 2) use HttpPost
        HttpPost post = new HttpPost("http://127.0.0.1:10105/eventmesh/subscribe/local");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("env", "prod");
        post.setHeader("idc", "default");
        post.setHeader("sys", "http-client-demo");
        post.setHeader("username", "eventmesh");
        post.setHeader("passwd", "eventmesh");
        post.setHeader("ip", IPUtils.getLocalAddress());
        post.setHeader("language", "JAVA");
        post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

        try (CloseableHttpResponse resp = httpClient.execute(post)) {
            String respBody = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            log.info("respStatusLine:{}", resp.getStatusLine());
            log.info("respBody:{}", respBody);
        }
    }

    private static void subscribeRemote() {
        SubscriptionItem subscriptionItem = new SubscriptionItem();
        subscriptionItem.setTopic(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC);
        subscriptionItem.setMode(SubscriptionMode.CLUSTERING);
        subscriptionItem.setType(SubscriptionType.ASYNC);

        final RequestParam subscribeParam = buildCommonRequestParam()
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.toJSONString(Collections.singletonList(subscriptionItem)))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
            .addBody(SubscribeRequestBody.URL, "http://127.0.0.1:8088/sub/test")
            .addBody("remoteMesh", "http://127.0.0.1:10105/eventmesh/subscribe/local");

        postMsg(subscribeParam);
    }

    private static void unsubscribeRemote() {
        final RequestParam subscribeParam = buildCommonRequestParam()
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.toJSONString(Collections.singletonList(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC)))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
            .addBody(SubscribeRequestBody.URL, "http://127.0.0.1:8088/sub/test");

        postMsg(subscribeParam);
    }

    private static void postMsg(RequestParam subscribeParam) {
        // cluster2 ip
        final String target = "http://127.0.0.1:11105/eventmesh/subscribe/remote";
        try {
            final String res = HttpUtils.post(httpClient, target, subscribeParam);
            final EventMeshRetObj ret = JsonUtils.parseObject(res, EventMeshRetObj.class);
            if (Objects.requireNonNull(ret).getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
        } catch (Exception ex) {
            throw new EventMeshException(String.format("Subscribe topic error, target:%s", target), ex);
        }
    }

    private static RequestParam buildCommonRequestParam() {
        return new RequestParam(HttpMethod.POST)
            .addHeader(ProtocolKey.ClientInstanceKey.IP.getKey(), IPUtils.getLocalAddress())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT);
    }
}
