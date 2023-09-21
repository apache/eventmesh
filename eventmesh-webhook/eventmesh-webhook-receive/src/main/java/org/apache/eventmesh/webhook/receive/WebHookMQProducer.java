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

package org.apache.eventmesh.webhook.receive;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.factory.StoragePluginFactory;
import org.apache.eventmesh.api.producer.Producer;

import java.util.Objects;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class WebHookMQProducer {

    private transient Producer hookMQProducer;

    public WebHookMQProducer(final Properties properties, String storagePluginType) throws Exception {
        this.hookMQProducer = StoragePluginFactory.getMeshMQProducer(storagePluginType);
        Objects.requireNonNull(hookMQProducer, "doesn't load the hookMQProducer plugin, please check.");

        this.hookMQProducer.init(properties);
    }

    public void send(final CloudEvent cloudEvent, final SendCallback sendCallback) throws Exception {
        Objects.requireNonNull(cloudEvent, "cloudEvent can not be null");

        hookMQProducer.publish(cloudEvent, sendCallback);
    }

    public void request(final CloudEvent cloudEvent, final RequestReplyCallback rrCallback, final long timeout)
        throws Exception {
        Objects.requireNonNull(cloudEvent, "cloudEvent can not be null");

        hookMQProducer.request(cloudEvent, rrCallback, timeout);
    }

    public boolean reply(final CloudEvent cloudEvent, final SendCallback sendCallback) throws Exception {
        Objects.requireNonNull(cloudEvent, "cloudEvent can not be null");

        return hookMQProducer.reply(cloudEvent, sendCallback);
    }

    public Producer getHookMQProducer() {
        return hookMQProducer;
    }

}
