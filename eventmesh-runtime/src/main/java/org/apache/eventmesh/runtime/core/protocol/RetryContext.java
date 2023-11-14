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

package org.apache.eventmesh.runtime.core.protocol;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.retry.api.conf.RetryConfiguration;
import org.apache.eventmesh.retry.api.strategy.RetryStrategy;
import org.apache.eventmesh.retry.api.timer.TimerTask;
import org.apache.eventmesh.runtime.core.protocol.consumer.HandleMessageContext;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.cloudevents.CloudEvent;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RetryContext implements TimerTask {

    private static final Set<String> RETRY_STRATEGY_PROCESSED_EVENT_LIST = Collections.synchronizedSet(new HashSet<>());

    public CloudEvent event;

    public String seq;

    public int retryTimes;

    public CommonConfiguration commonConfiguration;

    public long executeTime = System.currentTimeMillis();

    public void setEvent(CloudEvent event) {
        this.event = event;
    }

    @Override
    public void setExecuteTimeHook(long executeTime) {
        this.executeTime = executeTime;
    }

    @Override
    public final void run() throws Exception {
        String eventMeshRetryPluginType = Optional.ofNullable(commonConfiguration.getEventMeshRetryPluginType())
            .orElse(Constants.DEFAULT);
        if (Constants.DEFAULT.equals(eventMeshRetryPluginType)) {
            log.warn("Because eventmesh retry plugin is default, retry in memory.");
            doRun();
            return;
        }
        if (!eventMeshRetryPluginType.equals(commonConfiguration.getEventMeshStoragePluginType())) {
            log.warn("Because eventmesh retry plugin type mismatched with storage plugin type, retry in memory.");
            doRun();
            return;
        }
        Optional<RetryStrategy> retryStrategy = Optional.ofNullable(
            EventMeshExtensionFactory.getExtension(RetryStrategy.class,
                commonConfiguration.getEventMeshRetryPluginType()));
        if (!retryStrategy.isPresent()) {
            log.warn("Storage retry SPI not found, retry in memory.");
            doRun();
            return;
        }
        if (!RETRY_STRATEGY_PROCESSED_EVENT_LIST.contains(event.getId())) {
            String consumerGroupName = getHandleMessageContext().getConsumerGroup();
            EventMeshProducer producer = getProducerManager().getEventMeshProducer(consumerGroupName);
            RetryConfiguration retryConfiguration = RetryConfiguration.builder()
                .event(event)
                .consumerGroupName(consumerGroupName)
                .producer(producer.getMqProducerWrapper().getMeshMQProducer())
                .topic(getHandleMessageContext().getTopic())
                .build();
            retryStrategy.get().retry(retryConfiguration);
            RETRY_STRATEGY_PROCESSED_EVENT_LIST.add(event.getId());
        } else {
            RETRY_STRATEGY_PROCESSED_EVENT_LIST.remove(event.getId());
            getHandleMessageContext().finish();
        }
    }

    protected HandleMessageContext getHandleMessageContext() throws Exception {
        throw new IllegalAccessException("method not supported.");
    }

    public abstract void doRun() throws Exception;

    @SneakyThrows
    protected ProducerManager getProducerManager() {
        throw new IllegalAccessException("method not supported.");
    }

}
