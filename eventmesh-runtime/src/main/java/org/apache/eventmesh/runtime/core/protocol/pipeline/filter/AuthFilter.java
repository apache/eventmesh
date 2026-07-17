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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Authentication filter — validates Token / AK/SK.
 * This is the FIRST filter in the chain and CANNOT be bypassed.
 *
 * <p>Extracts auth credentials from CloudEvent extension attributes
 * (e.g. {@code authToken}, {@code accessKey}) and delegtes to the auth service.
 */
@Slf4j
public class AuthFilter implements PipelineFilter {

    public static final String NAME = "AuthFilter";

    // CloudEvent extension attribute keys
    public static final String AUTH_TOKEN_KEY = "authtoken";
    public static final String ACCESS_KEY_KEY = "accesskey";
    public static final String SECRET_KEY_KEY = "secretkey";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public boolean isBypassable() {
        return false; // Security-critical — never bypassable
    }

    @Override
    public PipelineResult filter(CloudEvent event, PipelineContext ctx) {
        String token = getExtension(event, AUTH_TOKEN_KEY);
        String ak = getExtension(event, ACCESS_KEY_KEY);
        String sk = getExtension(event, SECRET_KEY_KEY);

        if (token == null && ak == null) {
            log.warn("[AuthFilter] No credentials found for event {}, protocol={}",
                event.getId(), ctx.getEntryProtocol());
            return PipelineResult.drop(event);
        }

        // Token-based auth
        if (token != null) {
            if (!validateToken(token, ctx)) {
                log.warn("[AuthFilter] Invalid token for event {}", event.getId());
                return PipelineResult.drop(event);
            }
            return PipelineResult.cont(event);
        }

        // AK/SK-based auth
        if (!validateAkSk(ak, sk, ctx)) {
            log.warn("[AuthFilter] Invalid AK/SK for event {}", event.getId());
            return PipelineResult.drop(event);
        }

        return PipelineResult.cont(event);
    }

    // ---- internal helpers ----

    private static String getExtension(CloudEvent event, String key) {
        Object v = event.getExtension(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Validate token-based authentication.
     * Subclass or configure to integrate with external auth providers (OAuth2, JWKS, etc.)
     */
    protected boolean validateToken(String token, PipelineContext ctx) {
        // Default: accept non-empty tokens (production should override with real auth)
        return token != null && !token.isEmpty();
    }

    /**
     * Validate Access Key / Secret Key pair.
     */
    protected boolean validateAkSk(String ak, String sk, PipelineContext ctx) {
        // Default: accept non-empty AK/SK (production should override with real auth)
        return ak != null && !ak.isEmpty() && sk != null && !sk.isEmpty();
    }
}
