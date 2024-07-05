package org.apache.eventmesh.common.config.connector.rdb.canal;

import com.mysql.cj.MysqlType;

import java.sql.JDBCType;

public enum CanalMySQLType {
    BIT("BIT"),
    TINYINT("TINYINT"),
    SMALLINT("SMALLINT"),
    MEDIUMINT("MEDIUMINT"),
    INT("INT"),
    BIGINT("BIGINT"),
    DECIMAL("DECIMAL"),
    FLOAT("FLOAT", JDBCType.REAL),
    DOUBLE("DOUBLE", JDBCType.DOUBLE),
    DATE("DATE", JDBCType.DATE),
    DATETIME("DATETIME", JDBCType.TIMESTAMP),
    TIMESTAMP("TIMESTAMP", JDBCType.TIMESTAMP),
    TIME("TIME", JDBCType.TIME),
    YEAR("YEAR", JDBCType.DATE),
    CHAR("CHAR", JDBCType.CHAR),
    VARCHAR("VARCHAR", JDBCType.VARCHAR),
    BINARY("BINARY", JDBCType.BINARY),
    VARBINARY("VARBINARY", JDBCType.VARBINARY),
    TINYBLOB("TINYBLOB", JDBCType.VARBINARY),
    BLOB("BLOB", JDBCType.LONGVARBINARY),
    MEDIUMBLOB("MEDIUMBLOB", JDBCType.LONGVARBINARY),
    LONGBLOB("LONGBLOB", JDBCType.LONGVARBINARY),
    TINYTEXT("TINYTEXT", JDBCType.VARCHAR),
    TEXT("TEXT", JDBCType.LONGVARCHAR),
    MEDIUMTEXT("MEDIUMTEXT", JDBCType.LONGVARCHAR),
    LONGTEXT("LONGTEXT", JDBCType.LONGVARCHAR),
    ENUM("ENUM", JDBCType.CHAR),
    SET("SET", JDBCType.CHAR),
    JSON("JSON", JDBCType.LONGVARCHAR),
    GEOMETRY("GEOMETRY", JDBCType.BINARY),
    POINT("POINT", JDBCType.BINARY),
    LINESTRING("LINESTRING", JDBCType.BINARY),
    POLYGON("POLYGON", JDBCType.BINARY),
    MULTIPOINT("MULTIPOINT", JDBCType.BINARY),
    GEOMETRY_COLLECTION("GEOMETRYCOLLECTION", JDBCType.BINARY),
    GEOM_COLLECTION("GEOMCOLLECTION", JDBCType.BINARY),
    MULTILINESTRING("MULTILINESTRING", JDBCType.BINARY),
    MULTIPOLYGON("MULTIPOLYGON", JDBCType.BINARY);

    private final String codeKey;
    private final MysqlType mysqlType;
    
    CanalMySQLType(String codeKey) {
        this.codeKey = codeKey;
        this.mysqlType = MysqlType.getByName(codeKey);
    }

    public static CanalMySQLType valueOfCode(String code) {
        CanalMySQLType[] values = values();
        for (CanalMySQLType tableType : values) {
            if (tableType.codeKey.equalsIgnoreCase(code)) {
                return tableType;
            }
        }
        switch (MysqlType.getByName(code)) {
            case BOOLEAN:
            case TINYINT:
            case TINYINT_UNSIGNED:
                return TINYINT;
            case SMALLINT:
            case SMALLINT_UNSIGNED:
                return SMALLINT;
            case INT:
            case INT_UNSIGNED:
                return INT;
            case BIGINT:
            case BIGINT_UNSIGNED:
                return BIGINT;
            case MEDIUMINT:
            case MEDIUMINT_UNSIGNED:
                return MEDIUMINT;
            case DECIMAL:
            case DECIMAL_UNSIGNED:
                return DECIMAL;
            case FLOAT:
            case FLOAT_UNSIGNED:
                return FLOAT;
            case DOUBLE:
            case DOUBLE_UNSIGNED:
                return DOUBLE;
            case BIT:
                return BIT;
            case BINARY:
                return BINARY;
            case VARBINARY:
                return VARBINARY;
            case TINYBLOB:
                return TINYBLOB;
            case MEDIUMBLOB:
                return MEDIUMBLOB;
            case LONGBLOB:
                return LONGBLOB;
            case BLOB:
                return BLOB;
            case CHAR:
                return CHAR;
            case VARCHAR:
                return VARCHAR;
            case TINYTEXT:
                return TINYTEXT;
            case MEDIUMTEXT:
                return MEDIUMTEXT;
            case LONGTEXT:
                return LONGTEXT;
            case TEXT:
                return TEXT;
            case DATE:
                return DATE;
            case TIME:
                return TIME;
            case TIMESTAMP:
                return TIMESTAMP;
            case DATETIME:
                return DATETIME;
            case YEAR:
                return YEAR;
            case JSON:
                return JSON;
            case ENUM:
                return ENUM;
            case SET:
                return SET;
            case GEOMETRY:
                return GEOMETRY;
            case NULL:
            case UNKNOWN:
            default:
                throw new UnsupportedOperationException("Unsupported mysql columnType " + code);
        }
    }

    public MysqlType getMysqlType() {
        return mysqlType;
    }
}
