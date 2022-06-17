package org.apache.eventmesh.webhook.receive.storage;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;

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
        this.filePath = filePath;
        this.cacheWebHookConfig = cacheWebHookConfig;
        filePatternInit();
    }

    /**
     * Read the directory and register the listener
     *
     * @throws FileNotFoundException
     */
    public void filePatternInit() throws FileNotFoundException {
        File webHookFileDir = new File(filePath);
        if (!webHookFileDir.isDirectory()) {
            throw new FileNotFoundException("File path " + filePath + " is not directory");
        }
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
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
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
                    if (flashPath.equals(filePath)) {
                        if (ENTRY_CREATE == event.kind()) {
                            try {
                                key = Paths.get(filePath + event.context()).register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                            } catch (IOException e) {
                                logger.error("registerWatchKey failed", e);
                            }
                            watchKeyPathMap.put(key, filePath + event.context());
                        }
                    } else { // config change
                        cacheInit(new File(flashPath + event.context()));
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
