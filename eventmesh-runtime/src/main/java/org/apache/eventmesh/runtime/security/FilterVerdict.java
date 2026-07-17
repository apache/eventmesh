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
 * Outcome of one {@link IngressFilter}. {@link #isAllowed()} = true passes the request on; a denied
 * verdict carries the HTTP status (401 unauthenticated, 403 forbidden) and a reason.
 */
public final class FilterVerdict {

    public static final int STATUS_UNAUTHENTICATED = 401;
    public static final int STATUS_FORBIDDEN = 403;

    private static final FilterVerdict ALLOW = new FilterVerdict(true, 0, null);

    private final boolean allowed;
    private final int rejectStatus;
    private final String reason;

    private FilterVerdict(boolean allowed, int rejectStatus, String reason) {
        this.allowed = allowed;
        this.rejectStatus = rejectStatus;
        this.reason = reason;
    }

    public static FilterVerdict allow() {
        return ALLOW;
    }

    public static FilterVerdict deny(int httpStatus, String reason) {
        return new FilterVerdict(false, httpStatus, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getRejectStatus() {
        return rejectStatus;
    }

    public String getReason() {
        return reason;
    }
}
