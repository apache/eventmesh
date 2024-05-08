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

package org.apache.eventmesh.connector.http.sink.config;

import lombok.Data;

@Data
public class HttpRetryConfig {
    // maximum number of retries, default 3, minimum 0
    private int maxRetries = 3;

    // retry interval, default 2000ms
    private int interval = 2000;

    // Default value is false, indicating that only requests with network-level errors will be retried.
    // If set to true, all failed requests will be retried, including network-level errors and non-2xx responses.
    private boolean retryOnNonSuccess = false;
}
