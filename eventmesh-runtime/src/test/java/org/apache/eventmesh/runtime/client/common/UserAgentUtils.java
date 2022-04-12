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

package org.apache.eventmesh.runtime.client.common;

import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import java.util.concurrent.ThreadLocalRandom;

public class UserAgentUtils {
    public static UserAgent createPubUserAgent() {
        UserAgent userAgent = new UserAgent();
        userAgent.setSubsystem("5023");
        userAgent.setPid(32893);
        userAgent.setVersion("2.0.11");
        userAgent.setIdc("FT");
        userAgent.setPath("/data/app/umg_proxy");
        userAgent.setHost("127.0.0.1");
        userAgent.setPort(8362);
        userAgent.setUsername("PU4283");
        userAgent.setPassword(generateRandomString(8));
        userAgent.setPurpose(EventMeshConstants.PURPOSE_PUB);

        return userAgent;
    }

    public static UserAgent createUserAgent() {
        UserAgent userAgent = new UserAgent();
        userAgent.setSubsystem("5123");
        //userAgent.setPid(UtilAll.getPid());
        //userAgent.setHost(RemotingUtil.getLocalAddress());
        userAgent.setVersion("2.0.8");
        userAgent.setUsername("username");
        userAgent.setPassword("1234");
        return userAgent;
    }

    public static UserAgent createSubUserAgent() {
        UserAgent userAgent = new UserAgent();
        userAgent.setSubsystem("5243");
        //userAgent.setPid(UtilAll.getPid());
        //userAgent.setHost(RemotingUtil.getLocalAddress());
        userAgent.setPort(8888);
        userAgent.setVersion("2.0.8");
        userAgent.setUsername("username");
        userAgent.setPassword("1234");
        userAgent.setPath("/data/app/defibus-acl/");
        userAgent.setPurpose(EventMeshConstants.PURPOSE_SUB);
        return userAgent;
    }

    public static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }
}
