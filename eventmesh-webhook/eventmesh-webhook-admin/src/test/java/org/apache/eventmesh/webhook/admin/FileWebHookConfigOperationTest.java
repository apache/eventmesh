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

package org.apache.eventmesh.webhook.admin;

import org.apache.eventmesh.webhook.api.WebHookConfig;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileWebHookConfigOperationTest {

    @Test
    public void testInsertWebHookConfig() {
        Properties properties = new Properties();
        properties.setProperty("filePath", "test_dir");

        WebHookConfig config = new WebHookConfig();
        config.setCallbackPath("/webhook/github/eventmesh/all");
        config.setManufacturerName("github");
        config.setManufacturerDomain("www.github.com");
        config.setManufacturerEventName("all");
        config.setSecret("eventmesh");
        config.setCloudEventName("github-eventmesh");

        try {
            FileWebHookConfigOperation fileWebHookConfigOperation = new FileWebHookConfigOperation(properties);
            Integer result = fileWebHookConfigOperation.insertWebHookConfig(config);
            Assertions.assertTrue(Objects.nonNull(result) && result == 1);

            WebHookConfig queryConfig = new WebHookConfig();
            queryConfig.setManufacturerName("github");
            List<WebHookConfig> queryResult = fileWebHookConfigOperation.queryWebHookConfigByManufacturer(queryConfig, 1, 1);
            Assertions.assertTrue(Objects.nonNull(queryResult) && queryResult.size() == 1);
            Assertions.assertEquals(queryResult.get(0).getCallbackPath(), config.getCallbackPath());
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        } finally {
            deleteDir("test_dir");
        }
    }

    private boolean deleteDir(String path) {
        try {
            Files.walk(FileSystems.getDefault().getPath(path))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile).forEach(File::delete);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
