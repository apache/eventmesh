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
	 * 厂商调用的path。厂商事件调用地址、 [http or https ]://[域名 or IP 【厂商可以被调用】]:[端口]/webhook/[callbackPath]
	 * 比如：http://127.0.0.1:10504/webhook/test/event 需要把全完url填入厂商调用输入中
	 * callbackPath 是唯一
	 * manufacturer callback path
	 */
    private String callbackPath;

    /**
     * 厂商的名字
     * manufacturer name ,like github
     */
    private String manufacturerName;

    /**
     * 厂商的事件名
     * webhook event name ,like rep-push
     */
    private String manufacturerEventName;

    /**
     * 
     * http header content type
     */
    private String contentType = "application/json";

    /**
     * 说明
     * description of this WebHookConfig
     */
    private String description;

    /**
     * 有一些厂商使用验签方式，
     * secret key ,for authentication
     */
    private String secret;

    /**
     *  有一些厂商使用验签方式，使用账户密码方式
     * userName ,for HTTP authentication
     */
    private String userName;

    /**
     *  有一些厂商使用验签方式，使用账户密码方式
     * password ,for HTTP authentication
     */
    private String password;

    /**
     * 
     * roll out protocol ,like http/kafka
     */
    private String cloudEventProtocol;

    /**
     * roll out addr
     */
    private String cloudEventServiceAddress;

    /**
     * 事件发送到那个topic
     * roll out event name ,like topic to mq
     */
    private String cloudEventName;

    /**
     * roll out data format -> CloudEvent serialization mode
     * If HTTP protocol is used, the request header contentType needs to be marked
     */
    private String dataContentType = "application/json";;

    /**
     * source of event
     */
    private String cloudEventSource;

    /**
     * cloudEvent事件对象唯一标识符识别方式，uuid或者厂商id
     * id of cloudEvent ,like uuid/manufacturerEventId
     */
    private String cloudEventIdGenerateMode = "manufacturerEventId";
}
