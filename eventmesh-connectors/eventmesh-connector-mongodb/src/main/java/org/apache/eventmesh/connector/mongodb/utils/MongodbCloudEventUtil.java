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

package org.apache.eventmesh.connector.mongodb.utils;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.mongodb.constant.MongodbConstants;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.bson.Document;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

public class MongodbCloudEventUtil {

    public static CloudEvent convertToCloudEvent(Document document) throws Exception {
        document.remove("_id");
        String versionStr = document.getString(MongodbConstants.CLOUD_EVENT_DOC_VERSION);
        SpecVersion version = SpecVersion.valueOf(versionStr);
        CloudEventBuilder builder;
        switch (version) {
            case V03:
                builder = CloudEventBuilder.v03();
                break;
            case V1:
                builder = CloudEventBuilder.v1();
                break;
            default:
                throw new Exception(String.format("CloudEvent version %s does not support.", version));
        }
        builder.withData(document.remove(MongodbConstants.CLOUD_EVENT_DOC_DATA).toString().getBytes(Constants.DEFAULT_CHARSET))
            .withId(document.remove(MongodbConstants.CLOUD_EVENT_DOC_ID).toString())
            .withSource(URI.create(document.remove(MongodbConstants.CLOUD_EVENT_DOC_SOURCE).toString()))
            .withType(document.remove(MongodbConstants.CLOUD_EVENT_DOC_TYPE).toString())
            .withDataContentType(document.remove(MongodbConstants.CLOUD_EVENT_DOC_DATACONTENTTYPE).toString())
            .withSubject(document.remove(MongodbConstants.CLOUD_EVENT_DOC_SUBJECT).toString());
        document.forEach((key, value) -> builder.withExtension(key, value.toString()));

        return builder.build();
    }

    public static Document convertToDocument(CloudEvent cloudEvent) {
        Document document = new Document();
        document.put(MongodbConstants.CLOUD_EVENT_DOC_VERSION, cloudEvent.getSpecVersion().name());
        document.put(MongodbConstants.CLOUD_EVENT_DOC_DATA, cloudEvent.getData() == null
            ? null
            : new String(cloudEvent.getData().toBytes(), StandardCharsets.UTF_8));
        document.put(MongodbConstants.CLOUD_EVENT_DOC_ID, cloudEvent.getId());
        document.put(MongodbConstants.CLOUD_EVENT_DOC_SOURCE, cloudEvent.getSource().toString());
        document.put(MongodbConstants.CLOUD_EVENT_DOC_TYPE, cloudEvent.getType());
        document.put(MongodbConstants.CLOUD_EVENT_DOC_DATACONTENTTYPE, cloudEvent.getDataContentType());
        document.put(MongodbConstants.CLOUD_EVENT_DOC_SUBJECT, cloudEvent.getSubject());
        cloudEvent.getExtensionNames().forEach(key -> document.put(key, cloudEvent.getExtension(key)));

        return document;
    }
}
