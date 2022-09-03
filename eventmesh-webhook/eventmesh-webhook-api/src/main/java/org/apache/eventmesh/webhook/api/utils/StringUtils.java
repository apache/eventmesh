package org.apache.eventmesh.webhook.api.utils;

public class StringUtils {

	public final static String getFileName(String path) {
		return path.substring(1).replace('/', '.');
	}
}
