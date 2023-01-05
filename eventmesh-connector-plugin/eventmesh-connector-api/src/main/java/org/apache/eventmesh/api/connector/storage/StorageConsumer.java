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
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.pull.PullCallbackImpl;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import io.cloudevents.CloudEvent;

public class StorageConsumer implements Consumer {

	private StorageConnector storageOperation;

	private EventListener listener;

	private Properties keyValue;

	private Set<String> topicSet = new HashSet<>();

	@Override
	public boolean isStarted() {
		return storageOperation.isStarted();
	}

	@Override
	public boolean isClosed() {
		return storageOperation.isClosed();
	}

	@Override
	public void start() {
		if (Objects.isNull(listener)) {

		}

		if (topicSet.isEmpty()) {

		}

		StorageConnectorService storageConnectorService = StorageConnectorService.getInstance();
		List<PullRequest> pullRequestList = new ArrayList<PullRequest>(topicSet.size());
		for (String topic : topicSet) {
			PullRequest pullRequest = new PullRequest();
			pullRequest.setTopicName(topic);
			pullRequest.setConsumerGroupName(keyValue.getProperty(Constants.CONSUMER_GROUP));
			PullCallbackImpl pullCallback = new PullCallbackImpl();
			pullCallback.setEventListener(listener);
			pullCallback.setExecutor(storageConnectorService.getExecutor());
			pullRequest.setPullCallback(pullCallback);
			pullRequestList.add(pullRequest);
		}
		storageOperation = storageConnectorService.createConsumerByStorageConnector(this.keyValue, pullRequestList);

	}

	@Override
	public void shutdown() {
		storageOperation.shutdown();
	}

	@Override
	public void init(Properties keyValue) throws Exception {
		this.keyValue = keyValue;
	}

	@Override
	public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
		storageOperation.updateOffset(cloudEvents, context);
	}

	@Override
	public void subscribe(String topic) throws Exception {
		topicSet.add(topic);
	}

	@Override
	public void unsubscribe(String topic) {
		topicSet.remove(topic);
	}

	@Override
	public void registerEventListener(EventListener listener) {
		this.listener = listener;
	}

}
