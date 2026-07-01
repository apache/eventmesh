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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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

    @Test
    public void testCompareVersion() {
        // Test case 1: versionX is less than versionY
        String versionX1 = "1.0.0";
        String versionY1 = "1.1.0";
        int expected1 = -1;
        int result1 = JdbcStringUtils.compareVersion(versionX1, versionY1);
        assertEquals(expected1, result1);

        // Test case 2: versionX is equal to versionY
        String versionX2 = "1.2.3";
        String versionY2 = "1.2.3";
        int expected2 = 0;
        int result2 = JdbcStringUtils.compareVersion(versionX2, versionY2);
        assertEquals(expected2, result2);

        // Test case 3: versionX is greater than versionY
        String versionX3 = "2.0.0";
        String versionY3 = "1.2.3";
        int expected3 = 1;
        int result3 = JdbcStringUtils.compareVersion(versionX3, versionY3);
        assertEquals(expected3, result3);

        assertEquals(0, JdbcStringUtils.compareVersion("1.0", "1.0"));
        assertEquals(1, JdbcStringUtils.compareVersion("1.1", "1.0"));
        assertEquals(-1, JdbcStringUtils.compareVersion("1.0", "1.1"));
        assertEquals(1, JdbcStringUtils.compareVersion("1.10", "1.9"));
        assertEquals(-1, JdbcStringUtils.compareVersion("1.9", "1.10"));
        assertEquals(0, JdbcStringUtils.compareVersion("1.0.0", "1"));
        assertEquals(0, JdbcStringUtils.compareVersion("1.0.0.0", "1.0"));
        assertEquals(0, JdbcStringUtils.compareVersion("1.0.0.0", "1"));
        assertEquals(-1, JdbcStringUtils.compareVersion("1.0.0.0", "1.1"));
        assertEquals(1, JdbcStringUtils.compareVersion("1.1", "1.0.0.0"));
        try {
            assertEquals(0, JdbcStringUtils.compareVersion("1.1", "1.a"));
        } catch (Exception e) {
            assertTrue(e instanceof NumberFormatException);
        }
    }
}
