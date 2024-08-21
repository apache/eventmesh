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

package org.apache.eventmesh.common.config.connector.http;


import lombok.Data;

@Data
public class SinkConnectorConfig {

    private String connectorName;

    private String[] urls;

    // keepAlive, default true
    private boolean keepAlive = true;

    // timeunit: ms, default 60000ms
    private int keepAliveTimeout = 60 * 1000; // Keep units consistent

    // timeunit: ms, default 5000ms, recommended scope: 5000ms - 10000ms
    private int connectionTimeout = 5000;

    // timeunit: ms, default 5000ms
    private int idleTimeout = 5000;

    // maximum number of HTTP/1 connections a client will pool, default 5
    private int maxConnectionPoolSize = 5;

    // retry config
    private HttpRetryConfig retryConfig = new HttpRetryConfig();

    // webhook config
    private HttpWebhookConfig webhookConfig = new HttpWebhookConfig();


    /**
     * Fill default values if absent (When there are multiple default values for a field)
     *
     * @param config SinkConnectorConfig
     */
    public static void populateFieldsWithDefaults(SinkConnectorConfig config) {
        /*
         * set default values for idleTimeout
         * recommended scope: common(5s - 10s), webhook(15s - 30s)
         */
        final int commonHttpIdleTimeout = 5000;
        final int webhookHttpIdleTimeout = 15000;

        // Set default values for idleTimeout
        if (config.getIdleTimeout() == 0) {
            int idleTimeout = config.webhookConfig.isActivate() ? webhookHttpIdleTimeout : commonHttpIdleTimeout;
            config.setIdleTimeout(idleTimeout);
        }

    }
}
