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
package org.apache.eventmesh.runtime.core.protocol;

import org.apache.eventmesh.runtime.acl.AuthResult;
import org.apache.eventmesh.runtime.acl.AuthTO;
import org.apache.eventmesh.runtime.acl.EventMeshAclServcie;
import org.apache.eventmesh.runtime.core.protocol.EventMeshTraceService.TraceOperation;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshResponse;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;
import org.apache.eventmesh.runtime.core.protocol.context.Context;
import org.apache.eventmesh.runtime.session.AbstractSession;
import org.apache.eventmesh.runtime.session.Session;
import org.apache.eventmesh.runtime.session.SessionSerivce;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j(topic = "message")
public class EventMeshHandlerExecute implements Runnable{

	private EventMeshHandlerInfo  eventMeshHandlerInfo;
	
	private EventMeshAclServcie eventMeshAclServcie;
	
	private Context context;
	
	//private Logger logger;
	
	private Validator validator;
	
	private EventMeshResponse response;
	
	private List<ArrayMetric> arrayMetricList;
	
	private Throwable exception;
	
	private TraceOperation traceOperation;
	
	private Boolean isError = false;
	
	private SessionSerivce sessionSerivce;
	
	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		
		try {
            this.preHandler();
            
            AuthTO authTO = new AuthTO();
            AuthResult authResult = eventMeshAclServcie.validate(authTO);
            
            if(!authResult.isSuccess()) {
            	this.context.sendErrorResponse(null, "");
            	this.isError = true;
            	return;
            }
            
            //context.doInit();
            Object object = context.getObject();
            if(Objects.nonNull(object)) {
	            Set<ConstraintViolation<Object>> constraintViolationSet = validator.validate(object);
	            if(Objects.isNull(constraintViolationSet) || constraintViolationSet.size()>0) {
	            	this.isError = true;
	            	return;
	            }
            }
            if(Objects.equals(context.protocolType(), ProtocolType.HTTP)) {
            	Session session= sessionSerivce.getSession(context);
            	if(Objects.isNull(session)) {
            		session = sessionSerivce.createSession(context);
            		AbstractSession<Object> abstractSession = (AbstractSession<Object>)session;
            		abstractSession.setMetadata(context.getMetadata());
            	}
            }
            // header 对象
            
            
            if(Objects.isNull(eventMeshHandlerInfo.getRateLimiterWrapper())) {
            	eventMeshHandlerInfo.getEventMeshProtocolHandler().handler(context.request(), context);
            }else {
            	RateLimiterWrapper rateLimiterWrapper = eventMeshHandlerInfo.getRateLimiterWrapper();
            	if (rateLimiterWrapper.getRateLimiter().tryAcquire(rateLimiterWrapper.getTimeout(), rateLimiterWrapper.getUnit())){
            		eventMeshHandlerInfo.getEventMeshProtocolHandler().handler(context.request(), context);
            	}
            }
            if (eventMeshHandlerInfo.isAsync()) {
                return;
            }
            this.context.sendResponse();
            this.postHandler();
        } catch (Throwable e) {
            exception = e;          
        }finally {
			if(this.isError || Objects.nonNull(this.exception)) {
				this.error();
			}
		}
		
	}

    private void postHandler() {
    	for( ArrayMetric arrayMetric : arrayMetricList) {
    		arrayMetric.addException(1);
    	}
        if (log.isDebugEnabled()) {
        	log.debug("{}", context);
        }
        if (Objects.isNull(response)) {
           
        }
        this.traceOperation.endTrace(null);
    }

    public void preHandler() {
    	for( ArrayMetric arrayMetric : arrayMetricList) {
    		arrayMetric.addException(1);
    	}
        if (log.isDebugEnabled()) {
        	log.debug("{}", response);
        }
    }

    public void error() {
    	for( ArrayMetric arrayMetric : arrayMetricList) {
    		arrayMetric.addException(1);
    	}
    	log.error(this.exception.getMessage(), this.exception);
        this.traceOperation.exceptionTrace(this.exception, this.context.getObject());
        context.sendErrorResponse(this.exception);
    }
	
}
