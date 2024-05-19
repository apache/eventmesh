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

package org.apache.eventmesh.connector.prometheus.source.connector;

import org.apache.eventmesh.connector.prometheus.model.QueryPrometheusReq;
import org.apache.eventmesh.connector.prometheus.model.QueryPrometheusRsp;
import org.apache.eventmesh.connector.prometheus.source.config.PrometheusSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrometheusSourceConnector implements Source {

    private static final int MAX_RETRY_TIME = 3;

    private static final int FIXED_WAIT_SECOND = 1;

    private final Retryer<Boolean> retryer =
        RetryerBuilder.<Boolean>newBuilder()
            .retryIfException()
            .retryIfResult(res -> !res)
            .withWaitStrategy(WaitStrategies.fixedWait(FIXED_WAIT_SECOND, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(MAX_RETRY_TIME))
            .withRetryListener(
                new RetryListener() {

                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        long times = attempt.getAttemptNumber();
                        log.warn("retry invoke http,times={}", times);
                    }
                })
            .build();

    private PrometheusSourceConfig sourceConfig;

    private CloseableHttpClient httpClient;

    private QueryPrometheusReq queryPrometheusReq;

    private Long initTime;

    private Long startTime;

    private Integer interval;

    private String url;

    @Override
    public Class<? extends Config> configClass() {
        return PrometheusSourceConfig.class;
    }

    @Override
    public void init(Config config) {
        this.sourceConfig = (PrometheusSourceConfig) config;

        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (PrometheusSourceConfig) sourceConnectorContext.getSourceConfig();

        doInit();
    }

    private void doInit() {
        queryPrometheusReq = new QueryPrometheusReq();
        queryPrometheusReq.setQuery(sourceConfig.getConnectorConfig().getQuery());
        queryPrometheusReq.setStep(sourceConfig.getConnectorConfig().getStep());

        interval = sourceConfig.getConnectorConfig().getInterval();
        initTime = sourceConfig.getConnectorConfig().getInitTime();

        url = MessageFormat.format("{0}/{1}", sourceConfig.getConnectorConfig().getAddress(), sourceConfig.getConnectorConfig().getApi());

        // TODO: replace with feature #4481
        httpClient = HttpClientBuilder.create().build();
    }

    @Override
    public void start() {
        log.info("prometheus source connector start.");
        startTime = initTime != null ? initTime : System.currentTimeMillis() / 1000;
    }

    @Override
    public void commit(ConnectRecord record) {
        startTime += interval;
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        log.info("prometheus source connector stop.");
    }

    @Override
    public List<ConnectRecord> poll() {
        try {
            if (startTime > System.currentTimeMillis() / 1000 - interval) {
                Thread.sleep(interval * 1000);
            }

            AtomicReference<CloseableHttpResponse> response = null;
            retryer.call(() -> {
                try {
                    queryPrometheusReq.setStart(startTime);
                    queryPrometheusReq.setEnd(startTime + interval);

                    HttpPost httpPost = new HttpPost(url);
                    httpPost.setEntity(new StringEntity(JSON.toJSONString(queryPrometheusReq), ContentType.APPLICATION_JSON));
                    response.set(httpClient.execute(httpPost));
                    return response.get().getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                } catch (Exception e) {
                    log.error("invoke http failed", e);
                    return false;
                }
            });

            String result = EntityUtils.toString(response.get().getEntity(), StandardCharsets.UTF_8);
            QueryPrometheusRsp responseData = JSONObject.parseObject(result, QueryPrometheusRsp.class);
            List<ConnectRecord> connectRecords =
                responseData.getData().getResult().stream().map(this::assembleRecord).collect(Collectors.toList());

            return connectRecords;
        } catch (Exception e) {
            log.error("failed to poll message from prometheus", e);
            return null;
        }
    }

    private ConnectRecord assembleRecord(String data) {
        Long timestamp = System.currentTimeMillis();
        RecordPartition recordPartition = new RecordPartition();
        RecordOffset recordOffset = new RecordOffset();

        return new ConnectRecord(recordPartition, recordOffset, timestamp, data);
    }
}
