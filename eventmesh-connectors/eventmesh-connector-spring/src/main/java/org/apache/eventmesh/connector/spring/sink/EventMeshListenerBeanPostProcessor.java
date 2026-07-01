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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.spring.config.SpringConnectServerConfig;
import org.apache.eventmesh.connector.spring.sink.connector.SpringSinkConnector;
import org.apache.eventmesh.openconnect.Application;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshListenerBeanPostProcessor implements ApplicationContextAware,
    CommandLineRunner, BeanPostProcessor {

    private static final String SPRING_SINK = "springSink";

    private static final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        Runtime.getRuntime().availableProcessors() * 2,
        "EventMesh-MessageListenerBeanPostProcessor-");

    private ApplicationContext applicationContext;

    private List<EventMeshConsumerMetadata> metadataList = new ArrayList<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();
        ReflectionUtils.doWithMethods(targetClass, new ReflectionUtils.MethodCallback() {

            @Override
            public void doWith(final Method method) throws IllegalArgumentException, IllegalAccessException {
                EventMeshListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, EventMeshListener.class);
                if (annotation == null || method.isBridge()) {
                    return;
                }
                metadataList.add(new EventMeshConsumerMetadata(bean, method, annotation));
            }
        });
        return bean;
    }

    @Override
    public void run(String... args) throws Exception {
        runSinkConnector();
        metadataList.forEach(metadata -> {
            Object bean = metadata.getBean();
            Method method = metadata.getMethod();
            EventMeshListener annotation = metadata.getAnnotation();
            SpringSinkConnector sinkConnector = applicationContext.getBean(SpringSinkConnector.class);
            executor.execute(() -> {
                ConnectRecord poll;
                while (sinkConnector.isRunning()) {
                    try {
                        poll = sinkConnector.getQueue().poll(annotation.requestTimeout(), TimeUnit.SECONDS);
                        if (poll == null || poll.getData() == null) {
                            continue;
                        }
                        String messageBody = new String((byte[]) poll.getData());
                        Type[] parameterizedTypes = method.getGenericParameterTypes();
                        if (parameterizedTypes.length == 0) {
                            throw new IllegalStateException("There has not any arguments for consumer method.");
                        }
                        if (parameterizedTypes.length > 1) {
                            throw new IllegalStateException("There has more than one arguments for consumer method.");
                        }
                        Class<?> rawType;
                        if (parameterizedTypes[0] instanceof Class) {
                            rawType = (Class<?>) parameterizedTypes[0];
                        } else {
                            throw new IllegalStateException(
                                "The arguments type for consumer method can't cast to be Class and ParameterizedTypeImpl");
                        }
                        if (rawType == String.class) {
                            metadata.getMethod().invoke(bean, messageBody);
                        } else {
                            metadata.getMethod().invoke(bean, JsonUtils.parseObject(messageBody, parameterizedTypes[0]));
                        }
                    } catch (Exception e) {
                        log.warn("Consume snapshot event error", e);
                    }
                }
            });
        });
    }

    @SneakyThrows
    public void runSinkConnector() {
        SpringConnectServerConfig springConnectServerConfig = ConfigUtil.parse(SpringConnectServerConfig.class,
            Constants.CONNECT_SERVER_CONFIG_FILE_NAME);

        if (springConnectServerConfig.isSinkEnable()) {
            Map<String, String> extensions = new HashMap<>();
            extensions.put(Application.CREATE_EXTENSION_KEY, SPRING_SINK);
            Application application = new Application(extensions);
            application.run(SpringSinkConnector.class);
        }
    }
}
