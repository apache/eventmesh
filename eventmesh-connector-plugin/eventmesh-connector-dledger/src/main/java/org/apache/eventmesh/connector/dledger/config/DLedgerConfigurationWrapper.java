package org.apache.eventmesh.connector.dledger.config;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class DLedgerConfigurationWrapper {
    public static final String DLEDGER_CONF_FILE = "dledger-client.properties";

    private static final Properties properties = new Properties();

    static {
        loadProperties();
    }

    public String getProp(String key) {
        return StringUtils.isEmpty(key) ? null : properties.getProperty(key, null);
    }

    /**
     * Load DLedger properties file from classpath and conf home.
     * The properties defined in conf home will override classpath.
     */
    private void loadProperties() {
        try (InputStream resourceAsStream = DLedgerConfigurationWrapper.class.getResourceAsStream(
            "/" + DLEDGER_CONF_FILE)) {
            if (resourceAsStream != null) {
                properties.load(resourceAsStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Load %s.properties file from classpath error", DLEDGER_CONF_FILE));
        }
        try {
            String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + DLEDGER_CONF_FILE;
            if (new File(configPath).exists()) {
                properties.load(new BufferedReader(new FileReader(configPath)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot load %s file from conf", DLEDGER_CONF_FILE));
        }
    }
}
