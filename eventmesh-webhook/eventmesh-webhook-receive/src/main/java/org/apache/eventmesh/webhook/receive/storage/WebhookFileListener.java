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

package org.apache.eventmesh.webhook.receive.storage;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;
import org.apache.eventmesh.webhook.api.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookFileListener {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private String filePath;

    private Map<String, WebHookConfig> cacheWebHookConfig;

    private final Set<String> pathSet = new LinkedHashSet<>(); // monitored subdirectory

    private final Map<WatchKey, String> watchKeyPathMap = new HashMap<>(); // WatchKey's path

    public WebhookFileListener() {
    }

    public WebhookFileListener(String filePath, Map<String, WebHookConfig> cacheWebHookConfig) throws FileNotFoundException {
        this.filePath = WebHookOperationConstant.getFilePath(filePath);
        this.cacheWebHookConfig = cacheWebHookConfig;
        filePatternInit();
    }

    /**
     * Read the directory and register the listener
     *
     */
    public void filePatternInit()  {
        File webHookFileDir = new File(filePath);
        if (!webHookFileDir.exists()) {
            webHookFileDir.mkdirs();
        } else {
            readFiles(webHookFileDir);
        }
        fileWatchRegister();
    }

    /**
     * Recursively traverse the folder
     *
     * @param file file
     */
    public void readFiles(File file) {
        File[] fs = file.listFiles();
        for (File f : Objects.requireNonNull(fs)) {
            if (f.isDirectory()) {
                readFiles(f);
            } else if (f.isFile()) {
                cacheInit(f);
            }
        }
    }

    /**
     * Read the file and cache it in map
     *
     * @param webhookConfigFile webhookConfigFile
     */
    public void cacheInit(File webhookConfigFile) {
        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(webhookConfigFile)))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                fileContent.append(line);
            }
        } catch (IOException e) {
            logger.error("cacheInit failed", e);
        }
        WebHookConfig webHookConfig = JsonUtils.deserialize(fileContent.toString(), WebHookConfig.class);
        cacheWebHookConfig.put(webhookConfigFile.getName(), webHookConfig);
    }
    
    public void deleteConfig(File webhookConfigFile) {
    	cacheWebHookConfig.remove(webhookConfigFile.getName());
    }

    /**
     * Register listeners with folders
     */
    public void fileWatchRegister() {
        ExecutorService cachedThreadPool = Executors.newFixedThreadPool(1);
        cachedThreadPool.execute(() -> {
            File root = new File(filePath);
            loopDirInsertToSet(root, pathSet);

            WatchService service = null;
            try {
                service = FileSystems.getDefault().newWatchService();
            } catch (Exception e) {
                logger.error("getWatchService failed.", e);
            }

            for (String path : pathSet) {
                WatchKey key = null;
                try {
                    key = Paths.get(path).register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                } catch (IOException e) {
                    logger.error("registerWatchKey failed", e);
                }
                watchKeyPathMap.put(key, path);
            }

            while (true) {
                WatchKey key = null;
                try {
                    assert service != null;
                    key = service.take();
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                }

                assert key != null;
                for (WatchEvent<?> event : key.pollEvents()) {
                    String flashPath = watchKeyPathMap.get(key);
                    // manufacturer change
                    String path = flashPath +"/"+ event.context();
                    File file= new File(path);
                    if(ENTRY_CREATE == event.kind() || ENTRY_MODIFY == event.kind()) {
                    	if(file.isFile()) {
                    		cacheInit(file);
                    	}else {
                    		try {
                    			key = Paths.get(path).register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                    			watchKeyPathMap.put(key, path);
                    		}catch (IOException e) {
                                logger.error("registerWatchKey failed", e);
                            }
                    	}
                    }else if(ENTRY_DELETE == event.kind()) {
                    	if(file.isDirectory()) {
                    		watchKeyPathMap.remove(key);
                    	}else {
                    		deleteConfig(file);
                    	}
                    }
                }
                if (!key.reset()) {
                    break;
                }
            }
        });
    }

    /**
     * Recursive folder, adding folder's path to set
     *
     * @param parent  parent folder
     * @param pathSet folder's path set
     */
    private void loopDirInsertToSet(File parent, Set<String> pathSet) {
        if (!parent.isDirectory()) {
            return;
        }
        pathSet.add(parent.getPath());
        for (File child : Objects.requireNonNull(parent.listFiles())) {
            loopDirInsertToSet(child, pathSet);
        }
    }
}
