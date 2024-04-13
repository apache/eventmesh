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
import java.nio.file.Files;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class WatchFileManagerTest {

    @TempDir
    File tempConfigDir;

    @Test
    public void testWatchFile() throws IOException, InterruptedException {
        String file = WatchFileManagerTest.class.getResource("/configuration.properties").getFile();
        File configFile = new File(file);
        File tempConfigFile = new File(tempConfigDir, "configuration.properties");
        Files.copy(configFile.toPath(), tempConfigFile.toPath());

        final FileChangeListener mockFileChangeListener = Mockito.mock(FileChangeListener.class);
        Mockito.when(mockFileChangeListener.support(
                Mockito.argThat(isFileUnderTest(tempConfigFile.getParent(), tempConfigFile.getName())))
        ).thenReturn(true);

        WatchFileManager.registerFileChangeListener(tempConfigFile.getParent(), mockFileChangeListener);

        Properties properties = new Properties();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(tempConfigFile))) {
            properties.load(bufferedReader);
        }

        try (FileWriter fw = new FileWriter(tempConfigFile)) {
            properties.setProperty("eventMesh.server.newAdd", "newAdd");
            properties.store(fw, "newAdd");
        }

        Mockito.verify(mockFileChangeListener, Mockito.timeout(15_000).atLeastOnce())
                .onChanged(Mockito.argThat(isFileUnderTest(tempConfigFile.getParent(), tempConfigFile.getName())));
    }

    private ArgumentMatcher<FileChangeContext> isFileUnderTest(String directoryPath, String fileName) {
        return argument -> argument.getDirectoryPath().equals(directoryPath) && argument.getFileName().equals(fileName);
    }
}
