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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.Utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpStreamMsgContext extends RetryContext {

    private final Session session;

    private final long createTime = System.currentTimeMillis();

    private final Header header;

    private final long startTime;

    private final long taskExecuteTime;

    public UpStreamMsgContext(Session session, CloudEvent event, Header header, long startTime, long taskExecuteTime) {
        this.seq = header.getSeq();
        this.session = session;
        this.event = event;
        this.header = header;
        this.startTime = startTime;
        this.taskExecuteTime = taskExecuteTime;
    }

    public Session getSession() {
        return session;
    }

    public CloudEvent getEvent() {
        return event;
    }

    public long getCreateTime() {
        return createTime;
    }

    @Override
    public String toString() {
        return "UpStreamMsgContext{seq=" + seq
            + ",topic=" + event.getSubject()
            + ",client=" + session.getClient()
            + ",retryTimes=" + retryTimes
            + ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT) + "}"
            + ",executeTime=" + DateFormatUtils.format(executeTime, EventMeshConstants.DATE_FORMAT);
    }

    public void retry() {
        log.info("retry upStream msg start,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes,
            EventMeshUtil.getMessageBizSeq(this.event));

        try {
            Command replyCmd = getReplyCmd(header.getCmd());
            long sendTime = System.currentTimeMillis();

            retryTimes++;

            // check session availability
            if (session.isRunning()) {
                EventMeshTcpSendResult sendStatus = session.upstreamMsg(header, event,
                    createSendCallback(replyCmd, taskExecuteTime, event, this), startTime, taskExecuteTime);

                if (StringUtils.equals(EventMeshTcpSendStatus.SUCCESS.name(), sendStatus.getSendStatus().name())) {
                    log.info("pkg|eventMesh2mq|cmd={}|event={}|user={}|wait={}ms|cost={}ms", header.getCmd(), event,
                        session.getClient(), taskExecuteTime - startTime, sendTime - startTime);
                } else {
                    throw new Exception(sendStatus.getDetail());
                }
            }
        } catch (Exception e) {
            log.error("TCP UpstreamMsg Retry error", e);
        }
    }

    protected SendCallback createSendCallback(Command replyCmd, long taskExecuteTime, CloudEvent event, UpStreamMsgContext retryContext) {
        final long createTime = System.currentTimeMillis();
        Package msg = new Package();

        return new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                session.getSender().getUpstreamBuff().release();
                log.info("upstreamMsg message success|user={}|callback cost={}", session.getClient(),
                    System.currentTimeMillis() - createTime);
                if (replyCmd == Command.BROADCAST_MESSAGE_TO_SERVER_ACK || replyCmd == Command.ASYNC_MESSAGE_TO_SERVER_ACK) {
                    msg.setHeader(new Header(replyCmd, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), seq));
                    msg.setBody(event);
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
                }
            }

            @Override
            public void onException(OnExceptionContext context) {
                session.getSender().getUpstreamBuff().release();

                // retry
                Objects.requireNonNull(session.getClientGroupWrapper().get()).getTcpRetryer()
                    .newTimeout(retryContext, 10, TimeUnit.SECONDS);

                session.getSender().getFailMsgCount().incrementAndGet();
                log.error("upstreamMsg mq message error|user={}|callback cost={}, errMsg={}", session.getClient(),
                    System.currentTimeMillis() - createTime, new Exception(context.getException()));
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), context.getException().toString(), seq));
                msg.setBody(event);
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
            }

        };
    }

    private Command getReplyCmd(Command cmd) {
        switch (cmd) {
            case REQUEST_TO_SERVER:
                return Command.RESPONSE_TO_CLIENT;
            case ASYNC_MESSAGE_TO_SERVER:
                return Command.ASYNC_MESSAGE_TO_SERVER_ACK;
            case BROADCAST_MESSAGE_TO_SERVER:
                return Command.BROADCAST_MESSAGE_TO_SERVER_ACK;
            default:
                return cmd;
        }
    }

    @Override
    public void doRun() throws Exception {
        retry();
    }
}
