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

package org.apache.eventmesh.connector.pulsar.producer;

import org.apache.eventmesh.api.exception.ConnectorRuntimeException;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;


import io.cloudevents.CloudEvent;

public abstract class AbstractProducer {

    protected final AtomicBoolean started = new AtomicBoolean(false);
    final Properties properties;

    AbstractProducer(final Properties properties) {
        this.properties = properties;
    }

    public Properties properties() {
        return this.properties;
    }

    ConnectorRuntimeException checkProducerException(CloudEvent cloudEvent, Throwable e) {
        if (cloudEvent.getData() == null) {
            return new ConnectorRuntimeException(String.format("CloudEvent message data does not exist.", e));
        }
        return new ConnectorRuntimeException(String.format("Unknown connector runtime exception.", e));
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }
}
