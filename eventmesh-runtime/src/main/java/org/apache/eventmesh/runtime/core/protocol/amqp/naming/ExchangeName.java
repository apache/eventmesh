package org.apache.eventmesh.runtime.core.protocol.amqp.naming;

public class ExchangeName {

    private final String ExchangeName;
    private final String virtualHostName;

    public ExchangeName(String exchangeName, String virtualHostName) {
        this.ExchangeName = exchangeName;
        this.virtualHostName = virtualHostName;
    }

    public static ExchangeName get(String virtualHost, String exchangeName) {

        return new ExchangeName(virtualHost, exchangeName);
    }


}
