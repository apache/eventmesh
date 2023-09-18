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

package org.apache.eventmesh.client.http;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.client.http.producer.EventMeshProtocolProducer;
import org.apache.eventmesh.client.http.producer.RRCallback;
import org.apache.eventmesh.client.http.producer.RRCallbackResponseHandlerAdapter;
import org.apache.eventmesh.client.http.util.HttpUtils;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * AbstractProducerHttpClient
 *
 * @param <T>
 */
public abstract class AbstractProducerHttpClient<T> extends AbstractHttpClient implements EventMeshProtocolProducer<T> {

    public AbstractProducerHttpClient(final EventMeshHttpClientConfig eventMeshHttpClientConfig)
                                                                                                 throws EventMeshException {
        super(eventMeshHttpClientConfig);
    }

    @Override
    public void publish(final T t) throws EventMeshException {
        validateMessage(t);
        final String target = selectEventMesh();
        try {
            final String response = HttpUtils.post(httpClient, target, builderPublishRequestParam(t));
            final EventMeshRetObj ret = JsonUtils.parseObject(response, EventMeshRetObj.class);
            if (Objects.requireNonNull(ret).getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
        } catch (Exception exception) {
            throw new EventMeshException(String.format("Publish message error, target:%s", target), exception);
        }
    }

    @Override
    public T request(final T message, final long timeout) throws EventMeshException {
        validateMessage(message);
        final String target = selectEventMesh();
        try {
            final String response = HttpUtils.post(httpClient, target, builderRequestParam(message, timeout));
            final EventMeshRetObj ret = JsonUtils.parseObject(response, EventMeshRetObj.class);
            if (Objects.requireNonNull(ret).getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
                return transformMessage(ret);
            }
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        } catch (Exception e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }
    }

    @Override
    public void request(final T message, final RRCallback<T> rrCallback, final long timeout) throws EventMeshException {
        validateMessage(message);
        final String target = selectEventMesh();
        final RRCallbackResponseHandlerAdapter<T> adapter = new RRCallbackResponseHandlerAdapter<>(
                message, rrCallback, timeout);
        try {
            HttpUtils.post(httpClient, null, target, builderRequestParam(message, timeout), adapter);
        } catch (IOException e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }

    }

    public abstract RequestParam builderPublishRequestParam(T t);

    public abstract RequestParam builderRequestParam(T t, long timeout);

    public abstract void validateMessage(T t);

    public abstract T transformMessage(EventMeshRetObj retObj);
}
