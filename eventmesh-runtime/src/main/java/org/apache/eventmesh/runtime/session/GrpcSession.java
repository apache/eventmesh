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

import org.apache.eventmesh.runtime.core.protocol.api.EventMeshRequest;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshResponse;

import java.util.Map;

import io.cloudevents.CloudEvent;
import io.grpc.stub.StreamObserver;

public class GrpcSession extends AbstractSession<StreamObserver<Object>>{

	@Override
	public void downstreamMessage(Map<String, Object> header, CloudEvent event, DownstreamHandler downstreamHandler) {
		try {
			this.before(event);
			this.channel.onNext(null);
			this.channel.onCompleted();
			downstreamHandler.downstreamSuccess();
			this.after(event);
		}catch(Exception e) {
			downstreamHandler.exception(e);
			this.exception(e,event);
		}
		
	}

	@Override
	public void downstreamMessage(EventMeshRequest request, DownstreamHandler downstreamHandler) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void downstream(EventMeshResponse response, DownstreamHandler downstreamHandler) {
		// TODO Auto-generated method stub
		
	}

}
