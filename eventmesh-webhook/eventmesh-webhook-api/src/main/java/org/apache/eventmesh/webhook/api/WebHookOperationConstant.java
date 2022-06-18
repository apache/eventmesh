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

package org.apache.eventmesh.webhook.api;

import java.io.File;

/**
 * Webhook constant class
 * @author Jelly Mai
 */
public class WebHookOperationConstant {

    public static final String FILE_SEPARATOR = File.separator;

    public static final String FILE_EXTENSION = ".json";

    public static final String GROUP_PREFIX = "webhook_";

    public static final String CALLBACK_PATH_PREFIX = "/webhook" ;

    public static final String DATA_ID_EXTENSION = ".json";

    public static final String MANUFACTURERS_DATA_ID = "manufacturers" + DATA_ID_EXTENSION;

    public static final long TIMEOUT_MS = 3 * 1000L;

}
