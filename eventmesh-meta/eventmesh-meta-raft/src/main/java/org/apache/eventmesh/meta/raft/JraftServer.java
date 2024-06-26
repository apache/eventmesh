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

import org.apache.eventmesh.meta.raft.rpc.MetaServerHelper;
import org.apache.eventmesh.meta.raft.rpc.RequestProcessor;
import org.apache.eventmesh.meta.raft.rpc.RequestResponse;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;


public class JraftServer {
    
    private RaftGroupService raftGroupService;
    
    private Node node;
    
    private MetaStateMachine fsm = new MetaStateMachine();
    
    public MetaStateMachine getFsm() {
        return fsm;
    }

    private JraftMetaServiceImpl metaImpl;
    
    public JraftServer(final String dataPath, final String groupId, final PeerId serverId,
            final NodeOptions nodeOptions) throws IOException {
        // init raft data path, it contains log,meta,snapshot
        FileUtils.forceMkdir(new File(dataPath));
        // here use same RPC server for raft and business. It also can be seperated generally
        final RpcServer rpcServer = RaftRpcServerFactory.createRaftRpcServer(serverId.getEndpoint());
        MetaServerHelper.initGRpc();
        MetaServerHelper.setRpcServer(rpcServer);
        // register business processor
        metaImpl = new JraftMetaServiceImpl(this);
        rpcServer.registerProcessor(new RequestProcessor(metaImpl));
        nodeOptions.setFsm(this.fsm);
        // set storage path (log,meta,snapshot)
        // log, must
        nodeOptions.setLogUri(dataPath + File.separator + "log");
        // meta, must
        nodeOptions.setRaftMetaUri(dataPath + File.separator + "raft_meta");
        // snapshot, optional, generally recommended
        nodeOptions.setSnapshotUri(dataPath + File.separator + "snapshot");
        // init raft group service framework
        this.raftGroupService = new RaftGroupService(groupId, serverId, nodeOptions, rpcServer);
        // start raft node
        this.node = this.raftGroupService.start();
        
    }

    public RequestResponse redirect() {
        final RequestResponse.Builder builder = RequestResponse.newBuilder().setSuccess(false);
        if (this.node != null) {
            final PeerId leader = this.node.getLeaderId();
            if (leader != null) {
                builder.setRedirect(leader.toString());
            }
        }
        return builder.build();
    }

    public JraftMetaServiceImpl getMetaImpl() {
        return metaImpl;
    }

    public Node getNode() {
        return node;
    }
}
