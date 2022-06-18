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
package org.apache.eventmesh.webhook.api;

import java.util.List;

public interface WebHookConfigOperation {

	/**
	 * 添加配置
	 * 
	 * @return
	 */
	public Integer insertWebHookConfig(WebHookConfig webHookConfig);

	/**
	 * 修改配置
	 * 
	 * @return
	 */
	public Integer updateWebHookConfig(WebHookConfig webHookConfig);

	/**
	 * 删除配置
	 * 
	 * @return
	 */
	public Integer deleteWebHookConfig(WebHookConfig webHookConfig);

	/**
	 * 通过id查询配置
	 * 
	 * @return
	 */
	public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig);

	/**
	 * 通过厂商查询配置
	 */
	public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
			Integer pageSize);
}
