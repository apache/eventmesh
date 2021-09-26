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
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.types.Time;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.stream.Stream;

public class Data {

    public static final String ID = "1";
    public static final String TYPE = "mock.test";
    public static final URI SOURCE = URI.create("http://localhost/source");
    public static final String DATACONTENTTYPE_JSON = "application/json";
    public static final String DATACONTENTTYPE_XML = "application/xml";
    public static final String DATACONTENTTYPE_TEXT = "text/plain";
    public static final URI DATASCHEMA = URI.create("http://localhost/schema");
    public static final String SUBJECT = "sub";
    public static final OffsetDateTime TIME = Time.parseTime("2018-04-26T14:48:09+02:00");

    protected static final byte[] DATA_JSON_SERIALIZED = "{}".getBytes();
    protected static final byte[] DATA_XML_SERIALIZED = "<stuff></stuff>".getBytes();
    protected static final byte[] DATA_TEXT_SERIALIZED = "Hello World Lorena!".getBytes();
    protected static final byte[] BINARY_VALUE = { (byte) 0xE0, (byte) 0xFF, (byte) 0x00, (byte) 0x44, (byte) 0xAA }; // Base64: 4P8ARKo=

    protected static final CloudEvent V1_MIN = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .build();

    public static final CloudEvent V1_WITH_JSON_DATA = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withData(DATACONTENTTYPE_JSON, DATASCHEMA, DATA_JSON_SERIALIZED)
        .withSubject(SUBJECT)
        .withTime(TIME)
        .build();

    public static final CloudEvent V1_WITH_JSON_DATA_WITH_FRACTIONAL_TIME = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withData(DATACONTENTTYPE_JSON, DATASCHEMA, DATA_JSON_SERIALIZED)
        .withSubject(SUBJECT)
        .withTime(Time.parseTime("2018-04-26T14:48:09.1234Z"))
        .build();

    public static final CloudEvent V1_WITH_JSON_DATA_WITH_EXT = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withData(DATACONTENTTYPE_JSON, DATASCHEMA, DATA_JSON_SERIALIZED)
        .withSubject(SUBJECT)
        .withTime(TIME)
        .withExtension("astring", "aaa")
        .withExtension("aboolean", true)
        .withExtension("anumber", 10)
        .build();

    public static final CloudEvent V1_WITH_JSON_DATA_WITH_EXT_STRING = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withData(DATACONTENTTYPE_JSON, DATASCHEMA, DATA_JSON_SERIALIZED)
        .withSubject(SUBJECT)
        .withTime(TIME)
        .withExtension("astring", "aaa")
        .withExtension("aboolean", "true")
        .withExtension("anumber", "10")
        .build();

    public static final CloudEvent V1_WITH_XML_DATA = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withData(DATACONTENTTYPE_XML, DATA_XML_SERIALIZED)
        .withSubject(SUBJECT)
        .withTime(TIME)
        .build();

    public static final CloudEvent V1_WITH_TEXT_DATA = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withData(DATACONTENTTYPE_TEXT, DATA_TEXT_SERIALIZED)
        .withSubject(SUBJECT)
        .withTime(TIME)
        .build();

    public static final CloudEvent V1_WITH_BINARY_EXT = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withExtension("binary", BINARY_VALUE)
        .build();

    public static final CloudEvent V1_WITH_NUMERIC_EXT = CloudEventBuilder.v1()
        .withId(ID)
        .withType(TYPE)
        .withSource(SOURCE)
        .withExtension("integer", 42)
        .withExtension("decimal", new BigDecimal("42.42"))
        .withExtension("float", 4.2f)
        .withExtension("long", new Long(4200))
        .build();

    public static final CloudEvent V03_MIN = CloudEventBuilder.v03(V1_MIN).build();
    public static final CloudEvent V03_WITH_JSON_DATA = CloudEventBuilder.v03(V1_WITH_JSON_DATA).build();
    public static final CloudEvent V03_WITH_JSON_DATA_WITH_EXT = CloudEventBuilder.v03(V1_WITH_JSON_DATA_WITH_EXT).build();
    public static final CloudEvent V03_WITH_JSON_DATA_WITH_EXT_STRING = CloudEventBuilder.v03(V1_WITH_JSON_DATA_WITH_EXT_STRING).build();
    public static final CloudEvent V03_WITH_XML_DATA = CloudEventBuilder.v03(V1_WITH_XML_DATA).build();
    public static final CloudEvent V03_WITH_TEXT_DATA = CloudEventBuilder.v03(V1_WITH_TEXT_DATA).build();

    public static Stream<CloudEvent> allEvents() {
        return Stream.concat(v1Events(), v03Events());
    }

    public static Stream<CloudEvent> allEventsWithoutExtensions() {
        return Stream.concat(v1Events(), v03Events()).filter(e -> e.getExtensionNames().isEmpty());
    }

    public static Stream<CloudEvent> allEventsWithStringExtensions() {
        return Stream.concat(v1EventsWithStringExt(), v03EventsWithStringExt());
    }

    public static Stream<CloudEvent> v1Events() {
        return Stream.of(
            Data.V1_MIN,
            Data.V1_WITH_JSON_DATA,
            Data.V1_WITH_JSON_DATA_WITH_EXT,
            Data.V1_WITH_XML_DATA,
            Data.V1_WITH_TEXT_DATA
        );
    }

    /**
     * Due to the nature of CE there are scenarios where an event might be serialized
     * in such a fashion that it can not be deserialized while retaining the orginal
     * type information, this varies from format-2-format
     */

    public static Stream<CloudEvent> v1NonRoundTripEvents() {
        return Stream.of(
            Data.V1_WITH_BINARY_EXT
        );
    }

    public static Stream<CloudEvent> v03Events() {
        return Stream.of(
            Data.V03_MIN,
            Data.V03_WITH_JSON_DATA,
            Data.V03_WITH_JSON_DATA_WITH_EXT,
            Data.V03_WITH_XML_DATA,
            Data.V03_WITH_TEXT_DATA
        );
    }

    public static Stream<CloudEvent> v1EventsWithStringExt() {
        return v1Events().map(ce -> {
            io.cloudevents.core.v1.CloudEventBuilder builder = CloudEventBuilder.v1(ce);
            ce.getExtensionNames().forEach(k -> builder.withExtension(k, Objects.toString(ce.getExtension(k))));
            return builder.build();
        });
    }

    public static Stream<CloudEvent> v03EventsWithStringExt() {
        return v03Events().map(ce -> {
            io.cloudevents.core.v03.CloudEventBuilder builder = CloudEventBuilder.v03(ce);
            ce.getExtensionNames().forEach(k -> builder.withExtension(k, Objects.toString(ce.getExtension(k))));
            return builder.build();
        });
    }

}
