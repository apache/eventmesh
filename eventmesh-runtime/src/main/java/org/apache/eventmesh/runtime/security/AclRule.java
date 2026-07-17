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
 * One ACL rule (§13.4.2). A request {@code (principal, resource, action)} is matched against rules
 * in priority order (highest first, DENY wins ties); the first match's effect applies, and no match
 * means default-deny.
 *
 * <p>Principal/resource patterns support {@code *} (any) and a trailing {@code .*} (prefix, e.g.
 * {@code tenantA.*} matches {@code tenantA.userId} / {@code tenantA.orders}). Action {@code ANY}
 * matches every request action.</p>
 */
public final class AclRule {

    public enum Action {
        PUBLISH, SUBSCRIBE, REQUEST, ANY
    }

    public enum Effect {
        ALLOW, DENY
    }

    private final String principal;
    private final String resource;
    private final Action action;
    private final Effect effect;
    private final int priority;

    public AclRule(String principal, String resource, Action action, Effect effect, int priority) {
        this.principal = principal;
        this.resource = resource;
        this.action = action;
        this.effect = effect;
        this.priority = priority;
    }

    /**
     * Does this rule match the request? {@code reqAction == null} means "action not known" (the
     * filter context doesn't carry it yet) — in that case any rule action matches.
     */
    boolean matches(String reqPrincipal, String reqResource, Action reqAction) {
        return matchPattern(principal, reqPrincipal)
            && matchPattern(resource, reqResource)
            && (action == Action.ANY || reqAction == null || action == reqAction);
    }

    private static boolean matchPattern(String pattern, String value) {
        if (pattern == null) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // "tenantA."
            return value != null && value.startsWith(prefix);
        }
        return pattern.equals(value);
    }

    public Effect getEffect() {
        return effect;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "AclRule{priority=" + priority + ", " + effect + " " + principal + " " + resource + " " + action + "}";
    }
}
