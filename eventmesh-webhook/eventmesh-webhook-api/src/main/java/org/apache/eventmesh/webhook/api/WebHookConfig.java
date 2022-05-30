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

	private String callbackPath;

	/**
	 * 厂商名
	 */
	private String manufacturerName;

	/**
	 * 厂商的事件名
	 */
	private String manufacturerEventName;

	/**
	 * http header content type
	 */
	private String contentType;

	/**
	 * 说明
	 */
	private String description;

	/**
	 * 密钥，github需要密钥
	 */
	private String secret;

	/**
	 * 认证的用户名
	 */
	private String userName;

	/**
	 * 认证的密码
	 */
	private String password;

	/**
	 * 协议类型。http，kakfa
	 */
	private String cloudEventProtocol;

	/**
	 * 服务地址
	 */
	private String cloudEventServiceAddress;

	/**
	 * 事件名 消息队列就是topic
	 */
	private String cloudEventName;

	/**
	 * 如果是http协议就需要标记请求头 contentType的类型
	 */
	private String DataContentType;

	/**
	 * 事件的来源
	 */
	private String cloudEventSource;

	/**
	 * cloudEvent需要一个id： 支持uuid 支持使用厂商id
	 * 
	 */
	private String cloudEventIdGenerateMode;
}
