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

public class EventMeshVersion {

    public static final String CURRENT_VERSION = Version.V3_0_0.name();

    public static String getCurrentVersionDesc() {
        return CURRENT_VERSION.replaceAll("V", "")
                .replaceAll("_", ".")
                .replaceAll("_SNAPSHOT", "-SNAPSHOT");
    }

    public enum Version {
        V3_0_0,
        V3_0_1,
        V3_1_0,
        V3_2_0,
        V3_3_0
    }
}
