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

package org.apache.eventmesh.connector.canal;

import static org.apache.eventmesh.connector.canal.ByteArrayConverter.SQL_BYTES;
import static org.apache.eventmesh.connector.canal.SqlTimestampConverter.SQL_TIMESTAMP;

import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.lang.StringUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;

import com.mysql.cj.Constants;
import com.mysql.cj.MysqlType;
import com.taobao.tddl.dbsync.binlog.LogBuffer;

public class SqlUtils {

    public static final String REQUIRED_FIELD_NULL_SUBSTITUTE = " ";
    private static final Map<Integer, Class<?>> sqlTypeToJavaTypeMap = new HashMap<Integer, Class<?>>();
    private static final ConvertUtilsBean convertUtilsBean = new ConvertUtilsBean();
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final WKBReader WKB_READER = new WKBReader(GEOMETRY_FACTORY);
    private static final BigDecimal NANO_SEC = new BigDecimal(LogBuffer.DIG_BASE);
    private static final LocalDateTime BASE = LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0);
    private static final long ONE_HOUR = 3600;
    private static final long ONE_MINUTE = 60;

    static {
        // regist Converter
        convertUtilsBean.register(SQL_TIMESTAMP, Date.class);
        convertUtilsBean.register(SQL_TIMESTAMP, Time.class);
        convertUtilsBean.register(SQL_TIMESTAMP, Timestamp.class);
        convertUtilsBean.register(SQL_BYTES, byte[].class);

        // bool
        sqlTypeToJavaTypeMap.put(Types.BOOLEAN, Boolean.class);

        // int
        sqlTypeToJavaTypeMap.put(Types.TINYINT, Integer.class);
        sqlTypeToJavaTypeMap.put(Types.SMALLINT, Integer.class);
        sqlTypeToJavaTypeMap.put(Types.INTEGER, Integer.class);

        // long
        sqlTypeToJavaTypeMap.put(Types.BIGINT, Long.class);
        // mysql bit
        sqlTypeToJavaTypeMap.put(Types.BIT, BigInteger.class);

        // decimal
        sqlTypeToJavaTypeMap.put(Types.REAL, Float.class);
        sqlTypeToJavaTypeMap.put(Types.FLOAT, Float.class);
        sqlTypeToJavaTypeMap.put(Types.DOUBLE, Double.class);
        sqlTypeToJavaTypeMap.put(Types.NUMERIC, BigDecimal.class);
        sqlTypeToJavaTypeMap.put(Types.DECIMAL, BigDecimal.class);

        // date
        sqlTypeToJavaTypeMap.put(Types.DATE, Date.class);
        sqlTypeToJavaTypeMap.put(Types.TIME, Time.class);
        sqlTypeToJavaTypeMap.put(Types.TIMESTAMP, Timestamp.class);

        // blob
        sqlTypeToJavaTypeMap.put(Types.BLOB, byte[].class);

        // byte[]
        sqlTypeToJavaTypeMap.put(Types.REF, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.OTHER, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.ARRAY, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.STRUCT, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.SQLXML, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.BINARY, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.DATALINK, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.DISTINCT, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.VARBINARY, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.JAVA_OBJECT, byte[].class);
        sqlTypeToJavaTypeMap.put(Types.LONGVARBINARY, byte[].class);

        // String
        sqlTypeToJavaTypeMap.put(Types.CHAR, String.class);
        sqlTypeToJavaTypeMap.put(Types.VARCHAR, String.class);
        sqlTypeToJavaTypeMap.put(Types.LONGVARCHAR, String.class);
        sqlTypeToJavaTypeMap.put(Types.LONGNVARCHAR, String.class);
        sqlTypeToJavaTypeMap.put(Types.NCHAR, String.class);
        sqlTypeToJavaTypeMap.put(Types.NVARCHAR, String.class);
        sqlTypeToJavaTypeMap.put(Types.NCLOB, String.class);
        sqlTypeToJavaTypeMap.put(Types.CLOB, String.class);
    }

    public static String genPrepareSqlOfInClause(int size) {
        StringBuilder sql = new StringBuilder();
        sql.append("(");
        for (int i = 0; i < size; i++) {
            sql.append("?");
            if (i < size - 1) {
                sql.append(",");
            }
        }
        sql.append(")");
        return sql.toString();
    }

    public static void setInClauseParameters(PreparedStatement preparedStatement, List<String> params) throws SQLException {
        setInClauseParameters(preparedStatement, 0, params);
    }

    public static void setInClauseParameters(PreparedStatement preparedStatement, int paramIndexStart,
                                             List<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            preparedStatement.setString(paramIndexStart + i, params.get(i));
        }
    }

    public static String sqlValueToString(ResultSet rs, int index, int sqlType) throws SQLException {
        Class<?> requiredType = sqlTypeToJavaTypeMap.get(sqlType);
        if (requiredType == null) {
            throw new IllegalArgumentException("unknow java.sql.Types - " + sqlType);
        }

        return getResultSetValue(rs, index, requiredType);
    }

    public static Object stringToSqlValue(String value, int sqlType, boolean isRequired, boolean isEmptyStringNulled) {
        if (SqlUtils.isTextType(sqlType)) {
            if ((value == null) || (StringUtils.isEmpty(value) && isEmptyStringNulled)) {
                return isRequired ? REQUIRED_FIELD_NULL_SUBSTITUTE : null;
            } else {
                return value;
            }
        } else {
            if (StringUtils.isEmpty(value)) {
                return isEmptyStringNulled ? null : value;
            } else {
                Class<?> requiredType = sqlTypeToJavaTypeMap.get(sqlType);
                if (requiredType == null) {
                    throw new IllegalArgumentException("unknow java.sql.Types - " + sqlType);
                } else if (requiredType.equals(String.class)) {
                    return value;
                } else if (isNumeric(sqlType)) {
                    return convertUtilsBean.convert(value.trim(), requiredType);
                } else {
                    return convertUtilsBean.convert(value, requiredType);
                }
            }
        }
    }

    public static String encoding(String source, int sqlType, String sourceEncoding, String targetEncoding) {
        switch (sqlType) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                if (!StringUtils.isEmpty(source)) {
                    String fromEncoding = StringUtils.isBlank(sourceEncoding) ? "UTF-8" : sourceEncoding;
                    String toEncoding = StringUtils.isBlank(targetEncoding) ? "UTF-8" : targetEncoding;

                    // if (false == StringUtils.equalsIgnoreCase(fromEncoding,
                    // toEncoding)) {
                    try {
                        return new String(source.getBytes(fromEncoding), toEncoding);
                    } catch (UnsupportedEncodingException e) {
                        throw new IllegalArgumentException(e.getMessage(), e);
                    }
                    // }
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + sqlType);
        }

        return source;
    }

    /**
     * Retrieve a JDBC column value from a ResultSet, using the specified value type.
     * <p>
     * Uses the specifically typed ResultSet accessor methods, falling back to {@link #getResultSetValue(ResultSet, int)} for unknown types.
     * <p>
     * Note that the returned value may not be assignable to the specified required type, in case of an unknown type. Calling code needs to deal with
     * this case appropriately, e.g. throwing a corresponding exception.
     *
     * @param rs           is the ResultSet holding the data
     * @param index        is the column index
     * @param requiredType the required value type (may be <code>null</code>)
     * @return the value object
     * @throws SQLException if thrown by the JDBC API
     */
    private static String getResultSetValue(ResultSet rs, int index, Class<?> requiredType) throws SQLException {
        if (requiredType == null) {
            return getResultSetValue(rs, index);
        }

        Object value = null;
        boolean wasNullCheck = false;

        // Explicitly extract typed value, as far as possible.
        if (String.class.equals(requiredType)) {
            value = rs.getString(index);
        } else if (boolean.class.equals(requiredType) || Boolean.class.equals(requiredType)) {
            value = rs.getBoolean(index);
            wasNullCheck = true;
        } else if (byte.class.equals(requiredType) || Byte.class.equals(requiredType)) {
            value = rs.getByte(index);
            wasNullCheck = true;
        } else if (short.class.equals(requiredType) || Short.class.equals(requiredType)) {
            value = rs.getShort(index);
            wasNullCheck = true;
        } else if (int.class.equals(requiredType) || Integer.class.equals(requiredType)) {
            value = rs.getLong(index);
            wasNullCheck = true;
        } else if (long.class.equals(requiredType) || Long.class.equals(requiredType)) {
            value = rs.getBigDecimal(index);
            wasNullCheck = true;
        } else if (float.class.equals(requiredType) || Float.class.equals(requiredType)) {
            value = rs.getFloat(index);
            wasNullCheck = true;
        } else if (double.class.equals(requiredType) || Double.class.equals(requiredType)
            || Number.class.equals(requiredType)) {
            value = rs.getDouble(index);
            wasNullCheck = true;
        } else if (Time.class.equals(requiredType)) {
            value = rs.getString(index);
        } else if (Timestamp.class.equals(requiredType) || Date.class.equals(requiredType)) {
            value = rs.getString(index);
        } else if (BigDecimal.class.equals(requiredType)) {
            value = rs.getBigDecimal(index);
        } else if (BigInteger.class.equals(requiredType)) {
            value = rs.getBigDecimal(index);
        } else if (Blob.class.equals(requiredType)) {
            value = rs.getBlob(index);
        } else if (Clob.class.equals(requiredType)) {
            value = rs.getClob(index);
        } else if (byte[].class.equals(requiredType)) {
            byte[] bytes = rs.getBytes(index);
            if (bytes != null) {
                value = new String(bytes, StandardCharsets.ISO_8859_1);
            }
        } else {
            // Some unknown type desired -> rely on getObject.
            value = getResultSetValue(rs, index);
        }

        // Perform was-null check if demanded (for results that the
        // JDBC driver returns as primitives).
        if (wasNullCheck && (value != null) && rs.wasNull()) {
            value = null;
        }

        return (value == null) ? null : convertUtilsBean.convert(value);
    }

    /**
     * Retrieve a JDBC column value from a ResultSet, using the most appropriate value type. The returned value should be a detached value object, not
     * having any ties to the active ResultSet: in particular, it should not be a Blob or Clob object but rather a byte array respectively String
     * representation.
     * <p>
     * Uses the <code>getObject(index)</code> method, but includes additional "hacks" to get around Oracle 10g returning a non-standard object for its
     * TIMESTAMP datatype and a <code>java.sql.Date</code> for DATE columns leaving out the time portion: These columns will explicitly be extracted
     * as standard <code>java.sql.Timestamp</code> object.
     *
     * @param rs    is the ResultSet holding the data
     * @param index is the column index
     * @return the value object
     * @throws SQLException if thrown by the JDBC API
     * @see Blob
     * @see Clob
     * @see Timestamp
     */
    private static String getResultSetValue(ResultSet rs, int index) throws SQLException {
        Object obj = rs.getObject(index);
        return (obj == null) ? null : convertUtilsBean.convert(obj);
    }

    // private static Object convertTimestamp(Timestamp timestamp) {
    // return (timestamp == null) ? null : timestamp.getTime();
    // }

    /**
     * Check whether the given SQL type is numeric.
     */
    public static boolean isNumeric(int sqlType) {
        return (Types.BIT == sqlType) || (Types.BIGINT == sqlType) || (Types.DECIMAL == sqlType)
            || (Types.DOUBLE == sqlType) || (Types.FLOAT == sqlType) || (Types.INTEGER == sqlType)
            || (Types.NUMERIC == sqlType) || (Types.REAL == sqlType) || (Types.SMALLINT == sqlType)
            || (Types.TINYINT == sqlType);
    }

    public static boolean isTextType(int sqlType) {
        return sqlType == Types.CHAR || sqlType == Types.VARCHAR || sqlType == Types.CLOB || sqlType == Types.LONGVARCHAR
            || sqlType == Types.NCHAR || sqlType == Types.NVARCHAR || sqlType == Types.NCLOB
            || sqlType == Types.LONGNVARCHAR;
    }

    public static JDBCType toJDBCType(String connectorDataType) {
        MysqlType mysqlType = MysqlType.getByName(connectorDataType);
        return JDBCType.valueOf(mysqlType.getJdbcType());
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String strValue = (String) value;
            if (!org.apache.commons.lang3.StringUtils.isNotBlank(strValue)) {
                return null;
            }
            try {
                return new BigDecimal(strValue);
            } catch (Exception e) {
                if ("true".equals(strValue)) {
                    return BigDecimal.ONE;
                }
                if ("false".equals(strValue)) {
                    return BigDecimal.ZERO;
                }
                return new BigDecimal(strValue);
            }
        } else if (value instanceof Number) {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }
            if (value instanceof Integer) {
                return BigDecimal.valueOf(((Integer) value).longValue());
            }
            if (value instanceof Long) {
                return BigDecimal.valueOf(((Long) value));
            }
            if (value instanceof Double) {
                return BigDecimal.valueOf(((Double) value));
            }
            if (value instanceof Float) {
                return BigDecimal.valueOf(((Float) value).doubleValue());
            }
            if (value instanceof BigInteger) {
                return new BigDecimal((BigInteger) value);
            }
            if (value instanceof Byte) {
                return BigDecimal.valueOf(((Byte) value).longValue());
            }
            if (value instanceof Short) {
                return BigDecimal.valueOf(((Short) value).longValue());
            }
            return null;
        } else if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? BigDecimal.ONE : BigDecimal.ZERO;
        } else {
            throw new UnsupportedOperationException("class " + value.getClass() + ", value '" + value + "' , parse to big decimal failed.");
        }
    }

    public static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String strValue = (String) value;
            if (org.apache.commons.lang3.StringUtils.isBlank(strValue)) {
                return null;
            }
            try {
                return Double.parseDouble(strValue);
            } catch (Exception e) {
                if ("true".equals(strValue)) {
                    return 1.0d;
                }
                if ("false".equals(strValue)) {
                    return 0.0d;
                }
                return new BigDecimal(strValue).doubleValue();
            }
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value) ? 1.0d : 0.0d;
            }
            throw new UnsupportedOperationException("class " + value.getClass() + ", value '" + value + "' , parse to double failed.");
        }
    }

    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String strValue = (String) value;
            if (org.apache.commons.lang3.StringUtils.isBlank(strValue)) {
                return null;
            }
            try {
                return Long.parseLong(strValue);
            } catch (Exception e) {
                try {
                    return Long.decode(strValue);
                } catch (Exception e2) {
                    if ("true".equals(strValue)) {
                        return 1L;
                    }
                    if ("false".equals(strValue)) {
                        return 0L;
                    }
                    return new BigDecimal(strValue).longValue();
                }
            }
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value) ? 1L : 0L;
            }
            throw new UnsupportedOperationException(value.getClass() + ", value '" + value + "' , parse to long failed.");
        }
    }

    public static boolean isZeroTime(Object value) {
        if (value == null || org.apache.commons.lang3.StringUtils.isBlank(value.toString())) {
            return false;
        }
        return value.toString().startsWith("0000-00-00");
    }

    public static String removeZone(String datetime) {
        if (datetime == null || datetime.length() == 0) {
            return datetime;
        }
        int len = datetime.length();
        if (datetime.charAt(len - 1) == 'Z' || datetime.charAt(len - 1) == 'z') {
            return datetime.substring(0, len - 1).trim();
        }
        if (len >= 7) {
            char checkCharAt1 = datetime.charAt(len - 2);
            char checkCharAt2 = datetime.charAt(len - 3);
            char checkCharAt3 = datetime.charAt(len - 6);
            char checkCharAt4 = datetime.charAt(len - 5);
            char checkCharAt5 = len >= 9 ? datetime.charAt(len - 9) : ' ';
            char checkCharAt6 = datetime.charAt(len - 7);
            boolean matchTest1 = (checkCharAt1 == '+' || checkCharAt1 == '-') && len >= 10;
            boolean matchTest2 = (checkCharAt2 == '+' || checkCharAt2 == '-') && len >= 11;
            boolean matchTest3 = (checkCharAt3 == '+' || checkCharAt3 == '-') && checkCharAt2 == ':';
            boolean matchTest4 = (checkCharAt4 == '+' || checkCharAt4 == '-') && checkCharAt2 == ':';
            boolean matchTest5 = (checkCharAt5 == '+' || checkCharAt5 == '-') && checkCharAt2 == ':' && checkCharAt3 == ':';
            boolean matchTest6 = checkCharAt6 == '+' || checkCharAt6 == '-';
            boolean matchTest7 = checkCharAt4 == '+' || checkCharAt4 == '-';
            if (matchTest1) {
                return datetime.substring(0, len - 2).trim();
            }
            if (matchTest2) {
                return datetime.substring(0, len - 3).trim();
            }
            if (matchTest3) {
                return datetime.substring(0, len - 6).trim();
            }
            if (matchTest4) {
                return datetime.substring(0, len - 5).trim();
            }
            if (matchTest5) {
                return datetime.substring(0, len - 9).trim();
            }
            if (matchTest6) {
                return datetime.substring(0, len - 7).trim();
            }
            if (matchTest7) {
                return datetime.substring(0, len - 5).trim();
            }
        }
        return datetime;
    }

    private static LocalDateTime toLocalDateTime(String value) {
        if (value.trim().length() >= 4) {
            String dateStr2 = removeZone(value);
            int len = dateStr2.length();
            if (len == 4) {
                return LocalDateTime.of(Integer.parseInt(dateStr2), 1, 1, 0, 0, 0, 0);
            }
            if (dateStr2.charAt(4) == '-') {
                switch (len) {
                    case 7:
                        String[] dataParts = dateStr2.split("-");
                        return LocalDateTime.of(Integer.parseInt(dataParts[0]), Integer.parseInt(dataParts[1]), 1, 0, 0, 0, 0);
                    case 8:
                    case 9:
                    case 11:
                    case 12:
                    case 14:
                    case 15:
                    case 17:
                    case 18:
                    default:
                        String[] dataTime = dateStr2.split(" ");
                        String[] dataParts2 = dataTime[0].split("-");
                        String[] timeParts = dataTime[1].split(":");
                        String[] secondParts = timeParts[2].split("\\.");
                        secondParts[1] = StringUtils.rightPad(secondParts[1], 9, Constants.CJ_MINOR_VERSION);
                        return LocalDateTime.of(Integer.parseInt(dataParts2[0]), Integer.parseInt(dataParts2[1]), Integer.parseInt(dataParts2[2]),
                            Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]), Integer.parseInt(secondParts[0]),
                            Integer.parseInt(secondParts[1]));
                    case 10:
                        String[] dataParts3 = dateStr2.split("-");
                        return LocalDateTime.of(Integer.parseInt(dataParts3[0]), Integer.parseInt(dataParts3[1]), Integer.parseInt(dataParts3[2]), 0,
                            0, 0, 0);
                    case 13:
                        String[] dataTime2 = dateStr2.split(" ");
                        String[] dataParts4 = dataTime2[0].split("-");
                        return LocalDateTime.of(Integer.parseInt(dataParts4[0]), Integer.parseInt(dataParts4[1]), Integer.parseInt(dataParts4[2]),
                            Integer.parseInt(dataTime2[1]), 0, 0, 0);
                    case 16:
                        String[] dataTime3 = dateStr2.split(" ");
                        String[] dataParts5 = dataTime3[0].split("-");
                        String[] timeParts2 = dataTime3[1].split(":");
                        return LocalDateTime.of(Integer.parseInt(dataParts5[0]), Integer.parseInt(dataParts5[1]), Integer.parseInt(dataParts5[2]),
                            Integer.parseInt(timeParts2[0]), Integer.parseInt(timeParts2[1]), 0, 0);
                    case 19:
                        String[] dataTime4 = dateStr2.split(" ");
                        String[] dataParts6 = dataTime4[0].split("-");
                        String[] timeParts3 = dataTime4[1].split(":");
                        return LocalDateTime.of(Integer.parseInt(dataParts6[0]), Integer.parseInt(dataParts6[1]), Integer.parseInt(dataParts6[2]),
                            Integer.parseInt(timeParts3[0]), Integer.parseInt(timeParts3[1]), Integer.parseInt(timeParts3[2]), 0);
                }
            } else if (dateStr2.charAt(2) == ':') {
                switch (len) {
                    case 5:
                        String[] timeParts4 = dateStr2.split(":");
                        return LocalDateTime.of(0, 1, 1, Integer.parseInt(timeParts4[0]), Integer.parseInt(timeParts4[1]), 0, 0);
                    case 8:
                        String[] timeParts5 = dateStr2.split(":");
                        return LocalDateTime.of(0, 1, 1, Integer.parseInt(timeParts5[0]), Integer.parseInt(timeParts5[1]),
                            Integer.parseInt(timeParts5[2]), 0);
                    default:
                        String[] timeParts6 = dateStr2.split(":");
                        String[] secondParts2 = timeParts6[2].split("\\.");
                        secondParts2[1] = StringUtils.rightPad(secondParts2[1], 9, Constants.CJ_MINOR_VERSION);
                        return LocalDateTime.of(0, 1, 1, Integer.parseInt(timeParts6[0]), Integer.parseInt(timeParts6[1]),
                            Integer.parseInt(secondParts2[0]), Integer.parseInt(secondParts2[1]));
                }
            } else {
                throw new UnsupportedOperationException(value.getClass() + ", value '" + value + "' , parse to local date time failed.");
            }
        } else if (StringUtils.isNumeric(value)) {
            return LocalDateTime.of(Integer.parseInt(value), 1, 1, 0, 0, 0, 0);
        } else {
            throw new DateTimeException(value + " format error.");
        }
    }

    public static String bytes2hex(byte[] b) {
        if (b == null) {
            return null;
        }
        if (b.length == 0) {
            return "";
        }
        StringBuilder hs = new StringBuilder();
        for (byte element : b) {
            String stmp = Integer.toHexString(element & 255).toUpperCase();
            if (stmp.length() == 1) {
                hs.append(Constants.CJ_MINOR_VERSION);
                hs.append(stmp);
            } else {
                hs.append(stmp);
            }
        }
        return hs.toString();
    }

    public static String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).toPlainString();
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? "1" : "0";
        }
        if (value instanceof byte[]) {
            return "0x" + bytes2hex((byte[]) value);
        }
        if (value instanceof Timestamp) {
            long nanos = ((Timestamp) value).getNanos();
            value = Instant.ofEpochMilli(((Timestamp) value).getTime() - (nanos / 1000000)).plusNanos(nanos).atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        } else if (value instanceof Date) {
            value = ((Date) value).toLocalDate().atTime(0, 0);
        } else if (value instanceof Time) {
            value = LocalDateTime.of(LocalDate.of(1970, 1, 1),
                Instant.ofEpochMilli(((Time) value).getTime()).atZone(ZoneId.systemDefault()).toLocalTime());
        } else if (value instanceof java.util.Date) {
            value = ((java.util.Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return coverLocalDateTime2String((LocalDateTime) value);
        } else if (value instanceof OffsetDateTime) {
            OffsetDateTime zone = (OffsetDateTime) value;
            String datetimeStr = coverLocalDateTime2String(zone.toLocalDateTime());
            String zonedStr = zone.getOffset().toString();
            if ("Z".equals(zonedStr)) {
                return datetimeStr + "+00:00";
            }
            return datetimeStr + zonedStr;
        } else if (!(value instanceof LocalTime)) {
            return value.toString();
        } else {
            LocalTime local3 = (LocalTime) value;
            return String.format("%02d:%02d:%02d", local3.getHour(), local3.getMinute(), local3.getSecond());
        }
    }


    private static String coverLocalDateTime2String(LocalDateTime localDateTime) {
        LocalDate localDate = localDateTime.toLocalDate();
        LocalTime localTime = localDateTime.toLocalTime();
        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        int day = localDate.getDayOfMonth();
        int hour = localTime.getHour();
        int minute = localTime.getMinute();
        int second = localTime.getSecond();
        int nano = localTime.getNano();
        return nano == 0 ? String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second) :
            String.format("%04d-%02d-%02d %02d:%02d:%02d.%s", year, month, day, hour, minute, second,
                new BigDecimal(nano).divide(NANO_SEC).toPlainString().substring(2));
    }

    public static String toMySqlTime(Object value) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return null;
        }
        if (value instanceof String) {
            return value.toString();
        }
        LocalDateTime localTime = toLocalDateTime(value);
        if (BASE.isBefore(localTime) || BASE.isEqual(localTime)) {
            long diffHours = Duration.between(BASE, localTime).toHours();
            if (localTime.getNano() == 0) {
                return String.format("%02d:%02d:%02d", diffHours, localTime.getMinute(), localTime.getSecond());
            }
            return String.format("%02d:%02d:%02d.%s", diffHours, localTime.getMinute(), localTime.getSecond(),
                Integer.parseInt(trimEnd(String.valueOf(localTime.getNano()), '0')));
        }
        Duration duration = Duration.between(localTime, BASE);
        long totalSecond = duration.getSeconds();
        long hours = totalSecond / ONE_HOUR;
        long remaining = totalSecond - (hours * ONE_HOUR);
        long minutes = remaining / ONE_MINUTE;
        remaining = remaining - (minutes * ONE_MINUTE);
        if (duration.getNano() == 0) {
            return String.format("-%02d:%02d:%02d", hours, minutes, remaining);
        }
        return String.format("-%02d:%02d:%02d.%s", hours, minutes, remaining, Integer.parseInt(trimEnd(String.valueOf(duration.getNano()), '0')));
    }

    public static String trimEnd(String str, char trimChar) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        char[] val = str.toCharArray();
        int len = val.length;
        while (0 < len && val[len - 1] == trimChar) {
            len--;
        }
        return len < val.length ? str.substring(0, len) : str;
    }

    public static byte[] numberToBinaryArray(Number number) {
        BigInteger bigInt = BigInteger.valueOf(number.longValue());
        int size = (bigInt.bitLength() + 7) / 8;
        byte[] result = new byte[size];
        byte[] bigIntBytes = bigInt.toByteArray();
        int start = bigInt.bitLength() % 8 == 0 ? 1 : 0;
        int length = Math.min(bigIntBytes.length - start, size);
        System.arraycopy(bigIntBytes, start, result, size - length, length);
        return result;
    }

    public static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String strValue = ((String) value).toLowerCase();
            if (StringUtils.isBlank(strValue)) {
                return null;
            }
            try {
                return Integer.parseInt(strValue);
            } catch (Exception e) {
                try {
                    return Integer.decode(strValue);
                } catch (Exception e2) {
                    if ("true".equals(strValue)) {
                        return 1;
                    }
                    if ("false".equals(strValue)) {
                        return 0;
                    }
                    return new BigDecimal(strValue).intValue();
                }
            }
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value) ? 1 : 0;
            }
            throw new UnsupportedOperationException("class " + value.getClass() + ", value '" + value + "' , parse to int failed.");
        }
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return null;
        }
        if (value instanceof Temporal) {
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
            if (value instanceof OffsetDateTime) {
                return ((OffsetDateTime) value).toLocalDateTime();
            }
            if (value instanceof LocalTime) {
                return LocalDateTime.of(LocalDate.of(1970, 1, 1), (LocalTime) value);
            } else if (value instanceof LocalDate) {
                return LocalDateTime.of((LocalDate) value, LocalTime.of(0, 0));
            } else {
                throw new UnsupportedOperationException(value.getClass() + ", value '" + value + "' , parse local date time failed.");
            }
        } else if (!(value instanceof java.util.Date)) {
            return toLocalDateTime(value.toString());
        } else {
            if (value instanceof Timestamp) {
                long nanos = ((Timestamp) value).getNanos();
                return Instant.ofEpochMilli(((Timestamp) value).getTime() - (nanos / 1000000)).plusNanos(nanos).atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            } else if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value).toLocalDate().atTime(0, 0);
            } else {
                if (!(value instanceof Time)) {
                    return ((java.util.Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
                return LocalDateTime.of(LocalDate.of(1970, 1, 1),
                    Instant.ofEpochMilli(((Time) value).getTime()).atZone(ZoneId.systemDefault()).toLocalTime());
            }
        }
    }

    public static boolean isHexNumber(String str) {
        boolean flag = true;
        if (str.startsWith("0x") || str.startsWith("0X")) {
            str = str.substring(2);
        }
        int i = 0;
        while (true) {
            if (i < str.length()) {
                char cc = str.charAt(i);
                if (cc != '0' && cc != '1' && cc != '2' && cc != '3' && cc != '4' && cc != '5' && cc != '6' && cc != '7' && cc != '8' && cc != '9' &&
                    cc != 'A' && cc != 'B' && cc != 'C' && cc != 'D' && cc != 'E' && cc != 'F' && cc != 'a' && cc != 'b' && cc != 'c' && cc != 'd' &&
                    cc != 'e' && cc != 'f') {
                    flag = false;
                    break;
                }
                i++;
            } else {
                break;
            }
        }
        return flag;
    }

    public static byte[] toBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String strVal = (String) value;
            if ((strVal.startsWith("0x") || strVal.startsWith("0X")) && isHexNumber(strVal)) {
                return hex2bytes(strVal.substring(2));
            }
            return ((String) value).getBytes(StandardCharsets.ISO_8859_1);
        } else if (value instanceof byte[]) {
            return (byte[]) value;
        } else {
            throw new UnsupportedOperationException("class " + value.getClass() + ", value '" + value + "' , parse to bytes failed.");
        }
    }

    public static String toGeometry(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String strVal = (String) value;
            if (!strVal.startsWith("0x") && !strVal.startsWith("0X")) {
                return (String) value;
            }
            return new WKTReader().read((String) value).toText();
        } else if (value instanceof byte[]) {
            // mysql add 4 byte in header of geometry
            byte[] bytes = (byte[]) value;
            if (bytes.length > 4) {
                byte[] dst = new byte[bytes.length - 4];
                System.arraycopy(bytes, 4, dst, 0, bytes.length - 4);
                return new WKBReader().read(dst).toText();
            }
            return new WKBReader().read(bytes).toText();
        } else {
            throw new UnsupportedOperationException("class " + value.getClass() + ", value '" + value + "' , " +
                "parse to geometry failed.");
        }
    }

    public static byte[] hex2bytes(String hexStr) {
        if (hexStr == null) {
            return null;
        }
        if (org.apache.commons.lang3.StringUtils.isBlank(hexStr)) {
            return new byte[0];
        }

        if (hexStr.length() % 2 == 1) {
            hexStr = "0" + hexStr;
        }

        int count = hexStr.length() / 2;
        byte[] ret = new byte[count];
        for (int i = 0; i < count; i++) {
            int index = i * 2;
            char c1 = hexStr.charAt(index);
            char c2 = hexStr.charAt(index + 1);
            ret[i] = (byte) (toByte(c1) << 4);
            ret[i] = (byte) (ret[i] | toByte(c2));
        }
        return ret;
    }

    private static byte toByte(char src) {
        switch (Character.toUpperCase(src)) {
            case '0':
                return 0;
            case '1':
                return 1;
            case '2':
                return 2;
            case '3':
                return 3;
            case '4':
                return 4;
            case '5':
                return 5;
            case '6':
                return 6;
            case '7':
                return 7;
            case '8':
                return 8;
            case '9':
                return 9;
            case 'A':
                return 10;
            case 'B':
                return 11;
            case 'C':
                return 12;
            case 'D':
                return 13;
            case 'E':
                return 14;
            case 'F':
                return 15;
        }
        throw new IllegalStateException("0-F");
    }
}
