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

import org.apache.eventmesh.common.utils.ThreadUtils;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class WatchFileManagerTest {

    @Test
    public void testWatchFile() throws IOException {
        String filePathString = WatchFileManagerTest.class.getResource("/configuration.properties").getFile();
        File f = new File(filePathString);
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
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            Properties properties = new Properties();
            properties.load(reader);
            properties.setProperty("eventMesh.server.newAdd", "newAdd");
            properties.store(writer, "newAdd");
        } catch (IOException e) {
            Assert.fail("Test failed to read from or write to configuration.properties file");
        } finally {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
        }

        ThreadUtils.sleep(500, TimeUnit.MILLISECONDS);
    }
}
