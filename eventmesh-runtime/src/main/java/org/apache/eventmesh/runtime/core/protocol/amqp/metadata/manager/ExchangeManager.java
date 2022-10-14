package org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ExchangeManager {

    void createExchange(String virtualHostName,final String exchangeName, ExchangeInfo meta) throws AmqpNotFoundException;

    ExchangeInfo getExchange(String virtualHostName,final String exchangeName);


    void deleteExchange(String virtualHostName,final String exchangeName);


    void exchangeBind(final String virtualHostName, String exchange, String queue,
                                         BindingInfo bindingInfo) throws AmqpNotFoundException;

    void exchangeUnBind(final String virtualHostName, String exchange,
                                           String queue, String bindingKey) throws AmqpNotFoundException;


    void exchangeUnBind(String virtualHostName,final String exchangeName, String queue) throws AmqpNotFoundException;

    void exchangeUnBindAll(String virtualHostName,final String exchangeName) throws AmqpNotFoundException;

    Set<BindingInfo> getBindings(String virtualHostName,String exchangeName) throws Exception;

    boolean isQueueBindingExist(String virtualHostName,String exchangeName, String queue) throws AmqpNotFoundException;

    boolean checkExist(String virtualHostName,String exchangeName);

    Set<String> getExchangeList(String virtualHostName) throws Exception;

}
