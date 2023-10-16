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

package org.apache.eventmesh.connector.spring.sink;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.spring.sink.connector.SpringSinkConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshMessageListenerBeanPostProcessor implements ApplicationContextAware, BeanPostProcessor {

    private static final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        Runtime.getRuntime().availableProcessors() * 2,
        "EventMesh-MessageListenerBeanPostProcessor-");

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @SneakyThrows
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        EventMeshMessageListener annotation = targetClass.getAnnotation(EventMeshMessageListener.class);
        if (annotation != null) {
            if (!(bean instanceof EventMeshListener)) {
                throw new EventMeshException("EventMeshListener interface not implemented.");
            }
            EventMeshListener listener = (EventMeshListener) bean;

            SpringSinkConnector sinkConnector = applicationContext.getBean(SpringSinkConnector.class);

            executor.execute(() -> {
                ConnectRecord poll;
                while (sinkConnector.isRunning()) {
                    try {
                        poll = sinkConnector.getQueue().poll(annotation.requestTimeout(), TimeUnit.SECONDS);
                        if (null == poll || null == poll.getData()) {
                            log.warn("Event is empty.");
                            continue;
                        }
                        listener.onMessage(poll.getData());
                    } catch (Exception e) {
                        log.warn("Consume snapshot event error", e);
                    }
                }
            });
        }
        return bean;
    }

}
