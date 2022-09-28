package org.apache.eventmesh.runtime.core.protocol.amqp;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.metamodels.AmqpExchange;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * provide services to use ExchangeContainer
 */
public class ExchangeService {
    public CompletableFuture<AmqpExchange> exchangeDeclare(VirtualHost virtualHost, String exchange, String type,
                                                           boolean passive, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) {
        if (isDefaultExchange(exchange)) {
            // if default exchange is declaring

        } else {
            if (isBuildInExchange(type)) {
                // invoke container to create AmqpExchange
            } else {
                // exception handle
            }
        }
        return null;
    }

    public static boolean isDefaultExchange(String exchangeName) {
        return StringUtils.isBlank(exchangeName);
    }

    public static boolean isBuildInExchange(String type) {
        return AmqpExchange.Type.value(type) == null;
    }
}