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
import java.util.ArrayList;
import java.util.List;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWebHookConfigOperation implements WebHookConfigOperation {

	private static final Logger logger = LoggerFactory.getLogger(FileWebHookConfigOperation.class);

	private String filePath;

	private static final String FILE_SEPARATOR = File.separator;

	private static final String FILE_EXTENSION = ".json";

	public FileWebHookConfigOperation(String filePath) throws FileNotFoundException {
		File webHookFileDir = new File(filePath);
		if (!webHookFileDir.isDirectory()) {
			throw new FileNotFoundException("File path " + filePath + " is not directory");
		}
		if (!webHookFileDir.exists()) {
			webHookFileDir.mkdirs();
		}
		this.filePath = filePath;
	}

	@Override
	public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
		String manufacturerName = webHookConfig.getManufacturerName();
		String manuDirPath = filePath + FILE_SEPARATOR + manufacturerName;
		File manuDir = new File(manuDirPath);
		if (!manuDir.exists()) {
			manuDir.mkdir();
		}
		File webhookConfigFile = new File(manuDirPath + FILE_SEPARATOR + webHookConfig.getManufacturerEventName() + FILE_EXTENSION);
		if (webhookConfigFile.exists()) {
			logger.error("webhookConfig " + manufacturerName + "_" + webHookConfig.getManufacturerEventName() + " is existed");
			return 0;
		}
		try (FileWriter fw = new FileWriter(webhookConfigFile); BufferedWriter bw = new BufferedWriter(fw)) {
			bw.write(JsonUtils.serialize(webHookConfig));
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}
		return 1;
	}

	@Override
	public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
		String manufacturerName = webHookConfig.getManufacturerName();
		String manuDirPath = filePath + FILE_SEPARATOR + manufacturerName;
		File webhookConfigFile = new File(manuDirPath + FILE_SEPARATOR + webHookConfig.getManufacturerEventName() + FILE_EXTENSION);
		if (!webhookConfigFile.exists()) {
			logger.error("webhookConfig " + manufacturerName + "_" + webHookConfig.getManufacturerEventName() + " is not existed");
			return 0;
		}
		try (FileWriter fw = new FileWriter(webhookConfigFile); BufferedWriter bw = new BufferedWriter(fw)) {
			bw.write(JsonUtils.serialize(webHookConfig));
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}
		return 1;
	}

	@Override
	public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
		String manufacturerName = webHookConfig.getManufacturerName();
		String manuDirPath = filePath + FILE_SEPARATOR + manufacturerName;
		File webhookConfigFile = new File(manuDirPath + FILE_SEPARATOR + webHookConfig.getManufacturerEventName() + FILE_EXTENSION);
		if (!webhookConfigFile.exists()) {
			logger.error("webhookConfig " + manufacturerName + "_" + webHookConfig.getManufacturerEventName() + " is not existed");
			return 0;
		}
		return webhookConfigFile.delete() ? 1 : 0;
	}

	@Override
	public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
		String manufacturerName = webHookConfig.getManufacturerName();
		String manuDirPath = filePath + FILE_SEPARATOR + manufacturerName;
		File webhookConfigFile = new File(manuDirPath + FILE_SEPARATOR + webHookConfig.getManufacturerEventName() + FILE_EXTENSION);
		if (!webhookConfigFile.exists()) {
			logger.error("webhookConfig " + manufacturerName + "_" + webHookConfig.getManufacturerEventName() + " is not existed");
			return null;
		}

		return getWebHookConfigFromFile(webhookConfigFile);
	}

	@Override
	public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
			Integer pageSize) {
		String manufacturerName = webHookConfig.getManufacturerName();
		String manuDirPath = filePath + FILE_SEPARATOR + manufacturerName;
		File manuDir = new File(manuDirPath);
		if (!manuDir.exists()) {
			logger.warn("webhookConfig dir " + manufacturerName + " is not existed");
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
		String fileContent = "";
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(webhookConfigFile)))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				fileContent += line;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return JsonUtils.deserialize(fileContent, WebHookConfig.class);
	}

}
