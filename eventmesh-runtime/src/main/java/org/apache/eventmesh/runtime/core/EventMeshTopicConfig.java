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
package org.apache.eventmesh.runtime.core;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.runtime.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "message")
public class EventMeshTopicConfig {
	
	
	private SubscriptionItem subscriptionItem;
	
	private Queue<Session> sessionList = new ArrayBlockingQueue<>(10);
	
	public EventMeshTopicConfig(SubscriptionItem subscriptionItem) {
		this.subscriptionItem = subscriptionItem;
	}
	public void registerSession(Session session,SubscriptionItem subscriptionItem) {
		if(this.subscriptionItem.equals(subscriptionItem)) {
			sessionList.add(session);
			log.info("session register success，item is {} ， session is {}" , subscriptionItem , session);
		}
		String message = String.format("subscriptionItem errer current : %s , register: %s", this.subscriptionItem,subscriptionItem);
		throw new RuntimeException(message);
		
	}
	
	public void unRegisterSession(Session session ) {
		sessionList.remove(session);
		log.info("session unRegister success， session is {}"  , session);
	}
	
	public List<Session> getSession(){
		return new ArrayList<>(sessionList);
	}
	
}
