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

package org.apache.eventmesh.common.protocol.tcp;

public enum Command {

    //heartbeat
    HEARTBEAT_REQUEST(0),                              //client send heartbeat packet to server
    HEARTBEAT_RESPONSE(1),                             //server response heartbeat packet of client

    //handshake
    HELLO_REQUEST(2),                                  //client send handshake request to server
    HELLO_RESPONSE(3),                                 //server response handshake request of client

    //disconnection
    CLIENT_GOODBYE_REQUEST(4),                         //Notify server when client actively disconnects
    CLIENT_GOODBYE_RESPONSE(5),                        //Server replies to client's active disconnection notification
    SERVER_GOODBYE_REQUEST(6),                         //Notify client when server actively disconnects
    SERVER_GOODBYE_RESPONSE(7),                        //Client replies to server's active disconnection notification

    //subscription management
    SUBSCRIBE_REQUEST(8),                              //Subscription request sent by client to server
    SUBSCRIBE_RESPONSE(9),                             //Server replies to client's subscription request
    UNSUBSCRIBE_REQUEST(10),                           //Unsubscribe request from client to server
    UNSUBSCRIBE_RESPONSE(11),                          //Server replies to client's unsubscribe request

    //monitor
    LISTEN_REQUEST(12),                                //Request from client to server to start topic listening
    LISTEN_RESPONSE(13),                               //The server replies to the client's listening request

    //RR
    REQUEST_TO_SERVER(14),                             //The client sends the RR request to the server
    REQUEST_TO_CLIENT(15),                             //The server pushes the RR request to the client
    REQUEST_TO_CLIENT_ACK(16),                         //After receiving RR request, the client sends ACK to the server
    RESPONSE_TO_SERVER(17),                            //The client sends the RR packet back to the server
    RESPONSE_TO_CLIENT(18),                            //The server pushes the RR packet back to the client
    RESPONSE_TO_CLIENT_ACK(19),                        //After receiving the return packet, the client sends ACK to the server

    //Asynchronous events
    ASYNC_MESSAGE_TO_SERVER(20),                       //The client sends asynchronous events to the server
    ASYNC_MESSAGE_TO_SERVER_ACK(21),                   //After receiving the asynchronous event, the server sends ack to the client
    ASYNC_MESSAGE_TO_CLIENT(22),                       //The server pushes asynchronous events to the client
    ASYNC_MESSAGE_TO_CLIENT_ACK(23),                   //After the client receives the asynchronous event, the ACK is sent to the server

    //radio broadcast
    BROADCAST_MESSAGE_TO_SERVER(24),                   //The client sends the broadcast message to the server
    BROADCAST_MESSAGE_TO_SERVER_ACK(25),               //After receiving the broadcast message, the server sends ACK to the client
    BROADCAST_MESSAGE_TO_CLIENT(26),                   //The server pushes the broadcast message to the client
    BROADCAST_MESSAGE_TO_CLIENT_ACK(27),               //After the client receives the broadcast message, the ACK is sent to the server

    //Log reporting
    SYS_LOG_TO_LOGSERVER(28),                          //Business log reporting

    //RMB tracking log reporting
    TRACE_LOG_TO_LOGSERVER(29),                        //RMB tracking log reporting

    //Redirecting instruction
    REDIRECT_TO_CLIENT(30),                            //The server pushes the redirection instruction to the client

    //service register
    REGISTER_REQUEST(31),                              //Client sends registration request to server
    REGISTER_RESPONSE(32),                             //The server sends the registration result to the client

    //service unregister
    UNREGISTER_REQUEST(33),                              //The client sends a de registration request to the server
    UNREGISTER_RESPONSE(34),                             //The server will register the result to the client

    //The client asks which EventMesh to recommend
    RECOMMEND_REQUEST(35),                              //Client sends recommendation request to server
    RECOMMEND_RESPONSE(36);                             //The server will recommend the results to the client

    private final byte value;

    Command(int value) {
        this.value = (byte) value;
    }

    public byte value() {
        return this.value;
    }

    public static Command valueOf(int value) {
        for (Command t : Command.values()) {
            if (t.value == value) {
                return t;
            }
        }
        throw new IllegalArgumentException("No enum constant value=" + value);
    }
}
