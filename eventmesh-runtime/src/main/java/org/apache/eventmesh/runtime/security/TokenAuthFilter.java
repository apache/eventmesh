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

import java.util.Collections;
import java.util.Set;

import io.cloudevents.CloudEvent;

/**
 * Authenticates the caller by a bearer token (§13.4.1). In production this delegates to the
 * existing security-plugin ({@code auth-token} / {@code auth-http-basic}); the uni skeleton
 * validates against a configured token set. Missing or unknown token → 401.
 */
public class TokenAuthFilter implements IngressFilter {

    private final Set<String> validTokens;

    public TokenAuthFilter(Set<String> validTokens) {
        this.validTokens = Collections.unmodifiableSet(validTokens);
    }

    @Override
    public FilterVerdict check(CloudEvent event, FilterContext ctx) {
        String credential = ctx.getCredential();
        if (credential != null && validTokens.contains(credential)) {
            return FilterVerdict.allow();
        }
        return FilterVerdict.deny(FilterVerdict.STATUS_UNAUTHENTICATED,
            credential == null ? "missing credential" : "invalid credential");
    }
}
