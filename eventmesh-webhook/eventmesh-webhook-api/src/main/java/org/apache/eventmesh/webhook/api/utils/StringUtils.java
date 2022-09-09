package org.apache.eventmesh.webhook.api.utils;

public class StringUtils {

    public static final String getFileName(String path) {
        return path.substring(1).replace('/', '.');
    }
}
