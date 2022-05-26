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
package org.apache.eventmesh.webhook.admin;

import java.util.List;

import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

public class FileWebHookConfigOperation implements WebHookConfigOperation {

	public FileWebHookConfigOperation(String filePath) {
	}

	@Override
	public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
			Integer pageSize) {
		// TODO Auto-generated method stub
		return null;
	}

}
