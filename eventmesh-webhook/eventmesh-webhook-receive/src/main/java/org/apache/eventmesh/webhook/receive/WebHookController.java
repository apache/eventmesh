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
package org.apache.eventmesh.webhook.receive;

import java.util.Map;

import org.apache.eventmesh.webhook.receive.protocol.ProtocolManage;
import org.apache.eventmesh.webhook.receive.storage.WebHookConfigOperationManage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebHookController {
	
	public Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private ProtocolManage protocolManage = new ProtocolManage();
	
	private WebHookConfigOperationManage webHookConfigOperationManage;
	
	public WebHookController() {
		this.webHookConfigOperationManage = new WebHookConfigOperationManage();
	}
	
	
	/**
	 * 1. 通过path获得webhookConfig
	 * 2. 获得对应的厂商的处理对象,并解析协议
	 * 3. 通过WebHookConfig获得cloudEvent 协议对象
	 * 4. WebHookRequest 转换为cloudEvent。
	 * @param path
	 * @param header 需要把请求头信息重写到map里面
	 * @param body
	 */
	public void execute(String path,Map<String,String> header,byte[] body) {
		
	}

}
