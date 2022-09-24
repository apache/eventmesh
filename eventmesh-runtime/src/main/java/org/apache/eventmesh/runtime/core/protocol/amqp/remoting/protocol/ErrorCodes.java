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

package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol;

/**
 * Defines constants for AMQP codes
 */
public interface ErrorCodes {
    /**
     * Indicates that the method completed successfully.
     */
    int REPLY_SUCCESS = 200;

    /**
     * The client asked for a specific message that is no longer available. The message was delivered to another client,
     * or was purged from the queue for some other reason.
     */
    int NOT_DELIVERED = 310;

    /**
     * The client attempted to transfer content larger than the server could accept at the present time.  The client may
     * retry at a later time.
     */
    int MESSAGE_TOO_LARGE = 311;

    /**
     * When the exchange cannot route the result of a .Publish, most likely due to an invalid routing key. Only when the
     * mandatory flag is set.
     */
    int NO_ROUTE = 312;

    /**
     * When the exchange cannot deliver to a consumer when the immediate flag is set. As a result of pending data on the
     * queue or the absence of any consumers of the queue.
     */
    int NO_CONSUMERS = 313;

    /**
     * An operator intervened to close the connection for some reason. The client may retry at some later date.
     */
    int CONNECTION_FORCED = 320;

    /**
     * The client tried to work with an unknown virtual host or cluster.
     */
    int INVALID_PATH = 402;

    /**
     * The client attempted to work with a server entity to which it has no access due to security settings.
     */
    int ACCESS_REFUSED = 403;

    /**
     * The client attempted to work with a server entity that does not exist.
     */
    int NOT_FOUND = 404;

    /**
     * The client attempted to work with a server entity to which it has no access because another client is working
     * with it.
     */
    int ALREADY_EXISTS = 405;

    /**
     * The client requested a method that was not allowed because some precondition failed.
     */
    int IN_USE = 406;

    int INVALID_ROUTING_KEY = 407;

    int REQUEST_TIMEOUT = 408;

    int ARGUMENT_INVALID = 409;

    /**
     * The client sent a malformed frame that the server could not decode. This strongly implies a programming error in
     * the client.
     */
    int FRAME_ERROR = 501;

    /**
     * The client sent a frame that contained illegal values for one or more fields. This strongly implies a programming
     * error in the client.
     */
    int SYNTAX_ERROR = 502;

    /**
     * The client sent an invalid sequence of frames, attempting to perform an operation that was considered invalid by
     * the server. This usually implies a programming error in the client.
     */
    int COMMAND_INVALID = 503;

    /**
     * The client attempted to work with a channel that had not been correctly opened. This most likely indicates a
     * fault in the client layer.
     */
    int CHANNEL_ERROR = 504;

    /**
     * The server could not complete the method because it lacked sufficient resources. This may be due to the client
     * creating too many of some type of entity.
     */
    int RESOURCE_ERROR = 506;

    /**
     * The client tried to work with some entity in a manner that is prohibited by the server, due to security settings
     * or by some other criteria.
     */
    int NOT_ALLOWED = 530;

    /**
     * The client tried to use functionality that is not implemented in the server.
     */
    int NOT_IMPLEMENTED = 540;

    /**
     * The server could not complete the method because of an internal error. The server may require intervention by an
     * operator in order to resume normal operations.
     */
    int INTERNAL_ERROR = 541;

    int INVALID_ARGUMENT = 542;

    /**
     * The client impl does not support the protocol version
     */
    int UNSUPPORTED_CLIENT_PROTOCOL_ERROR = 543;
}
