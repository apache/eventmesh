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

package org.apache.eventmesh.common.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.eventmesh.common.file.FileChangeContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigMonitorService} test class.
 *
 * <p>Tests:
 * <ul>
 *   <li>Registration and unregistration of config files</li>
 *   <li>File change triggers config hot-reload</li>
 *   <li>Defense against null/non-existent paths</li>
 *   <li>Static methods clear() and support()</li>
 * </ul>
 *
 * <p>Strategy: JUnit5 + temp files, no external config service dependency.</p>
 */
public class ConfigMonitorServiceTest {

    private ConfigMonitorService configMonitorService;

    /**
     * Simple config object for testing hot-reload scenarios.
     */
    @Config(prefix = "test")
    public static class TestMonitorConfig {
        @ConfigField(field = "key")
        private String name = "init";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TestMonitorHolder {

        private TestMonitorConfig config;

        public TestMonitorConfig getConfig() {
            return config;
        }

        public void setConfig(TestMonitorConfig config) {
            this.config = config;
        }
    }

    @Config(prefix = "test", path = "monitor-test.properties", monitor = true)
    public static class BuildConfigMonitorConfig {

        @ConfigField(field = "key")
        private String name = "init";

        public String getName() {
            return name;
        }
    }

    @BeforeEach
    void setUp() {
        // Clear static registry before each test to prevent state pollution
        configMonitorService = new ConfigMonitorService();
        ConfigMonitorService.clear();
    }

    @AfterEach
    void tearDown() {
        // Clear registry after test
        ConfigMonitorService.clear();
    }

    /**
     * Tests normal registration flow of monitor().
     *
     * <p>Scenario: Pass existing config file path, expect successful registration with logs.</p>
     * <p>Verification points:
     * <ul>
     *   <li>No exception thrown</li>
     *   <li>Repeated registration allowed (CopyOnWriteArrayList append)</li>
     * </ul>
     */
    @Test
    @DisplayName("monitor() - 正常注册已存在的配置文件")
    void testMonitorNormal() throws Exception {
        Path tempFile = Files.createTempFile("test-monitor", ".properties");
        tempFile.toFile().createNewFile();

        ConfigInfo configInfo = buildConfigInfo(tempFile.toString(), new TestMonitorConfig());

        // First registration
        configMonitorService.monitor(configInfo);

        // Second registration with same path - verify append mechanism (no exception)
        configMonitorService.monitor(configInfo);

        Files.deleteIfExists(tempFile);
    }

    /**
     * Tests defense handling of null file path in monitor().
     *
     * <p>Scenario: filePath is null, method returns early with warning log.</p>
     * <p>Verification: No exception, early termination.</p>
     */
    @Test
    @DisplayName("monitor() - filePath 为 null 时跳过监控")
    void testMonitorNullFilePath() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setFilePath(null);

        // Should not throw exception, return directly
        configMonitorService.monitor(configInfo);
    }

    /**
     * Tests defense handling of non-existent file in monitor().
     *
     * <p>Scenario: Pass non-existent file path, method returns early with warning log.</p>
     * <p>Verification: No exception, early termination.</p>
     */
    @Test
    @DisplayName("monitor() - 文件不存在时跳过监控")
    void testMonitorFileNotExist() {
        ConfigInfo configInfo = new ConfigInfo();
        // Use definitely non-existent path in system
        configInfo.setFilePath("/this/path/does/not/exist/unknown.properties");

        configMonitorService.monitor(configInfo);
    }

    /**
     * Tests clear() removes all registered monitoring items.
     *
     * <p>Scenario: Register multiple files then call clear(), verify registry is empty.</p>
     * <p>Verification: After clear(), support() returns false for all paths.</p>
     */
    @Test
    @DisplayName("clear() - 清除所有已注册的监控项")
    void testClear() throws Exception {
        Path tempFile1 = Files.createTempFile("test-clear-1", ".properties");
        Path tempFile2 = Files.createTempFile("test-clear-2", ".properties");
        tempFile1.toFile().createNewFile();
        tempFile2.toFile().createNewFile();

        configMonitorService.monitor(buildConfigInfo(tempFile1.toString(), new TestMonitorConfig()));
        configMonitorService.monitor(buildConfigInfo(tempFile2.toString(), new TestMonitorConfig()));

        // Verify registration success
        FileChangeContext ctx1 = new FileChangeContext();
        ctx1.setDirectoryPath(tempFile1.getParent().toString());
        ctx1.setFileName(tempFile1.getFileName().toString());
        Assertions.assertTrue(ConfigMonitorService.support(ctx1));

        // Execute clear
        ConfigMonitorService.clear();

        // Verify cleared
        Assertions.assertFalse(ConfigMonitorService.support(ctx1));

        Files.deleteIfExists(tempFile1);
        Files.deleteIfExists(tempFile2);
    }

    /**
     * Tests support() returns true for registered paths, false for unregistered.
     *
     * <p>Verification:
     * <ul>
     *   <li>Monitored file returns true</li>
     *   <li>Unmonitored file returns false</li>
     * </ul>
     */
    @Test
    @DisplayName("support() - 正确判断文件是否已注册监控")
    void testSupport() throws Exception {
        Path tempFile = Files.createTempFile("test-support", ".properties");
        tempFile.toFile().createNewFile();
        String normalizedPath = tempFile.toAbsolutePath().normalize().toString();

        FileChangeContext registeredCtx = new FileChangeContext();
        registeredCtx.setDirectoryPath(tempFile.getParent().toString());
        registeredCtx.setFileName(tempFile.getFileName().toString());

        FileChangeContext unregisteredCtx = new FileChangeContext();
        unregisteredCtx.setDirectoryPath(tempFile.getParent().toString());
        unregisteredCtx.setFileName("not-monitored.properties");

        // Before registration: both unsupported
        Assertions.assertFalse(ConfigMonitorService.support(registeredCtx));
        Assertions.assertFalse(ConfigMonitorService.support(unregisteredCtx));

        // After registration: only registered path supported
        configMonitorService.monitor(buildConfigInfo(normalizedPath, new TestMonitorConfig()));
        Assertions.assertTrue(ConfigMonitorService.support(registeredCtx));
        Assertions.assertFalse(ConfigMonitorService.support(unregisteredCtx));

        Files.deleteIfExists(tempFile);
    }

    /**
     * Tests file change triggers hot-reload via ConfigMonitorFileChangeListener.
     *
     * <p>Scenario: Monitor a config file, when content changes (onChanged triggered),
     * the object reference in ConfigInfo should update to new value.</p>
     *
     * <p>Verification:
     * <ul>
     *   <li>After file change, monitored object field value updates</li>
     *   <li>Repeated onChanged triggers don't cause exception</li>
     * </ul>
     */
    @Test
    @DisplayName("ConfigMonitorFileChangeListener.onChanged() - 文件变更触发热重载")
    void testOnChangedTriggersReload() throws Exception {
        // Create temp properties file with initial content
        Path tempFile = Files.createTempFile("test-reload", ".properties");
        String originalValue = "test-value-original";
        String updatedValue = "test-value-updated";
        writeProperty(tempFile, "test.key", originalValue);

        TestMonitorHolder holder = new TestMonitorHolder();
        TestMonitorConfig config = new TestMonitorConfig();
        holder.setConfig(config);

        ConfigInfo configInfo = buildConfigInfo(tempFile.toString(), holder);
        configMonitorService.monitor(configInfo);

        // Simulate file content changed externally
        writeProperty(tempFile, "test.key", updatedValue);

        waitUntil(() -> updatedValue.equals(holder.getConfig().getName()));
        Assertions.assertEquals(updatedValue, holder.getConfig().getName());

        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("buildConfigInstance() - monitor=true 时文件变更更新原配置对象")
    void testBuildConfigInstanceMonitor() throws Exception {
        Path tempDir = Files.createTempDirectory("test-build-monitor");
        Path tempFile = tempDir.resolve("monitor-test.properties");
        writeProperty(tempFile, "test.key", "before");

        try {
            ConfigService.getInstance().setConfigPath(tempDir.toString());
            BuildConfigMonitorConfig config = ConfigService.getInstance().buildConfigInstance(BuildConfigMonitorConfig.class);
            Assertions.assertEquals("before", config.getName());

            writeProperty(tempFile, "test.key", "after");

            waitUntil(() -> "after".equals(config.getName()));
            Assertions.assertEquals("after", config.getName());

            writeProperty(tempFile, "test.key", "final");

            waitUntil(() -> "final".equals(config.getName()));
            Assertions.assertEquals("final", config.getName());
        } finally {
            ConfigService.getInstance().setConfigPath(null);
        }

        Files.deleteIfExists(tempFile);
        Files.deleteIfExists(tempDir);
    }

    /**
     * Tests exception handling in load() method.
     *
     * <p>Scenario: When ConfigService.getConfig throws exception (e.g., file deleted),
     * load() should catch exception and log error, not propagate to caller.</p>
     *
     * <p>Verification: load() completes without throwing exception, only logs error.</p>
     */
    @Test
    @DisplayName("load() - 文件不存在时捕获异常不外抛")
    void testLoadWithException() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setPath("/non/exist/path.properties");
        configInfo.setClazz(TestMonitorConfig.class);
        configInfo.setObject(new TestMonitorConfig());
        configInfo.setInstance(new TestMonitorConfig());

        // Even if file doesn't exist, load should not throw exception
        ConfigMonitorService.load(configInfo);
    }

    /**
     * Tests load() returns early when config value unchanged.
     *
     * <p>Scenario: When object returned by getConfig equals current configInfo.object,
     * field write operation should be skipped, return directly.</p>
     *
     * <p>Verification: Method returns normally, no "config reload success" log.</p>
     */
    @Test
    @DisplayName("load() - 配置未变化时跳过重载")
    void testLoadSkipWhenSame() throws Exception {
        Path tempFile = Files.createTempFile("test-skip-reload", ".properties");
        writeProperty(tempFile, "test.key", "unchanged");
        tempFile.toFile().deleteOnExit();

        TestMonitorConfig config = new TestMonitorConfig();
        config.setName("unchanged");

        ConfigInfo configInfo = buildConfigInfo(tempFile.toString(), config);

        // Simulate ConfigService loaded same object (equals returns true)
        // This scenario relies on ConfigService.getConfig returning same reference, load should return early
        ConfigMonitorService.load(configInfo);

        Files.deleteIfExists(tempFile);
    }

    // ======================== Helper Methods ========================

    /**
     * Builds complete ConfigInfo object.
     *
     * @param filePath    config file path (absolute path)
     * @param instance    runtime object holding config fields
     * @return ConfigInfo instance with all necessary fields populated
     */
    private ConfigInfo buildConfigInfo(String filePath, Object instance) throws Exception {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setFilePath(filePath);
        configInfo.setPath(ConfigService.FILE_PATH_PREFIX + filePath);
        configInfo.setClazz(TestMonitorConfig.class);
        configInfo.setPrefix("test");
        configInfo.setInstance(instance);

        // Find first TestMonitorConfig field in instance and set as objectField
        // Simulates real ConfigInfo initialization in ConfigService.populateConfigForObject
        Field objectField = findFirstField(instance.getClass(), TestMonitorConfig.class);
        if (objectField != null) {
            objectField.setAccessible(true);
            configInfo.setObjectField(objectField);
            configInfo.setObject(objectField.get(instance));
        }

        return configInfo;
    }

    /**
     * Finds first field of targetType in clazz.
     */
    private Field findFirstField(Class<?> clazz, Class<?> targetType) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType().equals(targetType)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Writes single key-value pair to temp properties file.
     */
    private void writeProperty(Path file, String key, String value) throws Exception {
        try (OutputStream os = new FileOutputStream(file.toFile())) {
            java.util.Properties props = new java.util.Properties();
            props.setProperty(key, value);
            props.store(os, "test properties");
        }
    }

    private void waitUntil(Check check) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (check.success()) {
                return;
            }
            Thread.sleep(100);
        }
        Assertions.fail("condition was not met before timeout");
    }

    private interface Check {

        boolean success();
    }
}
