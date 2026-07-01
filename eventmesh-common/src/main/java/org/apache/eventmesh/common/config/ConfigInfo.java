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

package org.apache.eventmesh.common.config;

import java.lang.reflect.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigInfo<T> {

    public static final String HUMP_SPOT = "spot";
    public static final String HUMP_ROD = "rod";

    private String path;
    private String field;
    private String prefix;
    private String hump;
    private boolean monitor;
    private boolean removePrefix;

    private Class<? super T> clazz;
    private Object object;
    private String filePath;

    private String reloadMethodName;

    Field objectField;
    Object instance;

    private String resourceUrl;
}