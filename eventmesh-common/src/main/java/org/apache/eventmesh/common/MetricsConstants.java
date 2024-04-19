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

package org.apache.eventmesh.common;


public class MetricsConstants {

    private MetricsConstants() {

    }

    public static final String UNKOWN = "unkown";

    public static final String LABEL_PROCESSOR = "processor";

    public static final String CLIENT_ADDRESS = "client.address";

    public static final String RPC_SYSTEM = "rpc.system";

    public static final String RPC_SERVICE = "rpc.service";

    //GRPC-https://opentelemetry.io/docs/reference/specification/metrics/semantic_conventions/rpc-metrics/
    public static final String GRPC_NET_PEER_PORT = "net.peer.port";

    public static final String GRPC_NET_PEER_NAME = "net.peer.name";

    public static final String GRPC_NET_SOCK_PEER_ADDR = "net.sock.peer.addr";

    public static final String GRPC_NET_SOCK_PEER_PORT = "net.sock.peer.port";

    // HTTP https://opentelemetry.io/docs/reference/specification/metrics/semantic_conventions/http-metrics/

    public static final String HTTP_HTTP_SCHEME = "http.scheme";

    public static final String HTTP_HTTP_FLAVOR = "http.flavor";

    public static final String HTTP_NET_HOST_NAME = "net.host.name";

    public static final String HTTP_NET_HOST_PORT = "net.host.port";

    //TCP
    public static final String TCP_NET_HOST_NAME = "net.host.name";

    public static final String TCP_NET_HOST_PORT = "net.host.port";


    public static final String CLIENT_PROTOCOL_TYPE = "client.protocol.type";


}
