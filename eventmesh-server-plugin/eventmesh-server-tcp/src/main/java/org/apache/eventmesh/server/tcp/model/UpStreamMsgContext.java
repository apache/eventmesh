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

package org.apache.eventmesh.server.tcp.model;

import io.openmessaging.api.Message;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.server.tcp.client.session.Session;
import org.apache.eventmesh.server.tcp.config.TcpProtocolConstants;

public class UpStreamMsgContext {

    private Session session;

    private Message msg;

    private String seq;

    private long createTime = System.currentTimeMillis();

    public UpStreamMsgContext(String seq, Session session, Message msg) {
        this.seq = seq;
        this.session = session;
        this.msg = msg;
    }

    public Session getSession() {
        return session;
    }

    public Message getMsg() {
        return msg;
    }

    public long getCreateTime() {
        return createTime;
    }

    @Override
    public String toString() {
        return "UpStreamMsgContext{seq=" + seq
                + ",topic=" + msg.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION)
                + ",client=" + session.getClient()
                + ",createTime=" + DateFormatUtils.format(createTime, TcpProtocolConstants.DATE_FORMAT) + "}";
    }
}
