package org.apache.eventmesh.runtime.core.protocol.amqp;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.metamodels.AmqpExchange;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.metamodels.AmqpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * manage all exchanges used in server
 */
public class ExchangeContainer {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeContainer.class);

    private Map<VirtualHost, Map<String, AmqpExchange>>  exchangeMap;

    public ExchangeContainer () {
        this.exchangeMap = new ConcurrentHashMap<>();
    }

    public AmqpExchange getOrCreateExchange(VirtualHost virtualHost, String exchangeName, String exchangeType, boolean passive, boolean durable, boolean autoDelete, boolean internal) {
        if (StringUtils.isEmpty(exchangeType) && passive) {
            logger.error("[{}][{}] ExchangeType should be set when createIfMissing is true.", virtualHost, exchangeName);
            return null;
        }
        if (virtualHost == null || StringUtils.isEmpty(exchangeName)) {
            logger.error("[{}][{}] Parameter error, namespaceName or exchangeName is empty.", virtualHost, exchangeName);
            return null;
        }

        AmqpExchange.Type type = AmqpExchange.Type.value(exchangeType);
        AmqpExchange amqpExchange = new AmqpExchange(exchangeName, type, passive, durable, autoDelete, internal);

        this.exchangeMap.putIfAbsent(virtualHost, new ConcurrentHashMap<>());
        AmqpExchange existingAmqpExchange = this.exchangeMap.get(virtualHost).putIfAbsent(exchangeName, amqpExchange);
        if (existingAmqpExchange != null) {
            return existingAmqpExchange;
        } else {
            return amqpExchange;
        }
    }

    public void deleteExchange(VirtualHost virtualHost, String exchangeName) {
        if (StringUtils.isEmpty(exchangeName)) {
            return;
        }
        removeExchangeFuture(virtualHost, exchangeName);
    }

    private void removeExchangeFuture(VirtualHost virtualHost, String exchangeName) {
        if (exchangeMap.containsKey(virtualHost)) {
            exchangeMap.get(virtualHost).remove(exchangeName);
        }
    }
}