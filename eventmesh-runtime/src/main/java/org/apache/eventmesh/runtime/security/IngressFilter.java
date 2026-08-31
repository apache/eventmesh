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

import org.apache.eventmesh.common.wire.EventMeshFrame;

import io.cloudevents.CloudEvent;

/**
 * One stage of the ingress security pipeline (§4.5). Implementations: authentication (who are
 * you — {@code TokenAuthFilter}), authorization (what may you do — {@code AclFilter}), signature
 * verification ({@code SignatureVerifierFilter}). TLS / mTLS is enforced at the transport, not here.
 *
 * <p>Filters operate on the runtime's internal wire format ({@link EventMeshFrame}) since
 * #5299; the legacy {@code CloudEvent} overload is retained as a bridge for code paths that have
 * not yet migrated (notably the TCP ingress in sub-PR C). Implementations should override the
 * {@code EventMeshFrame} variant; the {@code CloudEvent} variant is implemented as a default
 * that delegates via {@code frame.toCloudEvent()} so existing custom filters keep working.</p>
 */
public interface IngressFilter {

    /**
     * Decide whether {@code event} from {@code ctx} may proceed.
     *
     * @deprecated since #5299 — override {@link #check(EventMeshFrame, FilterContext)} instead.
     *     Will be removed once all ingress paths (HTTP, TCP, A2A) emit {@link EventMeshFrame}.
     */
    @Deprecated
    default FilterVerdict check(CloudEvent event, FilterContext ctx) {
        return check(EventMeshFrame.fromCloudEvent(event), ctx);
    }

    /**
     * Decide whether {@code frame} from {@code ctx} may proceed. Default implementation reads
     * tenant / signature / token directly from {@code frame.attributes()}.
     */
    FilterVerdict check(EventMeshFrame frame, FilterContext ctx);
}
