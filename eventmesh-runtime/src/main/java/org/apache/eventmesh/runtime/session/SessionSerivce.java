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
package org.apache.eventmesh.runtime.session;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.runtime.core.protocol.EventMeshMetricsService;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;
import org.apache.eventmesh.runtime.core.protocol.context.GrpcRpcContext;
import org.apache.eventmesh.runtime.core.protocol.context.TcpRpcContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionSerivce {

	private Map<String, AbstractSession<Object>> sessionMap = new ConcurrentHashMap<>();

	private Map<String/* consumerGroup */, Map<String/* topic */, SubscribeInfo>> subscribeMap = new ConcurrentHashMap<>();

	private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

	private EventMeshMetricsService eventMeshMetricsService = new EventMeshMetricsService();
	
	public SessionSerivce() {
		scheduledExecutorService.scheduleAtFixedRate(this::run, 1, 10, TimeUnit.SECONDS);
	}

	private void run() {
		long currentTime = System.currentTimeMillis();
		for ( Entry<String, AbstractSession<Object>> entry : sessionMap.entrySet()) {
			AbstractSession<Object> abstractSession = (AbstractSession<Object>) entry.getValue();
			if (currentTime - abstractSession.getLastHeartbeatTime() > 70000) {
				abstractSession.close();
				log.warn("session timeout , time out is {} , sessionId is{}",currentTime - abstractSession.getLastHeartbeatTime() ,entry.getKey() );
				sessionMap.remove(entry.getKey());
			}
		}
	}

	public void hearBeat(RpcContext context) {
		AbstractSession<Object> session = (AbstractSession<Object>) sessionMap.get(context.sessionId());
		if(Objects.isNull(session)) {
			log.warn("session is nothingness ,session id is {}", context.sessionId());
			return;
		}
		session.heartbeat();

	}

	@SuppressWarnings("unchecked")
	public Session createSession(RpcContext context) {
		AbstractSession<?> session = null;
		if (Objects.equals(context.protocolType(), ProtocolType.TCP)) {
			TcpSession tcpSession = new TcpSession();
			TcpRpcContext rpcContext = (TcpRpcContext) context;
			tcpSession.setChannel(rpcContext.getChannle());
			session = tcpSession;
		} else if (Objects.equals(context.protocolType(), ProtocolType.GRPC)) {
			GrpcSession grpcSession = new GrpcSession();
			GrpcRpcContext rpcContext = (GrpcRpcContext) context;
			grpcSession.setChannel(rpcContext.getEmitter());
			session = grpcSession;
		} else if (Objects.equals(context.protocolType(), ProtocolType.HTTP)) {
			session = new HttpSession();
		}
		AbstractSession<Object> abstractSession = (AbstractSession<Object>) session;

		List<ArrayMetric> arrayMetricList = new ArrayList<>();
		arrayMetricList.add(eventMeshMetricsService.getServiceArrayMetric());
		arrayMetricList.add(eventMeshMetricsService.getArrayMetricByProtocol(context.protocolType()));
		arrayMetricList.add(eventMeshMetricsService.getArrayMetricBySession(context));
		abstractSession.setArrayMetricList(arrayMetricList);
		sessionMap.put(context.sessionId(), (AbstractSession<Object>)session);
		return session;
	}

	public void deleteSession(RpcContext context) {
		AbstractSession<Object> session = sessionMap.remove(context.sessionId());
		session.close();
		this.unSubscribe(context);

	}

	@SuppressWarnings("unchecked")
	public void subscribe(RpcContext context, SubscribeRequestBody subscribeRequestBody) {
		Session session = sessionMap.get(context.sessionId());
		Map<String, SubscribeInfo> sessionByTopic = subscribeMap
				.computeIfAbsent(subscribeRequestBody.getConsumerGroup(), key -> new ConcurrentHashMap<>());
		Map<String, SubscriptionItem> subscriptionItemMap = new HashMap<String, SubscriptionItem>();
		for (SubscriptionItem topic : subscribeRequestBody.getTopics()) {
			SubscribeInfo subscribeInfo = sessionByTopic.computeIfAbsent(topic.getTopic(), key -> new SubscribeInfo());
			synchronized (subscribeInfo) {
				subscriptionItemMap.put(topic.getTopic(), topic);
				if (Objects.equals(topic.getMode(), SubscriptionMode.CLUSTERING)) {
					subscribeInfo.getQueue().remove(session);
					subscribeInfo.getQueue().add(session);
				} else {
					subscribeInfo.getBroadcast().remove(session);
					subscribeInfo.getBroadcast().add(session);
				}
				if(Objects.isNull(subscribeInfo.getArrayMetric())) {
					subscribeInfo.setArrayMetric(eventMeshMetricsService.getArrayMetricByInterfaces(topic.getTopic()));
				}
			}
		}
		AbstractSession<Object> abstractSession = ((AbstractSession<Object>) session);
		abstractSession.setSubscriptionItemMap(subscriptionItemMap);
		abstractSession.setSessionByTopic(sessionByTopic);
		abstractSession.setSubscribeRequestBody(subscribeRequestBody);
		abstractSession.getArrayMetricList().add(eventMeshMetricsService.getArrayMetricByConsumerGroup(subscribeRequestBody.getConsumerGroup()));
	}

	public Session getSession(RpcContext context) {
		return sessionMap.get(context.sessionId());
	}

	public void unSubscribe(RpcContext context) {
		Session session = sessionMap.get(context.sessionId());
		this.unSubscribe(session);
	}

	@SuppressWarnings("unchecked")
	private void unSubscribe(Session session) {
		SubscribeRequestBody subscribeRequestBody = ((AbstractSession<Object>) session).getSubscribeRequestBody();
		Map<String, SubscribeInfo> subscribeInfoMap = subscribeMap.get(subscribeRequestBody.getConsumerGroup());
		for (SubscribeInfo subscribeInfo : subscribeInfoMap.values()) {
			synchronized (subscribeInfo) {
				subscribeInfo.getQueue().remove(session);
				subscribeInfo.getBroadcast().remove(session);
				session.unSubscribe();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public List<Session> getSession(String consumerGroup, String topic, SubscriptionMode subscriptionMode) {
		Map<String, SubscribeInfo> sessionByTopic = subscribeMap.get(consumerGroup);
		if (Objects.isNull(sessionByTopic)) {
			return null;
		}

		SubscribeInfo subscribeInfo = sessionByTopic.get(topic);
		if (Objects.isNull(subscribeInfo)) {
			return null;
		}

		if (subscribeInfo.getQueue().isEmpty()) {
			return null;
		}

		boolean isClusteing = subscriptionMode == SubscriptionMode.BROADCASTING;

		List<Session> sessionList = new ArrayList<>(isClusteing ? 1 : subscribeInfo.getQueue().size());
		if (isClusteing) {
			sessionList.addAll(subscribeInfo.getBroadcast());
		} else {
			for (Session session : subscribeInfo.getQueue()) {
				AbstractSession<Object> abstractSession = (AbstractSession<Object>) session;
				if (!abstractSession.isClose()) {
					sessionList.add(session);
				}
			}
		}
		return sessionList.isEmpty() ? null : sessionList;
	}

	@Data
	static class SubscribeInfo {

		private ArrayBlockingQueue<Session> queue = new ArrayBlockingQueue<>(16);

		private ArrayBlockingQueue<Session> broadcast = new ArrayBlockingQueue<>(16);

		private SubscriptionItem subscriptionItem;
		
		private ArrayMetric arrayMetric;
	}

}
