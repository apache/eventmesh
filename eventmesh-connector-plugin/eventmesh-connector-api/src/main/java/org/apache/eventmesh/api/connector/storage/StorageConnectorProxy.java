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

package org.apache.eventmesh.api.connector.storage;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.data.TopicInfo;
import org.apache.eventmesh.api.connector.storage.metadata.RouteHandler;
import org.apache.eventmesh.api.connector.storage.metadata.StorageMetaServcie;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperation;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperationService;
import org.apache.eventmesh.api.connector.storage.reply.RequestReplyInfo;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import io.cloudevents.CloudEvent;
import lombok.Setter;

public class StorageConnectorProxy implements StorageConnector {

    private Map<StorageConnector, String> storageConnectorMap = new ConcurrentHashMap<>();

    private Map<String, StorageConnector> storageConnectorByKeyMap = new ConcurrentHashMap<>();

    private RouteHandler routeHandler = new RouteHandler();

    @Setter
    private ReplyOperationService replyService;

    @Setter
    private StorageMetaServcie storageMetaServcie;

    @Setter
    private Executor executor;

    @Override
    public void start() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void init(Properties properties) throws Exception {
    }

    public  Collection<StorageConnector> getStorageConnectorList(){
    	return this.storageConnectorByKeyMap.values();
    }
    
    public void setConnector(StorageConnector storageConnector, String key) {
    	routeHandler.addStorageConnector(storageConnector);
        storageConnectorMap.put(storageConnector, key);
        storageConnectorByKeyMap.put(key, storageConnector);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                StorageConnectorProxy.this.doPublish(cloudEvent, sendCallback);
            }
        });
    }

    private void doPublish(CloudEvent cloudEvent, SendCallback sendCallback) {
        try {
            StorageConnector storageConnector = routeHandler.select();
            if (storageConnector instanceof StorageConnectorMetedata
                && !storageMetaServcie.isTopic(storageConnector, CloudEventUtils.getTopic(cloudEvent))) {
                TopicInfo topicInfo = new TopicInfo();
                topicInfo.setTopicName(CloudEventUtils.getTopic(cloudEvent));
                StorageConnectorMetedata storageConnectorMetedata = (StorageConnectorMetedata) storageConnector;
                storageConnectorMetedata.createTopic(topicInfo);
            }
            storageConnector.publish(cloudEvent, sendCallback);
        } catch (Exception e) {
            sendCallback.onException(createOnExceptionContext(e, cloudEvent));
        }
    }

    private OnExceptionContext createOnExceptionContext(Exception e, CloudEvent cloudEvent) {
        OnExceptionContext onExceptionContext = new OnExceptionContext();
        onExceptionContext.setException(new ConnectorRuntimeException(e));
        return onExceptionContext;
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback requestReplyCallback, long timeout)
        throws Exception {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                StorageConnectorProxy.this.doRequest(cloudEvent, requestReplyCallback, timeout);
            }
        });

    }

    public void doRequest(CloudEvent cloudEvent, RequestReplyCallback requestReplyCallback, long timeout) {
        try {
            StorageConnector storageConnector = routeHandler.select();
            if(!(storageConnector instanceof ReplyOperation)) {
            	return;
            }
            String key = storageConnectorMap.get(storageConnector);
            CloudEventUtils.setValue(cloudEvent, Constant.STORAGE_CONFIG_ADDRESS, key);
            storageConnector.request(cloudEvent, requestReplyCallback, timeout);
            Long storageId = Long.valueOf( cloudEvent.getExtension(Constant.STORAGE_ID).toString());
            RequestReplyInfo requestReplyInfo = new RequestReplyInfo();
            requestReplyInfo.setStorageId(storageId);
            requestReplyInfo.setTimeOut(System.currentTimeMillis() + timeout);
            requestReplyInfo.setRequestReplyCallback(requestReplyCallback);
            replyService.setRequestReplyInfo((ReplyOperation)storageConnector, cloudEvent.getType(), storageId, requestReplyInfo);
        } catch (Exception e) {
            requestReplyCallback.onException(e);
        }
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                StorageConnectorProxy.this.doUpdateOffset(cloudEvents, context);
            }
        });
    }

    private void doUpdateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        for (CloudEvent cloudEvent : cloudEvents) {
            try {
                StorageConnector storageConnector = storageConnectorByKeyMap
                    .get(CloudEventUtils.getNodeAdress(cloudEvent));
                storageConnector.updateOffset(cloudEvents, context);
            } catch (Exception e) {

            }
        }
    }

    @Override
    public List<CloudEvent> pull(PullRequest pullRequest) {
        return null;
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        StorageConnector storageConnector = storageConnectorByKeyMap.get(CloudEventUtils.getNodeAdress(cloudEvent));
        return storageConnector.reply(cloudEvent, sendCallback);
    }

}
