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

package client.impl;

import client.ProxyClient;
import client.PubClient;
import client.SubClient;
import client.common.UserAgentUtils;
import client.hook.ReceiveMsgHook;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;

public class ProxyClientImpl implements ProxyClient {
    protected UserAgent agent;
    private String accessHost;
    private int accessPort;

    private PubClient pubClient;
    private SubClient subClient;

    public ProxyClientImpl(String accessHost, int accessPort, UserAgent agent) {
        this.accessHost = accessHost;
        this.accessPort = accessPort;
        this.agent = agent;
        this.subClient = new SubClientImpl(accessHost, accessPort, agent);
        this.pubClient = new PubClientImpl(accessHost, accessPort, agent);
    }

    public ProxyClientImpl(String accessHost, int accessPort) {
        this.accessHost = accessHost;
        this.accessPort = accessPort;
        this.subClient = new SubClientImpl(accessHost, accessPort, UserAgentUtils.createSubUserAgent());
        this.pubClient = new PubClientImpl(accessHost, accessPort, UserAgentUtils.createPubUserAgent());
    }

    public Package rr(Package msg, long timeout) throws Exception {
        return this.pubClient.rr(msg, timeout);
    }

    public Package publish(Package msg, long timeout) throws Exception {
        return this.pubClient.publish(msg, timeout);
    }

    public Package broadcast(Package msg, long timeout) throws Exception {
        return this.pubClient.broadcast(msg, timeout);
    }

    public void init() throws Exception {
        this.subClient.init();
        this.pubClient.init();
    }

    public void close() {
        this.pubClient.close();
        this.subClient.close();
    }

    public void heartbeat() throws Exception {
        this.pubClient.heartbeat();
        this.subClient.heartbeat();
    }

    public Package justSubscribe(String serviceId, String scenario, String dcn) throws Exception {
        return this.subClient.justSubscribe(serviceId, scenario, dcn);
    }

    public Package listen() throws Exception {
        return this.subClient.listen();
    }

    public Package justUnsubscribe(String serviceId, String scenario, String dcn) throws Exception {
        return this.subClient.justUnsubscribe(serviceId, scenario, dcn);
    }

    @Override
    public Package justSubscribe(String topic) throws Exception {
        return this.subClient.justSubscribe(topic);
    }

    @Override
    public Package justUnsubscribe(String topic) throws Exception {
        return this.subClient.justUnsubscribe(topic);
    }


    public void registerSubBusiHandler(ReceiveMsgHook handler) throws Exception {
        this.subClient.registerBusiHandler(handler);
    }

    public void registerPubBusiHandler(ReceiveMsgHook handler) throws Exception {
        this.pubClient.registerBusiHandler(handler);
    }

    @Override
    public String toString() {
        return "AccessClientImpl{" +
                "accessHost='" + accessHost + '\'' +
                ", accessPort=" + accessPort +
                ", agent=" + agent +
                '}';
    }

    @Deprecated
    public ProxyClientImpl(String accessServer, String busiTag, String subSystem) {
//        this.accessServer = accessServer;
//        this.pubClient = new PubClientImpl(StringUtils.split(this.accessServer, ":")[0],
//                Integer.parseInt(StringUtils.split(this.accessServer, ":")[1]), OldTestUserAgentFactory.createPubUserAgent
//                (busiTag, subSystem));
//        this.subClient = new SubClientImpl(StringUtils.split(this.accessServer, ":")[0],
//                Integer.parseInt(StringUtils.split(this.accessServer, ":")[1]), OldTestUserAgentFactory.createSubUserAgent
//                (busiTag, subSystem));
    }

//    @Override
//    public void sysLog() throws Exception {
//        subClient.sysLog();
//    }
//
//    @Override
//    public void traceLog() throws Exception {
//        subClient.traceLog();
//    }

    @Override
    public void goodbye() throws Exception {
        subClient.goodbye();
        pubClient.goodbye();
    }

}
