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

package org.apache.eventmesh.connector.spring.source;

import org.apache.eventmesh.common.enums.ProtocolType;

/**
 * Operations for sending messages to a destination.
 */
public interface MessageSendingOperations {

    /**
     * Send a message to the given destination.
     * @param message the message to send
     */
    void send(Object message, ProtocolType protocolType);

    /**
     * Send a message to the given destination.
     * @param message the message to send
     * @param timeout the message send timeout.
     */
    void send(Object message, ProtocolType protocolType, Long timeout);

}
