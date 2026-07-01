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

package org.apache.eventmesh.meta.raft.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.meta.raft.consts.MetaRaftConstants;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.metaStorage.raft")
public class RaftMetaStorageConfiguration {

    @ConfigField(field = MetaRaftConstants.DATAPATH)
    private String dataPath;

    @ConfigField(field = MetaRaftConstants.SELF)
    private String selfIpAndPort;

    @ConfigField(field = MetaRaftConstants.MEMBERS)
    private String membersIpAndPort;

    @ConfigField(field = MetaRaftConstants.ELECTIONTIMEOUT)
    private Integer electionTimeoutMs;

    @ConfigField(field = MetaRaftConstants.SNAPSHOTINTERVAL)
    private Integer snapshotIntervalSecs;

    @ConfigField(field = MetaRaftConstants.REFRESHLEADERINTERVAL)
    private Integer refreshLeaderInterval;

}
