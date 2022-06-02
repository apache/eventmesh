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

import java.io.*;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HookConfigOperationManage implements WebHookConfigOperation {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private String filePath;

    private String serverAddr;

    private Boolean filePattern;

    private ConfigService configService;
    private static final String GROUP_PREFIX = "webhook_" ;

    private static final String DATA_ID_EXTENSION = ".json";

    private static final Integer TIMEOUT_MS = 3*1000;

    /**
     * webhook config pool -> <CallbackPath,WebHookConfig>
     */
    private final Map<String, WebHookConfig> cacheWebHookConfig = new ConcurrentHashMap<>();

    public HookConfigOperationManage() {
        try {
            filePatternInit(filePath);
        } catch (FileNotFoundException e) {
            filePattern = false;
            logger.error("filePatternInit failed", e);
        }
        try {
            nacosPatternInit(serverAddr);
        } catch (NacosException e) {
            logger.error("nacosPatternInit failed", e);
        }
    }

    private void nacosPatternInit(String serverAddr) throws NacosException {
        configService = ConfigFactory.createConfigService(serverAddr);
    }

    @Override
    public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    @Override
    public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    @Override
    public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
        cacheWebHookConfig.remove(webHookConfig.getCallbackPath());
        return 1;
    }

    @Override
    public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
        if(filePattern) return cacheWebHookConfig.get(webHookConfig.getCallbackPath());
        else{
            try {
                String content = configService.getConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + webHookConfig.getManufacturerName(), TIMEOUT_MS);
                return JsonUtils.deserialize(content, WebHookConfig.class);
            } catch (NacosException e) {
                logger.error("updateWebHookConfig failed", e);
            }
            return null;
        }
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
                                                                Integer pageSize) {
        return null;
    }

    public void filePatternInit(String filePath) throws FileNotFoundException {
        File webHookFileDir = new File(filePath);
        if (!webHookFileDir.isDirectory()) {
            throw new FileNotFoundException("File path " + filePath + " is not directory");
        }
        if (!webHookFileDir.exists()) {
            webHookFileDir.mkdirs();
        } else {
            readFunc(webHookFileDir);
        }
        fileWatch(filePath);
    }

    public void readFunc(File file) {
        File[] fs = file.listFiles();
        for (File f : Objects.requireNonNull(fs)) {
            if (f.isDirectory()) readFunc(f);
            if (f.isFile()) cacheInit(f);
        }
    }

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
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
    }

    private final Kind[] kinds = {
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE};

    Set<String> pathSet = new LinkedHashSet<>(); // monitored subdirectory

    Map<WatchKey, String> watchKeyPathMap = new HashMap<>(); //WatchKey's path

    public void fileWatch(String filePath) {
        ExecutorService cachedThreadPool = Executors.newFixedThreadPool(1);
        cachedThreadPool.execute(() -> {
            File root = new File(filePath);
            loopDir(root, pathSet);

            WatchService service = null;
            try {
                service = FileSystems.getDefault().newWatchService();
            } catch (Exception e) {
                logger.error("getWatchService failed", e);
            }

            for (String path : pathSet) {
                WatchKey key = null;
                try {
                    key = Paths.get(path).register(service, kinds);
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
                    //manufacturer change
                    if (flashPath.equals(filePath)) {
                        if (StandardWatchEventKinds.ENTRY_CREATE == event.kind()) {
                            try {
                                key = Paths.get(filePath + event.context()).register(service, kinds);
                            } catch (IOException e) {
                                logger.error("registerWatchKey failed", e);
                            }
                            watchKeyPathMap.put(key, filePath + event.context());
                        }
                    } else { //config change
                        cacheInit(new File(flashPath + event.context()));
                    }
                }
                if (!key.reset()) break;
            }
        });
    }

    private void loopDir(File parent, Set<String> pathSet) {
        if (!parent.isDirectory()) {
            return;
        }
        pathSet.add(parent.getPath());
        for (File child : Objects.requireNonNull(parent.listFiles())) {
            loopDir(child, pathSet);
        }
    }

}
