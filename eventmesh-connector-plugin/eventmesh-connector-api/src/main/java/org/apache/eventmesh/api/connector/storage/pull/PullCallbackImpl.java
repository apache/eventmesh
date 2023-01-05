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

package org.apache.eventmesh.api.connector.storage.pull;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.Executor;

import io.cloudevents.CloudEvent;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "message")
public class PullCallbackImpl implements PullCallback {

    @Setter
    private EventListener eventListener;

    @Setter
    private Executor executor;

    @Override
    public void onSuccess(PullRequest pullRequest, List<CloudEvent> cloudEvents) {
        try {
            if (CollectionUtils.isEmpty(cloudEvents)) {
                return;
            }
            pullRequest.getStock().getAndUpdate(value -> value + cloudEvents.size());
            for (CloudEvent cloudEvent : cloudEvents) {
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            StorageAbstractContext abstractContext = new StorageAbstractContext();
                            StorageAsyncConsumeContext context = new StorageAsyncConsumeContext();
                            context.setAbstractContext(abstractContext);
                            context.setStorageConnector(pullRequest.getStorageConnector());
                            eventListener.consume(cloudEvent, context);
                            pullRequest.getStock().decrementAndGet();
                        } catch (Exception e) {
							log.error(e.getMessage(),e);
                        }
                    }
                });

            }

        } catch (Exception e) {
        	log.error(e.getMessage(),e);
        }
    }
}
