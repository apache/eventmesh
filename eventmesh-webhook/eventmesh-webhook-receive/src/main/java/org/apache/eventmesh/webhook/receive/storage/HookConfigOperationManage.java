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
package org.apache.eventmesh.webhook.receive.storage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HookConfigOperationManage implements WebHookConfigOperation {

	public Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private Map<String,WebHookConfig> cacheWebHookConfig = new ConcurrentHashMap<>();
	
	public HookConfigOperationManage() {
		
	}
	
	@Override
	public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
		cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
		return 1;
	}

	@Override
	public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
		cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
		return 1;
	}

	@Override
	public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
		cacheWebHookConfig.remove(webHookConfig.getCallbackPath());
		return 1;
	}

	@Override
	public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
		return null;
	}

	@Override
	public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
			Integer pageSize) {
		return null;
	}

	// 这里写一个方法，实现文件监听
}
