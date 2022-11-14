package org.apache.eventmesh.runtime.core.protocol.amqp.producer;

import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.consumergroup.ProducerGroupConf;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpProducerManager {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ConcurrentHashMap<String, AmqpProducer> producerTable = new ConcurrentHashMap<>();

    private EventMeshAmqpServer amqpServer;

    public void init() throws Exception {
        logger.info("AMQP ProducerManager inited......");
    }

    public void start() throws Exception {
        logger.info("AMQP ProducerManager started......");
    }

    public AmqpProducer getAmqpProducer(String producerGroup) {

        return producerTable.computeIfAbsent(producerGroup, pr -> {
            ProducerGroupConf producerGroupConfig = new ProducerGroupConf(producerGroup);
            AmqpProducer amqpProducer = null;
            try {
                amqpProducer = createAmqpProducer(producerGroupConfig);
                amqpProducer.start();
                return amqpProducer;
            } catch (Exception e) {
                e.printStackTrace();
                logger.info("create amqp producer error {}", producerGroup, e);
            }
            return null;
        });
    }

    private synchronized AmqpProducer createAmqpProducer(
            ProducerGroupConf producerGroupConfig) throws Exception {
        if (producerTable.containsKey(producerGroupConfig.getGroupName())) {
            return producerTable.get(producerGroupConfig.getGroupName());
        }
        AmqpProducer amqpProducer = new AmqpProducer();
        amqpProducer.init(amqpServer.getEventMeshAmqpConfiguration(),
                producerGroupConfig);
        producerTable.put(producerGroupConfig.getGroupName(), amqpProducer);
        return amqpProducer;
    }

    public void shutdown() {
        for (AmqpProducer amqpProducer : producerTable.values()) {
            try {
                amqpProducer.shutdown();
            } catch (Exception ex) {
                logger.error("shutdown amqpProducer [{}] err", amqpProducer, ex);
            }
        }
        logger.info("amqpProducerManager shutdown......");
    }

}
