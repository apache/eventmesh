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

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.connector.storage.metadata.RouteHandler;
import org.apache.eventmesh.api.connector.storage.metadata.StorageMetaServcie;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperation;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperationService;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class StorageConnectorProxyTest {

	private StorageConnectorProxy storageConnectorProxy = new StorageConnectorProxy();

	private RouteHandler routeHandler = new RouteHandler();

	private StorageConnector one = Mockito.mock(StorageConnectorMetedataAndConnector.class);

	private StorageConnector two = Mockito.mock(StorageConnectorMetedataAndConnector.class);

	private String key = "127.0.0.1";

	private Executor executor;

	private StorageMetaServcie storageMetaServcie = Mockito.mock(StorageMetaServcie.class);

	private ReplyOperationService replyOperationService = Mockito.mock(ReplyOperationService.class);
	
	CloudEvent cloudEvent;

	@Before
	public void init() throws URISyntaxException {
		this.executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 10,
				Runtime.getRuntime().availableProcessors() * 300, 1000 * 60 * 60, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(), new ThreadFactory() {
					AtomicInteger index = new AtomicInteger();

					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "storage-connent-" + index.getAndIncrement());
					}
				});
		storageConnectorProxy.setExecutor(executor);
		storageConnectorProxy.setStorageMetaServcie(storageMetaServcie);
		storageConnectorProxy.setReplyService(replyOperationService);
		cloudEvent = CloudEventBuilder.v03().withExtension("cloudeventid", "1").withId("1").withType("1231")
		.withSource(new URI("")).withSubject("1").withExtension(Constant.STORAGE_ID, "1").withData("1".getBytes()).build();
	}

	@Test
	public void test_routehandler() {
		routeHandler.addStorageConnector(one);
		routeHandler.addStorageConnector(two);
		for (int i = 0; i < 100; i++) {
			StorageConnector select = routeHandler.select();
			StorageConnector check = i % 2 == 0 ? one : two;
			Assert.assertEquals(select, check);
		}
	}

	@Test
	public void tst_setConnector() {
		storageConnectorProxy.setConnector(one, key);
	}

	@Test
	public void test_publish() throws Exception {
		storageConnectorProxy.setConnector(one, key);

		Mockito.when(storageMetaServcie.isTopic(Mockito.any(), Mockito.anyString())).thenReturn(false).thenReturn(true).thenThrow(RuntimeException.class);
		SendCallback sendCallback = Mockito.mock(SendCallback.class);
		storageConnectorProxy.publish(cloudEvent, sendCallback);
		storageConnectorProxy.publish(cloudEvent, sendCallback);
		storageConnectorProxy.publish(cloudEvent, sendCallback);
		Thread.sleep(10);
		Mockito.verify(storageMetaServcie, Mockito.times(3)).isTopic(Mockito.any(), Mockito.any());
		Mockito.verify(sendCallback, Mockito.atLeastOnce()).onException(Mockito.any());
	}

	@Test
	public void test_request() throws Exception {
		storageConnectorProxy.setConnector(one, key);
		RequestReplyCallback requestReplyCallback = Mockito.mock(RequestReplyCallback.class);
		storageConnectorProxy.request(cloudEvent, requestReplyCallback, 1000);
		Thread.sleep(10);
	}
	
	@Test
	public void test() throws UnsupportedEncodingException {
		String ddd = URLEncoder.encode("EventMeshTest/consumerGroup-_");
		System.out.println(ddd);
	}
	
	public interface StorageConnectorMetedataAndConnector extends StorageConnectorMetedata,StorageConnector,ReplyOperation{
		
	}
}
