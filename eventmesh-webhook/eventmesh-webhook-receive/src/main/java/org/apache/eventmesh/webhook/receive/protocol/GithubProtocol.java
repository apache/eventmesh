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
package org.apache.eventmesh.webhook.receive.protocol;

import java.util.Map;

import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.receive.ManufacturerProtocol;
import org.apache.eventmesh.webhook.receive.WebHookRequest;

/**
 * @author laohu
 *
 */
public class GithubProtocol implements ManufacturerProtocol {

	/* (non-Javadoc)
	 * @see org.apache.eventmesh.webhook.receive.ManufacturerProtocol#execute(org.apache.eventmesh.webhook.receive.WebHookRequest, org.apache.eventmesh.webhook.api.WebHookConfig, java.util.Map)
	 */
	@Override
	public void execute(WebHookRequest webHookRequest, WebHookConfig webHookConfig, Map<String, String> header) {
		

	}

	@Override
	public String getManufacturerName() {
		return "github";
	}

}
