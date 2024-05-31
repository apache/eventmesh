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

package org.apache.eventmesh.connector.dingtalk.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.dingtalk.DingDingSinkConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.dingtalk.common.constants.ConnectRecordExtensionKeys;
import org.apache.eventmesh.connector.dingtalk.config.DingDingMessageTemplateType;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.Common;
import com.aliyun.teautil.models.RuntimeOptions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DingDingSinkConnector implements Sink {

    public static final Cache<String, String> AUTH_CACHE = CacheBuilder.newBuilder()
        .initialCapacity(12)
        .maximumSize(10)
        .concurrencyLevel(5)
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build();

    public static final String ACCESS_TOKEN_CACHE_KEY = "access_token";

    private DingDingSinkConfig sinkConfig;

    private com.aliyun.dingtalkrobot_1_0.Client sendMessageClient;

    private com.aliyun.dingtalkoauth2_1_0.Client authClient;

    private volatile boolean isRunning = false;

    @Override
    public Class<? extends Config> configClass() {
        return DingDingSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for dingding sink connector
        this.sinkConfig = (DingDingSinkConfig) config;
        sendMessageClient = createSendMessageClient();
        authClient = createOAuthClient();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for dingding source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (DingDingSinkConfig) sinkConnectorContext.getSinkConfig();
        sendMessageClient = createSendMessageClient();
        authClient = createOAuthClient();
    }

    @Override
    public void start() {
        isRunning = true;
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getSinkConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @SneakyThrows
    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord record : sinkRecords) {
            try {
                if (Objects.isNull(record.getData())) {
                    log.warn("ConnectRecord data is null, ignore.");
                    continue;
                }
                String accessToken = getAccessToken();
                OrgGroupSendHeaders orgGroupSendHeaders =
                    new OrgGroupSendHeaders();
                orgGroupSendHeaders.xAcsDingtalkAccessToken = accessToken;

                String templateTypeKey = record.getExtension(ConnectRecordExtensionKeys.DINGTALK_TEMPLATE_TYPE);
                if (templateTypeKey == null || "null".equals(templateTypeKey)) {
                    templateTypeKey = DingDingMessageTemplateType.PLAIN_TEXT.getTemplateType();
                }
                DingDingMessageTemplateType templateType = DingDingMessageTemplateType.of(templateTypeKey);

                Map<String, String> contentMap = new HashMap<>();
                if (DingDingMessageTemplateType.PLAIN_TEXT == templateType) {
                    contentMap.put("content", new String((byte[]) record.getData()));
                } else if (DingDingMessageTemplateType.MARKDOWN == templateType) {
                    String title = Optional.ofNullable(record.getExtension(ConnectRecordExtensionKeys.DINGTALK_MARKDOWN_MESSAGE_TITLE))
                        .orElse("EventMesh-Message");
                    contentMap.put("title", title);
                    contentMap.put("text", new String((byte[]) record.getData()));
                }

                OrgGroupSendRequest orgGroupSendRequest =
                    new OrgGroupSendRequest()
                        .setMsgParam(JsonUtils.toJSONString(contentMap))
                        .setMsgKey(templateType.getTemplateKey())
                        .setOpenConversationId(sinkConfig.getSinkConnectorConfig().getOpenConversationId())
                        .setRobotCode(sinkConfig.getSinkConnectorConfig().getRobotCode())
                        .setCoolAppCode(sinkConfig.getSinkConnectorConfig().getCoolAppCode());

                try {
                    sendMessageClient.orgGroupSendWithOptions(orgGroupSendRequest, orgGroupSendHeaders, new RuntimeOptions());
                } catch (TeaException e) {
                    if (!Common.empty(e.code) && !Common.empty(e.message)) {
                        String errorMessage = e.getMessage();
                        if ("invalidParameter.token.invalid".equals(errorMessage)) {
                            AUTH_CACHE.invalidate(ACCESS_TOKEN_CACHE_KEY);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to sink message to DingDing.", e);
            }
        }
    }

    @SneakyThrows
    private String getAccessToken() {
        return AUTH_CACHE.get(ACCESS_TOKEN_CACHE_KEY, () -> {
            GetAccessTokenRequest getAccessTokenRequest =
                new GetAccessTokenRequest()
                    .setAppKey(sinkConfig.getSinkConnectorConfig().getAppKey())
                    .setAppSecret(sinkConfig.getSinkConnectorConfig().getAppSecret());
            return authClient.getAccessToken(getAccessTokenRequest).getBody().getAccessToken();
        });
    }

    public static com.aliyun.dingtalkrobot_1_0.Client createSendMessageClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkrobot_1_0.Client(config);
    }

    public static com.aliyun.dingtalkoauth2_1_0.Client createOAuthClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkoauth2_1_0.Client(config);
    }
}
