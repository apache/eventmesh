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

import org.apache.eventmesh.common.schema.SchemaAgenda;
import org.apache.eventmesh.schema.api.SchemaService;

import org.apache.avro.AvroTypeException;
import org.apache.avro.InvalidAvroMagicException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

public class AvroSchemaService implements SchemaService {
    @Override
    public void contract() {
        //TODO
    }

    @Override
    public void serialize() {
        //TODO
    }

    @Override
    public void deserialize() {
        //TODO
    }

    /**
     * check the validity of avro message according to avro schema
     * @param schemaAgenda a wrapper of content, content type, and schema
     * @return whether the avro message is valid or not
     */
    @Override
    public boolean checkSchemaValidity(SchemaAgenda schemaAgenda) {
        try {
            Schema schema = new Schema.Parser().parse(schemaAgenda.getSchema().getSchemaDefinition());
            GenericDatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
            SeekableByteArrayInput sin = new SeekableByteArrayInput(schemaAgenda.getContent().getBytes());
            DataFileReader<GenericRecord> reader = new DataFileReader<>(sin, datumReader);
            GenericRecord item = null;
            while (reader.hasNext()) {
                item = reader.next(item);
            }
        } catch (InvalidAvroMagicException | AvroTypeException | SchemaParseException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
