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

package org.apache.eventmesh.runtime.delivery;

import java.util.Map;

/**
 * Minimal HTTP POST primitive used by push transports (e.g. {@link WebHookChannel}). Kept in the
 * runtime so it has no dependency on the independent connector-runtime module.
 */
@FunctionalInterface
public interface HttpCaller {

    /**
     * @return the HTTP status code
     */
    int post(String url, byte[] body, Map<String, String> headers);
}
