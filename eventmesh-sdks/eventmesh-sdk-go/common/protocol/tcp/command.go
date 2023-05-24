// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package tcp

type Command string

var DefaultCommand = struct {
	//heartbeat
	HEARTBEAT_REQUEST  Command //client send heartbeat packet to server
	HEARTBEAT_RESPONSE Command //server response heartbeat packet of client

	//handshake
	HELLO_REQUEST  Command //client send handshake request to server
	HELLO_RESPONSE Command //server response handshake request of client

	//disconnection
	CLIENT_GOODBYE_REQUEST  Command //Notify server when client actively disconnects
	CLIENT_GOODBYE_RESPONSE Command //Server replies to client's active disconnection notification
	SERVER_GOODBYE_REQUEST  Command //Notify client when server actively disconnects
	SERVER_GOODBYE_RESPONSE Command //Client replies to server's active disconnection notification

	//subscription management
	SUBSCRIBE_REQUEST    Command //Subscription request sent by client to server
	SUBSCRIBE_RESPONSE   Command //Server replies to client's subscription request
	UNSUBSCRIBE_REQUEST  Command //Unsubscribe request from client to server
	UNSUBSCRIBE_RESPONSE Command //Server replies to client's unsubscribe request

	//monitor
	LISTEN_REQUEST  Command //Request from client to server to start topic listening
	LISTEN_RESPONSE Command //The server replies to the client's listening request

	//RR
	REQUEST_TO_SERVER      Command //The client sends the RR request to the server
	REQUEST_TO_CLIENT      Command //The server pushes the RR request to the client
	REQUEST_TO_CLIENT_ACK  Command //After receiving RR request, the client sends ACK to the server
	RESPONSE_TO_SERVER     Command //The client sends the RR packet back to the server
	RESPONSE_TO_CLIENT     Command //The server pushes the RR packet back to the client
	RESPONSE_TO_CLIENT_ACK Command //After receiving the return packet, the client sends ACK to the server

	//Asynchronous events
	ASYNC_MESSAGE_TO_SERVER     Command //The client sends asynchronous events to the server
	ASYNC_MESSAGE_TO_SERVER_ACK Command //After receiving the asynchronous event, the server sends ack to the client
	ASYNC_MESSAGE_TO_CLIENT     Command //The server pushes asynchronous events to the client
	ASYNC_MESSAGE_TO_CLIENT_ACK Command //After the client receives the asynchronous event, the ACK is sent to the server

	//radio broadcast
	BROADCAST_MESSAGE_TO_SERVER     Command //The client sends the broadcast message to the server
	BROADCAST_MESSAGE_TO_SERVER_ACK Command //After receiving the broadcast message, the server sends ACK to the client
	BROADCAST_MESSAGE_TO_CLIENT     Command //The server pushes the broadcast message to the client
	BROADCAST_MESSAGE_TO_CLIENT_ACK Command //After the client receives the broadcast message, the ACK is sent to the server

	//Log reporting
	SYS_LOG_TO_LOGSERVER Command //Business log reporting

	//RMB tracking log reporting
	TRACE_LOG_TO_LOGSERVER Command //RMB tracking log reporting

	//Redirecting instruction
	REDIRECT_TO_CLIENT Command //The server pushes the redirection instruction to the client

	//service register
	REGISTER_REQUEST  Command //Client sends registration request to server
	REGISTER_RESPONSE Command //The server sends the registration result to the client

	//service unregister
	UNREGISTER_REQUEST  Command //The client sends a de registration request to the server
	UNREGISTER_RESPONSE Command //The server will register the result to the client

	//The client asks which EventMesh to recommend
	RECOMMEND_REQUEST  Command //Client sends recommendation request to server
	RECOMMEND_RESPONSE Command //The server will recommend the results to the client
}{
	//heartbeat
	HEARTBEAT_REQUEST:  "HEARTBEAT_REQUEST",
	HEARTBEAT_RESPONSE: "HEARTBEAT_RESPONSE",

	//handshake
	HELLO_REQUEST:  "HELLO_REQUEST",
	HELLO_RESPONSE: "HELLO_RESPONSE",

	//disconnection
	CLIENT_GOODBYE_REQUEST:  "CLIENT_GOODBYE_REQUEST",
	CLIENT_GOODBYE_RESPONSE: "CLIENT_GOODBYE_RESPONSE",
	SERVER_GOODBYE_REQUEST:  "SERVER_GOODBYE_REQUEST",
	SERVER_GOODBYE_RESPONSE: "SERVER_GOODBYE_RESPONSE",

	//subscription management
	SUBSCRIBE_REQUEST:    "SUBSCRIBE_REQUEST",
	SUBSCRIBE_RESPONSE:   "SUBSCRIBE_RESPONSE",
	UNSUBSCRIBE_REQUEST:  "UNSUBSCRIBE_REQUEST",
	UNSUBSCRIBE_RESPONSE: "UNSUBSCRIBE_RESPONSE",

	//monitor
	LISTEN_REQUEST:  "LISTEN_REQUEST",
	LISTEN_RESPONSE: "LISTEN_RESPONSE",

	//RR
	REQUEST_TO_SERVER:      "REQUEST_TO_SERVER",
	REQUEST_TO_CLIENT:      "REQUEST_TO_CLIENT",
	REQUEST_TO_CLIENT_ACK:  "REQUEST_TO_CLIENT_ACK",
	RESPONSE_TO_SERVER:     "RESPONSE_TO_SERVER",
	RESPONSE_TO_CLIENT:     "RESPONSE_TO_CLIENT",
	RESPONSE_TO_CLIENT_ACK: "RESPONSE_TO_CLIENT_ACK",

	//Asynchronous events
	ASYNC_MESSAGE_TO_SERVER:     "ASYNC_MESSAGE_TO_SERVER",
	ASYNC_MESSAGE_TO_SERVER_ACK: "ASYNC_MESSAGE_TO_SERVER_ACK",
	ASYNC_MESSAGE_TO_CLIENT:     "ASYNC_MESSAGE_TO_CLIENT",
	ASYNC_MESSAGE_TO_CLIENT_ACK: "ASYNC_MESSAGE_TO_CLIENT_ACK",

	//radio broadcast
	BROADCAST_MESSAGE_TO_SERVER:     "BROADCAST_MESSAGE_TO_SERVER",
	BROADCAST_MESSAGE_TO_SERVER_ACK: "BROADCAST_MESSAGE_TO_SERVER_ACK",
	BROADCAST_MESSAGE_TO_CLIENT:     "BROADCAST_MESSAGE_TO_CLIENT",
	BROADCAST_MESSAGE_TO_CLIENT_ACK: "BROADCAST_MESSAGE_TO_CLIENT_ACK",

	//Log reporting
	SYS_LOG_TO_LOGSERVER: "SYS_LOG_TO_LOGSERVER",

	//RMB tracking log reporting
	TRACE_LOG_TO_LOGSERVER: "TRACE_LOG_TO_LOGSERVER",

	//Redirecting instruction
	REDIRECT_TO_CLIENT: "REDIRECT_TO_CLIENT",

	//service register
	REGISTER_REQUEST:  "REGISTER_REQUEST",
	REGISTER_RESPONSE: "REGISTER_RESPONSE",

	//service unregister
	UNREGISTER_REQUEST:  "UNREGISTER_REQUEST",
	UNREGISTER_RESPONSE: "UNREGISTER_RESPONSE",

	//The client asks which EventMesh to recommend
	RECOMMEND_REQUEST:  "RECOMMEND_REQUEST",
	RECOMMEND_RESPONSE: "RECOMMEND_RESPONSE",
}

//{
//	//heartbeat
//	HEARTBEAT_REQUEST:  0,
//	HEARTBEAT_RESPONSE: 1,
//
//	//handshake
//	HELLO_REQUEST:  2,
//	HELLO_RESPONSE: 3,
//
//	//disconnection
//	CLIENT_GOODBYE_REQUEST:  4,
//	CLIENT_GOODBYE_RESPONSE: 5,
//	SERVER_GOODBYE_REQUEST:  6,
//	SERVER_GOODBYE_RESPONSE: 7,
//
//	//subscription management
//	SUBSCRIBE_REQUEST:    8,
//	SUBSCRIBE_RESPONSE:   9,
//	UNSUBSCRIBE_REQUEST:  10,
//	UNSUBSCRIBE_RESPONSE: 11,
//
//	//monitor
//	LISTEN_REQUEST:  12,
//	LISTEN_RESPONSE: 13,
//
//	//RR
//	REQUEST_TO_SERVER:      14,
//	REQUEST_TO_CLIENT:      15,
//	REQUEST_TO_CLIENT_ACK:  16,
//	RESPONSE_TO_SERVER:     17,
//	RESPONSE_TO_CLIENT:     18,
//	RESPONSE_TO_CLIENT_ACK: 19,
//
//	//Asynchronous events
//	ASYNC_MESSAGE_TO_SERVER:     20,
//	ASYNC_MESSAGE_TO_SERVER_ACK: 21,
//	ASYNC_MESSAGE_TO_CLIENT:     22,
//	ASYNC_MESSAGE_TO_CLIENT_ACK: 23,
//
//	//radio broadcast
//	BROADCAST_MESSAGE_TO_SERVER:     24,
//	BROADCAST_MESSAGE_TO_SERVER_ACK: 25,
//	BROADCAST_MESSAGE_TO_CLIENT:     26,
//	BROADCAST_MESSAGE_TO_CLIENT_ACK: 27,
//
//	//Log reporting
//	SYS_LOG_TO_LOGSERVER: 28,
//
//	//RMB tracking log reporting
//	TRACE_LOG_TO_LOGSERVER: 29,
//
//	//Redirecting instruction
//	REDIRECT_TO_CLIENT: 30,
//
//	//service register
//	REGISTER_REQUEST:  31,
//	REGISTER_RESPONSE: 32,
//
//	//service unregister
//	UNREGISTER_REQUEST:  33,
//	UNREGISTER_RESPONSE: 34,
//
//	//The client asks which EventMesh to recommend
//	RECOMMEND_REQUEST:  35,
//	RECOMMEND_RESPONSE: 36,
//}
