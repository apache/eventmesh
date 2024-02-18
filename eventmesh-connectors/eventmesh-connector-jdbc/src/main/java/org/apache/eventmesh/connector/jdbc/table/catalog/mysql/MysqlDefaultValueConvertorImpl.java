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

package org.apache.eventmesh.connector.jdbc.table.catalog.mysql;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.DefaultValueConvertor;
import org.apache.eventmesh.connector.jdbc.utils.ByteArrayUtils;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.JDBCType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlDefaultValueConvertorImpl implements DefaultValueConvertor {

    private static final String EPOCH_DATE = "1970-01-01";

    // Time The range is '-838:59:59.000000' to '838:59:59.000000'
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\-?[0-9]*):([0-9]*)(:([0-9]*))?(\\.([0-9]*))?");

    private static final Set<JDBCType> NUMBER_DATA_TYPES = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(JDBCType.TINYINT, JDBCType.INTEGER, JDBCType.DATE, JDBCType.TIMESTAMP, JDBCType.TIMESTAMP_WITH_TIMEZONE, JDBCType.TIME,
            JDBCType.BOOLEAN, JDBCType.BIT, JDBCType.NUMERIC, JDBCType.DECIMAL, JDBCType.FLOAT, JDBCType.DOUBLE, JDBCType.REAL)));

    private static final Set<JDBCType> BINARY_DATA_TYPES = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(JDBCType.BINARY, JDBCType.VARBINARY)));

    @Override
    public Object parseDefaultValue(Column<?> column, String defaultValueExpression) {
        if (null == defaultValueExpression) {
            return null;
        }
        defaultValueExpression = defaultValueExpression.trim();

        if (NUMBER_DATA_TYPES.contains(column.getJdbcType()) && StringUtils.equalsAnyIgnoreCase(defaultValueExpression, Boolean.TRUE.toString(),
            Boolean.FALSE.toString())) {
            /*
             * These types are synonyms for DECIMAL: DEC[(M[,D])] [UNSIGNED] [ZEROFILL], NUMERIC[(M[,D])] [UNSIGNED] [ZEROFILL], FIXED[(M[,D])]
             * [UNSIGNED] [ZEROFILL]
             */
            if (column.getJdbcType() == JDBCType.DECIMAL || column.getJdbcType() == JDBCType.NUMERIC) {
                return convert2Decimal(column, defaultValueExpression);
            }
            return StringUtils.equalsIgnoreCase(Boolean.TRUE.toString(), defaultValueExpression) ? 1 : 0;
        }

        if (BINARY_DATA_TYPES.contains(column.getJdbcType()) && column.getDefaultValueExpression() != null) {
            // https://dev.mysql.com/doc/refman/8.0/en/binary-varbinary.html
            String cleanedDefaultValueExpression = StringUtils.replace(column.getDefaultValueExpression(), "\\0", "");
            return ByteArrayUtils.bytesToHexString(cleanedDefaultValueExpression.getBytes(Constants.DEFAULT_CHARSET));
        }

        switch (column.getDataType().getSQLType()) {
            case DATE:
                return convert2LocalDate(column, defaultValueExpression);
            case TIMESTAMP:
                return convertToLocalDateTime(column, defaultValueExpression);
            case TIMESTAMP_WITH_TIMEZONE:
                return convertToTimestamp(column, defaultValueExpression);
            case TIME:
                return convertToLocalTime(column, defaultValueExpression);
            case BOOLEAN:
                return convert2Boolean(column, defaultValueExpression);
            case BIT:
                return convertToBits(column, defaultValueExpression);

            case NUMERIC:
            case DECIMAL:
                return convert2Decimal(column, defaultValueExpression);

            case FLOAT:
            case DOUBLE:
            case REAL:
                return Double.parseDouble(defaultValueExpression);
            default:
        }
        return defaultValueExpression;
    }

    private Object convert2Boolean(Column<?> column, String value) {
        // value maybe is numeric or string
        if (StringUtils.isNumeric(value)) {
            return Integer.parseInt(value) != 0;
        }
        return Boolean.parseBoolean(value);
    }

    private Object convert2Decimal(Column<?> column, String value) {
        return Optional.ofNullable(column.getDecimal()).isPresent() ? new BigDecimal(value).setScale(column.getDecimal(), RoundingMode.HALF_UP)
            : new BigDecimal(value);
    }

    private Object convertToBits(Column<?> column, String value) {
        // value: '101010111'
        if (column.getColumnLength() > 1) {
            int nums = value.length() / Byte.SIZE + (value.length() % Byte.SIZE == 0 ? 0 : 1);
            byte[] bytes = new byte[nums];
            int length = value.length();
            for (int i = 0; i < nums; i++) {
                int size = value.length() - Byte.SIZE < 0 ? 0 : value.length() - Byte.SIZE;
                bytes[nums - i - 1] = (byte) Integer.parseInt(value.substring(size, length), 2);
                value = value.substring(0, size);
            }
            return bytes;
        }

        // value: '1' or '0' parse to boolean
        return Short.parseShort(value) != 0;
    }

    private Object convertToLocalTime(Column<?> column, String value) {

        Matcher matcher = TIME_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected format for TIME column: " + value);
        }

        final int hours = Integer.parseInt(matcher.group(1));
        final int minutes = Integer.parseInt(matcher.group(2));
        final String secondsGroup = matcher.group(4);
        int seconds = 0;
        int nanoSeconds = 0;

        if (secondsGroup != null) {
            seconds = Integer.parseInt(secondsGroup);
            String microSecondsString = matcher.group(6);
            if (microSecondsString != null) {
                nanoSeconds = Integer.parseInt(microSecondsString) * 1000;
            }
        }
        return LocalTime.of(hours, minutes, seconds, nanoSeconds);
    }

    private Object convertToTimestamp(Column<?> column, String value) {
        // Mysql not support
        return null;
    }

    private Object convertToLocalDateTime(Column<?> column, String value) {
        if (StringUtils.containsAny(value, "CURRENT_TIMESTAMP", "current_timestamp")) {
            return value;
        }
        // The TIMESTAMP data type is used for values that contain both date and time parts.
        // TIMESTAMP has a range of '1970-01-01 00:00:01' UTC to '2038-01-19 03:14:07' UTC.
        return LocalDateTime.from(timestampFormat(Optional.ofNullable(column.getColumnLength()).orElse(0L).intValue()).parse(value));
    }

    private Object convert2LocalDate(Column<?> column, String value) {
        // The DATE type is used for values with a date part but no time part.
        // MySQL retrieves and displays DATE values in 'YYYY-MM-DD' format.
        // The supported range is '1000-01-01' to '9999-12-31'.

        try {
            if (StringUtils.contains(value, "-")) {
                return LocalDate.parse(value);
            }
            // maybe is year, e.g. 2020
            if (StringUtils.isNumeric(value)) {
                return LocalDate.parse(value + "-01-01");
            }
            // format: 20200101
            return LocalDate.from(dateFormat().parse(value));
        } catch (Exception e) {
            log.warn("Convert date error[value={}]", value);
            return LocalDate.parse(EPOCH_DATE);
        }
    }

    private DateTimeFormatter timestampFormat(int length) {
        final DateTimeFormatterBuilder dtf = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd").optionalStart().appendLiteral(" ")
            .append(DateTimeFormatter.ISO_LOCAL_TIME).optionalEnd().parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0).parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
        if (length > 0) {
            dtf.appendFraction(ChronoField.MICRO_OF_SECOND, 0, length, true);
        }
        return dtf.toFormatter();
    }

    private DateTimeFormatter dateFormat() {
        final DateTimeFormatterBuilder dtf = new DateTimeFormatterBuilder().appendValue(ChronoField.YEAR, 4).appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .optionalStart().appendValue(ChronoField.DAY_OF_YEAR, 2);
        return dtf.toFormatter();
    }

}
