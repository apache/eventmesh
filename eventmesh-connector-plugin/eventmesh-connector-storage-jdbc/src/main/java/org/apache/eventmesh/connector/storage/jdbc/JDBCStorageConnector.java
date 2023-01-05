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

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.connector.storage.CloudEventUtils;
import org.apache.eventmesh.api.connector.storage.Constant;
import org.apache.eventmesh.api.connector.storage.StorageConnector;
import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperation;
import org.apache.eventmesh.api.connector.storage.reply.ReplyRequest;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class JDBCStorageConnector extends AbstractJDBCStorageConnectorMetadata
		implements StorageConnector, ReplyOperation {

	public void init(Properties properties) throws Exception {
		super.init(properties);
	}

	@Override
	public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
		String topic = CloudEventUtils.getTableName(cloudEvent);
		String sql = this.cloudEventSQLOperation.insertCloudEventSQL(topic);
		Long id = this.execute(sql, CloudEventUtils.getParameterToCloudEvent(cloudEvent), true);
		CloudEventUtils.setValue(cloudEvent, Constant.STORAGE_ID, id.toString());
	}

	@Override
	public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
		this.publish(cloudEvent, null);
	}

	@Override
	public List<CloudEvent> pull(PullRequest pullRequest) throws Exception {
		String locationEventSQL = this.cloudEventSQLOperation.locationEventSQL(pullRequest.getTopicName(),
				CloudEventUtils.checkConsumerGroupName(pullRequest.getConsumerGroupName()), pullRequest.getProcessSign(), CloudEventUtils.checkConsumerGroupName(pullRequest.getConsumerGroupName()));
		List<Object> parameter = new ArrayList<>();
		parameter.add(pullRequest.getNextId());
		long num = this.execute(locationEventSQL, parameter);
		if (num == 0) {
			return null;
		}
		String queryLocationEventSQL = this.cloudEventSQLOperation.queryLocationEventSQL(pullRequest.getTopicName(),
				CloudEventUtils.checkConsumerGroupName(pullRequest.getConsumerGroupName()), pullRequest.getProcessSign());
		List<CloudEventInfo> cloudEventInfoList = this.query(queryLocationEventSQL, parameter,
				ResultSetTransformUtils::transformCloudEvent);

		List<CloudEvent> cloudEventList = new ArrayList<>();
		if (cloudEventInfoList.size() != 0) {
			for (CloudEventInfo cloudEventInfo : cloudEventInfoList) {
				CloudEvent cloudEvent = CloudEventUtils.eventFormat
						.deserialize(cloudEventInfo.getCloudEventData().getBytes(Charset.forName("UTF-8")));
				cloudEventList.add(cloudEvent);
			}
		}
		pullRequest.setNextId(cloudEventInfoList.get(cloudEventInfoList.size() - 1).getCloudEventInfoId().toString());
		return cloudEventList;
	}

	@Override
	public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
		List<Object> parameterList = new ArrayList<>(cloudEvents.size());
		for (CloudEvent cloudEvent : cloudEvents) {
			try {
				String topic = CloudEventUtils.getTableName(cloudEvent);
				String sql = this.cloudEventSQLOperation.updateCloudEventOffsetSQL(topic);
				parameterList.add(cloudEvent.getExtension("cloudEventInfoId"));
				long i = this.execute(sql, parameterList);
				if (i != cloudEvents.size()) {
					messageLogger.warn("");
				}
				parameterList.clear();
			} catch (Exception e) {
				messageLogger.error(e.getMessage(), e);
			}
		}
	}

	@Override
	public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
		String sql = this.cloudEventSQLOperation.updateCloudEventReplySQL(CloudEventUtils.getTopic(cloudEvent));
		List<Object> parameterList = new ArrayList<>();
		parameterList.add(CloudEventUtils.serializeReplyData(cloudEvent));
		parameterList.add(CloudEventUtils.getId(cloudEvent));
		this.execute(sql, parameterList, true);
		return true;
	}

	@Override
	public void start() {

	}

	@Override
	public void shutdown() {
		druidDataSource.close();
	}

	@Override
	public List<CloudEventInfo> queryReplyCloudEvent(ReplyRequest replyRequest) throws Exception {
		List<Object> parameter = new ArrayList<>();
		StringBuffer stringBuffer = new StringBuffer();
		for (int i = 1; i < replyRequest.getIdList().size(); i++) {
			stringBuffer.append('?');
			if (i++ != replyRequest.getIdList().size()) {
				stringBuffer.append(',');
			}
		}
		String sql = this.cloudEventSQLOperation.selectCloudEventByReplySQL(replyRequest.getTopic(),
				stringBuffer.toString());
		return this.query(sql, parameter, ResultSetTransformUtils::transformCloudEvent);
	}
}
