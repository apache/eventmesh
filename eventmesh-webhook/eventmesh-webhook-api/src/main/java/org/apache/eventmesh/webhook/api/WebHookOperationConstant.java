
package org.apache.eventmesh.webhook.api;

import java.io.File;

/**
 * Webhook constant class
 */
public class WebHookOperationConstant {

    public static final String FILE_SEPARATOR = File.separator;

    public static final String FILE_EXTENSION = ".json";

    public static final String GROUP_PREFIX = "webhook_";

    public static final String DATA_ID_EXTENSION = ".json";

    public static final String MANUFACTURERS_DATA_ID = "manufacturers" + DATA_ID_EXTENSION;

    public static final Integer TIMEOUT_MS = 3 * 1000;

}