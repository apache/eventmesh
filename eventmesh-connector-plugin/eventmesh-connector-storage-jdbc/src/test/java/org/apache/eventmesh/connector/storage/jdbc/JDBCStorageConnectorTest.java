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

package org.apache.eventmesh.connector.storage.jdbc;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.data.TopicInfo;
import org.apache.eventmesh.api.exception.OnExceptionContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class JDBCStorageConnectorTest {

	private String topicOne = "test_1";

	private String topicTwo = "test_2";

	private String consumerGroupA = "consumerGroupA";

	private String consumerGroupB = "consumerGroupB";

	private PullRequest oneConsumerGroupA;

	private PullRequest oneConsumerGroupB;

	private PullRequest twoConsumerGroupA;

	private PullRequest twoConsumerGroupB;

	private Set<String> topicSet;

	JDBCStorageConnector connector = new JDBCStorageConnector();

	@Before
	public void init() throws Exception {
		
		Properties properties = new Properties();
		properties.put("dbType", "mysql");
		properties.put("address", "127.0.0.1:3306");
		properties.put("parameter",
				"useSSL=false&useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull");
		properties.put("username", "root");
		properties.put("password", "Ab123123@");
		properties.put("maxActive", "20");
		properties.put("maxWait", "10000");
		connector.init(properties);

		oneConsumerGroupA = new PullRequest();
		oneConsumerGroupA.setConsumerGroupName(consumerGroupA);
		oneConsumerGroupA.setProcessSign("A1");
		oneConsumerGroupA.setTopicName(topicOne);

		oneConsumerGroupB = new PullRequest();
		oneConsumerGroupB.setConsumerGroupName(consumerGroupA);
		oneConsumerGroupB.setProcessSign("A2");
		oneConsumerGroupB.setTopicName(topicOne);

		twoConsumerGroupA = new PullRequest();
		twoConsumerGroupA.setConsumerGroupName(consumerGroupB);
		twoConsumerGroupA.setProcessSign("B1");
		twoConsumerGroupA.setTopicName(topicOne);

		twoConsumerGroupB = new PullRequest();
		twoConsumerGroupB.setConsumerGroupName(consumerGroupB);
		twoConsumerGroupB.setProcessSign("B2");
		twoConsumerGroupB.setTopicName(topicOne);

		topicSet = connector.getTopic();
		this.createTopic(topicOne);
		this.createTopic(topicTwo);
		List<PullRequest> pullRequestList = new ArrayList<>();
		pullRequestList.add(oneConsumerGroupA);
		pullRequestList.add(oneConsumerGroupB);
		pullRequestList.add(twoConsumerGroupA);
		pullRequestList.add(twoConsumerGroupB);
		List<TopicInfo> topicInfoList = connector.geTopicInfos(pullRequestList);
		for (TopicInfo topicInfo : topicInfoList) {
			Assert.assertEquals(Long.valueOf(0), topicInfo.getCurrentId());
		}

	}

	private void createTopic(String topic) throws Exception {
		if (!topicSet.contains("cloud_event_" + topic)) {
			TopicInfo topicInfo = new TopicInfo();
			topicInfo.setTopicName(topic);
			connector.createTopic(topicInfo);
		}
	}

	@Test
	public void testInit() throws Exception {
		Properties properties = new Properties();
		properties.put("url",
				"jdbc:mysql://127.0.0.1:3306/electron?useSSL=false&useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull");
		properties.put("username", "root");
		properties.put("password", "Ab123123@");
		properties.put("maxActive", "20");
		properties.put("maxWait", "10000");
		connector.init(properties);
	}

	@Test
	public void test_publish_and_pull() throws Exception {
		this.publish(topicOne, "0");
		List<CloudEvent> cloudEventList = this.connector.pull(oneConsumerGroupA);
		//Assert.assertNull(cloudEventList);
		cloudEventList = this.connector.pull(oneConsumerGroupA);
		Assert.assertNull(cloudEventList);
		cloudEventList = this.connector.pull(oneConsumerGroupB);
		Assert.assertNull(cloudEventList);
		cloudEventList = this.connector.pull(twoConsumerGroupA);

		this.publish_bach(topicOne);
		this.publish_bach(topicTwo);

		connector.pull(oneConsumerGroupA);
	}

	public void publish_bach(String topic) throws Exception {
		for (int i = 1; i <= 900; i++) {
			this.publish(topic, i + "");
		}
	}

	public void publish(String topic, String id) throws Exception {

		CloudEvent cloudEvent = CloudEventBuilder.v03().withExtension("cloudeventid", id).withId(id).withType("1231").withSource(new URI("")).withSubject(topic).withExtension("id", id)
				.withData(id.getBytes()).build();

		connector.publish(cloudEvent, new SendCallback() {

			@Override
			public void onSuccess(SendResult sendResult) {
				System.out.println("111");
			}

			@Override
			public void onException(OnExceptionContext context) {
				System.out.println("111");
			}
		});
	}

	public void test_reply() throws Exception {
		this.publish_bach(topicOne);
	}
}
