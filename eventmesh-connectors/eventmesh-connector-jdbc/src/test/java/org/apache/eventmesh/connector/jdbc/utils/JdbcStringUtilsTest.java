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

package org.apache.eventmesh.connector.jdbc.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JdbcStringUtilsTest {

    @Test
    public void testIsWrapped() {
        assertTrue(JdbcStringUtils.isWrapped("`Hello`"));
        assertTrue(JdbcStringUtils.isWrapped("'World'"));
        assertTrue(JdbcStringUtils.isWrapped("\"Java\""));
        assertFalse(JdbcStringUtils.isWrapped("NotWrapped"));
        assertFalse(JdbcStringUtils.isWrapped("`NotClosed"));
        assertFalse(JdbcStringUtils.isWrapped("NotOpened`"));
    }

    @Test
    public void testWithoutWrapper() {
        assertEquals("Hello", JdbcStringUtils.withoutWrapper("`Hello`"));
        assertEquals("World", JdbcStringUtils.withoutWrapper("'World'"));
        assertEquals("Java", JdbcStringUtils.withoutWrapper("\"Java\""));
        assertEquals("NotWrapped", JdbcStringUtils.withoutWrapper("NotWrapped"));
        assertEquals("`NotClosed", JdbcStringUtils.withoutWrapper("`NotClosed"));
        assertEquals("NotOpened`", JdbcStringUtils.withoutWrapper("NotOpened`"));
    }

    @Test
    public void testIsWrappedWithChar() {
        assertTrue(JdbcStringUtils.isWrapped('`'));
        assertTrue(JdbcStringUtils.isWrapped('\''));
        assertTrue(JdbcStringUtils.isWrapped('\"'));
        assertFalse(JdbcStringUtils.isWrapped('A'));
    }
}
