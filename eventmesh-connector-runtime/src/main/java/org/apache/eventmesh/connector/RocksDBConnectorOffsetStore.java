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

package org.apache.eventmesh.connector;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import lombok.extern.slf4j.Slf4j;

/**
 * RocksDB-backed {@link ConnectorOffsetStore} — durable across process restarts so connectors can
 * resume from the last checkpoint (§8.9 resume from checkpoint). Key and value are UTF-8 strings.
 */
@Slf4j
public class RocksDBConnectorOffsetStore implements ConnectorOffsetStore {

    static {
        RocksDB.loadLibrary();
    }

    private final RocksDB db;

    public RocksDBConnectorOffsetStore(String dataPath) {
        Options options = new Options().setCreateIfMissing(true);
        try {
            this.db = RocksDB.open(options, dataPath);
        } catch (RocksDBException e) {
            throw new IllegalStateException("failed to open RocksDB offset store at " + dataPath, e);
        }
        options.close();
    }

    @Override
    public void put(String key, String value) {
        try {
            db.put(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            log.warn("RocksDB put failed for key={}: {}", key, e.toString());
        }
    }

    @Override
    public String get(String key) {
        try {
            byte[] val = db.get(key.getBytes(StandardCharsets.UTF_8));
            return val == null ? null : new String(val, StandardCharsets.UTF_8);
        } catch (RocksDBException e) {
            log.warn("RocksDB get failed for key={}: {}", key, e.toString());
            return null;
        }
    }

    @Override
    public Map<String, String> all() {
        Map<String, String> result = new HashMap<>();
        try (RocksIterator it = db.newIterator()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                result.put(
                    new String(it.key(), StandardCharsets.UTF_8),
                    new String(it.value(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    @Override
    public void flush() {
        try (FlushOptions opts = new FlushOptions().setWaitForFlush(true)) {
            db.flush(opts);
        } catch (RocksDBException e) {
            log.warn("RocksDB flush failed: {}", e.toString());
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
