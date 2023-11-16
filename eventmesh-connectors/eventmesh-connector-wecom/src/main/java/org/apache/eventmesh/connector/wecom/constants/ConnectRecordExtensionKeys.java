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

package org.apache.eventmesh.connector.wecom.constants;

/**
 * Constants of record extension key.
 */
public interface ConnectRecordExtensionKeys {

    String WECOM_MESSAGE_TEMPLATE_TYPE_KEY = "weCom:MessageTemplateTypeKey";

    String WECOM_TO_USER_ID = "weCom:toUserId";

    String WECOM_TO_PARTY_ID = "weCom:toPartyId";

    String WECOM_TO_TAG_ID = "weCom:toTagId";
}
