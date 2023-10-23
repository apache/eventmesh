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

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;
import org.apache.eventmesh.webhook.api.common.SharedLatchHolder;
import org.apache.eventmesh.webhook.api.utils.ClassUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileWebHookConfigOperation implements WebHookConfigOperation {

    private final transient String webHookFilePath;

    public FileWebHookConfigOperation(final Properties properties) throws FileNotFoundException {
        final String webHookFilePath = WebHookOperationConstant.getFilePath(properties.getProperty("filePath"));

        final File webHookFileDir = new File(webHookFilePath);
        if (!webHookFileDir.exists()) {
            webHookFileDir.mkdirs();
        }
        if (!webHookFileDir.isDirectory()) {
            throw new FileNotFoundException("File path " + webHookFilePath + " is not directory");
        }
        this.webHookFilePath = webHookFilePath;
    }

    @Override
    public Integer insertWebHookConfig(final WebHookConfig webHookConfig) {
        if (!webHookConfig.getCallbackPath().startsWith(WebHookOperationConstant.CALLBACK_PATH_PREFIX)) {
            LogUtils.error(log, "webhookConfig callback path must start with {}",
                    WebHookOperationConstant.CALLBACK_PATH_PREFIX);
            return 0;
        }

        final File manuDir = new File(getWebhookConfigManuDir(webHookConfig));
        if (!manuDir.exists()) {
            manuDir.mkdir();
        }

        final File webhookConfigFile = getWebhookConfigFile(webHookConfig);
        if (webhookConfigFile.exists()) {
            LogUtils.error(log, "webhookConfig {} exists", webHookConfig.getCallbackPath());
            return 0;
        }
        return writeToFile(webhookConfigFile, webHookConfig) ? 1 : 0;
    }

    @Override
    public Integer updateWebHookConfig(final WebHookConfig webHookConfig) {
        final File webhookConfigFile = getWebhookConfigFile(webHookConfig);
        if (!webhookConfigFile.exists()) {
            LogUtils.error(log, "webhookConfig {} does not exist", webHookConfig.getCallbackPath());
            return 0;
        }
        return writeToFile(webhookConfigFile, webHookConfig) ? 1 : 0;
    }

    @Override
    public Integer deleteWebHookConfig(final WebHookConfig webHookConfig) {
        synchronized (SharedLatchHolder.lock) {
            final File webhookConfigFile = getWebhookConfigFile(webHookConfig);
            if (!webhookConfigFile.exists()) {
                LogUtils.error(log, "webhookConfig {} does not exist", webHookConfig.getCallbackPath());
                return 0;
            }
            return webhookConfigFile.delete() ? 1 : 0;
        }
    }

    /**
     * Query WebHook configuration information based on the WebHook callback path specified in {@link WebHookConfig}.
     */
    @Override
    public WebHookConfig queryWebHookConfigById(final WebHookConfig webHookConfig) {
        final File webhookConfigFile = getWebhookConfigFile(webHookConfig);
        if (!webhookConfigFile.exists()) {
            LogUtils.error(log, "webhookConfig {} does not exist", webHookConfig.getCallbackPath());
            return null;
        }

        return getWebHookConfigFromFile(webhookConfigFile);
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(final WebHookConfig webHookConfig,
        final Integer pageNum,
        final Integer pageSize) {
        final String manuDirPath = getWebhookConfigManuDir(webHookConfig);
        final File manuDir = new File(manuDirPath);
        if (!manuDir.exists()) {
            LogUtils.warn(log, "webhookConfig dir {} does not exist", manuDirPath);
            return new ArrayList<>();
        }

        final List<WebHookConfig> webHookConfigs = new ArrayList<>();

        final File[] webhookFiles = manuDir.listFiles();
        if (webhookFiles == null || webhookFiles.length == 0) {
            return webHookConfigs;
        }

        final int startIndex = (pageNum - 1) * pageSize;
        final int endIndex = pageNum * pageSize - 1;
        if (webhookFiles.length > startIndex) {
            for (int i = startIndex; i <= endIndex && i < webhookFiles.length; i++) {
                webHookConfigs.add(getWebHookConfigFromFile(webhookFiles[i]));
            }
        }
        return webHookConfigs;
    }

    private WebHookConfig getWebHookConfigFromFile(final File webhookConfigFile) {
        final StringBuilder fileContent = new StringBuilder();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(webhookConfigFile.getAbsolutePath()),
            StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                fileContent.append(line);
            }
        } catch (IOException e) {
            LogUtils.error(log, "get webHookConfig from file {} error", webhookConfigFile.getPath(), e);
            return null;
        }

        return JsonUtils.parseObject(fileContent.toString(), WebHookConfig.class);
    }

    public static boolean writeToFile(final File webhookConfigFile, final WebHookConfig webHookConfig) {
        // Wait for the previous cacheInit to complete in case of concurrency
        synchronized (SharedLatchHolder.lock) {
            try (FileOutputStream fos = new FileOutputStream(webhookConfigFile);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
                // Lock this file to prevent concurrent modification and it will be automatically unlocked when fos closes
                fos.getChannel().lock();
                bw.write(Objects.requireNonNull(JsonUtils.toJSONString(webHookConfig)));
            } catch (IOException e) {
                LogUtils.error(log, "write webhookConfig {} to file error", webHookConfig.getCallbackPath());
                return false;
            }
            return true;
        }
    }

    private String getWebhookConfigManuDir(final WebHookConfig webHookConfig) {
        return webHookFilePath + WebHookOperationConstant.FILE_SEPARATOR + webHookConfig.getManufacturerName();
    }

    private File getWebhookConfigFile(final WebHookConfig webHookConfig) {
        final String webhookConfigFilePath = this.getWebhookConfigManuDir(webHookConfig)
            + WebHookOperationConstant.FILE_SEPARATOR
            + ClassUtils.convertResourcePathToClassName(webHookConfig.getCallbackPath());

        return new File(webhookConfigFilePath);
    }
}
