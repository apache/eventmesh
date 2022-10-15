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

package org.apache.eventmesh.runtime.core.protocol.amqp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Name Util.
 */
public class NameUtils {
    public static final Pattern NAMED_PATTERN = Pattern.compile("^[-._@\\w]{3,256}$");

    //可包含字母、数字、短划线（-）、下划线（_）、点（.）和 @符号
    //长度限制3-256个字符之间
    public static void checkName(String name) {
        Matcher m = NAMED_PATTERN.matcher(name);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid named : " + name);
        }
    }
}
