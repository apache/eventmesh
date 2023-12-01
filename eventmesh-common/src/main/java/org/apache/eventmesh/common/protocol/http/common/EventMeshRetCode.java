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

package org.apache.eventmesh.common.protocol.http.common;

public enum EventMeshRetCode {

    SUCCESS(0, "success"),
    OVERLOAD(1, "eventMesh overload, try later"),
    EVENTMESH_REQUESTCODE_INVALID(2, "requestCode can't be null, or must be number"),
    EVENTMESH_SEND_SYNC_MSG_ERR(3, "eventMesh send rr msg error"),
    EVENTMESH_WAITING_RR_MSG_ERR(4, "eventMesh waiting rr msg error"),
    EVENTMESH_PROTOCOL_HEADER_ERR(6, "eventMesh protocol[header] error"),
    EVENTMESH_PROTOCOL_BODY_ERR(7, "eventMesh protocol[body] error"),
    EVENTMESH_PROTOCOL_BODY_SIZE_ERR(8, "event size exceeds the limit"),
    EVENTMESH_STOP(9, "eventMesh will stop or had stopped"),
    EVENTMESH_REJECT_BY_PROCESSOR_ERROR(10, "eventMesh reject by processor error"),
    EVENTMESH_BATCH_PRODUCER_STOPED_ERR(11, "eventMesh batch msg producer stopped"),
    EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR(12, "eventMesh batch msg speed over the limit"),
    EVENTMESH_PACKAGE_MSG_ERR(13, "eventMesh package msg err, "),
    EVENTMESH_GROUP_PRODUCER_STOPED_ERR(14, "eventMesh group producer stopped"),
    EVENTMESH_SEND_ASYNC_MSG_ERR(15, "eventMesh send async msg error"),
    EVENTMESH_REPLY_MSG_ERR(16, "eventMesh reply msg err, "),
    EVENTMESH_SEND_BATCHLOG_MSG_ERR(17, "eventMesh send batch log msg error"),
    EVENTMESH_RUNTIME_ERR(18, "eventMesh runtime error"),
    EVENTMESH_SUBSCRIBE_ERR(19, "eventMesh subscribe error"),
    EVENTMESH_UNSUBSCRIBE_ERR(20, "eventMesh unsubscribe error"),
    EVENTMESH_HEARTBEAT_ERR(21, "eventMesh heartbeat error"),
    EVENTMESH_ACL_ERR(22, "eventMesh acl error"),
    EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR(23, "eventMesh http msg send over the limit"),

    EVENTMESH_FILTER_MSG_ERR(24, "eventMesh filter async msg error"),
    EVENTMESH_OPERATE_FAIL(100, "operate fail");

    private final Integer retCode;

    private final String errMsg;

    EventMeshRetCode(Integer retCode, String errMsg) {
        this.retCode = retCode;
        this.errMsg = errMsg;
    }

    public Integer getRetCode() {
        return retCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

}
