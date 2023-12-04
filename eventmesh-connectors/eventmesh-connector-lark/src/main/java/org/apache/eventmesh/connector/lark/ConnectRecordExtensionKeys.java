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

package org.apache.eventmesh.connector.lark;

/**
 * Constants of record extension key.
 */
public interface ConnectRecordExtensionKeys {

    /**
     * {@code text} or {@code markdown}, otherwise use {@code text} to replace.
     */
    String TEMPLATE_TYPE_4_LARK = "templatetype4lark";

    /**
     * The value format is {@code id,name;id,name;}. <br/>
     * Recommend to use {@code open_id} as {@code id}. <br/>
     * To prevent bad situations, you should ensure that the {@code id} is valid
     */
    String AT_USERS_4_LARK = "atusers4lark";

    /**
     * true or false
     */
    String AT_ALL_4_LARK = "atall4lark";

    String MARKDOWN_MESSAGE_TITLE_4_LARK = "markdownmessagetitle4lark";
}
