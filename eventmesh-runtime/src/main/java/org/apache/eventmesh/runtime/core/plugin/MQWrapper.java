/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.eventmesh.runtime.core.plugin;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

public abstract class MQWrapper {

    public static final String EVENT_STORE_ROCKETMQ = "rocketmq";

    public static final String EVENT_STORE_DEFIBUS = "defibus";

    public static String CURRENT_EVENT_STORE = EVENT_STORE_DEFIBUS;

    public static final String EVENT_STORE_CONF = System.getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));

    static {
        if (StringUtils.isNotBlank(EVENT_STORE_CONF)) {
            CURRENT_EVENT_STORE = EVENT_STORE_CONF;
        }
    }

    public AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

}
