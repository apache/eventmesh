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

package org.apache.eventmesh.connector.jdbc.source.dialect.cdc.mysql;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.BitSet;
import java.util.Map;

import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.DeleteRowsEventDataDeserializer;
import com.github.shyiko.mysql.binlog.event.deserialization.UpdateRowsEventDataDeserializer;
import com.github.shyiko.mysql.binlog.event.deserialization.WriteRowsEventDataDeserializer;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * Custom deserializers for the MySQL Binlog Client.MySQL Binlog Client row deserializers convert MySQL raw row data into {@link java.sql.Date},
 * {@link java.sql.Time}, and {@link java.sql.Timestamp} values using {@link java.util.Calendar} instances. EventMesh convert the raw MySQL row values
 * directly into {@link LocalDate}, {@link LocalTime}, {@link LocalDateTime}, and {@link java.time.OffsetDateTime}.
 */
@Slf4j
public class RowDeserializers {

    public static class WriteRowsEventMeshDeserializer extends WriteRowsEventDataDeserializer {

        public WriteRowsEventMeshDeserializer(Map<Long, TableMapEventData> tableMapEventByTableId) {
            super(tableMapEventByTableId);
        }

        @Override
        protected Serializable deserializeString(int length, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeString(length, inputStream);
        }

        @Override
        protected Serializable deserializeVarString(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeVarString(meta, inputStream);
        }

        @Override
        protected Serializable deserializeDate(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDate(inputStream);
        }

        @Override
        protected Serializable deserializeDatetime(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDatetime(inputStream);
        }

        @Override
        protected Serializable deserializeDatetimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDatetimeV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeTimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimeV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeTime(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTime(inputStream);
        }

        @Override
        protected Serializable deserializeTimestamp(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimestamp(inputStream);
        }

        @Override
        protected Serializable deserializeTimestampV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimestampV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeYear(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeYear(inputStream);
        }

        @Override
        protected Serializable deserializeBit(int meta, ByteArrayInputStream inputStream) throws IOException {
            return ((BitSet) super.deserializeBit(meta, inputStream)).toByteArray();
        }
    }

    public static class UpdateRowsEventMeshDeserializer extends UpdateRowsEventDataDeserializer {

        public UpdateRowsEventMeshDeserializer(Map<Long, TableMapEventData> tableMapEventByTableId) {
            super(tableMapEventByTableId);
        }

        @Override
        protected Serializable deserializeString(int length, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeString(length, inputStream);
        }

        @Override
        protected Serializable deserializeVarString(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeVarString(meta, inputStream);
        }

        @Override
        protected Serializable deserializeDate(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDate(inputStream);
        }

        @Override
        protected Serializable deserializeDatetime(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDatetime(inputStream);
        }

        @Override
        protected Serializable deserializeDatetimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDatetimeV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeTimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimeV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeTime(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTime(inputStream);
        }

        @Override
        protected Serializable deserializeTimestamp(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimestamp(inputStream);
        }

        @Override
        protected Serializable deserializeTimestampV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimestampV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeYear(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeYear(inputStream);
        }

        @Override
        protected Serializable deserializeBit(int meta, ByteArrayInputStream inputStream) throws IOException {
            return ((BitSet) super.deserializeBit(meta, inputStream)).toByteArray();

        }
    }

    public static class DeleteRowsEventMeshDeserializer extends DeleteRowsEventDataDeserializer {

        public DeleteRowsEventMeshDeserializer(Map<Long, TableMapEventData> tableMapEventByTableId) {
            super(tableMapEventByTableId);
        }

        @Override
        protected Serializable deserializeString(int length, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeString(length, inputStream);
        }

        @Override
        protected Serializable deserializeVarString(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeVarString(meta, inputStream);
        }

        @Override
        protected Serializable deserializeDate(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDate(inputStream);
        }

        @Override
        protected Serializable deserializeDatetime(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDatetime(inputStream);
        }

        @Override
        protected Serializable deserializeDatetimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeDatetimeV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeTimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimeV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeTime(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTime(inputStream);
        }

        @Override
        protected Serializable deserializeTimestamp(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimestamp(inputStream);
        }

        @Override
        protected Serializable deserializeTimestampV2(int meta, ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeTimestampV2(meta, inputStream);
        }

        @Override
        protected Serializable deserializeYear(ByteArrayInputStream inputStream) throws IOException {
            return RowDeserializers.deserializeYear(inputStream);
        }

        protected Serializable deserializeBit(int meta, ByteArrayInputStream inputStream) throws IOException {
            return ((BitSet) super.deserializeBit(meta, inputStream)).toByteArray();
        }
    }

    protected static Serializable deserializeTimestamp(ByteArrayInputStream inputStream) throws IOException {
        long epochSecond = inputStream.readLong(4);
        int nanoSeconds = 0; // no fractional seconds
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nanoSeconds), ZoneOffset.UTC);
    }

    protected static Serializable deserializeTimestampV2(int meta, ByteArrayInputStream inputStream) throws IOException {
        long epochSecond = bigEndianLong(inputStream.read(4), 0, 4);
        int nanoSeconds = deserializeFractionalSecondsInNanos(meta, inputStream);
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nanoSeconds), ZoneOffset.UTC);
    }

    protected static Serializable deserializeYear(ByteArrayInputStream inputStream) throws IOException {
        return LocalDate.parse(String.format("%d-01-01", 1900 + inputStream.readInteger(1)));
    }

    /**
     * Deserializes a string from the given input stream. Since the charset is not present in the binary log, it is impossible to distinguish between
     * CHAR and BINARY types. Therefore, the method returns a byte array instead of a String.
     *
     * @param length      The length of the string.
     * @param inputStream The input stream from which to read the string.
     * @return A byte array representing the deserialized string.
     * @throws IOException If an I/O error occurs while reading the input stream.
     */
    private static Serializable deserializeString(int length, ByteArrayInputStream inputStream) throws IOException {
        // charset is not present in the binary log (meaning there is no way to distinguish between CHAR / BINARY)
        // as a result - return byte[] instead of an actual String
        int stringLength = length < 256 ? inputStream.readInteger(1) : inputStream.readInteger(2);
        return inputStream.read(stringLength);
    }

    private static Serializable deserializeVarString(int meta, ByteArrayInputStream inputStream) throws IOException {
        int varcharLength = meta < 256 ? inputStream.readInteger(1) : inputStream.readInteger(2);
        return inputStream.read(varcharLength);
    }

    private static Serializable deserializeDate(ByteArrayInputStream inputStream) throws IOException {
        int value = inputStream.readInteger(3);
        int day = value % 32;
        value >>>= 5;
        int month = value % 16;
        int year = value >> 4;
        // https://dev.mysql.com/doc/refman/8.0/en/datetime.html
        if (year == 0 || month == 0 || day == 0) {
            return null;
        }
        return LocalDate.of(year, month, day);
    }

    protected static Serializable deserializeDatetime(ByteArrayInputStream inputStream) throws IOException {
        int[] split = split(inputStream.readLong(8), 100, 6);
        int year = split[5];
        int month = split[4]; // 1-based month number
        int day = split[3]; // 1-based day of the month
        int hours = split[2];
        int minutes = split[1];
        int seconds = split[0];
        int nanoOfSecond = 0; // This version does not support fractional seconds
        if (year == 0 || month == 0 || day == 0) {
            return null;
        }
        return LocalDateTime.of(year, month, day, hours, minutes, seconds, nanoOfSecond);
    }

    private static final int MASK_10_BITS = (1 << 10) - 1;
    private static final int MASK_6_BITS = (1 << 6) - 1;

    protected static Serializable deserializeTimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {

        /**
         * Binary Format for TIME in MySQL binlog:
         * <a href="https://dev.mysql.com/doc/dev/mysql-server/latest/group__MY__TIME.html#time_low_level_rep">TIME</a>
         * +------+---------------+----------------------------------------------------+
         * | Bits |    Field      |                       Value Range                  |
         * +------+---------------+----------------------------------------------------+
         * | 1    | sign          | (Used for sign, when on disk)                      |
         * | 1    | unused        | (Reserved for wider hour range, e.g. for intervals)|
         * | 10   | hour          | (0-838)                                            |
         * | 6    | minute        | (0-59)                                             |
         * | 6    | second        | (0-59)                                             |
         * | 24   | microseconds  | (0-999999)                                         |
         * +------+---------------+----------------------------------------------------+
         *
         * + fractional-seconds storage (size depends on meta)
         */
        long time = bigEndianLong(inputStream.read(3), 0, 3);
        boolean isNegative = bitSlice(time, 0, 1, 24) == 0;
        int hours = bitSlice(time, 2, 10, 24);
        int minutes = bitSlice(time, 12, 6, 24);
        int seconds = bitSlice(time, 18, 6, 24);
        int nanoSeconds;
        if (isNegative) { // mysql binary arithmetic for negative encoded values
            hours = ~hours & MASK_10_BITS;
            hours = hours & ~(1 << 10); // unset sign bit
            minutes = ~minutes & MASK_6_BITS;
            minutes = minutes & ~(1 << 6); // unset sign bit
            seconds = ~seconds & MASK_6_BITS;
            seconds = seconds & ~(1 << 6); // unset sign bit
            nanoSeconds = deserializeFractionalSecondsInNanosNegative(meta, inputStream);
            if (nanoSeconds == 0 && seconds < 59) { // weird java Duration behavior
                ++seconds;
            }
            hours = -hours;
            minutes = -minutes;
            seconds = -seconds;
            nanoSeconds = -nanoSeconds;
        } else {
            nanoSeconds = deserializeFractionalSecondsInNanos(meta, inputStream);
        }

        return LocalTime.of(hours, minutes, seconds, nanoSeconds);
    }

    protected static Serializable deserializeTime(ByteArrayInputStream inputStream) throws IOException {
        // Times are stored as an integer as `HHMMSS`, so we need to split out the digits ...
        int value = inputStream.readInteger(3);
        int[] split = split(value, 100, 3);
        int hours = split[2];
        int minutes = split[1];
        int seconds = split[0];
        return LocalTime.of(hours, minutes, seconds);
    }

    protected static int deserializeFractionalSecondsInNanosNegative(int fsp, ByteArrayInputStream inputStream) throws IOException {
        // Calculate the number of bytes to read, which is
        // '1' when fsp=(1,2)
        // '2' when fsp=(3,4) and
        // '3' when fsp=(5,6)
        int length = (fsp + 1) / 2;
        if (length > 0) {
            long fraction = bigEndianLong(inputStream.read(length), 0, length);
            int maskBits = 0;
            switch (length) { // mask bits according to field precision
                case 1:
                    maskBits = 8;
                    break;
                case 2:
                    maskBits = 15;
                    break;
                case 3:
                    maskBits = 20;
                    break;
                default:
                    break;
            }
            fraction = ~fraction & ((1 << maskBits) - 1);
            fraction = (fraction & ~(1 << maskBits)) + 1; // unset sign bit
            // Convert the fractional value (which has extra trailing digit for fsp=1,3, and 5) to nanoseconds ...
            return (int) (fraction / (0.0000001 * Math.pow(100, length - 1)));
        }
        return 0;
    }

    private static int bigEndianInteger(byte[] bytes, int offset, int length) {
        int result = 0;
        for (int i = offset; i < (offset + length); i++) {
            byte b = bytes[i];
            result = (result << 8) | (b >= 0 ? (int) b : (b + 256));
        }
        return result;
    }

    protected static Serializable deserializeDatetimeV2(int meta, ByteArrayInputStream inputStream)
        throws IOException {

        /**
         * <a href = "https://dev.mysql.com/doc/dev/mysql-server/latest/group__MY__TIME.html">DATETIME</a>
         * 1  sign (used when on disk)
         * 17 year*13+month (year 0-9999, month 0-12)
         * 5  day (0-31)
         * 5  hour (0-23)
         * 6  minute (0-59)
         * 6  second (0-59)
         * 24 microseconds (0-999999)
         *
         * (5 bytes in total)
         *
         * + fractional-seconds storage (size depends on meta)
         */
        long datetime = bigEndianLong(inputStream.read(5), 0, 5);
        int yearMonth = bitSlice(datetime, 1, 17, 40);
        int year = yearMonth / 13;
        int month = yearMonth % 13; // 1-based month number
        int day = bitSlice(datetime, 18, 5, 40); // 1-based day of the month
        int hours = bitSlice(datetime, 23, 5, 40);
        int minutes = bitSlice(datetime, 28, 6, 40);
        int seconds = bitSlice(datetime, 34, 6, 40);
        int nanoOfSecond = deserializeFractionalSecondsInNanos(meta, inputStream);
        if (year == 0 || month == 0 || day == 0) {
            return null;
        }
        return LocalDateTime.of(year, month, day, hours, minutes, seconds, nanoOfSecond);
    }

    private static int[] split(long value, int divider, int length) {
        int[] result = new int[length];
        for (int i = 0; i < length - 1; i++) {
            result[i] = (int) (value % divider);
            value /= divider;
        }
        result[length - 1] = (int) value;
        return result;
    }

    protected static long bigEndianLong(byte[] bytes, int offset, int length) {
        long result = 0;
        for (int i = offset; i < (offset + length); i++) {
            byte b = bytes[i];
            result = (result << 8) | (b >= 0 ? (int) b : (b + 256));
        }
        return result;
    }

    protected static int bitSlice(long value, int bitOffset, int numberOfBits, int payloadSize) {
        long result = value >> payloadSize - (bitOffset + numberOfBits);
        return (int) (result & ((1 << numberOfBits) - 1));
    }

    protected static int deserializeFractionalSecondsInNanos(int fsp, ByteArrayInputStream inputStream) throws IOException {
        // Calculate the number of bytes to read, which is
        // '1' when fsp=(1,2) -- 7
        // '2' when fsp=(3,4) and -- 12
        // '3' when fsp=(5,6) -- 21
        int length = (fsp + 1) / 2;
        if (length > 0) {
            long fraction = bigEndianLong(inputStream.read(length), 0, length);
            // Convert the fractional value (which has extra trailing digit for fsp=1,3, and 5) to nanoseconds ...
            return (int) (fraction / (0.0000001 * Math.pow(100, length - 1)));
        }
        return 0;
    }

    protected static byte[] deserializeBit(int meta, ByteArrayInputStream inputStream) throws IOException {
        return inputStream.read(meta);
    }

}
