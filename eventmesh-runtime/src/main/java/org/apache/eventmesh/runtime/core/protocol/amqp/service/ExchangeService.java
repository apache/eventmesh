package org.apache.eventmesh.runtime.core.protocol.amqp.service;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpException;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * provide services to use ExchangeContainer
 */
public interface ExchangeService {

    /**
     * Declare a exchange.
     *
     * @param virtualHostName namespace
     * @param exchange        the name of the exchange
     * @param type            the exchange type
     *                        are ignored; and sending nowait makes this method a no-op, so we default it to false.
     * @param durable         true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete      true if the server should delete the exchange when it is no longer in use
     * @param internal        true if the exchange is internal, i.e. can't be directly published to by a client
     * @param arguments       other properties (construction arguments) for the exchange
     * @return completableFuture of process result
     */
    void exchangeDeclare(String virtualHostName, String exchange, String type, boolean durable, boolean autoDelete, boolean internal,
                         Map<String, Object> arguments) throws AmqpException;

    /**
     * Delete a exchange.
     *
     * @param virtualHostName namespace
     * @param exchange        the name of the exchange
     * @param ifUnused        true to indicate that the exchange is only to be deleted if it is unused
     * @return completableFuture of process result
     */
    void exchangeDelete(String virtualHostName, String exchange, boolean ifUnused) throws AmqpException;


    Set<BindingInfo> getBindings(String virtualHostName, String exchange) throws Exception;

    ExchangeInfo getExchange(String virtualHostName, String exchange);

    boolean checkExchangeExist(String virtualHostName, String exchange);

    Set<String> getExchangeList(String virtualHostName) throws Exception;

}