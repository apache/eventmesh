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

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import lombok.experimental.UtilityClass;

/**
 * Assert
 */
public final class AssertUtils {

    /**
     * Assert actual is not null
     *
     * @param actual  Object to test
     * @param message error message
     */
    public static void notNull(final Object actual, final String message) {
        isTrue(Objects.nonNull(actual), message);
    }

    /**
     * Assert actual is true
     *
     * @param actual  boolean to test
     * @param message error message
     */
    public static void isTrue(final Boolean actual, final String message) {
        if (!Boolean.TRUE.equals(actual)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Assert actual is not blank
     *
     * @param actual  String to test
     * @param message error message
     */
    public static void notBlank(final String actual, final String message) {
        isTrue(StringUtils.isNoneBlank(actual), message);
    }


}
