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

import static org.apache.eventmesh.meta.raft.EventOperation.DELETE;
import static org.apache.eventmesh.meta.raft.EventOperation.GET;
import static org.apache.eventmesh.meta.raft.EventOperation.PUT;

import org.apache.eventmesh.meta.raft.snapshot.MetaSnapshotFile;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetaStateMachine extends StateMachineAdapter {

    private final AtomicLong leaderTerm = new AtomicLong(-1);

    private static ObjectMapper objectMapper = new ObjectMapper();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private Map<String, String> contentTable = new ConcurrentHashMap<>();

    public boolean isLeader() {
        return this.leaderTerm.get() > 0;
    }

    @Override
    public boolean onSnapshotLoad(final SnapshotReader reader) {
        if (isLeader()) {
            log.warn("Leader is not supposed to load snapshot");
            return false;
        }
        if (reader.getFileMeta("data") == null) {
            log.error("Fail to find data file in {}", reader.getPath());
            return false;
        }
        final MetaSnapshotFile snapshot = new MetaSnapshotFile(reader.getPath() + File.separator + "data");
        try {
            Map<String, String> snapshotLoaded = objectMapper.readValue(snapshot.load(), Map.class);
            contentTable.clear();
            contentTable.putAll(snapshotLoaded);
            return true;
        } catch (final IOException e) {
            log.error("Fail to load snapshot from {}", snapshot.getPath());
            return false;
        }

    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        executor.submit(() -> {
            final MetaSnapshotFile snapshot = new MetaSnapshotFile(writer.getPath() + File.separator + "data");
            try {
                if (snapshot.save(objectMapper.writeValueAsString(contentTable))) {
                    if (writer.addFile("data")) {
                        done.run(Status.OK());
                    } else {
                        done.run(new Status(RaftError.EIO, "Fail to add file to writer"));
                    }
                } else {
                    done.run(new Status(RaftError.EIO, "Fail to save snapshot %s", snapshot.getPath()));
                }
            } catch (IOException e) {
                done.run(new Status(RaftError.EIO, "Fail to deserialize snapshot %s", snapshot.getPath()));
            }
        });
    }

    @Override
    public void onApply(Iterator iter) {
        while (iter.hasNext()) {
            Exception e1 = null;
            EventOperation eventOperation = null;
            EventClosure closure = null;
            if (iter.done() != null) {
                // This task is applied by this node, get value from closure to avoid additional parsing.
                closure = (EventClosure) iter.done();
                eventOperation = closure.getEventOperation();
            } else {
                // Have to parse FetchAddRequest from this user log.
                final ByteBuffer data = iter.getData();
                try {
                    eventOperation = SerializerManager.getSerializer(SerializerManager.Hessian2)
                        .deserialize(data.array(), EventOperation.class.getName());
                } catch (final CodecException e) {
                    e.printStackTrace(System.err);
                    e1 = e;

                }
                // follower ignore read operation
                if (eventOperation != null && eventOperation.isReadOp()) {
                    iter.next();
                    continue;
                }
            }
            if (eventOperation != null) {
                switch (eventOperation.getOp()) {
                    case GET:
                        break;
                    case PUT:
                        Map<String, String> tempTable = eventOperation.getData();
                        contentTable.putAll(tempTable);
                        log.info("update MetaStateMachine successfully {}", contentTable);
                        break;
                    case DELETE:
                        Map<String, String> tempTable2 = eventOperation.getData();
                        tempTable2.forEach((key, value) -> {
                            String remove = contentTable.remove(key);
                            if (Objects.isNull(remove)) {
                                log.warn("delete MetaStateMachine key: {} fail.", remove);
                            } else {
                                log.info("delete MetaStateMachine key: {} successfully.", remove);
                            }

                        });

                        break;
                    default:
                        break;
                }

                if (closure != null) {
                    if (e1 != null) {
                        closure.failure(e1.getMessage(), StringUtils.EMPTY);
                    } else {
                        if (eventOperation.getOp() == PUT) {
                            closure.success(Collections.EMPTY_MAP);
                        } else {
                            closure.success(Collections.unmodifiableMap(contentTable));
                        }

                    }
                    closure.run(Status.OK());
                }
            }
            iter.next();
        }
    }

    @Override
    public void onLeaderStart(final long term) {
        this.leaderTerm.set(term);
        super.onLeaderStart(term);

    }

    @Override
    public void onLeaderStop(final Status status) {
        this.leaderTerm.set(-1);
        super.onLeaderStop(status);
    }
}
