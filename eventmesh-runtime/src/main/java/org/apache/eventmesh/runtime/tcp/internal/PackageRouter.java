/*

package org.apache.eventmesh.runtime.tcp.internal;

import org.apache.eventmesh.common.protocol.tcp.Package;

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

package org.apache.eventmesh.runtime.transport.tcp;

import org.apache.eventmesh.common.protocol.tcp.Package;

/**
 * Maps a decoded legacy TCP {@link Package} (after the netty {@code Codec} stage) into a
 * {@link TcpRequest} the new core understands.
 *
 * <p>Production wires {@code MeshMessageProtocolAdaptor.toCloudEvent(...)} to turn an
 * {@code EventMeshMessage} body into a CloudEvent, and reads the {@code Command} header
 * (ASYNC_MESSAGE_TO_SERVER → publish, SUBSCRIBE_REQUEST → subscribe,
 * ASYNC_MESSAGE_TO_CLIENT_ACK → ack). Tests inject a stub that maps a simple body shape.</p>
 */
@FunctionalInterface
public interface PackageRouter {

    TcpRequest route(Package pkg);
}
