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

package org.apache.eventmesh.connector.slack.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.slack.SlackSinkConfig;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Slack sink connector.
 * Slack doc: <a href="https://api.slack.com/messaging/sending">...</a>
 */
@Slf4j
public class SlackSinkConnector implements Sink {

    private SlackSinkConfig sinkConfig;

    private volatile boolean isRunning = false;

    private MethodsClient client;

    @Override
    public Class<? extends Config> configClass() {
        return SlackSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for dingding sink connector
        this.sinkConfig = (SlackSinkConfig) config;
        this.client = Slack.getInstance().methods();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for dingding source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (SlackSinkConfig) sinkConnectorContext.getSinkConfig();
        this.client = Slack.getInstance().methods();
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
            publishMessage(record);
        }
    }

    private void publishMessage(ConnectRecord record) {
        try {
            ChatPostMessageResponse response = client.chatPostMessage(r -> r
                .token(sinkConfig.getSinkConnectorConfig().getAppToken())
                .channel(sinkConfig.getSinkConnectorConfig().getChannelId())
                .text(new String((byte[]) record.getData())));
            if (Objects.nonNull(response) && StringUtils.isNotBlank(response.getError())) {
                throw new IllegalAccessException(response.getError());
            }
        } catch (Exception e) {
            log.error("Send message to slack error.", e);
        }
    }
}
