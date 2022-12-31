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

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.runtime.acl.AclConfig;
import org.apache.eventmesh.runtime.acl.EventMeshAclServcie;
import org.apache.eventmesh.runtime.acl.EventMeshLocalAclService;
import org.apache.eventmesh.runtime.core.EventMeshConsumerManager;
import org.apache.eventmesh.runtime.core.EventMeshEventCloudConfig;
import org.apache.eventmesh.runtime.core.EventMeshEventCloudService;
import org.apache.eventmesh.runtime.core.protocol.EventMeshTraceService.TraceOperation;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshObjectProtocolAsyncHandler;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshObjectProtocolHandler;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshProtocolAsyncHandler;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshProtocolHandler;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshRateLimiter;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshTrace;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshValidate;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolIdentifying;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;
import org.apache.eventmesh.runtime.core.protocol.context.Context;
import org.apache.eventmesh.runtime.core.protocol.processer.GoodbyeHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.HeartBeatHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.HelloHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.MessageAckHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.MessageTransferHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.SubscribeHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.TcpSubscribeHandler;
import org.apache.eventmesh.runtime.core.protocol.processer.UnSubscribeHandler;
import org.apache.eventmesh.runtime.session.SessionSerivce;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;
import com.google.common.util.concurrent.RateLimiter;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "message")
public class EventMeshNetworkProtocolService {
	
	public static EventMeshNetworkProtocolService service;

    private final Map<ProtocolType , Map<String,EventMeshHandlerInfo>> eventMeshHandlerInfoMap = new HashMap<>();
    
    private final Map<String,RateLimiter>  rateLimiterMap = new HashMap<>();
    
    private EventMeshMetricsService eventMeshMetricsService = new EventMeshMetricsService();
    
    private EventMeshTraceService eventMeshTraceService;
    
    private SessionSerivce sessionSerivce;
    
    private EventMeshEventCloudService eventMeshEventCloudService;
    
    private EventMeshConsumerManager eventMeshConsumerManager;
    
    private EventMeshEventCloudConfig eventMeshEventCloudConfig;
    
    private ThreadPoolExecutor threadPoolExecutor;
    
    private ValidatorFactory validatorFactory ;
    
    @Setter
    private Map<String, String> serviceContextData;
    
    @Setter
    private EventMeshAclServcie eventMeshAclServcie;
    {
    	for(ProtocolType type : ProtocolType.values()) {
    		eventMeshHandlerInfoMap.put(type, new ConcurrentHashMap<>());
    	}
    }
    
    public EventMeshNetworkProtocolService() {
    	try {
	    	this.validatorFactory = Validation.buildDefaultValidatorFactory();
	    	
	    	this.threadPoolExecutor = ThreadPoolFactory.createThreadPoolExecutor(100, 300, "message");
	    	
	    	this.sessionSerivce = new SessionSerivce();
	    	this.eventMeshEventCloudConfig = new EventMeshEventCloudConfig();
	    	//ConfigService configService = ConfigService.getInstance();
	    	this.eventMeshTraceService = new EventMeshTraceService(true);
	    	this.eventMeshEventCloudService = new EventMeshEventCloudService(eventMeshEventCloudConfig);
	    	this.eventMeshEventCloudService.setEventMeshConsumerManager(eventMeshConsumerManager);
	    	this.eventMeshConsumerManager = new EventMeshConsumerManager();
	    	this.eventMeshConsumerManager.setSessionSerivce(sessionSerivce);
	    	this.eventMeshConsumerManager.setEventMeshEventCloudConfig(eventMeshEventCloudConfig);
	    	this.eventMeshConsumerManager.setEventMeshEventCloudService(eventMeshEventCloudService);
	    	AclConfig aclConfig = new AclConfig();
	    	this.eventMeshAclServcie =  new EventMeshLocalAclService(aclConfig);
	    	//this.eventMeshEventCloudService = new EventMeshEventCloudService(configService.getConfig(EventMeshEventCloudConfig.class));
	    	//this.eventMeshAclServcie =  new EventMeshLocalAclService(configService.getConfig(AclConfig.class));;
	    	this.register();
    	}catch(Exception e) {
    		throw new RuntimeException(e.getMessage() , e);
    	}
    }
    

    public void init() {
        
    }

    public void register(EventMeshProtocolHandler handler, ThreadPoolExecutor threadPoolExecutor) {
    	EventMeshHandlerInfo handlerInfo = new EventMeshHandlerInfo();
    	if(Objects.isNull(threadPoolExecutor)) {
    		threadPoolExecutor = this.threadPoolExecutor;
    	}
    	handlerInfo.setThreadPoolExecutor(threadPoolExecutor);
    	handlerInfo.setEventMeshProtocolHandler(handler);
    	Class<?> clazz = handler.getClass();
    	
    	EventMeshValidate[] eventMeshValidateArray = clazz.getAnnotationsByType(EventMeshValidate.class);
    	
    	for(EventMeshValidate validate : eventMeshValidateArray) {
    		validate.fieldName();
    	}
    	
    	ProtocolIdentifying protocolIdentifying = clazz.getAnnotation(ProtocolIdentifying.class);
    	
    	if(Objects.isNull(protocolIdentifying)) {
    		//  todo errer
    	}
    	
    	EventMeshRateLimiter eventMeshRateLimiter = clazz.getAnnotation(EventMeshRateLimiter.class);
    	if(Objects.nonNull(eventMeshRateLimiter)) {
    		RateLimiter rateLimiter = rateLimiterMap.get(eventMeshRateLimiter.name());
    		if(Objects.nonNull(rateLimiter)) {
    			rateLimiter = RateLimiter.create(eventMeshRateLimiter.permitsPerSecond());
    			rateLimiterMap.put(eventMeshRateLimiter.name(), rateLimiter);
    		}
    		RateLimiterWrapper rateLimiterWrapper = new RateLimiterWrapper();
    		rateLimiterWrapper.setRateLimiter(rateLimiter);
    		rateLimiterWrapper.setTimeout(eventMeshRateLimiter.timeout());
    		rateLimiterWrapper.setUnit(eventMeshRateLimiter.unit());
    		handlerInfo.setRateLimiterWrapper(rateLimiterWrapper);
    	}
    	
    	EventMeshTrace eventMeshTrace = clazz.getAnnotation(EventMeshTrace.class);
    	if(Objects.nonNull(eventMeshTrace)) {
    		
    	}else {
    		
    	}
    	
    	if(handler instanceof EventMeshProtocolAsyncHandler || handler instanceof EventMeshObjectProtocolAsyncHandler) {
    		handlerInfo.setAsync(true);
    	}else {
    		
    	}
    	
    	if(handler instanceof EventMeshObjectProtocolHandler || handler instanceof EventMeshObjectProtocolAsyncHandler) {
    		Type genType = clazz.getGenericInterfaces()[0];
    		ParameterizedType parameterizedType = (ParameterizedType) genType;
    		handlerInfo.setClazz((Class<?>) parameterizedType.getActualTypeArguments()[0]);
    	}
    	
    	for(String inentifying : protocolIdentifying.tcp()) {
    		this.register(ProtocolType.TCP, handlerInfo, inentifying);
    	}
    	
    	for(String inentifying : protocolIdentifying.http()) {
    		this.register(ProtocolType.HTTP, handlerInfo, inentifying);
    	}
    	
    	for(String inentifying : protocolIdentifying.grpc()) {
    		this.register(ProtocolType.GRPC, handlerInfo, inentifying);
    	}
    	
    	ArrayMetric arrayMetric = eventMeshMetricsService.getArrayMetricByInterfaces(handler.getClass().getName());
    	handlerInfo.setArrayMetric(arrayMetric);
    }
    
    public boolean isUser() {
    	return true;
    }
    
    private void register() {
    	GoodbyeHandler goodbyeHandler = new GoodbyeHandler();
    	goodbyeHandler.setSessionSerivce(sessionSerivce);
    	this.register(goodbyeHandler, null);
    	
    	HeartBeatHandler heartBeatHandler = new HeartBeatHandler();
    	heartBeatHandler.setSessionSerivce(sessionSerivce);
    	this.register(heartBeatHandler, null);
    	
    	HelloHandler helloHandler = new HelloHandler();
    	helloHandler.setSessionSerivce(sessionSerivce);
    	helloHandler.setEventMeshAclServcie(eventMeshAclServcie);
    	helloHandler.setEventMeshConsumerManager(eventMeshConsumerManager);
    	this.register(helloHandler, null);
    	
    	MessageAckHandler messageAckHandler = new MessageAckHandler();
    	messageAckHandler.setEventMeshEventCloudService(eventMeshEventCloudService);
    	this.register(messageAckHandler, null);
    	
    	SubscribeHandler subscribeHandler = new SubscribeHandler();
    	subscribeHandler.setEventMeshConsumerManager(eventMeshConsumerManager);
    	this.register(subscribeHandler, null);
    	
    	TcpSubscribeHandler tcpSubscribeHandler = new TcpSubscribeHandler();
    	tcpSubscribeHandler.setEventMeshConsumerManager(eventMeshConsumerManager);
    	this.register(tcpSubscribeHandler, null);
    	
    	UnSubscribeHandler unSubscribeHandler = new UnSubscribeHandler();
    	unSubscribeHandler.setEventMeshConsumerManager(eventMeshConsumerManager);
    	this.register(unSubscribeHandler, null);
    	
    	MessageTransferHandler messageTransferHandler = new MessageTransferHandler();
    	messageTransferHandler.setEventMeshEventCloudConfig(eventMeshEventCloudConfig);
    	messageTransferHandler.setEventMeshEventCloudService(eventMeshEventCloudService);
    	this.register(messageTransferHandler, null);
    }
    
    private void register(ProtocolType protocolType , EventMeshHandlerInfo handlerInfo, String inentifying) {
    	eventMeshHandlerInfoMap.get(protocolType).put(inentifying, handlerInfo);
    }
    
    /**
     * @param httpRequest
     */
    public void handler(RpcContext rpcContext) {

    	Context context = (Context)rpcContext;
    	
    	EventMeshHandlerExecute execute = new EventMeshHandlerExecute();
    	try {
    		context.setServiceContextData(this.serviceContextData);
    		context.doInit();
    		TraceOperation traceOperation = eventMeshTraceService.getTraceOperation(rpcContext);
    		context.setTraceOperation(traceOperation);
    		execute.setTraceOperation(traceOperation);
    		
    		List<ArrayMetric>  arrayMetricList = new ArrayList<>(4);
    		arrayMetricList.add(eventMeshMetricsService.getServiceArrayMetric());
    		ArrayMetric protocolArrayMetric = eventMeshMetricsService.getArrayMetricByProtocol(rpcContext.protocolType());
    		arrayMetricList.add(protocolArrayMetric);
    		
    		ArrayMetric sessionArrayMetric = eventMeshMetricsService.getArrayMetricBySession(rpcContext);
    		arrayMetricList.add(sessionArrayMetric);    		
    		EventMeshHandlerInfo eventMeshHandlerInfo = eventMeshHandlerInfoMap.get(rpcContext.protocolType()).get(rpcContext.identifying());
    		if(Objects.isNull(eventMeshHandlerInfo) && Objects.equals(rpcContext.protocolType(), ProtocolType.HTTP)) {
    			// TODO http prefix
    		}
    		if(Objects.isNull(eventMeshHandlerInfo)) {
    			String message = String.format("protocol is %s , identifying is %s   handler not existence",  rpcContext.protocolType() , rpcContext.identifying());
    			log.warn( message );
    			rpcContext.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR,message);
    			eventMeshMetricsService.getServiceArrayMetric().addPass(1);
    			eventMeshMetricsService.getServiceArrayMetric().addException(1);
    			protocolArrayMetric.addPass(1);
    			protocolArrayMetric.addException(1);
    			sessionArrayMetric.addPass(1);
    			sessionArrayMetric.addException(1);
    			return;
    		}
    		
    		ArrayMetric interfacesArrayMetric = eventMeshMetricsService.getArrayMetricByInterfaces(eventMeshHandlerInfo.getClass().getSimpleName());
    		arrayMetricList.add(interfacesArrayMetric);
    		context.setArrayMetric(eventMeshHandlerInfo.getArrayMetric());
    		context.setClazz(eventMeshHandlerInfo.getClazz());
    		execute.setValidator(this.validatorFactory.getValidator());
    		execute.setArrayMetricList(arrayMetricList);
    		execute.setEventMeshAclServcie(eventMeshAclServcie);
	    	execute.setEventMeshHandlerInfo(eventMeshHandlerInfo);
	    	execute.setContext(context);
	    	execute.setSessionSerivce(this.sessionSerivce);
	    	AsyncSendDridging asyncSendDridging = new AsyncSendDridging();
	    	context.setAsyncSendDridging(asyncSendDridging);
	    	eventMeshHandlerInfo.getThreadPoolExecutor().execute(execute);
    	}catch(Exception e) {
    		log.error(e.getMessage() ,e);
    		rpcContext.sendErrorResponse(e);
    	}
    }
	
}
