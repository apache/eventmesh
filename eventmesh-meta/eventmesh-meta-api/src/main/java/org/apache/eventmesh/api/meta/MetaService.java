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

package org.apache.eventmesh.api.meta;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.bo.EventMeshAppSubTopicInfo;
import org.apache.eventmesh.api.meta.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MetaService
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.META)
public interface MetaService {

    void init() throws MetaException;

    void start() throws MetaException;

    void shutdown() throws MetaException;

    List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException;

    List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException;

    default Map<String/* eventMeshName */, Map<String/* purpose */, Integer/* num */>> findEventMeshClientDistributionData(
                                                                                                                           String clusterName,
                                                                                                                           String group,
                                                                                                                           String purpose) throws MetaException {
        // todo find metadata
        return Collections.emptyMap();
    }

    void registerMetadata(Map<String, String> metadataMap);

    String getMetaData(String key);

    void updateMetaData(Map<String, String> metadataMap);

    void removeMetaData(String key);

    boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException;

    boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException;

    default EventMeshAppSubTopicInfo findEventMeshAppSubTopicInfoByGroup(String group) throws MetaException {
        return null;
    }

    default List<EventMeshServicePubTopicInfo> findEventMeshServicePubTopicInfos() throws MetaException {
        return Collections.emptyList();
    }
}
