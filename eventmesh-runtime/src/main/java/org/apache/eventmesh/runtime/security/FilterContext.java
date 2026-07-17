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

package org.apache.eventmesh.runtime.security;

/**
 * Per-request context made available to {@link IngressFilter}s. Carries who is calling and what
 * they are trying to do, so authentication / authorization / tenant-isolation filters can decide
 * without re-deriving it (§4.5 / §13.4).
 */
public final class FilterContext {

    private final String topic;
    private final String clientId;
    private final String tenant;
    private final String credential;
    private final String remoteAddress;

    public FilterContext(String topic, String clientId, String tenant, String credential, String remoteAddress) {
        this.topic = topic;
        this.clientId = clientId;
        this.tenant = tenant;
        this.credential = credential;
        this.remoteAddress = remoteAddress;
    }

    public String getTopic() {
        return topic;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTenant() {
        return tenant;
    }

    public String getCredential() {
        return credential;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }
}
