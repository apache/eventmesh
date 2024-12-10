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

package org.apache.eventmesh.admin.server.web.utils;

public class Base64 {
    private static char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toCharArray();
    private static byte[] codes = new byte[256];

    public Base64() {
    }

    public static char[] encode(byte[] data) {
        char[] out = new char[(data.length + 2) / 3 * 4];
        int i = 0;

        for (int index = 0; i < data.length; index += 4) {
            boolean quad = false;
            boolean trip = false;
            int val = 255 & data[i];
            val <<= 8;
            if (i + 1 < data.length) {
                val |= 255 & data[i + 1];
                trip = true;
            }

            val <<= 8;
            if (i + 2 < data.length) {
                val |= 255 & data[i + 2];
                quad = true;
            }

            out[index + 3] = alphabet[quad ? val & 63 : 64];
            val >>= 6;
            out[index + 2] = alphabet[trip ? val & 63 : 64];
            val >>= 6;
            out[index + 1] = alphabet[val & 63];
            val >>= 6;
            out[index + 0] = alphabet[val & 63];
            i += 3;
        }

        return out;
    }

    public static byte[] decode(char[] data) {
        int tempLen = data.length;

        int len;
        for (len = 0; len < data.length; ++len) {
            if (data[len] > 255 || codes[data[len]] < 0) {
                --tempLen;
            }
        }

        len = tempLen / 4 * 3;
        if (tempLen % 4 == 3) {
            len += 2;
        }

        if (tempLen % 4 == 2) {
            ++len;
        }

        byte[] out = new byte[len];
        int shift = 0;
        int accum = 0;
        int index = 0;

        for (int ix = 0; ix < data.length; ++ix) {
            int value = data[ix] > 255 ? -1 : codes[data[ix]];
            if (value >= 0) {
                accum <<= 6;
                shift += 6;
                accum |= value;
                if (shift >= 8) {
                    shift -= 8;
                    out[index++] = (byte) (accum >> shift & 255);
                }
            }
        }

        if (index != out.length) {
            throw new Error("Miscalculated data length (wrote " + index + " instead of " + out.length + ")");
        } else {
            return out;
        }
    }

    static {
        int i;
        for (i = 0; i < 256; ++i) {
            codes[i] = -1;
        }

        for (i = 65; i <= 90; ++i) {
            codes[i] = (byte) (i - 65);
        }

        for (i = 97; i <= 122; ++i) {
            codes[i] = (byte) (26 + i - 97);
        }

        for (i = 48; i <= 57; ++i) {
            codes[i] = (byte) (52 + i - 48);
        }

        codes[43] = 62;
        codes[47] = 63;
    }
}
