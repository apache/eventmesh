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

package org.apache.eventmesh.schema.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.eventmesh.common.schema.SchemaAgenda;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

public class AvroSchemaServiceTest {

    @Test
    public void checkSchemaValidity() {
        AvroSchemaService avroSchemaService = new AvroSchemaService();

        String realAvroSchema = "{\"namespace\":\"example.avro\",\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"favorite_number\",\"type\":[\"int\",\"null\"]},{\"name\":\"favorite_color\",\"type\":[\"string\",\"null\"]}]}";
        String fakeAvroSchema = "{\"namespace\":\"example.avro\",\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"favorite_number\",\"type\":[\"int\",\"string\",\"null\"]},{\"name\":\"favorite_color\",\"type\":[\"string\",\"null\"]}]}";
        Schema realSchema = new Schema.Parser().parse(realAvroSchema);
        Schema fakeSchema = new Schema.Parser().parse(fakeAvroSchema);

        org.apache.eventmesh.common.schema.Schema eventmeshSchema
                = new org.apache.eventmesh.common.schema.Schema();
        eventmeshSchema.setSchemaDefinition(realAvroSchema);

        String user1 = "asdfvsdfbwergsdf";
        SchemaAgenda agenda1 = new SchemaAgenda();
        agenda1.setContent(user1);
        agenda1.setSchema(eventmeshSchema);
        assertFalse(avroSchemaService.checkSchemaValidity(agenda1));


        GenericDatumWriter<GenericRecord> gdw2 = new GenericDatumWriter<>(realSchema);
        DataFileWriter<GenericRecord> dfw2 = new DataFileWriter<>(gdw2);
        ByteArrayOutputStream ops2 = new ByteArrayOutputStream();
        try {
            dfw2.create(realSchema, ops2);
            GenericRecord user2 = new GenericData.Record(realSchema);
            user2.put("name", "Alyssa");
            user2.put("favorite_number", 256);
            dfw2.append(user2);
            dfw2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SchemaAgenda agenda2 = new SchemaAgenda();
        agenda2.setContent(ops2.toString());
        agenda2.setSchema(eventmeshSchema);
        assertTrue(avroSchemaService.checkSchemaValidity(agenda2));

        GenericDatumWriter<GenericRecord> gdw3 = new GenericDatumWriter<>(fakeSchema);
        DataFileWriter<GenericRecord> dfw3 = new DataFileWriter<>(gdw3);
        ByteArrayOutputStream ops3 = new ByteArrayOutputStream();
        try {
            dfw3.create(fakeSchema, ops3);
            GenericRecord user3 = new GenericData.Record(fakeSchema);
            user3.put("name", "Alyssa");
            user3.put("favorite_number", "256");
            dfw3.append(user3);
            dfw3.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SchemaAgenda agenda3 = new SchemaAgenda();
        agenda3.setContent(ops3.toString());
        agenda3.setSchema(eventmeshSchema);
        assertFalse(avroSchemaService.checkSchemaValidity(agenda3));
    }
}