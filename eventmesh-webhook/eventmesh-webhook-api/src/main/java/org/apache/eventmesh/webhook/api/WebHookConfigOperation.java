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

/**
 * Define the operation of webhook configuration
 */
public interface WebHookConfigOperation {

    /**
     * Add the configuration
     *
     * @return
     */
    public Integer insertWebHookConfig(WebHookConfig webHookConfig);

    /**
     * Modify the configuration
     *
     * @return
     */
    public Integer updateWebHookConfig(WebHookConfig webHookConfig);

    /**
     * Delete the configuration
     *
     * @return
     */
    public Integer deleteWebHookConfig(WebHookConfig webHookConfig);

    /**
     * Query the configuration by ID
     *
     * @return
     */
    public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig);

    /**
     * Query configurations through the manufacturer
     */
    public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
                                                                Integer pageSize);
}
