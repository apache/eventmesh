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

package org.apache.eventmesh.connector.jdbc.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JdbcStringUtils {

    /**
     * Checks whether the given string is wrapped with a specific set of characters.
     *
     * @param possiblyWrapped the string to check
     * @return true if the string is wrapped with characters '`', "'", or "\""; false otherwise
     */
    public static boolean isWrapped(String possiblyWrapped) {
        if (possiblyWrapped == null || possiblyWrapped.length() < 2) {
            return false;
        }
        char firstChar = possiblyWrapped.charAt(0);
        char lastChar = possiblyWrapped.charAt(possiblyWrapped.length() - 1);
        return (firstChar == '`' && lastChar == '`')
            || (firstChar == '\'' && lastChar == '\'')
            || (firstChar == '\"' && lastChar == '\"');
    }

    public static boolean isWrapped(char c) {
        return c == '\'' || c == '"' || c == '`';
    }

    public static String withoutWrapper(String possiblyWrapped) {
        return isWrapped(possiblyWrapped) ? possiblyWrapped.substring(1, possiblyWrapped.length() - 1) : possiblyWrapped;
    }

    /**
     * Compares two version numbers and returns the result as an integer.
     *
     * @param versionX The first version number to compare.
     * @param versionY The second version number to compare.
     * @return An integer value representing the comparison result: -1 if versionX is less than versionY, 0 if versionX is equal to versionY, 1 if
     * versionX is greater than versionY.
     */
    public static int compareVersion(String versionX, String versionY) {
        String[] firstVersionParts = versionX.split("\\.");
        String[] secondVersionParts = versionY.split("\\.");
        int maxLength = Math.max(firstVersionParts.length, secondVersionParts.length);
        for (int i = 0; i < maxLength; i++) {
            int firstVersionNumber = getPartAsNumber(firstVersionParts, i);
            int secondVersionNumber = getPartAsNumber(secondVersionParts, i);
            if (firstVersionNumber != secondVersionNumber) {
                return Integer.signum(firstVersionNumber - secondVersionNumber);
            }
        }
        return 0;
    }

    private static int getPartAsNumber(String[] parts, int index) {
        return index < parts.length ? Integer.parseInt(parts[index]) : 0;
    }
}
