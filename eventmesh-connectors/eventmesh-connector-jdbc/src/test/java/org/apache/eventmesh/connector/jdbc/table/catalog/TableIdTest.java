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

package org.apache.eventmesh.connector.jdbc.table.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

public class TableIdTest {

    @Test
    public void testConstructorWithMapper() {
        TableId.TableIdToStringMapper customMapper = mock(TableId.TableIdToStringMapper.class);
        when(customMapper.toString(any(TableId.class))).thenReturn("custom");

        TableId tableId = new TableId("catalog", "schema", "table", customMapper);
        assertEquals("custom", tableId.getId());
    }

    @Test
    public void testConstructorWithDefaultMapper() {
        TableId tableId = new TableId("catalog", "schema", "table");
        assertEquals("catalog.schema.table", tableId.getId());
    }

    @Test
    public void testToStringMethod() {
        TableId tableId = new TableId("catalog", "schema", "table");
        assertEquals("catalog.schema.table", tableId.toString());
    }

    @Test
    public void testTablePathMethod() {
        TableId tableId = new TableId("catalog", "schema", "table");
        assertEquals("catalog.schema.table", tableId.tablePath());
    }

    @Test
    public void testEqualsMethod() {
        TableId tableId1 = new TableId("catalog", "schema", "table");
        TableId tableId2 = new TableId("catalog", "schema", "table");
        assertTrue(tableId1.equals(tableId2));
    }

    @Test
    public void testHashCodeMethod() {
        TableId tableId1 = new TableId("catalog", "schema", "table");
        TableId tableId2 = new TableId("catalog", "schema", "table");
        assertEquals(tableId1.hashCode(), tableId2.hashCode());
    }
}
