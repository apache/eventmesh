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

package org.apache.eventmesh.runtime.tcp.internal;

import org.apache.eventmesh.runtime.tcp.internal.TcpRequest;
/**
 * Decodes a raw TCP {@code Package} frame from a legacy client into a {@link TcpRequest}.
 *
 * <p>Production implementation uses the existing {@code Codec} + the appropriate
 * {@code ProtocolAdaptor} (meshmessage/cloudevents) + the TCP {@code Command} header to tell
 * HELLO/LISTEN/PUBLISH/RESPONSE apart, mapping them to the four {@link TcpRequest.Kind}s. Tests
 * inject a stub.</p>
 */
@FunctionalInterface
public interface TcpFrameDecoder {

    TcpRequest decode(String clientId, byte[] frame);
}
