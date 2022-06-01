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

package org.apache.eventmesh.common.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class WatchFileManagerTest {

    @Test
    public void testWatchFile() throws IOException, InterruptedException {
        String file = WatchFileManagerTest.class.getResource("/configuration.properties").getFile();
        File f = new File(file);
        final FileChangeListener fileChangeListener = new FileChangeListener() {
            @Override
            public void onChanged(FileChangeContext changeContext) {
                Assert.assertEquals(f.getName(), changeContext.getFileName());
                Assert.assertEquals(f.getParent(), changeContext.getDirectoryPath());
            }

            @Override
            public boolean support(FileChangeContext changeContext) {
                return changeContext.getWatchEvent().context().toString().contains(f.getName());
            }
        };
        WatchFileManager.registerFileChangeListener(f.getParent(), fileChangeListener);

        Properties properties = new Properties();
        properties.load(new BufferedReader(new FileReader(file)));
        properties.setProperty("eventMesh.server.newAdd", "newAdd");
        FileWriter fw = new FileWriter(file);
        properties.store(fw, "newAdd");

        Thread.sleep(500);
    }
}
