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

package org.apache.eventmesh.api.connector.storage.data;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.connector.storage.StorageConnector;
import org.apache.eventmesh.api.connector.storage.pull.PullCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Data;

@Data
public class PullRequest {

	private static final AtomicLong INCREASING_ID = new AtomicLong();

	private long id = INCREASING_ID.incrementAndGet();

	private String topicName;

	private String consumerGroupName;

	private String nextId = "0";

	private String processSign;

	private StorageConnector storageConnector;

	private AtomicBoolean isEliminate = new AtomicBoolean(true);

	private AtomicInteger stock = new AtomicInteger();

	private PullCallback pullCallback;

	private List<PullRequest> pullRequests;

	private Map<String, PullRequest> topicAndPullRequests;

	public synchronized void setPullRequests(List<PullRequest> pullRequests) {
		this.pullRequests = pullRequests;
		this.topicAndPullRequests = null;
	}

	public List<PullRequest> getPullRequests() {
		List<PullRequest> pullRequests = this.pullRequests;
		return pullRequests;
	}

	public Map<String, PullRequest> getTopicAndPullRequests() {
		if (Objects.isNull(this.topicAndPullRequests)) {
			Map<String, PullRequest> map = new HashMap<>();
			for (PullRequest pullRequest : pullRequests) {
				map.put(pullRequest.getTopicName(), pullRequest);
			}
			this.topicAndPullRequests = map;
		}
		return this.topicAndPullRequests;
	}
}
