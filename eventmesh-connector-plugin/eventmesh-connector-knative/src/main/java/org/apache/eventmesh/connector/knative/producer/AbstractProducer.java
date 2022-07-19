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

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.producer.Producer;
import org.asynchttpclient.AsyncHttpClient;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public abstract class AbstractProducer implements Producer {

    final Properties properties;
    AsyncHttpClient asyncHttpClient;
    protected final AtomicBoolean started = new AtomicBoolean(false);

    AbstractProducer(final Properties properties) throws IOException {
        this.properties = properties;
        this.asyncHttpClient = asyncHttpClient();
    }

    ConnectorRuntimeException checkProducerException(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            return new ConnectorRuntimeException(String.format("CloudEvent message data does not exist."));
        }
        return  new ConnectorRuntimeException(String.format("Unknown connector runtime exception."));
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }

    public AsyncHttpClient getAsyncHttpClient() {
        return asyncHttpClient;
    }
}
