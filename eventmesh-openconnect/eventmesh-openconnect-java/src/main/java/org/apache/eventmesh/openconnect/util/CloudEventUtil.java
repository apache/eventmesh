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

package org.apache.eventmesh.openconnect.util;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.LogUtil;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventUtil {

    public static CloudEvent convertRecordToEvent(ConnectRecord connectRecord) {
        final CloudEventBuilder cloudEventBuilder = CloudEventBuilder.v1().withData((byte[]) connectRecord.getData());
        Optional.ofNullable(connectRecord.getExtensions()).ifPresent((extensions) -> extensions.keySet().forEach(key -> {
            switch (key) {
                case "id":
                    cloudEventBuilder.withId(connectRecord.getExtension(key));
                    break;
                case "topic":
                    cloudEventBuilder.withSubject(connectRecord.getExtension(key));
                    break;
                case "source":
                    try {
                        cloudEventBuilder.withSource(new URI(connectRecord.getExtension(key)));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "type":
                    cloudEventBuilder.withType(connectRecord.getExtension(key));
                    break;
                default:
                    if (validateExtensionType(connectRecord.getExtensionObj(key))) {
                        cloudEventBuilder.withExtension(key, connectRecord.getExtension(key));
                    }
            }
        }));
        return cloudEventBuilder.build();
    }

    public static ConnectRecord convertEventToRecord(CloudEvent event) {
        byte[] body = Objects.requireNonNull(event.getData()).toBytes();
        LogUtil.info(log, "handle receive events {}", () -> new String(event.getData().toBytes(), Constants.DEFAULT_CHARSET));

        ConnectRecord connectRecord = new ConnectRecord(null, null, System.currentTimeMillis(), body);
        for (String extensionName : event.getExtensionNames()) {
            connectRecord.addExtension(extensionName, Objects.requireNonNull(event.getExtension(extensionName)).toString());
        }
        connectRecord.addExtension("id", event.getId());
        connectRecord.addExtension("topic", event.getSubject());
        connectRecord.addExtension("source", event.getSource().toString());
        connectRecord.addExtension("type", event.getType());
        connectRecord.addExtension("datacontenttype", event.getDataContentType());
        return connectRecord;
    }

    public static boolean validateExtensionType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean
            || obj instanceof URI || obj instanceof OffsetDateTime || obj instanceof byte[];
    }

}
