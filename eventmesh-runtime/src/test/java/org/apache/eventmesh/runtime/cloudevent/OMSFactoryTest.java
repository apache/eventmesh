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
import io.cloudevents.core.message.Encoding;
import io.cloudevents.core.message.MessageReader;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;
import io.cloudevents.types.Time;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.OMSMessageFactory;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.impl.OMSHeaders;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class OMSFactoryTest {

    private static final String PREFIX_TEMPLATE = OMSHeaders.CE_PREFIX + "%s";
    private static final String DATACONTENTTYPE_NULL = null;
    private static final byte[] DATAPAYLOAD_NULL = null;

    @Test
    public void readBinary() {
        Stream<Arguments> argumentsStream=binaryTestArguments();
        argumentsStream.forEach(argument -> {
            if (argument.contentType != null) {
                argument.props.put(OMSHeaders.CONTENT_TYPE, argument.contentType);
            }
            Properties properties = new Properties();
            properties.putAll(argument.props);
            final MessageReader reader = OMSMessageFactory.createReader(properties, argument.body);
            assertThat(reader.getEncoding()).isEqualTo(Encoding.BINARY);
            assertThat(reader.toEvent()).isEqualTo(argument.event);

        });

    }

    @Test
    public void readStructured() {
        Stream<CloudEvent> cloudEventStream= Data.allEventsWithoutExtensions();
        EventFormatProvider.getInstance().registerFormat(CSVFormat.INSTANCE);
        cloudEventStream.forEach(event -> {
            final String contentType = CSVFormat.INSTANCE.serializedContentType() + "; charset=utf8";
            final byte[] contentPayload = CSVFormat.INSTANCE.serialize(event);
            Properties properties = new Properties();
            properties.put(OMSHeaders.CONTENT_TYPE, contentType);
            final MessageReader reader = OMSMessageFactory.createReader(properties, contentPayload);
            assertThat(reader.getEncoding()).isEqualTo(Encoding.STRUCTURED);
            assertThat(reader.toEvent()).isEqualTo(event);
        });
    }

    private Stream<Arguments> binaryTestArguments() {

        return Stream.of(
                // V03
                new Arguments(
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property("ignored", "ignore")
                        ),
                        DATACONTENTTYPE_NULL,
                        DATAPAYLOAD_NULL,
                        Data.V03_MIN
                ),
                new Arguments(
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
                        Data.DATACONTENTTYPE_JSON,
                        Data.DATA_JSON_SERIALIZED,
                        Data.V03_WITH_JSON_DATA
                ),
                new Arguments(
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
                        Data.DATACONTENTTYPE_JSON,
                        Data.DATA_JSON_SERIALIZED,
                        Data.V03_WITH_JSON_DATA_WITH_EXT_STRING
                ),
                new Arguments(
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV03.SUBJECT, Data.SUBJECT),
                                property(CloudEventV03.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),
                        Data.DATACONTENTTYPE_XML,
                        Data.DATA_XML_SERIALIZED,
                        Data.V03_WITH_XML_DATA
                ),
                new Arguments(
                        properties(
                                property(CloudEventV03.SPECVERSION, SpecVersion.V03.toString()),
                                property(CloudEventV03.ID, Data.ID),
                                property(CloudEventV03.TYPE, Data.TYPE),
                                property(CloudEventV03.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV03.SUBJECT, Data.SUBJECT),
                                property(CloudEventV03.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),
                        Data.DATACONTENTTYPE_TEXT,
                        Data.DATA_TEXT_SERIALIZED,
                        Data.V03_WITH_TEXT_DATA
                ),
                // V1
                new Arguments(
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property("ignored", "ignored")
                        ),
                        DATACONTENTTYPE_NULL,
                        DATAPAYLOAD_NULL,
                        Data.V1_MIN
                ),
                new Arguments(
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
                        Data.DATACONTENTTYPE_JSON,
                        Data.DATA_JSON_SERIALIZED,
                        Data.V1_WITH_JSON_DATA
                ),
                new Arguments(
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
                        Data.DATACONTENTTYPE_JSON,
                        Data.DATA_JSON_SERIALIZED,
                        Data.V1_WITH_JSON_DATA_WITH_EXT_STRING
                ),
                new Arguments(
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV1.SUBJECT, Data.SUBJECT),
                                property(CloudEventV1.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),
                        Data.DATACONTENTTYPE_XML,
                        Data.DATA_XML_SERIALIZED,
                        Data.V1_WITH_XML_DATA
                ),
                new Arguments(
                        properties(
                                property(CloudEventV1.SPECVERSION, SpecVersion.V1.toString()),
                                property(CloudEventV1.ID, Data.ID),
                                property(CloudEventV1.TYPE, Data.TYPE),
                                property(CloudEventV1.SOURCE, Data.SOURCE.toString()),
                                property(CloudEventV1.SUBJECT, Data.SUBJECT),
                                property(CloudEventV1.TIME, Time.writeTime(Data.TIME)),
                                property("ignored", "ignored")
                        ),
                        Data.DATACONTENTTYPE_TEXT,
                        Data.DATA_TEXT_SERIALIZED,
                        Data.V1_WITH_TEXT_DATA
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
        private  Map<String, String> props;
        private  String contentType;
        private  byte[] body;
        private  CloudEvent event;

        public Arguments(Map<String, String> props, String contentType, byte[] body, CloudEvent event) {
            this.props = props;
            this.contentType = contentType;
            this.body = body;
            this.event = event;
        }
    }
}
