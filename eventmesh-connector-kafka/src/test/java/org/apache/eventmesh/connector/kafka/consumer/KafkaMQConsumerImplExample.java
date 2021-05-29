package org.apache.eventmesh.connector.kafka.consumer;

import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.connector.kafka.common.Constants;
import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;
import org.apache.eventmesh.connector.kafka.producer.KafkaMQProducerImplExample;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class KafkaMQConsumerImplExample {

    private static KafkaMQConsumerImpl kafkaMQConsumer;

    static {
        kafkaMQConsumer = new KafkaMQConsumerImpl(new Properties());
        String filePath = KafkaMQProducerImplExample.class.getClassLoader().getResource(Constants.KAFKA_CONF_FILE).getPath();
        ConfigurationWrapper configurationWrapper = new ConfigurationWrapper(filePath, false);
        KafkaConsumerConfig kafkaConsumerConfig = new KafkaConsumerConfig(configurationWrapper);
        kafkaConsumerConfig.setGroupId(IPUtil.getLocalAddress());
        kafkaMQConsumer.init(kafkaConsumerConfig);
    }

    public static void main(String[] args) {
        kafkaMQConsumer.subscribe("eventmesh-test-topic",
                (message, context) -> System.out.println(new String(message.getBody(), StandardCharsets.UTF_8)));
        kafkaMQConsumer.start();
    }
}
