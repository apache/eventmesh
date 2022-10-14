package org.apache.eventmesh.runtime.core.protocol.amqp.service;

import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeDefaults;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeType;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.MetaStore;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.ExchangeManager;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.ExchangeUtil;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.NameUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExchangeServiceImpl implements ExchangeService {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private EventMeshAmqpServer brokerService;

    private MetaStore metaStore;

    private ExchangeManager exchangeManager;

    public ExchangeServiceImpl(EventMeshAmqpServer brokerService, MetaStore metaStore) {
        this.brokerService = brokerService;
        this.metaStore = metaStore;
        this.exchangeManager = metaStore.exchange();
    }

    private String formatString(String s) {
        return s.replaceAll("\r", "").
            replaceAll("\n", "").trim();
    }


    @Override
    public void exchangeDeclare(String virtualHostName,
                                String exchange, String type, boolean durable, boolean autoDelete,
                                boolean internal, Map<String, Object> arguments) throws AmqpException {

        if (ExchangeUtil.isDefaultExchange(exchange)) {
            throw new AmqpException(ErrorCodes.ACCESS_REFUSED,
                "Attempt to redeclare default exchange: of type " + ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
        }

        try {
            NameUtils.checkName(exchange);
        } catch (IllegalArgumentException e) {
            log.error("Exchange Name is illegal：{}", exchange, e);
            throw new AmqpException(ErrorCodes.INVALID_ARGUMENT, "Exchange Name is illegal:" + exchange);
        }

        ExchangeType exchangeType;
        if (ExchangeUtil.isBuildInExchange(exchange)) {
            exchangeType = ExchangeUtil.getBuildInExchangeType(exchange);
        } else {
            exchangeType = ExchangeType.value(type);
        }

        if (exchangeType == null) {
            throw new AmqpException(ErrorCodes.ACCESS_REFUSED, "exchange type can not be empty");
        }

        ExchangeInfo exchangeInfo = exchangeManager.getExchange(virtualHostName, exchange);
        if (exchangeInfo == null) {

            ExchangeInfo meta = new ExchangeInfo();
            meta.setExchangeName(exchange);
            meta.setExchangeType(exchangeType);
            meta.setDurable(durable);
            meta.setAutoDelete(autoDelete);
            try {
                exchangeManager.createExchange(virtualHostName, exchange, meta);
            } catch (Throwable throwable) {
                log.error("createExchange error {}", exchange, throwable);
                throw new AmqpException(ErrorCodes.INTERNAL_ERROR,
                    "Failed to createExchange: " + exchange, throwable);
            }
        } else {
            if (exchangeInfo.isAutoDelete() != autoDelete) {
                throw new AmqpException(ErrorCodes.IN_USE,
                    "Attempt to redeclare exchange: '"
                        + exchange + "' of autoDelete " + exchangeInfo.isAutoDelete()
                        + " to " + autoDelete + ".");
            } else if (!exchangeInfo.getExchangeType().toString().
                equalsIgnoreCase(exchangeType.toString())) {
                throw new AmqpException(ErrorCodes.IN_USE,
                    "Attempt to redeclare exchange: '"
                        + exchange + "' of type " + exchangeInfo.getExchangeType()
                        + " to " + exchangeType + ".");
            }
        }

    }

    @Override
    public void exchangeDelete(String virtualHostName, String exchange, boolean ifUnused) throws AmqpException {

        if (ExchangeUtil.isDefaultExchange(exchange)) {
            throw new AmqpException(ErrorCodes.ACCESS_REFUSED,
                "Default Exchange cannot be deleted:" + exchange);
        }
        if (ExchangeUtil.isBuildInExchange(exchange)) {
            throw new AmqpException(ErrorCodes.ACCESS_REFUSED,
                "BuildIn Exchange cannot be deleted:" + exchange);
        }

        ExchangeInfo exchangeInfo = exchangeManager.getExchange(virtualHostName, exchange);
        if (exchangeInfo == null) {
            log.warn("delete not exist exchange {}", exchange);
            return;
        }

        try {
            Set<BindingInfo> bindingInfos = exchangeManager.getBindings(virtualHostName, exchange);
            if (ifUnused && bindingInfos != null && bindingInfos.size() > 0) {
                log.error("Failed to delete exchange, exchange:{}/{} has bindings, count = {}",
                    virtualHostName, exchange, bindingInfos.size());
                throw new AmqpException(ErrorCodes.IN_USE, "Exchange has bindings:" + exchange);
            } else {
                unbindAllQueues4Exchange(virtualHostName, exchange, bindingInfos);
                exchangeManager.exchangeUnBindAll(virtualHostName, exchange);
                exchangeManager.deleteExchange(virtualHostName, exchange);
            }
        } catch (Throwable throwable) {
            log.error("exchangeDelete error {}", exchange, throwable);
            throw new AmqpException(ErrorCodes.INTERNAL_ERROR, throwable.getMessage());
        }
    }

    private void unbindAllQueues4Exchange(String virtualHostName, String exchange,
                                          Set<BindingInfo> bindings) throws AmqpNotFoundException {
        if (bindings != null && bindings.size() > 0) {
            for (BindingInfo binding : bindings) {
                metaStore.queue().queueUnBind(virtualHostName, binding.getDestination(), exchange);
            }
        }
    }

    @Override
    public Set<BindingInfo> getBindings(String virtualHostName, String exchange) throws Exception {
        return exchangeManager.getBindings(virtualHostName, exchange);
    }

    @Override
    public ExchangeInfo getExchange(String virtualHostName, String exchange) {
        return exchangeManager.getExchange(virtualHostName, exchange);
    }

    @Override
    public boolean checkExchangeExist(String virtualHostName, String exchange) {
        return exchangeManager.checkExist(virtualHostName, exchange);
    }

    @Override
    public Set<String> getExchangeList(String virtualHostName) throws Exception {
        return exchangeManager.getExchangeList(virtualHostName);
    }


}
