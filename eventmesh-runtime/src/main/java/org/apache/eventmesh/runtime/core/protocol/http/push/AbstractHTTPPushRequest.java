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

package org.apache.eventmesh.runtime.core.protocol.http.push;

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;
import org.apache.eventmesh.runtime.core.protocol.http.retry.RetryContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Lists;

public abstract class AbstractHTTPPushRequest extends RetryContext {

    public EventMeshHTTPServer eventMeshHTTPServer;

    public long createTime = System.currentTimeMillis();

    public long lastPushTime = System.currentTimeMillis();

    public Map<String /** IDC*/, List<String>> urls;

    public List<String> totalUrls;

    public volatile int startIdx;

    public EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    public HttpRetryer retryer;

    public int ttl;

    public HandleMsgContext handleMsgContext;

    private AtomicBoolean complete = new AtomicBoolean(Boolean.FALSE);

    public AbstractHTTPPushRequest(HandleMsgContext handleMsgContext) {
        this.eventMeshHTTPServer = handleMsgContext.getEventMeshHTTPServer();
        this.handleMsgContext = handleMsgContext;
        this.urls = handleMsgContext.getConsumeTopicConfig().getIdcUrls();
        this.totalUrls = Lists.newArrayList(handleMsgContext.getConsumeTopicConfig().getUrls());
        this.eventMeshHttpConfiguration = handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration();
        this.retryer = handleMsgContext.getEventMeshHTTPServer().getHttpRetryer();
        this.ttl = handleMsgContext.getTtl();
        this.startIdx = RandomUtils.nextInt(0, totalUrls.size());
    }

    public void tryHTTPRequest() {
    }

    public void delayRetry(long delayTime) {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES && delayTime > 0) {
            retryTimes++;
            delay(delayTime);
            retryer.pushRetry(this);
        } else {
            complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        }
    }

    public void delayRetry() {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES) {
            retryTimes++;
            delay(retryTimes * EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS);
            retryer.pushRetry(this);
        } else {
            complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        }
    }

    public String getUrl() {
        List<String> localIDCUrl = MapUtils.getObject(urls,
                eventMeshHttpConfiguration.eventMeshIDC, null);
        if (CollectionUtils.isNotEmpty(localIDCUrl)) {
            return localIDCUrl.get((startIdx + retryTimes) % localIDCUrl.size());
        }

        List<String> otherIDCUrl = new ArrayList<String>();
        for (List<String> tmp : urls.values()) {
            otherIDCUrl.addAll(tmp);
        }

        if (CollectionUtils.isNotEmpty(otherIDCUrl)) {
            return otherIDCUrl.get((startIdx + retryTimes) % otherIDCUrl.size());
        }

        return null;
    }

    public boolean isComplete() {
        return complete.get();
    }

    public void complete() {
        complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
    }

    public void timeout() {
        if (!isComplete() && System.currentTimeMillis() - lastPushTime >= ttl) {
            delayRetry();
        }
    }
}