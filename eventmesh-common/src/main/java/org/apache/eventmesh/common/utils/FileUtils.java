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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class FileUtils {

    /**
     * Get the suffix of file name.
     * For example, "/.../foo.yml" will return ".yml"
     *
     * @param filename
     * @return
     */
    public static String getExtension(final String filename) {
        if (StringUtils.isBlank(filename)) {
            return "";
        }
        final int lastSeparator = filename.lastIndexOf(File.separator);
        final int extensionPos = filename.lastIndexOf(Constants.DOT);
        final int index = lastSeparator > extensionPos ? -1 : extensionPos;
        if (index == -1) {
            return "";
        } else {
            return filename.substring(index);
        }
    }

    /**
     * When a certain type of configuration file does not exist, try to find other types of configuration files.
     * Like ".properties", ".yaml" or ".yml".
     * Return the new file path if exists, otherwise return "".
     *
     * @param filePath
     * @param file
     * @return
     */
    public static String tryToFindOtherPropFile(String filePath, File file) {
        String[] fileSuffixes = {".properties", ".yaml", ".yml"};
        int i = 0;
        while (i < fileSuffixes.length) {
            String fileSuffix = filePath.substring(filePath.lastIndexOf(Constants.DOT));
            if (!StringUtils.equals(fileSuffix, fileSuffixes[i])) {
                filePath = filePath.replace(fileSuffix, fileSuffixes[i]);
                file = new File(filePath);
                if (file.exists()) {
                    return filePath;
                }
            }
            i++;
        }
        return "";
    }
}
