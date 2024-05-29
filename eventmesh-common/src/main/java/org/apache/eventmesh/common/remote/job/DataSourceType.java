package org.apache.eventmesh.common.remote.job;

public enum DataSourceType {
    MYSQL("MySQL", DataSourceDriverType.MYSQL, DataSourceClassify.RDB),
    REDIS("Redis", DataSourceDriverType.REDIS, DataSourceClassify.CACHE),
    ROCKETMQ("RocketMQ", DataSourceDriverType.ROCKETMQ, DataSourceClassify.MQ);
    private final String name;
    private final DataSourceDriverType driverType;
    private final DataSourceClassify classify;

    DataSourceType(String name, DataSourceDriverType driverType, DataSourceClassify classify) {
        this.name = name;
        this.driverType = driverType;
        this.classify = classify;
    }

    public String getName() {
        return name;
    }

    public DataSourceDriverType getDriverType() {
        return driverType;
    }

    public DataSourceClassify getClassify() {
        return classify;
    }

    private static final DataSourceType[] TYPES = DataSourceType.values();

    public static DataSourceType getDataSourceType(Integer index) {
        if (index == null || index < 0 || index >= TYPES.length) {
            return null;
        }
        return TYPES[index];
    }
}
