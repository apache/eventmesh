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

package org.apache.eventmesh.connector.knative.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.knative.cloudevent.KnativeMessageFactory;
import org.apache.eventmesh.connector.knative.cloudevent.impl.KnativeHeaders;
import org.apache.eventmesh.connector.knative.utils.CloudEventUtils;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;

import io.cloudevents.CloudEvent;

public class ProducerImpl extends AbstractProducer {

    public ProducerImpl(final Properties properties) throws IOException {
        super(properties);
    }

    public Properties attributes() {
        return properties;
    }

    public void send(CloudEvent cloudEvent, SendCallback sendCallback) {
        // Set HTTP header, body and send CloudEvent message:
        try {
            ListenableFuture<Response> execute = super.getAsyncHttpClient().preparePost("http://" + this.attributes().getProperty("url"))
                .addHeader(KnativeHeaders.CONTENT_TYPE, cloudEvent.getDataContentType())
                .addHeader(KnativeHeaders.CE_ID, cloudEvent.getId())
                .addHeader(KnativeHeaders.CE_SPECVERSION, String.valueOf(cloudEvent.getSpecVersion()))
                .addHeader(KnativeHeaders.CE_TYPE, cloudEvent.getType())
                .addHeader(KnativeHeaders.CE_SOURCE, String.valueOf(cloudEvent.getSource()))
                .setBody(KnativeMessageFactory.createReader(cloudEvent))
                .execute();

            Response response = execute.get(10, TimeUnit.SECONDS);
            if (response.getStatusCode() == HttpConstants.ResponseStatusCodes.OK_200) {
                sendCallback.onSuccess(CloudEventUtils.convertSendResult(cloudEvent));
                return;
            }
            throw new IllegalStateException("HTTP response code error: " + response.getStatusCode());
        } catch (Exception e) {
            ConnectorRuntimeException onsEx = ProducerImpl.this.checkProducerException(cloudEvent, e);
            OnExceptionContext context = new OnExceptionContext();
            context.setTopic(KnativeMessageFactory.createReader(cloudEvent));
            context.setException(onsEx);
            sendCallback.onException(context);
        }
    }

    public void sendAsync(CloudEvent cloudEvent, SendCallback sendCallback) {
        try {
            this.send(cloudEvent, sendCallback);
        } catch (Exception e) {
            logger.error(String.format("Send cloudevent message Exception, %s", e));
            throw new ConnectorRuntimeException("Send cloudevent message Exception.");
        }
    }

    @Override
    public void init(Properties properties) throws Exception {
        new ProducerImpl(properties);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        this.sendAsync(cloudEvent, sendCallback);
    }

    public void sendOneway(CloudEvent cloudEvent) {
        throw new ConnectorRuntimeException("SendOneWay is not supported");
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new ConnectorRuntimeException("Request is not supported");
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new ConnectorRuntimeException("Reply is not supported");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        throw new ConnectorRuntimeException("CheckTopicExist is not supported");
    }

    @Override
    public void setExtFields() {
        throw new ConnectorRuntimeException("SetExtFields is not supported");
    }

    @Override
    public void start() {
        started.set(true);
    }

    @Override
    public void shutdown() {
        started.set(false);
    }
}
