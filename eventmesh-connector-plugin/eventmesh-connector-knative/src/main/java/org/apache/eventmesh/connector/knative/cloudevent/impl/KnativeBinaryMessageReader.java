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

package org.apache.eventmesh.connector.knative.cloudevent.impl;

import java.util.function.BiConsumer;

import io.cloudevents.SpecVersion;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.core.message.impl.BaseGenericBinaryMessageReaderImpl;

public class KnativeBinaryMessageReader extends BaseGenericBinaryMessageReaderImpl<String, String> {

    protected KnativeBinaryMessageReader(SpecVersion version, byte[] body) {
        super(version, body != null && body.length > 0 ? BytesCloudEventData.wrap(body) : null);
    }

    @Override
    protected boolean isContentTypeHeader(String key) {
        return key.equals(KnativeHeaders.CONTENT_TYPE);
    }

    @Override
    protected boolean isCloudEventsHeader(String key) {
        return true;
    }

    @Override
    protected String toCloudEventsKey(String key) {
        return key.toLowerCase();
    }

    @Override
    protected void forEachHeader(BiConsumer<String, String> fn) {

    }

    @Override
    protected String toCloudEventsValue(String value) {
        return value;
    }
}
