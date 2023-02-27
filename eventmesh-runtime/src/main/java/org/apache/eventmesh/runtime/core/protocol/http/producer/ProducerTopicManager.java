package org.apache.eventmesh.runtime.core.protocol.http.producer;

import org.apache.eventmesh.api.registry.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.boot.EventMeshServer;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProducerTopicManager {

    private Logger retryLogger = LoggerFactory.getLogger("p-topic-m");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private EventMeshServer eventMeshServer;

    public ProducerTopicManager(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    private transient ScheduledFuture<?> scheduledTask;

    protected static ScheduledExecutorService scheduler;

    private ConcurrentHashMap<String, EventMeshServicePubTopicInfo> eventMeshServicePubTopicInfoMap = new ConcurrentHashMap<>();

    public void init() {

        scheduler = ThreadPoolFactory.createScheduledExecutor(Runtime.getRuntime().availableProcessors(),
            new EventMeshThreadFactory("Producer-Topic-Manager", true));
        logger.info("ProducerTopicManager inited......");

    }

    public void start() {

        if (scheduledTask == null) {
            synchronized (ProducerTopicManager.class) {
                scheduledTask = scheduler.scheduleAtFixedRate(() -> {

                    try {
                        List<EventMeshServicePubTopicInfo> list = eventMeshServer.getRegistry().findEventMeshServicePubTopicInfos();
                        list.forEach(e -> {
                            eventMeshServicePubTopicInfoMap.put(e.getService(), e);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }, 5, 20, TimeUnit.SECONDS);
            }
        }


        logger.info("ProducerTopicManager started......");
    }

    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        logger.info("ProducerTopicManager shutdown......");
    }

    public ConcurrentHashMap<String, EventMeshServicePubTopicInfo> getEventMeshServicePubTopicInfoMap() {
        return eventMeshServicePubTopicInfoMap;
    }

    public EventMeshServicePubTopicInfo getEventMeshServicePubTopicInfo(String producerGroup) {
        return eventMeshServicePubTopicInfoMap.get(producerGroup);
    }


}