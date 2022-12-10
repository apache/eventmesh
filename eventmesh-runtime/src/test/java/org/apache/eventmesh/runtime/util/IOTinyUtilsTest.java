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

package org.apache.eventmesh.runtime.util;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import org.apache.commons.io.file.Counters;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.file.StandardDeleteOption;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class IOTinyUtilsTest {

    @Test
    public void testCopy() throws Exception {
        try (BufferedReader input = mock(BufferedReader.class);
             BufferedWriter output = mock(BufferedWriter.class)) {
            int count = 10;
            char[] buffer = new char[1 << 12];
            doNothing().when(output).write(buffer, 0, count);
            when(input.read(buffer)).thenReturn(10, 10, -1);
            long result = IOTinyUtils.copy(input, output);
            Assert.assertEquals(result, count * 2);
        }
    }

    @Test
    public void testReadLines() throws IOException {
        try (BufferedReader input = mock(BufferedReader.class)) {
            when(input.readLine()).thenReturn("hello", "world", null);
            List<String> result = IOTinyUtils.readLines(input);
            Assert.assertEquals(result.get(0), "hello");
        }
    }

    @Test
    public void testToString() throws IOException {
        File temp = null;
        try {
            temp = File.createTempFile("temp", ".txt");
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temp), StandardCharsets.UTF_8))) {
                bw.write("test toString");
            }

            String res = IOTinyUtils.toString(Files.newInputStream(temp.toPath()), EventMeshConstants.DEFAULT_CHARSET);
            Assert.assertEquals("test toString", res);
        } finally {
            Assert.assertNotNull(temp);
            temp.delete();
        }
    }

    //@Test
    //public void testCopyFile() {
    //}

    @Test
    public void testCleanDirectory() throws IOException {
        MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class);
        filesMockedStatic.when(() -> Files.isDirectory(any(), any())).thenReturn(true);


        // file is not exist
        File dirFile1 = mock(File.class);
        when(dirFile1.exists()).thenReturn(false);
        when(dirFile1.getName()).thenReturn("file1");
        Assert.assertThrows(IllegalArgumentException.class, () -> IOTinyUtils.cleanDirectory(dirFile1));

        // file is not a directory
        File dirFile2 = mock(File.class);
        when(dirFile2.exists()).thenReturn(true);
        when(dirFile2.isDirectory()).thenReturn(false);
        when(dirFile2.delete()).thenReturn(true);
        when(dirFile2.getName()).thenReturn("file2");
        Assert.assertThrows(IllegalArgumentException.class, () -> IOTinyUtils.cleanDirectory(dirFile2));

        // directory is empty
        File dirFile3 = mock(File.class);
        when(dirFile3.exists()).thenReturn(true);
        when(dirFile3.isDirectory()).thenReturn(true);
        when(dirFile3.listFiles()).thenReturn(null);
        when(dirFile3.getName()).thenReturn("files3");
        Assert.assertThrows(IOException.class, () -> IOTinyUtils.cleanDirectory(dirFile3));

        // clean directory failed
        File dirFile4 = mock(File.class);
        File[] files4 = {dirFile3};
        when(dirFile4.exists()).thenReturn(true);
        when(dirFile4.isDirectory()).thenReturn(true);
        when(dirFile4.listFiles()).thenReturn(files4);
        when(dirFile4.getName()).thenReturn("files4");
        Assert.assertThrows(IOException.class, () -> IOTinyUtils.cleanDirectory(dirFile4));

        // successfully clean directory
        File dirFile5 = mock(File.class);
        File[] files5 = {dirFile2};
        when(dirFile5.exists()).thenReturn(true);
        when(dirFile5.isDirectory()).thenReturn(true);
        when(dirFile5.listFiles()).thenReturn(files5);
        when(dirFile5.getName()).thenReturn("files5");

        MockedStatic<PathUtils> pathUtilsMockedStatic = Mockito.mockStatic(PathUtils.class);
        pathUtilsMockedStatic.when(() -> PathUtils.delete(any(), any(), any())).thenReturn(new Counters.PathCounters() {

            @Override
            public Counters.Counter getByteCounter() {
                return null;
            }

            @Override
            public Counters.Counter getDirectoryCounter() {
                return null;
            }

            @Override
            public Counters.Counter getFileCounter() {
                Counters.Counter counter = Counters.longCounter();
                counter.increment();
                return counter;
            }
        });

        IOTinyUtils.cleanDirectory(dirFile5);
    }

    @Test
    public void testWriteStringToFile() {
        File file = mock(File.class);
        try (MockedConstruction<FileOutputStream> ignored = mockConstruction(FileOutputStream.class,
                (mock, context) -> doNothing().when(mock).write(any()))) {

            IOTinyUtils.writeStringToFile(file, "data", "utf-8");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }
}