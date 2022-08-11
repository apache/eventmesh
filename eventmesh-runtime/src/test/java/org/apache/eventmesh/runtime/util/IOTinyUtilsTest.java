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
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

import org.apache.eventmesh.runtime.constants.EventMeshConstants;
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
    public void testToString() throws IOException {
        File temp = null;
        try {
            temp = File.createTempFile("temp", ".txt");
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            bw.write("test toString");
            bw.close();
            String res = IOTinyUtils.toString(Files.newInputStream(temp.toPath()), EventMeshConstants.DEFAULT_CHARSET);
            Assert.assertEquals("test toString", res);
        } finally {
            Assert.assertNotNull(temp);
            temp.delete();
        }
    }

    @Test
    public void testCopyFile() {
    }

    @Test
    public void testCleanDirectory() throws IOException {
        // file is not exist
        File dirFile1 = mock(File.class);
        when(dirFile1.exists()).thenReturn(false);
        Assert.assertThrows(IllegalArgumentException.class, () -> IOTinyUtils.cleanDirectory(dirFile1));

        // file is not a directory
        File dirFile2 = mock(File.class);
        when(dirFile2.exists()).thenReturn(true);
        when(dirFile2.isDirectory()).thenReturn(false);
        when(dirFile2.delete()).thenReturn(true);
        Assert.assertThrows(IllegalArgumentException.class, () -> IOTinyUtils.cleanDirectory(dirFile2));

        // directory is empty
        File dirFile3 = mock(File.class);
        when(dirFile3.exists()).thenReturn(true);
        when(dirFile3.isDirectory()).thenReturn(true);
        when(dirFile3.listFiles()).thenReturn(null);
        Assert.assertThrows(IOException.class, () -> IOTinyUtils.cleanDirectory(dirFile3));

        // clean directory failed
        File dirFile4 = mock(File.class);
        File[] files4 = {dirFile3};
        when(dirFile4.exists()).thenReturn(true);
        when(dirFile4.isDirectory()).thenReturn(true);
        when(dirFile4.listFiles()).thenReturn(files4);
        Assert.assertThrows(IOException.class, () -> IOTinyUtils.cleanDirectory(dirFile4));

        // successfully clean directory
        File dirFile5 = mock(File.class);
        File[] files5 = {dirFile2, null};
        when(dirFile5.exists()).thenReturn(true);
        when(dirFile5.isDirectory()).thenReturn(true);
        when(dirFile5.listFiles()).thenReturn(files5);
        IOTinyUtils.cleanDirectory(dirFile5);
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