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

package cn.webank.eventmesh.common.protocol.tcp;

public enum Command {

    //心跳
    HEARTBEAT_REQUEST(0),                              //client发给server的心跳包
    HEARTBEAT_RESPONSE(1),                             //server回复client的心跳包

    //握手
    HELLO_REQUEST(2),                                  //client发给server的握手请求
    HELLO_RESPONSE(3),                                 //server回复client的握手请求

    //断连
    CLIENT_GOODBYE_REQUEST(4),                         //client主动断连时通知server
    CLIENT_GOODBYE_RESPONSE(5),                        //server回复client的主动断连通知
    SERVER_GOODBYE_REQUEST(6),                         //server主动断连时通知client
    SERVER_GOODBYE_RESPONSE(7),                        //client回复server的主动断连通知

    //订阅管理
    SUBSCRIBE_REQUEST(8),                              //client发给server的订阅请求
    SUBSCRIBE_RESPONSE(9),                             //server回复client的订阅请求
    UNSUBSCRIBE_REQUEST(10),                           //client发给server的取消订阅请求
    UNSUBSCRIBE_RESPONSE(11),                          //server回复client的取消订阅请求

    //监听
    LISTEN_REQUEST(12),                                //client发给server的启动topic监听的请求
    LISTEN_RESPONSE(13),                               //server回复client的监听请求

    //RR
    REQUEST_TO_SERVER(14),                             //client将RR请求发送给server
    REQUEST_TO_CLIENT(15),                             //server将RR请求推送给client
    REQUEST_TO_CLIENT_ACK(16),                         //client收到RR请求后ACK给server
    RESPONSE_TO_SERVER(17),                            //client将RR回包发送给server
    RESPONSE_TO_CLIENT(18),                            //server将RR回包推送给client
    RESPONSE_TO_CLIENT_ACK(19),                        //client收到回包后ACK给server

    //异步事件
    ASYNC_MESSAGE_TO_SERVER(20),                       //client将异步事件发送给server
    ASYNC_MESSAGE_TO_SERVER_ACK(21),                   //server收到异步事件后ACK给client
    ASYNC_MESSAGE_TO_CLIENT(22),                       //server将异步事件推送给client
    ASYNC_MESSAGE_TO_CLIENT_ACK(23),                   //client收到异步事件后ACK给server

    //广播
    BROADCAST_MESSAGE_TO_SERVER(24),                   //client将广播消息发送给server
    BROADCAST_MESSAGE_TO_SERVER_ACK(25),               //server收到广播消息后ACK给client
    BROADCAST_MESSAGE_TO_CLIENT(26),                   //server将广播消息推送给client
    BROADCAST_MESSAGE_TO_CLIENT_ACK(27),               //client收到广播消息后ACK给server

    //日志上报
    SYS_LOG_TO_LOGSERVER(28),                          //业务日志上报

    //RMB跟踪日志上报
    TRACE_LOG_TO_LOGSERVER(29),                        //RMB跟踪日志上报

    //重定向指令
    REDIRECT_TO_CLIENT(30),                            //server将重定向指令推动给client

    //服务注册
    REGISTER_REQUEST(31),                              //client发送注册请求给server
    REGISTER_RESPONSE(32),                             //server将注册结果给client

    //服务去注册
    UNREGISTER_REQUEST(33),                              //client发送去注册请求给server
    UNREGISTER_RESPONSE(34),                             //server将去注册结果给client

    //client询问proxy推荐连哪个proxy
    RECOMMEND_REQUEST(35),                              //client发送推荐请求给server
    RECOMMEND_RESPONSE(36);                             //server将推荐结果给client

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
