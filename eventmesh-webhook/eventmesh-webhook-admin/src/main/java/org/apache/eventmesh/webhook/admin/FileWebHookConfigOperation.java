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

import java.io.*;
import java.net.URLEncoder;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWebHookConfigOperation implements WebHookConfigOperation {

    private static final Logger logger = LoggerFactory.getLogger(FileWebHookConfigOperation.class);

	private final String webHookFilePath;


	public FileWebHookConfigOperation(Properties properties) throws FileNotFoundException {
		String webHookFilePath =  WebHookOperationConstant.getFilePath(properties.getProperty("filePath"));;
		assert webHookFilePath != null;
		File webHookFileDir = new File(webHookFilePath);
		if (!webHookFileDir.isDirectory()) {
			throw new FileNotFoundException("File path " + webHookFilePath + " is not directory");
		}
		if (!webHookFileDir.exists()) {
			webHookFileDir.mkdirs();
		}
		this.webHookFilePath = webHookFilePath;
	}

	@Override
	public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
		if (!webHookConfig.getCallbackPath().startsWith(WebHookOperationConstant.CALLBACK_PATH_PREFIX)) {
			logger.error("webhookConfig callback path must start with {}", WebHookOperationConstant.CALLBACK_PATH_PREFIX);
			return 0;
		}
		File manuDir = new File(getWebhookConfigManuDir(webHookConfig));
		if (!manuDir.exists()) {
			manuDir.mkdir();
		}
		File webhookConfigFile = getWebhookConfigFile(webHookConfig);
		if (webhookConfigFile.exists()) {
			logger.error("webhookConfig {} is existed", webHookConfig.getCallbackPath());
			return 0;
		}
		return writeToFile(webhookConfigFile, webHookConfig) ? 1 : 0;
	}

	@Override
	public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
		File webhookConfigFile = getWebhookConfigFile(webHookConfig);
		if (!webhookConfigFile.exists()) {
			logger.error("webhookConfig {} is not existed", webHookConfig.getCallbackPath());
			return 0;
		}
		return writeToFile(webhookConfigFile, webHookConfig) ? 1 : 0;
	}

	@Override
	public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
		File webhookConfigFile = getWebhookConfigFile(webHookConfig);
		if (!webhookConfigFile.exists()) {
			logger.error("webhookConfig {} is not existed", webHookConfig.getCallbackPath());
			return 0;
		}
		return webhookConfigFile.delete() ? 1 : 0;
	}

	@Override
	public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
		File webhookConfigFile = getWebhookConfigFile(webHookConfig);
		if (!webhookConfigFile.exists()) {
			logger.error("webhookConfig {} is not existed", webHookConfig.getCallbackPath());
			return null;
		}
		return getWebHookConfigFromFile(webhookConfigFile);
	}

	@Override
	public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
			Integer pageSize) {
		String manuDirPath = getWebhookConfigManuDir(webHookConfig);
		File manuDir = new File(manuDirPath);
		if (!manuDir.exists()) {
			logger.warn("webhookConfig dir {} is not existed", manuDirPath);
			return new ArrayList<>();
		}
		File[] webhookFiles = manuDir.listFiles();
		int startIndex = (pageNum-1)*pageSize, endIndex = pageNum*pageSize-1;
		List<WebHookConfig> webHookConfigs = new ArrayList<>();
		if (webhookFiles.length > startIndex) {
			for (int i = startIndex; i < endIndex && i < webhookFiles.length; i++) {
				webHookConfigs.add(getWebHookConfigFromFile(webhookFiles[i]));
			}
		}
		return webHookConfigs;
	}

	private WebHookConfig getWebHookConfigFromFile(File webhookConfigFile) {
		StringBuffer fileContent = new StringBuffer();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(webhookConfigFile)))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				fileContent.append(line);
			}
		} catch (IOException e) {
			logger.error("get webhook from file {} error", webhookConfigFile.getPath(), e);
			return null;
		}
		return JsonUtils.deserialize(fileContent.toString(), WebHookConfig.class);
	}

	private boolean writeToFile(File webhookConfigFile, WebHookConfig webHookConfig) {
		FileLock lock = null;
		try (FileOutputStream fos = new FileOutputStream(webhookConfigFile);BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))){
			// lock this file
			lock = fos.getChannel().lock();
			bw.write(JsonUtils.serialize(webHookConfig));
		} catch (IOException e) {
			logger.error("write webhookConfig {} to file error", webHookConfig.getCallbackPath());
			return false;
		} finally {
			try {
				if (lock != null) {
					lock.release();
				}
			} catch (IOException e) {
				logger.warn("writeToFile finally caught an exception", e);
			}
		}
		return true;
	}

	private String getWebhookConfigManuDir(WebHookConfig webHookConfig) {
		return webHookFilePath + WebHookOperationConstant.FILE_SEPARATOR + webHookConfig.getManufacturerName();
	}

	private File getWebhookConfigFile(WebHookConfig webHookConfig) {
		String webhookConfigFilePath = null;
		try {
			// use URLEncoder.encode before, because the path may contain some speacial char like '/', which is illegal as a file name.
			webhookConfigFilePath = this.getWebhookConfigManuDir(webHookConfig) + WebHookOperationConstant.FILE_SEPARATOR + URLEncoder.encode(webHookConfig.getCallbackPath(), "UTF-8") + WebHookOperationConstant.FILE_EXTENSION;
		} catch (UnsupportedEncodingException e) {
			logger.error("get webhookConfig file path {} failed", webHookConfig.getCallbackPath(), e);
		}
		assert webhookConfigFilePath != null;
		return new File(webhookConfigFilePath);
	}


}
