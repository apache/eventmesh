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
    String LARK_TEMPLATE_TYPE = "larktemplatetype";

    /**
     * The value format is {@code id,name;id,name;}. <br/>
     * Recommend to use {@code open_id} as {@code id}. <br/>
     * To prevent bad situations, you should ensure that the {@code id} is valid
     */
    String LARK_AT_USERS = "larkatusers";

    /**
     * true or false
     */
    String LARK_AT_ALL = "larkatall";

    String LARK_MARKDOWN_MESSAGE_TITLE = "larkmarkdownmessagetitle";
}
