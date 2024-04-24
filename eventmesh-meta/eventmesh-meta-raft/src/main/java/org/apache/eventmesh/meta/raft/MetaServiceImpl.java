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

import org.apache.eventmesh.meta.raft.rpc.RequestResponse;

import org.apache.commons.lang.StringUtils;

import java.nio.ByteBuffer;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.error.RaftError;

public class MetaServiceImpl implements MetaService {
    
    JraftServer server;
    
    
    public MetaServiceImpl(JraftServer server) {
        this.server = server;
    }
    
    @Override
    public void handle(RequestResponse request, EventClosure closure) {
        applyOperation(EventOperation.createOpreation(request), closure);
    }
    
    public void applyOperation(EventOperation opreation, EventClosure closure) {
        if (!isLeader()) {
            handlerNotLeaderError(closure);
            return;
        }
        try {
            closure.setEventOperation(opreation);
            final Task task = new Task();
            task.setData(ByteBuffer.wrap(SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(opreation)));
            task.setDone(closure);
            this.server.getNode().apply(task);
        } catch (CodecException e) {
            String errorMsg = "Fail to encode EventOperation";
            closure.failure(errorMsg, StringUtils.EMPTY);
            closure.run(new Status(RaftError.EINTERNAL, errorMsg));
        }
    }
    
    
    private String getRedirect() {
        return this.server.redirect().getRedirect();
    }
    
    private boolean isLeader() {
        return this.server.getFsm().isLeader();
    }
    
    
    private void handlerNotLeaderError(final EventClosure closure) {
        closure.failure("Not leader.", getRedirect());
        closure.run(new Status(RaftError.EPERM, "Not leader"));
    }
}
