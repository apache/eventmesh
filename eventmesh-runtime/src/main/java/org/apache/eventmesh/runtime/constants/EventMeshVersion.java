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

package org.apache.eventmesh.runtime.constants;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshVersion {

    private static String CURRENT_VERSION = "";

    private static final String VERSION_KEY = "Implementation-Version";

    private static final String SPEC_VERSION_KEY = "Specification-Version";

    /**
     * The version pattern in build.gradle. In general, it is like version='0.0.1' or version="0.0.1" if exists.
     */
    private static final String VERSION_PATTERN = "version\\s*=\\s*['\"](.+)['\"]";

    /**
     * @return Eg: "v0.0.1-release"
     */
    public static String getCurrentVersionDesc() {
        if (StringUtils.isNotBlank(getCurrentVersion())) {
            return "v" + CURRENT_VERSION;
        }
        return "";
    }

    /**
     * @return Eg: "0.0.1-release"
     */
    public static String getCurrentVersion() {
        if (StringUtils.isNotBlank(CURRENT_VERSION)) {
            return CURRENT_VERSION;
        }
        // get version from jar.
        getVersionFromJarFile();
        // get version from build file.
        if (StringUtils.isBlank(CURRENT_VERSION)) {
            getVersionFromBuildFile();
        }
        return CURRENT_VERSION;
    }

    private static void getVersionFromJarFile() {
        // get version from MANIFEST.MF in jar.
        CodeSource codeSource = EventMeshVersion.class.getProtectionDomain().getCodeSource();
        if (Objects.isNull(codeSource)) {
            log.warn("Failed to get CodeSource for EventMeshVersion.class");
            return;
        }
        URL url = codeSource.getLocation();
        if (Objects.isNull(url)) {
            log.warn("Failed to get URL for EventMeshVersion.class");
            return;
        }
        try (JarFile jarFile = new JarFile(url.getPath())) {
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            CURRENT_VERSION = StringUtils.isBlank(attributes.getValue(VERSION_KEY))
                ? attributes.getValue(SPEC_VERSION_KEY) : attributes.getValue(VERSION_KEY);

            // get version from the file name of jar.
            if (StringUtils.isBlank(CURRENT_VERSION)) {
                getVersionFromJarFileName(url.getFile());
            }
        } catch (IOException e) {
            log.error("Failed to load project version from MANIFEST.MF due to IOException {}.", e.getMessage());
        }
    }

    private static void getVersionFromBuildFile() {
        String projectDir = System.getProperty("user.dir");

        String gradlePropertiesPath = projectDir + File.separator + "gradle.properties";
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(gradlePropertiesPath)) {
            properties.load(fis);
            CURRENT_VERSION = properties.getProperty("version");
        } catch (IOException e) {
            log.error("Failed to load version from gradle.properties due to IOException {}.", e.getMessage());
        }

        if (StringUtils.isBlank(CURRENT_VERSION)) {
            String buildGradlePath = projectDir + File.separator + "build.gradle";
            try {
                File buildFile = new File(buildGradlePath);
                String content = new String(Files.readAllBytes(buildFile.toPath()));
                Pattern pattern = Pattern.compile(VERSION_PATTERN);
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    CURRENT_VERSION = matcher.group(1);
                } else {
                    log.warn("Failed to load version from build.gradle due to missing the configuration of \"version='xxx'\".");
                }
            } catch (IOException e) {
                log.error("Failed to load version from build.gradle due to IOException {}.", e.getMessage());
            }
        }
    }

    // path: /.../.../eventmesh-runtime-0.0.1-xxx.jar, version: 0.0.1-xxx
    private static void getVersionFromJarFileName(String path) {
        if (!StringUtils.isEmpty(path) && path.endsWith(".jar")) {
            Path filePath = Paths.get(path);
            String fileName = filePath.getFileName().toString();
            fileName = fileName.replace(".jar", "");
            Pattern pattern = Pattern.compile("-\\d");
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.find()) {
                int index = matcher.start();
                CURRENT_VERSION = fileName.substring(index + 1);
            } else {
                log.info("Failed to load version from jar name due to missing related info.");
            }
        }
    }
}
