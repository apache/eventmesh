package com.apache.eventmesh.adminkotlin.config;

public class Constants {

    // config
    public static final String ADMIN_PROPS_PREFIX = "eventmesh";
    public static final String META_TYPE_NACOS = "nacos";
    public static final String META_TYPE_ETCD = "etcd";

    // Open-API
    public static final String HTTP_PREFIX = "http://";
    public static final String HTTPS_PREFIX = "https://";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // Nacos
    public static final String NACOS_LOGIN_API = "/nacos/v1/auth/login";
    public static final String NACOS_CONFIGS_API = "/nacos/v1/cs/configs";
}
