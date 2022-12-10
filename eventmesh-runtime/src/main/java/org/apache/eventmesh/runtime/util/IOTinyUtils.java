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

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class IOTinyUtils {

    public static String toString(final InputStream inputStream, final String charsetName) throws IOException {
        return IOUtils.toString(inputStream, Charsets.toCharset(charsetName));
    }

    public static String toString(final Reader reader) throws IOException {
        return IOUtils.toString(reader);
    }

    public static long copy(final Reader reader, final Writer writer) throws IOException {
        return IOUtils.copy(reader, writer);
    }

    public static List<String> readLines(final Reader reader) throws IOException {
        return IOUtils.readLines(reader);
    }

    private static BufferedReader toBufferedReader(final Reader reader) {
        return IOUtils.toBufferedReader(reader);
    }

    public static void copyFile(final String src, final String dest) throws IOException {
        final File srcFile = new File(src);
        final File destFile = new File(dest);
        FileUtils.copyFile(srcFile, destFile);
    }

    public static void delete(final File file) throws IOException {

        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            FileUtils.deleteDirectory(file);
        }

        FileUtils.delete(file);
    }

    public static void cleanDirectory(final File directory) throws IOException {
        FileUtils.cleanDirectory(directory);
    }

    public static void writeStringToFile(final File file, final String data, final String charset) throws IOException {
        FileUtils.writeStringToFile(file, data, charset);
    }
}
