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

package org.apache.eventmesh.runtime.core.protocol.cloudevent.impl;

import io.cloudevents.SpecVersion;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.core.message.impl.BaseGenericBinaryMessageReaderImpl;

import java.util.Objects;
import java.util.Properties;
import java.util.function.BiConsumer;

/**
 * binary message reader
 */
public class OMSBinaryMessageReader extends BaseGenericBinaryMessageReaderImpl<String, String> {

    private final Properties headers;

    public OMSBinaryMessageReader(SpecVersion version, Properties headers, byte[] payload) {
        super(version, payload != null && payload.length > 0 ? BytesCloudEventData.wrap(payload) : null);

        Objects.requireNonNull(headers);
        this.headers = headers;
    }

    /**
     * whether header key is content type
     * @param key
     * @return
     */
    @Override
    protected boolean isContentTypeHeader(String key) {
        return key.equals(OMSHeaders.CONTENT_TYPE);
    }

    /**
     * whether message header is cloudEvent header
     * @param key
     * @return
     */
    @Override
    protected boolean isCloudEventsHeader(String key) {
        return key.length() > 3 && key.substring(0, OMSHeaders.CE_PREFIX.length()).startsWith(OMSHeaders.CE_PREFIX);
    }

    /**
     * parse message header to cloudEvent attribute
     * @param key
     * @return
     */
    @Override
    protected String toCloudEventsKey(String key) {
        return key.substring(OMSHeaders.CE_PREFIX.length()).toLowerCase();
    }

    /**
     *
     * @param fn
     */
    @Override
    protected void forEachHeader(BiConsumer<String, String> fn) {
        this.headers.forEach((k, v) -> {
            if (k != null && v != null) {
                fn.accept(k.toString(), v.toString());
            }

        });
    }

    @Override
    protected String toCloudEventsValue(String value) {
        return value;
    }
}
