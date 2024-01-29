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

import org.apache.eventmesh.webhook.api.WebHookConfig;

import java.util.Map;

/**
 * Information and protocol resolution methods for different manufacturers
 */
public interface ManufacturerProtocol {

    String getManufacturerName();

    /**
     * - 1.authentication - 2.parse webhook content to WebHookRequest
     *
     * @param webHookRequest formatted data
     * @param webHookConfig  webhook config
     * @param header         webhook content header
     * @throws Exception authenticate failed ,or content parse failed
     */
    void execute(WebHookRequest webHookRequest, WebHookConfig webHookConfig, Map<String, String> header) throws Exception;
}
