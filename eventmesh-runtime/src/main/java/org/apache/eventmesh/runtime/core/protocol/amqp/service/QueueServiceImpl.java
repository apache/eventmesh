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
package org.apache.eventmesh.runtime.core.protocol.amqp.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeDefaults;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.MetaStore;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.ExchangeManager;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.QueueManager;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.QueueInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.ExchangeUtil;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.NameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class QueueServiceImpl implements QueueService {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final EventMeshAmqpServer brokerService;
    private final QueueManager queueManager;
    private final ExchangeManager exchangeManager;
    private final MetaStore adminManager;

    public QueueServiceImpl(EventMeshAmqpServer brokerService, MetaStore adminManager) {
        this.brokerService = brokerService;
        this.adminManager = adminManager;
        this.queueManager = adminManager.queue();
        this.exchangeManager = adminManager.exchange();
    }


    @Override
    public QueueInfo queueDeclare(String connectionId, String virtualHostName,
                                  String queue, boolean durable, boolean exclusive, boolean autoDelete,
                                  Map<String, Object> arguments) throws AmqpException {
        if (StringUtils.isBlank(queue)) {
            queue = "tmp_" + UUID.randomUUID();
        }


        // TODO exclusive queue
        QueueInfo queueInfo = queueManager.getQueue(virtualHostName, queue);
        if (queueInfo == null) {

            queueInfo = new QueueInfo();
            queueInfo.setQueueName(queue);
            queueInfo.setDurable(true);
            queueInfo.setExclusive(exclusive);
            queueInfo.setAutoDelete(autoDelete);
            queueInfo.setConnectionId(connectionId);
            queueInfo.setLastVisitTime(System.currentTimeMillis());
            queueInfo.setArguments(buildArgument(arguments));
            try {
                queueManager.createQueue(virtualHostName, queue, queueInfo);
            } catch (AmqpNotFoundException e) {
                log.error("create queue error {}", queue, e);
                throw new AmqpException(ErrorCodes.NOT_FOUND,
                        "Failed to create queue: " + queue, e);
            }

        } else {
            log.debug("Queue has been created ");
        }
        return queueInfo;

    }

    private void defaultExchangeBind(String virtualHostName, String queueName) throws AmqpNotFoundException {
        exchangeManager.exchangeBind(virtualHostName, ExchangeDefaults.DEFAULT_EXCHANGE_NAME_DURABLE,
                queueName, new BindingInfo(queueName, queueName, null));
    }

    @Override
    public void queueDelete(String virtualHostName, QueueInfo defaultQueue,
                            String queue, boolean ifUnused, boolean ifEmpty) throws AmqpException {
        if (StringUtils.isBlank(queue)) {
            //get the default queue
            if (defaultQueue == null) {
                throw new AmqpException(ErrorCodes.NOT_FOUND, "Queue does not exist.");
            }
            return;
        }

        QueueInfo amqpQueue = queueManager.getQueue(virtualHostName, queue);
        if (amqpQueue == null) {
            log.warn("delete not exist queue {}", queue);
            return;
        }

        try {
            unbindAllExchanges4Queue(virtualHostName, queue);
        } catch (AmqpNotFoundException e) {
            log.error("queueDelete error {}", queue, e);
            throw new AmqpException(ErrorCodes.NOT_FOUND,
                    "Failed to delete queue: " + queue, e);
        }
        queueManager.deleteQueue(virtualHostName, queue);
    }


    private void unbindAllExchanges4Queue(String virtualHostName, String queue) throws AmqpNotFoundException {

        Set<String> exchanges = queueManager.getBindings(virtualHostName, queue);
        if (exchanges == null || exchanges.isEmpty()) {
            return;
        }

        for (String exchange : exchanges) {
            exchangeManager.exchangeUnBind(virtualHostName, exchange, queue);
            queueManager.queueUnBind(virtualHostName, queue, exchange);
        }
    }

    @Override
    public void queueBind(String virtualHostName, QueueInfo defaultQueue,
                          String queue, String exchange, String bindingKey, Map<String, Object> argumentsTable)
            throws AmqpException {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (ExchangeUtil.isDefaultExchange(exchange)) {
            throw new AmqpException(ErrorCodes.ACCESS_REFUSED, "Default exchange can not bind");
        }

        String queueName;
        if (StringUtils.isBlank(queue)) {
            if (defaultQueue != null) {
                queueName = defaultQueue.getQueueName();
            } else {
                throw new AmqpException(ErrorCodes.INTERNAL_ERROR, "queueName is empty: ");
            }
        } else {
            queueName = queue;
        }

        if (StringUtils.isBlank(bindingKey)) {
            bindingKey = queueName;
        }

        try {
            NameUtils.checkName(bindingKey);
        } catch (IllegalArgumentException e) {
            throw new AmqpException(ErrorCodes.INVALID_ARGUMENT,
                    "bindingKey Name is illegal:" + bindingKey);
        }

        ExchangeInfo exchangeInfo = exchangeManager.getExchange(virtualHostName, exchange);
        if (exchangeInfo == null) {
            throw new AmqpException(ErrorCodes.NOT_FOUND, "exchange not found: " + exchange);
        }
        QueueInfo queueInfo = queueManager.getQueue(virtualHostName, queue);
        if (queueInfo == null) {
            throw new AmqpException(ErrorCodes.NOT_FOUND, "queue not found: " + exchange);
        }

        BindingInfo bindingInfo = new BindingInfo(queueName, bindingKey, null);

        try {
            queueManager.queueBind(virtualHostName, queueName, exchange);
            exchangeManager.exchangeBind(virtualHostName, exchange, queueName, bindingInfo);
        } catch (AmqpNotFoundException e) {
            log.error("queueBind error {}", queue, e);
            throw new AmqpException(ErrorCodes.NOT_FOUND,
                    "Failed to bind queue: " + queue, e);
        }

    }

    private Map<String, String> buildArgument(Map<String, Object> arguments) {
        if (arguments == null) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        if (arguments.containsKey("x-expires")) {
            result.put("x-expires", String.valueOf(arguments.get("x-expires")));
        }

        return result;
    }

    @Override
    public void queueUnbind(String virtualHostName,
                            String queue, String exchange, String bindingKey, Map<String, Object> arguments) throws AmqpException {
        if (ExchangeUtil.isDefaultExchange(exchange)) {
            throw new AmqpException(ErrorCodes.ACCESS_REFUSED, "Default  exchange can not unbind");
        }

        if (StringUtils.isBlank(bindingKey)) {
            throw new AmqpException(ErrorCodes.ARGUMENT_INVALID, "bindingKey can not be null");
        }


        try {
            exchangeManager.exchangeUnBind(virtualHostName, exchange, queue, bindingKey);
            if (!exchangeManager.isQueueBindingExist(virtualHostName, exchange, queue)) {
                queueManager.queueUnBind(virtualHostName, queue, exchange);
            }
        } catch (AmqpNotFoundException e) {
            log.error("queueUnbind error {}", queue, e);
            throw new AmqpException(ErrorCodes.NOT_FOUND,
                    "Failed to unbind queue: " + queue, e);
        }

    }

    @Override
    public void queuePurge(String virtualHostName, String queue) {
        // TODO
    }


    @Override
    public QueueInfo getQueue(String virtualHost, String queueName) {
        return queueManager.getQueue(virtualHost, queueName);
    }

    @Override
    public boolean checkExist(String virtualHost, String queueName) {
        return queueManager.checkExist(virtualHost, queueName);
    }

    @Override
    public Set<String> getBindings(String virtualHost, String queueName) throws Exception {
        return queueManager.getBindings(virtualHost, queueName);
    }


    @Override
    public Set<String> getQueueList(String virtualHostName) throws Exception {
        return queueManager.getQueueList(virtualHostName);
    }

}
