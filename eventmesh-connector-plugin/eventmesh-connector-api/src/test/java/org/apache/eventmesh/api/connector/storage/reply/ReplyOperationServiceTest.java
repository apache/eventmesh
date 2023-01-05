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
package org.apache.eventmesh.api.connector.storage.reply;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.connector.storage.CloudEventUtils;
import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class ReplyOperationServiceTest {

	private ReplyOperationService replyOperationService = new ReplyOperationService();

	private List<ReplyOperation> replyOperationList = new ArrayList<>();

	private List<String> topicList = new ArrayList<>();

	private ReplyOperation replyOperation = Mockito.mock(ReplyOperation.class);

	private ReplyOperation twoReplyOperation = Mockito.mock(ReplyOperation.class);

	private RequestReplyCallback requestReplyCallback = Mockito.mock(RequestReplyCallback.class);

	private String topic1 = "topic1";

	private String topic2 = "topic2";

	private String topic3 = "topic3";

	private Map<ReplyOperation, Map<String, Map<Long, RequestReplyInfo>>> replyOperationMap;

	@SuppressWarnings("unchecked")
	@Before
	public void init() throws IllegalArgumentException, IllegalAccessException {
		replyOperationList.add(replyOperation);
		replyOperationList.add(twoReplyOperation);
		topicList.add(topic1);
		topicList.add(topic2);
		topicList.add(topic3);
		Field field = FieldUtils.getField(ReplyOperationService.class, "replyOperationMap", true);
		field.setAccessible(true);
		replyOperationMap = (Map<ReplyOperation, Map<String, Map<Long, RequestReplyInfo>>>) field
				.get(replyOperationService);
	}

	@Test
	public void setRequestReplyInfo_test() {
		for (ReplyOperation replyOperation : replyOperationList) {
			for (String topic : topicList) {
				for (long i = 0; i < 20; i++) {
					replyOperationService.setRequestReplyInfo(replyOperation, topic, i, new RequestReplyInfo());
				}
			}
		}
		for (ReplyOperation replyOperation : replyOperationList) {
			Map<String, Map<Long, RequestReplyInfo>> requestReplyInfoMap = replyOperationMap.get(replyOperation);
			Assert.assertEquals(topicList.size(), requestReplyInfoMap.size());
			for (String topic : topicList) {
				Map<Long, RequestReplyInfo> map = requestReplyInfoMap.get(topic);
				Assert.assertEquals(20, map.size());
			}
		}
	}

	private Map<String, Map<Long, RequestReplyInfo>> createReplyMap(boolean timeout) {
		long time = System.currentTimeMillis();
		Map<String, Map<Long, RequestReplyInfo>> replyMap = new ConcurrentHashMap<>();
		for (String topic : topicList) {
			Map<Long, RequestReplyInfo> requestReplyInfoMap = new ConcurrentHashMap<>();
			replyMap.put(topic, requestReplyInfoMap);
			for (long i = 0; i < 10; i++) {
				RequestReplyInfo requestReplyInfo = new RequestReplyInfo();
				if (timeout) {
					requestReplyInfo.setTimeOut(time - 1000);
				} else {
					requestReplyInfo.setTimeOut(time + 4000);
				}
				requestReplyInfo.setRequestReplyCallback(requestReplyCallback);
				requestReplyInfoMap.put(i, requestReplyInfo);
			}
		}
		return replyMap;
	}

	@Test
	public void checkReply_timeout_result_null() {
		List<ReplyRequest> replyRequests = replyOperationService.checkReply(this.createReplyMap(true));
		Assert.assertTrue(replyRequests.isEmpty());
		Mockito.verify(requestReplyCallback, Mockito.times(30)).onException(Mockito.any());
	}

	@Test
	public void checkReply_timeout_result_ok() {
		List<ReplyRequest> replyRequests = replyOperationService.checkReply(this.createReplyMap(false));
		for (ReplyRequest replyRequest : replyRequests) {
			Assert.assertEquals(replyRequest.getIdList().size(), 10);
		}
	}

	@Test
	public void callback_onSuccess() throws URISyntaxException {
		CloudEvent cloudEvent = CloudEventBuilder.v03().withExtension("cloudeventid", "1").withId("1").withType("1231")
				.withSource(new URI("")).withSubject("1").withExtension("id", "1").withData("1".getBytes()).build();
		String cloudEventData = new String(CloudEventUtils.eventFormat.serialize(cloudEvent), Charset.forName("UTF-8"));
		List<CloudEventInfo> cloudEventList = new ArrayList<>();
		for (String topic : topicList) {
			for (long i = 0; i < 10; i++) {
				CloudEventInfo cloudEventInfo = new CloudEventInfo();
				cloudEventInfo.setCloudEventTopic(topic);
				cloudEventInfo.setCloudEventInfoId(i);
				cloudEventInfo.setCloudEventReplyData(cloudEventData);
				cloudEventList.add(cloudEventInfo);
			}
		}
		Map<String, Map<Long, RequestReplyInfo>> replyMap = this.createReplyMap(false);

		replyOperationService.callback(cloudEventList, replyMap);
	}
}
