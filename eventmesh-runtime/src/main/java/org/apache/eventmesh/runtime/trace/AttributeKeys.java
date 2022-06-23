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

package org.apache.eventmesh.runtime.trace;

import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;

/**
 * keys.
 */
public final class AttributeKeys {
    public static final AttributeKey<Context> WRITE_CONTEXT =
        AttributeKey.valueOf(AttributeKeys.class, "passed-context");

    // this is the context that has the server span
    //
    // note: this attribute key is also used by ratpack instrumentation
    public static final AttributeKey<Context> SERVER_CONTEXT =
        AttributeKey.valueOf(AttributeKeys.class, "server-span");

    public static final AttributeKey<Context> CLIENT_CONTEXT =
        AttributeKey.valueOf(AttributeKeys.class, "client-context");

    public static final AttributeKey<Context> CLIENT_PARENT_CONTEXT =
        AttributeKey.valueOf(AttributeKeys.class, "client-parent-context");

    private AttributeKeys() {
    }
}
