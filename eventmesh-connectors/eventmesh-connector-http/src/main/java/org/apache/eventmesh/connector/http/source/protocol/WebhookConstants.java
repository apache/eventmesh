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

package org.apache.eventmesh.connector.http.source.protocol;

public class WebhookConstants {

    /**
     * -------------------------------------- About GitHub --------------------------------------
     */

    // A globally unique identifier (GUID) to identify the delivery.
    public static final String GITHUB_DELIVERY = "X-GitHub-Delivery";

    // This header is sent if the webhook is configured with a secret.
    // We recommend that you use the more secure X-Hub-Signature-256 instead
    public static final String GITHUB_SIGNATURE = "X-Hub-Signature";

    // This header is sent if the webhook is configured with a secret
    public static final String GITHUB_SIGNATURE_256 = "X-Hub-Signature-256";

    public static final String GITHUB_HASH_265_PREFIX = "sha256=";

    // The name of the event that triggered the delivery.
    public static final String GITHUB_EVENT = "X-GitHub-Event";

    // The unique identifier of the webhook.
    public static final String GITHUB_HOOK_ID = "X-GitHub-Hook-ID";

    // The unique identifier of the resource where the webhook was created.
    public static final String GITHUB_HOOK_INSTALLATION_TARGET_ID = "X-GitHub-Hook-Installation-Target-ID";

    // The type of resource where the webhook was created.
    public static final String GITHUB_HOOK_INSTALLATION_TARGET_TYPE = "X-GitHub-Hook-Installation-Target-Type";

}
