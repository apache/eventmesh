package org.apache.eventmesh.common.remote.job;

import java.util.HashMap;
import java.util.Map;

public enum JobTransportType {
    MYSQL_MYSQL(DataSourceType.MYSQL, DataSourceType.MYSQL),
    REDIS_REDIS(DataSourceType.REDIS, DataSourceType.REDIS),
    ROCKETMQ_ROCKETMQ(DataSourceType.ROCKETMQ,DataSourceType.ROCKETMQ);
    private static final Map<String,JobTransportType> INDEX_TYPES = new HashMap<>();
    private static final JobTransportType[] TYPES = JobTransportType.values();
    private static final String SEPARATOR = "@";
    static {
        for (JobTransportType type : TYPES) {
            INDEX_TYPES.put(generateKey(type.src, type.dst),type);
        }
    }

    DataSourceType src;

    DataSourceType dst;

    JobTransportType(DataSourceType src, DataSourceType dst) {
        this.src = src;
        this.dst = dst;
    }

    private static String generateKey(DataSourceType src, DataSourceType dst) {
        return src.ordinal() + SEPARATOR + dst.ordinal();
    }

    public DataSourceType getSrc() {
        return src;
    }

    public DataSourceType getDst() {
        return dst;
    }

    public static JobTransportType getJobTransportType(DataSourceType src, DataSourceType dst) {
        return INDEX_TYPES.get(generateKey(src,dst));
    }

    public static JobTransportType getJobTransportType(Integer index) {
        if (index == null || index < 0 || index >= TYPES.length) {
            return null;
        }
        return TYPES[index];
    }
}
