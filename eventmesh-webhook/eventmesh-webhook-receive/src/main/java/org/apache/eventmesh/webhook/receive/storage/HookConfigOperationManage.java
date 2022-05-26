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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HookConfigOperationManage implements WebHookConfigOperation {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private String filePath;

    private static final String FILE_SEPARATOR = File.separator;

    private static final String FILE_EXTENSION = ".json";

    /**
     * webhook 配置池 -> <CallbackPath,WebHookConfig>
     */
    private final Map<String, WebHookConfig> cacheWebHookConfig = new ConcurrentHashMap<>();

    public HookConfigOperationManage() {
        try {
            filePatternInit(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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

        return cacheWebHookConfig.get(webHookConfig.getCallbackPath());
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
                                                                Integer pageSize) {
        return null;
    }

    /**
     * 文件模式初始化
     *
     * @param filePath
     */
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
        //todo 注册文件监听
    }

    public void readFunc(File file) {
        File[] fs = file.listFiles();
        for (File f : fs) {
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
            e.printStackTrace();
        }
        WebHookConfig webHookConfig = JsonUtils.deserialize(fileContent.toString(), WebHookConfig.class);
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
    }

    private final WatchEvent.Kind[] kinds = {
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE};

    Set<String> pathSet = new LinkedHashSet<>();

    Map<WatchKey, String> watchKeyPathMap = new HashMap<>();

    public void fileWatch(String filePath) {
        ExecutorService cachedThreadPool = Executors.newFixedThreadPool(1);
        cachedThreadPool.execute(() -> {
            File root = new File(filePath); //根目录
            loopDir(root, pathSet); //需要监视的子目录

            //获取当前文件系统的监控对象
            WatchService service = null;
            try {
                service = FileSystems.getDefault().newWatchService();
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (String path : pathSet) {
                WatchKey key = null;
                try {
                    key = Paths.get(path).register(service, kinds);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                watchKeyPathMap.put(key, path);
            }

            while (true) {
                WatchKey key = null;
                try {
                    assert service != null;
                    key = service.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                assert key != null;
                for (WatchEvent<?> event : key.pollEvents()) {
                    //新增厂商
                    if (watchKeyPathMap.get(key).equals(filePath) && StandardWatchEventKinds.ENTRY_CREATE == event.kind()) {
                        try {
                            key = Paths.get(filePath + event.context()).register(service, kinds);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        watchKeyPathMap.put(key, filePath + event.context());
                    } else { //厂商配置变更
                        if (StandardWatchEventKinds.ENTRY_CREATE == event.kind()) {
                            cacheInit(new File(watchKeyPathMap.get(key) + event.context()));
                        } else if (StandardWatchEventKinds.ENTRY_DELETE == event.kind()) {
                            deleteConfigWhenFile(filePath, key, event);
                        } else if (StandardWatchEventKinds.ENTRY_MODIFY == event.kind()) {
                            deleteConfigWhenFile(filePath, key, event);
                            cacheInit(new File(watchKeyPathMap.get(key) + event.context()));
                        }
                    }

                    System.out.println("event.context() = " + event.context() + "\t"
                            + "event.kind() = " + event.kind() + "\t"
                            + "监控目录 = " + watchKeyPathMap.get(key) + "\t"
                            + "event.count() = " + event.count());
                }
                if (!key.reset()) break;
            }
        });
    }

    private void deleteConfigWhenFile(String filePath, WatchKey key, WatchEvent<?> event) {
        WebHookConfig hookConfig = new WebHookConfig();
        String eventName = event.context().toString();
        hookConfig.setManufacturerEventName(eventName.substring(0, eventName.length() - 5));
        hookConfig.setManufacturerName(watchKeyPathMap.get(key).substring(filePath.length() - 1));
        deleteWebHookConfig(hookConfig);
    }

    /**
     * 递归目录，将子目录存到 set
     *
     * @param parent
     * @param pathSet
     */
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
