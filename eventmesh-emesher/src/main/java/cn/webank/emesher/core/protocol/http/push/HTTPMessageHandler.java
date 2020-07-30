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

package cn.webank.emesher.core.protocol.http.push;

import cn.webank.emesher.core.protocol.http.consumer.HandleMsgContext;
import cn.webank.emesher.core.protocol.http.consumer.ProxyConsumer;
import cn.webank.eventmesh.common.ThreadPoolFactory;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HTTPMessageHandler implements MessageHandler {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ProxyConsumer proxyConsumer;

    private static final ScheduledExecutorService SCHEDULER = ThreadPoolFactory.createSingleScheduledExecutor("proxy-pushMsgTimeout-");

    private ThreadPoolExecutor pushExecutor;

    private final Integer CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD = 10000;

    private void checkTimeout() {
        waitingRequests.entrySet().stream().forEach(entry -> {
            for (AbstractHTTPPushRequest request : entry.getValue()) {
                request.timeout();
                waitingRequests.get(request.handleMsgContext.getConsumerGroup()).remove(request);
            }
        });
    }

    public static Map<String, Set<AbstractHTTPPushRequest>> waitingRequests = Maps.newConcurrentMap();

    public HTTPMessageHandler(ProxyConsumer proxyConsumer) {
        this.proxyConsumer = proxyConsumer;
        this.pushExecutor = proxyConsumer.getProxyHTTPServer().pushMsgExecutor;
        waitingRequests.put(this.proxyConsumer.getConsumerGroupConf().getConsumerGroup(), Sets.newConcurrentHashSet());
        SCHEDULER.scheduleAtFixedRate(this::checkTimeout, 0, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean handle(final HandleMsgContext handleMsgContext) {
        Set waitingRequests4Group = MapUtils.getObject(waitingRequests,
                handleMsgContext.getConsumerGroup(), Sets.newConcurrentHashSet());
        if (waitingRequests4Group.size() > CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD) {
            logger.warn("waitingRequests is too many, so reject, this message will be send back to MQ, consumerGroup:{}, threshold:{}",
                    handleMsgContext.getConsumerGroup(), CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD);
            return false;
        }

        try {
            pushExecutor.submit(() -> {
                AsyncHTTPPushRequest asyncPushRequest = new AsyncHTTPPushRequest(handleMsgContext, waitingRequests);
                asyncPushRequest.tryHTTPRequest();
            });
            return true;
        } catch (RejectedExecutionException e) {
            logger.warn("pushMsgThreadPoolQueue is full, so reject, current task size {}", handleMsgContext.getProxyHTTPServer().getPushMsgExecutor().getQueue().size(), e);
            return false;
        }
    }
}
