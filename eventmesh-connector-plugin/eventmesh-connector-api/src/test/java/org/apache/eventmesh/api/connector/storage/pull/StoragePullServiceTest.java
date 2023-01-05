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

import org.apache.eventmesh.api.connector.storage.StorageConfig;
import org.apache.eventmesh.api.connector.storage.StorageConnector;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;

public class StoragePullServiceTest {

	private StorageConfig storageConfig;

	private Executor executor;

	private ScheduledExecutorService scheduledExecutor;

	private StoragePullService storagePullService = new StoragePullService();

	private PullRequest pullRequest = new PullRequest();

	@Before
	public void init() throws Exception {
		this.executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 10,
				Runtime.getRuntime().availableProcessors() * 300, 1000 * 60 * 60, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(), new ThreadFactory() {
					AtomicInteger index = new AtomicInteger();

					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "storage-connent-" + index.getAndIncrement());
					}
				});
		this.scheduledExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 10,
				new ThreadFactory() {
					AtomicInteger index = new AtomicInteger();

					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "storage-connent-shceduled-" + index.getAndIncrement());
					}
				});
		storageConfig = new StorageConfig();
		storagePullService.setStorageConfig(storageConfig);
		storagePullService.setExecutor(executor);
		storagePullService.setScheduledExecutor(scheduledExecutor);

		StorageConnector storageConnector = Mockito.mock(StorageConnector.class);
		List<CloudEvent> cloudEventList = new ArrayList<>();
		cloudEventList.add(Mockito.mock(CloudEvent.class));
		Mockito.when(storageConnector.pull(Mockito.any())).thenReturn(null).thenReturn(new ArrayList<>())
				.thenReturn(cloudEventList);
		pullRequest.setStorageConnector(storageConnector);
		pullRequest.setPullCallback(Mockito.mock(PullCallback.class));

	}

	@Test
	public void test_run_thread() {
		this.executor.execute(storagePullService);
		this.storagePullService.executePullRequestImmediately(pullRequest);
		System.out.println(1);
	}
}
