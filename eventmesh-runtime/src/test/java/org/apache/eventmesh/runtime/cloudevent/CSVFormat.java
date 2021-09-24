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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.runtime.cloudevent;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.rw.CloudEventDataMapper;
import io.cloudevents.types.Time;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class CSVFormat implements EventFormat {

    public static final CSVFormat INSTANCE = new CSVFormat();

    @Override
    public byte[] serialize(CloudEvent event) {
        return String.join(
            ",",
            event.getSpecVersion().toString(),
            event.getId(),
            event.getType(),
            event.getSource().toString(),
            Objects.toString(event.getDataContentType()),
            Objects.toString(event.getDataSchema()),
            Objects.toString(event.getSubject()),
            event.getTime() != null
                ? Time.writeTime(event.getTime())
                : "null",
            event.getData() != null
                ? new String(Base64.getEncoder().encode(event.getData().toBytes()), StandardCharsets.UTF_8)
                : "null"
        ).getBytes();
    }

    @Override
    public CloudEvent deserialize(byte[] bytes, CloudEventDataMapper mapper) {
        String[] splitted = new String(bytes, StandardCharsets.UTF_8).split(Pattern.quote(","));
        SpecVersion sv = SpecVersion.parse(splitted[0]);

        String id = splitted[1];
        String type = splitted[2];
        URI source = URI.create(splitted[3]);
        String datacontenttype = splitted[4].equals("null") ? null : splitted[4];
        URI dataschema = splitted[5].equals("null") ? null : URI.create(splitted[5]);
        String subject = splitted[6].equals("null") ? null : splitted[6];
        OffsetDateTime time = splitted[7].equals("null") ? null : Time.parseTime(splitted[7]);
        byte[] data = splitted[8].equals("null") ? null : Base64.getDecoder().decode(splitted[8].getBytes());

        CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(sv)
            .withId(id)
            .withType(type)
            .withSource(source);

        if (datacontenttype != null) {
            builder.withDataContentType(datacontenttype);
        }
        if (dataschema != null) {
            builder.withDataSchema(dataschema);
        }
        if (subject != null) {
            builder.withSubject(subject);
        }
        if (time != null) {
            builder.withTime(time);
        }
        if (data != null) {
            builder.withData(mapper.map(BytesCloudEventData.wrap(data)));
        }
        return builder.build();
    }

    @Override
    public Set<String> deserializableContentTypes() {
        return Collections.singleton(serializedContentType());
    }

    @Override
    public String serializedContentType() {
        return "application/cloudevents+csv";
    }
}
