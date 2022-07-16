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

import org.apache.eventmesh.connector.knative.cloudevent.impl.KnativeHeaders;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractProducer {

    final Properties properties;
    HttpURLConnection httpUrlConnection;
    protected final AtomicBoolean started = new AtomicBoolean(false);

    AbstractProducer(final Properties properties) throws IOException {
        this.properties = properties;
        URL url = new URL(properties.getProperty("url"));
        this.httpUrlConnection = (HttpURLConnection) url.openConnection();
        httpUrlConnection.setDoOutput(true);
        httpUrlConnection.setDoInput(true);

        // Set HTTP header for CloudEvent:
        this.httpUrlConnection.setRequestProperty(KnativeHeaders.CONTENT_TYPE, properties.getProperty(KnativeHeaders.CONTENT_TYPE));
        this.httpUrlConnection.setRequestProperty(KnativeHeaders.CE_ID, properties.getProperty(KnativeHeaders.CE_ID));
        this.httpUrlConnection.setRequestProperty(KnativeHeaders.CE_SPECVERSION, properties.getProperty(KnativeHeaders.CE_SPECVERSION));
        this.httpUrlConnection.setRequestProperty(KnativeHeaders.CE_TYPE, properties.getProperty(KnativeHeaders.CE_TYPE));
        this.httpUrlConnection.setRequestProperty(KnativeHeaders.CE_SOURCE, properties.getProperty(KnativeHeaders.CE_SOURCE));
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }

    public HttpURLConnection getHttpUrlConnection() {
        return httpUrlConnection;
    }
}
