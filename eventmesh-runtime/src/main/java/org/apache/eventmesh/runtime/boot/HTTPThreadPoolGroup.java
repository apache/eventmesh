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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class HTTPThreadPoolGroup implements ThreadPoolGroup {

    private final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private ThreadPoolExecutor batchMsgExecutor;
    private ThreadPoolExecutor sendMsgExecutor;
    private ThreadPoolExecutor remoteMsgExecutor;
    private ThreadPoolExecutor replyMsgExecutor;
    private ThreadPoolExecutor pushMsgExecutor;
    private ThreadPoolExecutor clientManageExecutor;
    private ThreadPoolExecutor runtimeAdminExecutor;
    private ThreadPoolExecutor webhookExecutor;

    public HTTPThreadPoolGroup(EventMeshHTTPConfiguration eventMeshHttpConfiguration) {
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
    }

    @Override
    public void initThreadPool() {

        batchMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerBatchMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerBatchMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerBatchBlockQSize()),
            "eventMesh-batchMsg", true);

        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerSendMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerSendMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerSendMsgBlockQSize()),
            "eventMesh-sendMsg", true);

        remoteMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerRemoteMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerRemoteMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerRemoteMsgBlockQSize()),
            "eventMesh-remoteMsg", true);

        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerPushMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerPushMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerPushMsgBlockQSize()),
            "eventMesh-pushMsg", true);

        clientManageExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerClientManageThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerClientManageThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerClientManageBlockQSize()),
            "eventMesh-clientManage", true);

        // The runtimeAdminExecutor here is for the runtime.admin package.
        runtimeAdminExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerAdminThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerAdminThreadNum(),
            new LinkedBlockingQueue<>(50), "eventMesh-runtime-admin", true);

        replyMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerReplyMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerReplyMsgThreadNum(),
            new LinkedBlockingQueue<>(100), "eventMesh-replyMsg", true);

        webhookExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerWebhookThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerWebhookThreadNum(),
            new LinkedBlockingQueue<>(100), "eventMesh-webhook", true);
    }

    @Override
    public void shutdownThreadPool() {
        if (batchMsgExecutor != null) {
            batchMsgExecutor.shutdown();
        }
        if (runtimeAdminExecutor != null) {
            runtimeAdminExecutor.shutdown();
        }
        if (clientManageExecutor != null) {
            clientManageExecutor.shutdown();
        }
        if (sendMsgExecutor != null) {
            sendMsgExecutor.shutdown();
        }
        if (remoteMsgExecutor != null) {
            remoteMsgExecutor.shutdown();
        }
        if (pushMsgExecutor != null) {
            pushMsgExecutor.shutdown();
        }
        if (replyMsgExecutor != null) {
            replyMsgExecutor.shutdown();
        }
    }

    public ThreadPoolExecutor getBatchMsgExecutor() {
        return batchMsgExecutor;
    }

    public ThreadPoolExecutor getSendMsgExecutor() {
        return sendMsgExecutor;
    }

    public ThreadPoolExecutor getRemoteMsgExecutor() {
        return remoteMsgExecutor;
    }

    public ThreadPoolExecutor getReplyMsgExecutor() {
        return replyMsgExecutor;
    }

    public ThreadPoolExecutor getPushMsgExecutor() {
        return pushMsgExecutor;
    }

    public ThreadPoolExecutor getClientManageExecutor() {
        return clientManageExecutor;
    }

    public ThreadPoolExecutor getRuntimeAdminExecutor() {
        return runtimeAdminExecutor;
    }

    public ThreadPoolExecutor getWebhookExecutor() {
        return webhookExecutor;
    }
}
