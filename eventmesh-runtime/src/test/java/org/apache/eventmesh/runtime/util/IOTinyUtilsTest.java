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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedConstruction;

public class IOTinyUtilsTest {

    @Test
    public void testCopy() throws Exception {
        BufferedReader input = mock(BufferedReader.class);
        BufferedWriter output = mock(BufferedWriter.class);
        int count = 10;
        char[] buffer = new char[1 << 12];
        doNothing().when(output).write(buffer, 0, count);
        when(input.read(buffer)).thenReturn(10, 10, -1);
        long result = IOTinyUtils.copy(input, output);
        Assert.assertEquals(result, count * 2);
    }

    @Test
    public void testReadLines() throws IOException {
        BufferedReader input = mock(BufferedReader.class);
        when(input.readLine()).thenReturn("hello", "world", null);
        List<String> result = IOTinyUtils.readLines(input);
        Assert.assertEquals(result.get(0), "hello");
    }

    @Test
    public void testCopyFile() {
    }

    @Test
    public void testCleanDirectory() throws IOException {
        File dirFile = mock(File.class);
        when(dirFile.exists()).thenReturn(true);
        when(dirFile.isDirectory()).thenReturn(true);

        File normalFile = mock(File.class);
        when(normalFile.exists()).thenReturn(true);
        when(normalFile.isDirectory()).thenReturn(false);
        when(normalFile.delete()).thenReturn(true);

        File[] files = {normalFile};
        when(dirFile.listFiles()).thenReturn(files);
        IOTinyUtils.cleanDirectory(dirFile);
    }

    @Test
    public void testWriteStringToFile() throws IOException {
        File file = mock(File.class);
        try (MockedConstruction<FileOutputStream> ignored = mockConstruction(FileOutputStream.class,
            (mock, context) -> doNothing().when(mock).write(any()))) {
            IOTinyUtils.writeStringToFile(file, "data", "utf-8");
        }
    }
}