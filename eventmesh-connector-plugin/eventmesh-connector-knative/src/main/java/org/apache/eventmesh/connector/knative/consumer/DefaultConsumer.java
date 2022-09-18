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

package org.apache.eventmesh.connector.knative.consumer;

import static org.asynchttpclient.Dsl.asyncHttpClient;

import org.apache.eventmesh.connector.knative.cloudevent.KnativeMessageFactory;
import org.apache.eventmesh.connector.knative.patch.EventMeshMessageListenerConcurrently;

import java.util.concurrent.TimeUnit;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultConsumer {

    public Logger messageLogger = LoggerFactory.getLogger(DefaultConsumer.class);

    AsyncHttpClient asyncHttpClient;
    private EventMeshMessageListenerConcurrently messageListener;

    public DefaultConsumer() throws Exception {
        this.asyncHttpClient = asyncHttpClient();
    }

    public CloudEvent pullMessage(String topic, String subscribeUrl) throws Exception {
        Preconditions.checkNotNull(topic, "Subscribe item cannot be null");
        Preconditions.checkNotNull(subscribeUrl, "SubscribeUrl cannot be null");

        // Get event message via HTTP:
        String responseBody;
        ListenableFuture<Response> execute = asyncHttpClient.prepareGet("http://" + subscribeUrl + "/" + topic).execute();
        Response response = execute.get(10, TimeUnit.SECONDS);

        if (response.getStatusCode() == HttpConstants.ResponseStatusCodes.OK_200) {
            responseBody = response.getResponseBody();
            messageLogger.info(responseBody);

            // Parse HTTP responseBody to CloudEvent message:
            CloudEvent cloudEvent = KnativeMessageFactory.createWriter(topic, response);
            return cloudEvent;
        }
        throw new IllegalStateException("HTTP response code error: " + response.getStatusCode());
    }

    public void registerMessageListener(EventMeshMessageListenerConcurrently messageListener) {
        this.messageListener = messageListener;
    }
}
