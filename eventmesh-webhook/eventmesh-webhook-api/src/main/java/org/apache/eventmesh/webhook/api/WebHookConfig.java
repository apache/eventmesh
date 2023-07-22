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

import lombok.Data;

@Data
public class WebHookConfig {

    /**
     * manufacturer callback path
     */
    private String callbackPath;

    /**
     * manufacturer name ,like github
     */
    private String manufacturerName;

    /**
     * manufacturer domain name, like www.github.com
     */
    private String manufacturerDomain;

    /**
     * webhook event name ,like rep-push
     */
    private String manufacturerEventName;

    /**
     * http header content type
     */
    private String contentType = "application/json";

    /**
     * description of this WebHookConfig
     */
    private String description;

    /**
     * secret key ,for authentication
     */
    private String secret;

    /**
     * userName ,for HTTP authentication
     */
    private String userName;

    /**
     * password ,for HTTP authentication
     */
    private String password;

    /**
     * roll out protocol ,like http/kafka
     */
    private String cloudEventProtocol;

    /**
     * roll out addr
     */
    private String cloudEventServiceAddress;

    /**
     * roll out event name ,like topic to mq
     */
    private String cloudEventName;

    /**
     * roll out data format -> CloudEvent serialization mode If HTTP protocol is used, the request header contentType needs to be marked
     */
    private String dataContentType = "application/json";

    /**
     * source of event
     */
    private String cloudEventSource;

    /**
     * id of cloudEvent ,like uuid/manufacturerEventId
     */
    private String cloudEventIdGenerateMode = "manufacturerEventId";
}
