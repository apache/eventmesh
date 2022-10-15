package org.apache.eventmesh.runtime.core.protocol.amqp.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeDefaults;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeType;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;

public class ExchangeUtil {

    public static boolean isBuildInExchange(final String exchangeName) {
        if (exchangeName.equals(ExchangeDefaults.DIRECT_EXCHANGE_NAME)
            || (exchangeName.equals(ExchangeDefaults.FANOUT_EXCHANGE_NAME))
            || (exchangeName.equals(ExchangeDefaults.TOPIC_EXCHANGE_NAME))
            || (exchangeName.equals(ExchangeDefaults.HEADERS_EXCHANGE_NAME))) {
            return true;
        } else {
            return false;
        }
    }



    public static boolean isDefaultExchange(final String exchangeName) {
        return StringUtils.isBlank(exchangeName)
            || (ExchangeDefaults.DEFAULT_EXCHANGE_NAME_DURABLE.equals(exchangeName))
            || (ExchangeDefaults.DEFAULT_EXCHANGE_NAME.equals(exchangeName));
    }

    public static ExchangeType getBuildInExchangeType(String exchangeName) {
        if (null == exchangeName) {
            exchangeName = "";
        }
        switch (exchangeName) {
            case "":
            case ExchangeDefaults.DIRECT_EXCHANGE_NAME:
                return ExchangeType.Direct;
            case ExchangeDefaults.FANOUT_EXCHANGE_NAME:
                return ExchangeType.Fanout;
            case ExchangeDefaults.TOPIC_EXCHANGE_NAME:
                return ExchangeType.Topic;
            default:
                return null;
        }
    }

    public static ExchangeInfo defaultExchange() {
        return new ExchangeInfo(ExchangeDefaults.DEFAULT_EXCHANGE_NAME_DURABLE, true, false,
            ExchangeType.value(ExchangeDefaults.DIRECT_EXCHANGE_CLASS));
    }

    public static ExchangeInfo defaultDirectExchange() {
        return new ExchangeInfo(ExchangeDefaults.DIRECT_EXCHANGE_NAME, true, false,
            ExchangeType.value(ExchangeDefaults.DIRECT_EXCHANGE_CLASS));
    }

    public static ExchangeInfo defaultFanoutExchange() {
        return new ExchangeInfo(ExchangeDefaults.FANOUT_EXCHANGE_NAME, true, false,
            ExchangeType.value(ExchangeDefaults.FANOUT_EXCHANGE_CLASS));
    }

    public static ExchangeInfo defaultTopicExchange() {
        return new ExchangeInfo(ExchangeDefaults.TOPIC_EXCHANGE_NAME, true, false,
            ExchangeType.value(ExchangeDefaults.TOPIC_EXCHANGE_CLASS));
    }

    public static ExchangeInfo defaultHeaderExchange() {
        return new ExchangeInfo(ExchangeDefaults.HEADERS_EXCHANGE_NAME, true, false,
            ExchangeType.value(ExchangeDefaults.HEADERS_EXCHANGE_CLASS));
    }
}
