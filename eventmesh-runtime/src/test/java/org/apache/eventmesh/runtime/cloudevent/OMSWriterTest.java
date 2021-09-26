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
import io.cloudevents.core.message.StructuredMessageReader;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;
import io.cloudevents.types.Time;
import io.openmessaging.api.Message;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.OMSMessageFactory;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.impl.OMSHeaders;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class OMSWriterTest {

    private static final String PREFIX_TEMPLATE = OMSHeaders.CE_PREFIX + "%s";
    private static final String DATACONTENTTYPE_NULL = null;
    private static final byte[] DATAPAYLOAD_NULL = null;


    @Test
    public void testRequestWithStructured() {
        Stream<CloudEvent> cloudEventStream = Data.allEventsWithoutExtensions();
        //String expectedContentType = CSVFormat.INSTANCE.serializedContentType();
        cloudEventStream.forEach(event -> {
            byte[] expectedBuffer = CSVFormat.INSTANCE.serialize(event);

            String topic = "test";
            String keys = "keys";
            String tags = "tags";

            Message message = StructuredMessageReader
                    .from(event, CSVFormat.INSTANCE)
                    .read(OMSMessageFactory.createWriter(topic, keys, tags));

            assertThat(message.getTopic())
                    .isEqualTo(topic);
            assertThat(message.getKey())
                    .isEqualTo(keys);
            assertThat(message.getTag())
                    .isEqualTo(tags);
            assertThat(message.getBody())
                    .isEqualTo(expectedBuffer);
        });

    }

    @Test
    public void testRequestWithBinary() {
        Stream<Arguments> argumentsStream = binaryTestArguments();
        String topic = "test";
        String keys = "keys";
        String tags = "tags";
        argumentsStream.forEach(argument -> {
            Message message = OMSMessageFactory
                    .createWriter(topic, keys, tags)
                    .writeBinary(argument.cloudEvent);

            assertThat(message.getTopic())
                    .isEqualTo(topic);
            assertThat(message.getKey())
                    .isEqualTo(keys);
            assertThat(message.getTag())
                    .isEqualTo(tags);
            assertThat(message.getBody())
                    .isEqualTo(argument.body);
            assertThat(message.getUserProperties()
                    .keySet().containsAll(argument.properties.keySet()));
            assertThat(message.getUserProperties()
                    .values().containsAll(argument.properties.values()));


        });

    }

    private Stream<Arguments> binaryTestArguments() {

        return Stream.of(
                // V03
                new Arguments(
                        Data.V03_MIN,
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property("ignored", "ignore")
                        ),
                        DATAPAYLOAD_NULL
                ),
                new Arguments(
                        Data.V03_WITH_JSON_DATA,
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV03.SCHEMAURL, Data.DATASCHEMA.toString()),
                                property(CloudEventV03.SUBJECT, Data.SUBJECT),
                                property(CloudEventV03.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignore")
                        ),
                        Data.DATA_JSON_SERIALIZED

                ),
                new Arguments(
                        Data.V03_WITH_JSON_DATA_WITH_EXT_STRING,
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV03.SCHEMAURL, Data.DATASCHEMA.toString()),
                                property(CloudEventV03.SUBJECT, Data.SUBJECT),
                                property(CloudEventV03.TIME, Time.writeTime(Data.TIME)),
                                property("astring", "aaa"),
                                property("aboolean", "true"),
                                property("anumber", "10"),
                                property("ignored", "ignored")
                        ),
                        Data.DATA_JSON_SERIALIZED

                ),
                new Arguments(
                        Data.V03_WITH_XML_DATA,
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV03.SUBJECT, Data.SUBJECT),
                                property(CloudEventV03.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),

                        Data.DATA_XML_SERIALIZED

                ),
                new Arguments(
                        Data.V03_WITH_TEXT_DATA,
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV03.SUBJECT, Data.SUBJECT),
                                property(CloudEventV03.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),

                        Data.DATA_TEXT_SERIALIZED

                ),
                // V1
                new Arguments(
                        Data.V1_MIN,
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property("ignored", "ignored")
                        ),

                        DATAPAYLOAD_NULL

                ),
                new Arguments(
                        Data.V1_WITH_JSON_DATA,
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV1.DATASCHEMA, Data.DATASCHEMA.toString()),
                                property(CloudEventV1.SUBJECT, Data.SUBJECT),
                                property(CloudEventV1.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),

                        Data.DATA_JSON_SERIALIZED

                ),
                new Arguments(
                        Data.V1_WITH_JSON_DATA_WITH_EXT_STRING,
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV1.DATASCHEMA, Data.DATASCHEMA.toString()),
                                property(CloudEventV1.SUBJECT, Data.SUBJECT),
                                property(CloudEventV1.TIME, Time.writeTime(Data.TIME)),
                                property("astring", "aaa"),
                                property("aboolean", "true"),
                                property("anumber", "10"),
                                property("ignored", "ignored")
                        ),

                        Data.DATA_JSON_SERIALIZED

                ),
                new Arguments(
                        Data.V1_WITH_XML_DATA,
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV1.SUBJECT, Data.SUBJECT),
                                property(CloudEventV1.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),

                        Data.DATA_XML_SERIALIZED

                ),
                new Arguments(
                        Data.V1_WITH_TEXT_DATA,
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV1.SUBJECT, Data.SUBJECT),
                                property(CloudEventV1.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),

                        Data.DATA_TEXT_SERIALIZED

                )
        );
    }

    private static final AbstractMap.SimpleEntry<String, String> property(final String name, final String value) {
        return name.equalsIgnoreCase("ignored") ?
                new AbstractMap.SimpleEntry<>(name, value) :
                new AbstractMap.SimpleEntry<>(String.format(PREFIX_TEMPLATE, name), value);
    }

    @SafeVarargs
    private static final Map<String, String> properties(final AbstractMap.SimpleEntry<String, String>... entries) {
        return Stream.of(entries)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

    }

    private class Arguments {
        CloudEvent cloudEvent;
        Map<String, String> properties;
        byte[] body;

        public Arguments(CloudEvent cloudEvent, Map<String, String> properties, byte[] body) {
            this.cloudEvent = cloudEvent;
            this.properties = properties;
            this.body = body;
        }


    }
}
