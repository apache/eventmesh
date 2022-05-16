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

import org.apache.eventmesh.webhook.api.WebHookConfig;

public interface ManufacturerProtocol {

	
	public String getManufacturerName();
	
	/**
	 * 如果认证识别，或则协议解析失败，请抛出异常
	 * @param webHookRequest
	 * @param header
	 * @return
	 */
	public void execute(WebHookRequest webHookRequest,WebHookConfig webHookConfig,Map<String, String> header);
}
