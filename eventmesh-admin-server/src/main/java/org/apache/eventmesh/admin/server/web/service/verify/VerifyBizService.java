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

package org.apache.eventmesh.admin.server.web.service.verify;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshVerify;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshVerifyService;
import org.apache.eventmesh.common.remote.request.ReportVerifyRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VerifyBizService {
    @Autowired
    private EventMeshVerifyService verifyService;

    public boolean reportVerifyRecord(ReportVerifyRequest request) {
        EventMeshVerify verify = new EventMeshVerify();
        verify.setRecordID(request.getRecordID());
        verify.setRecordSig(request.getRecordSig());
        verify.setPosition(request.getPosition());
        verify.setTaskID(request.getTaskID());
        verify.setJobID(request.getJobID());
        verify.setConnectorName(request.getConnectorName());
        verify.setConnectorStage(request.getConnectorStage());
        return verifyService.save(verify);
    }
}
