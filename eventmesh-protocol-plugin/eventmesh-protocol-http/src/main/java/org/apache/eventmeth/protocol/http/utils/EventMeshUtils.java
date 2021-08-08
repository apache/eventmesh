/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmeth.protocol.http.utils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.config.EventMeshVersion;
import org.apache.eventmesh.common.utils.ThreadUtil;

public class EventMeshUtils {

    public static String buildPushMsgSeqNo() {
        return StringUtils.rightPad(String.valueOf(System.currentTimeMillis()), 6) + String.valueOf(RandomStringUtils.randomNumeric(4));
    }

    public static String buildMeshClientID(String clientGroup, String meshCluster) {
        return StringUtils.trim(clientGroup)
                + "(" + StringUtils.trim(meshCluster) + ")"
                + "-" + EventMeshVersion.getCurrentVersionDesc()
                + "-" + ThreadUtil.getPID();
    }
}
