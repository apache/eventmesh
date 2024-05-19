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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ByteArrayUtilsTest {

    @Test
    public void testBytesToHexString() {
        byte[] bytes = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        String hexString = ByteArrayUtils.bytesToHexString(bytes);
        assertEquals("cafebabe", hexString);
    }

    @Test
    public void testHexStringToBytes() {
        String hexString = "cafebabe";
        byte[] bytes = ByteArrayUtils.hexStringToBytes(hexString);
        assertArrayEquals(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, bytes);
    }

    @Test
    public void testBytesToHexStringAndBack() {
        byte[] originalBytes = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        String hexString = ByteArrayUtils.bytesToHexString(originalBytes);
        byte[] convertedBytes = ByteArrayUtils.hexStringToBytes(hexString);
        assertArrayEquals(originalBytes, convertedBytes);
    }

    @Test
    public void testHexStringToBytesWithOddLength() {
        assertThrows(IllegalArgumentException.class, () -> {
            ByteArrayUtils.hexStringToBytes("cafebabe1"); // Odd-length hex string
        });
    }

    @Test
    public void testHexStringToBytesWithInvalidCharacter() {
        assertThrows(IllegalArgumentException.class, () -> {
            ByteArrayUtils.hexStringToBytes("cafebabeG"); // Invalid hex character
        });
    }
}
