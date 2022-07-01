package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

public class PropertiesUtils {

    public static Properties getPropertiesByPrefix(final Properties form, final Properties to, String prefix) {
        if (StringUtils.isBlank(prefix) || form == null) {
            return to;
        }
        form.forEach((key, value) -> {
                String keyStr = String.valueOf(key);
                if (StringUtils.startsWith(keyStr, prefix)) {
                    String realKey = StringUtils.substring(keyStr, prefix.length());
                    String[] hierarchicalKeys = StringUtils.split(realKey, Constants.DOT);
                    if (hierarchicalKeys != null) {
                        Properties hierarchical = to;
                        for (int idx = 0; idx < hierarchicalKeys.length; idx++) {
                            String hierarchicalKey = hierarchicalKeys[idx];
                            if (StringUtils.isBlank(hierarchicalKey)) {
                                return;
                            }
                            if (idx < hierarchicalKeys.length - 1) {
                                Object pending = hierarchical.get(hierarchicalKey);
                                if (pending == null) {
                                    hierarchical.put(hierarchicalKey, hierarchical = new Properties());
                                } else if (pending instanceof Properties) {
                                    hierarchical = (Properties) pending;
                                }
                                // Not Properties No need to parse anymore.
                                else {
                                    return;
                                }
                            } else {
                                hierarchical.put(hierarchicalKey, value);
                            }
                        }
                    }
                }
            }
        );
        return to;
    }
}
