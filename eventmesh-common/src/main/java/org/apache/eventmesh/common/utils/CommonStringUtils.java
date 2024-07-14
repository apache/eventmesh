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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A string utils as supplement of org.apache.commons.lang3.StringUtils
 */
public class CommonStringUtils extends StringUtils {

    /**
     * Compares given string to a CharSequences vararg of searchStrings,
     * returning true if the string is equal to all of the searchStrings.
     *
     * CommonStringUtils.equalsAll("abc", "abc", "def")  = false
     * CommonStringUtils.equalsAll(null, "abc", "def")  = false
     * CommonStringUtils.equalsAll(null, (CharSequence[]) null) = true
     * CommonStringUtils.equalsAll(null, null, null)    = true
     * CommonStringUtils.equalsAll("abc", "abc", "abc")  = true
     *
     * @param string
     * @param searchStrings
     * @return
     */
    public static boolean equalsAll(final CharSequence string, final CharSequence... searchStrings) {
        if (ArrayUtils.isNotEmpty(searchStrings)) {
            for (final CharSequence next : searchStrings) {
                if (!equals(string, next)) {
                    return false;
                }
            }
        }
        return true;
    }
}
