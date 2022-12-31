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
package org.apache.eventmesh.runtime.core.protocol.processer;

import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.acl.AuthResult;
import org.apache.eventmesh.runtime.acl.AuthTO;
import org.apache.eventmesh.runtime.acl.EventMeshAclServcie;
import org.apache.eventmesh.runtime.core.EventMeshConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshObjectProtocolHandler;
import org.apache.eventmesh.runtime.core.protocol.api.Metadata;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolIdentifying;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;
import org.apache.eventmesh.runtime.session.AbstractSession;
import org.apache.eventmesh.runtime.session.Session;
import org.apache.eventmesh.runtime.session.SessionSerivce;

import lombok.Setter;

@ProtocolIdentifying(tcp= {"HELLO_REQUEST"})
public class HelloHandler implements EventMeshObjectProtocolHandler<UserAgent>{

	@Setter
	private SessionSerivce sessionSerivce;
	
	@Setter
	private EventMeshAclServcie  eventMeshAclServcie;
	
	@Setter
	private EventMeshConsumerManager eventMeshConsumerManager;
	
	@SuppressWarnings("unchecked")
	@Override
	public void handler(UserAgent userAgent, RpcContext rpcContext) throws Exception {
		
		AuthTO authTO = new AuthTO();
		authTO.setAccount(userAgent.getUsername());
		authTO.setPassword(userAgent.getPassword());
		AuthResult authResult = eventMeshAclServcie.auth(authTO);
		if(!authResult.isSuccess()) {
			log.warn("acl fail , acl info is {}", authTO);
			rpcContext.sendErrorResponse();
			return;
		}
		
		Session session = sessionSerivce.createSession(rpcContext);
		AbstractSession<Object> abstractSession = (AbstractSession<Object>)session;
		abstractSession.setMetadata(this.createMetadata(userAgent));
	}
	
	private Metadata createMetadata(UserAgent userAgent) {
		Metadata metadata = new Metadata();
		metadata.setPasswd(userAgent.getPassword());
		metadata.setUsername(userAgent.getUsername());
		metadata.setEnv(userAgent.getEnv());
		metadata.setIdc(userAgent.getIdc());
		metadata.setIp(userAgent.getHost());
		metadata.setConsumerGroup(userAgent.getGroup());
		return metadata;
	}
	
}
