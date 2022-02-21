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

package org.apache.eventmesh.common.protocol.grpc.common;

public enum StatusCode {

    SUCCESS("0", "success"),
    OVERLOAD("1", "eventMesh overload, try later, "),
    EVENTMESH_REQUESTCODE_INVALID("2", "requestCode can't be null, or must be number, "),
    EVENTMESH_SEND_SYNC_MSG_ERR("3", "eventMesh send rr msg err, "),
    EVENTMESH_WAITING_RR_MSG_ERR("4", "eventMesh waiting rr msg err, "),
    EVENTMESH_PROTOCOL_HEADER_ERR("6", "eventMesh protocol[header] err, "),
    EVENTMESH_PROTOCOL_BODY_ERR("7", "eventMesh protocol[body] err, "),
    EVENTMESH_STOP("8", "eventMesh will stop or had stopped, "),
    EVENTMESH_REJECT_BY_PROCESSOR_ERROR("9", "eventMesh reject by processor error, "),
    EVENTMESH_BATCH_PUBLISH_ERR("10", "eventMesh batch publish messages error, "),
    EVENTMESH_SEND_BATCHLOG_MSG_ERR("17", "eventMesh send batchlog msg err, "),
    EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR("11", "eventMesh batch msg speed over the limit, "),
    EVENTMESH_PACKAGE_MSG_ERR("12", "eventMesh package msg err, "),
    EVENTMESH_GROUP_PRODUCER_STOPED_ERR("13", "eventMesh group producer stopped, "),
    EVENTMESH_SEND_ASYNC_MSG_ERR("14", "eventMesh send async msg err, "),
    EVENTMESH_REPLY_MSG_ERR("15", "eventMesh reply msg err, "),
    EVENTMESH_RUNTIME_ERR("16", "eventMesh runtime err, "),
    EVENTMESH_SUBSCRIBE_ERR("17", "eventMesh subscribe err"),
    EVENTMESH_UNSUBSCRIBE_ERR("18", "eventMesh unsubscribe err"),
    EVENTMESH_HEARTBEAT_ERR("19", "eventMesh heartbeat err"),
    EVENTMESH_ACL_ERR("20", "eventMesh acl err"),
    EVENTMESH_SEND_MESSAGE_SPEED_OVER_LIMIT_ERR("21", "eventMesh send message speed over the limit err."),
    EVENTMESH_REQUEST_REPLY_MSG_ERR("22", "eventMesh request reply msg err, ");

    private String retCode;

    private String errMsg;

    StatusCode(String retCode, String errMsg) {
        this.retCode = retCode;
        this.errMsg = errMsg;
    }

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
}
