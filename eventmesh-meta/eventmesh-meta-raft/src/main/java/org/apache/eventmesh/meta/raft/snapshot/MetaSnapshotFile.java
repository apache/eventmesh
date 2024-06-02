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

package org.apache.eventmesh.meta.raft.snapshot;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetaSnapshotFile {

    private String path;

    public MetaSnapshotFile(String path) {
        super();
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }

    /**
     * Save value to snapshot file.
     */
    public boolean save(final String str) {
        try {
            FileUtils.writeStringToFile(new File(path), str, Charset.forName("UTF-8"));
            return true;
        } catch (IOException e) {
            log.error("Fail to save snapshot", e);
            return false;
        }
    }

    public String load() throws IOException {
        final String s = FileUtils.readFileToString(new File(path), Charset.forName("UTF-8"));
        if (!StringUtils.isBlank(s)) {
            return s;
        }
        throw new IOException("Fail to load snapshot from " + path + ",content: " + s);
    }
}
