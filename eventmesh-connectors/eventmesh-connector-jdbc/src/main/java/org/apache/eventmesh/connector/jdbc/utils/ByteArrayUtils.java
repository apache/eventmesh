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

public class ByteArrayUtils {

    private static final char[] HEX_CHARS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * Converts a byte array into a hexadecimal string.
     *
     * @param bytes the byte array to be converted
     * @return the hexadecimal string representation of the byte array
     * @throws NullPointerException if the byte array is null
     */
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("Parameter to be converted can not be null");
        }

        char[] converted = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            converted[i * 2] = HEX_CHARS[b >> 4 & 0x0F];
            converted[i * 2 + 1] = HEX_CHARS[b & 0x0F];
        }

        return String.valueOf(converted);
    }

    /**
     * This method converts a hexadecimal string into an array of bytes.
     *
     * @param str the hexadecimal string to be converted
     * @return the resulting byte array
     * @throws IllegalArgumentException if the supplied character array contains an odd number of hex characters
     */
    public static byte[] hexStringToBytes(String str) {
        final char[] chars = str.toCharArray();
        if (chars.length % 2 != 0) {
            throw new IllegalArgumentException("The supplied character array must contain an even number of hex chars.");
        }

        byte[] response = new byte[chars.length / 2];

        for (int i = 0; i < response.length; i++) {
            int posOne = i * 2;
            response[i] = (byte) (toByte(chars, posOne) << 4 | toByte(chars, posOne + 1));
        }

        return response;
    }

    private static byte toByte(final char[] chars, final int pos) {
        int response = Character.digit(chars[pos], 16);
        if (response < 0 || response > 15) {
            throw new IllegalArgumentException("Non-hex character '" + chars[pos] + "' at index=" + pos);
        }

        return (byte) response;
    }

}
