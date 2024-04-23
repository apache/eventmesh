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

package org.apache.eventmesh.meta.raft;

import org.apache.eventmesh.meta.raft.consts.MetaRaftConstants;
import org.apache.eventmesh.meta.raft.rpc.RequestResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.alipay.sofa.jraft.Closure;


public abstract class EventClosure implements Closure {

    private CompletableFuture<RequestResponse> future;

    private RequestResponse requestResponse;

    private EventOperation eventOperation;


    public void setFuture(CompletableFuture<RequestResponse> future) {
        this.future = future;
    }

    public void setRequestResponse(RequestResponse requestResponse) {
        this.requestResponse = requestResponse;
        if (future != null) {
            future.complete(getRequestResponse());
        }
    }

    public RequestResponse getRequestResponse() {
        return requestResponse;
    }

    public EventOperation getEventOperation() {
        return eventOperation;
    }

    protected void failure(final String errorMsg, final String redirect) {
        final RequestResponse response = RequestResponse.newBuilder().setSuccess(false).setErrorMsg(errorMsg)
            .setRedirect(redirect).build();
        setRequestResponse(response);
    }

    public void setEventOperation(EventOperation opreation) {
        this.eventOperation = opreation;
    }

    protected void success(final Map<String, String> map) {

        final RequestResponse response = RequestResponse.newBuilder().setValue(MetaRaftConstants.RESPONSE)
            .setSuccess(true).putAllInfo(map).build();
        setRequestResponse(response);
    }
}
