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

import java.util.Properties;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.api.producer.Producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class WebHookMQProducer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected Producer hookMQProducer;

    public WebHookMQProducer(Properties properties,String connectorPluginType) {
        this.hookMQProducer = ConnectorPluginFactory.getMeshMQProducer(connectorPluginType);
        if (hookMQProducer == null) {
            logger.error("can't load the hookMQProducer plugin, please check.");
            throw new RuntimeException("doesn't load the hookMQProducer plugin, please check.");
        }
        try {
        	this.hookMQProducer.init(properties);
        }catch(Exception e) {
        	throw new RuntimeException(e.getMessage() , e);
        }
    }

    public void send(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        hookMQProducer.publish(cloudEvent, sendCallback);
    }

    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout)
        throws Exception {
        hookMQProducer.request(cloudEvent, rrCallback, timeout);
    }

    public boolean reply(final CloudEvent cloudEvent, final SendCallback sendCallback) throws Exception {
        return hookMQProducer.reply(cloudEvent, sendCallback);
    }

    public Producer getHookMQProducer() {
        return hookMQProducer;
    }

}
