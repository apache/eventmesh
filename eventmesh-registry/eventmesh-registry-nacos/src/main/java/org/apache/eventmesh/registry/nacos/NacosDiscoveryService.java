package org.apache.eventmesh.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.registry.QueryInstances;
import org.apache.eventmesh.registry.RegisterServerInfo;
import org.apache.eventmesh.registry.RegistryListener;
import org.apache.eventmesh.registry.RegistryService;
import org.apache.eventmesh.registry.exception.RegistryException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
public class NacosDiscoveryService implements RegistryService {
    private final AtomicBoolean initFlag = new AtomicBoolean(false);

    private CommonConfiguration configuration;

    private NacosRegistryConfiguration nacosConf;

    private NamingService namingService;

    private final Map<String, Map<RegistryListener, EventListener>> listeners = new HashMap<>();

    private final Lock lock = new ReentrantLock();
    private static final String GROUP_NAME = "admin";

    @Override
    public void init() throws RegistryException {
        if (!initFlag.compareAndSet(false, true)) {
            return;
        }
        configuration = ConfigurationContextUtil.get(RegistryService.ConfigurationKey);
        if (configuration == null ) {
            throw new RegistryException("registry config instance is null");
        }
        nacosConf = ConfigService.getInstance().buildConfigInstance(NacosRegistryConfiguration.class);
        if (nacosConf == null) {
            log.info("nacos registry configuration is null");
        }
        Properties properties = buildProperties();
        // registry
        try {
            this.namingService = NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][start] error", e);
            throw new RegistryException(e.getMessage());
        }
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, configuration.getRegistryAddr());
        properties.setProperty(PropertyKeyConst.USERNAME, configuration.getEventMeshRegistryPluginUsername());
        properties.setProperty(PropertyKeyConst.PASSWORD, configuration.getEventMeshRegistryPluginPassword());
        if (nacosConf == null) {
            return properties;
        }
        String endpoint = nacosConf.getEndpoint();
        if (Objects.nonNull(endpoint) && endpoint.contains(":")) {
            int index = endpoint.indexOf(":");
            properties.put(PropertyKeyConst.ENDPOINT, endpoint.substring(0, index));
            properties.put(PropertyKeyConst.ENDPOINT_PORT, endpoint.substring(index + 1));
        } else {
            Optional.ofNullable(endpoint).ifPresent(value -> properties.put(PropertyKeyConst.ENDPOINT, endpoint));
            String endpointPort = nacosConf.getEndpointPort();
            Optional.ofNullable(endpointPort).ifPresent(value -> properties.put(PropertyKeyConst.ENDPOINT_PORT, endpointPort));
        }
        String accessKey = nacosConf.getAccessKey();
        Optional.ofNullable(accessKey).ifPresent(value -> properties.put(PropertyKeyConst.ACCESS_KEY, accessKey));
        String secretKey = nacosConf.getSecretKey();
        Optional.ofNullable(secretKey).ifPresent(value -> properties.put(PropertyKeyConst.SECRET_KEY, secretKey));
        String clusterName = nacosConf.getClusterName();
        Optional.ofNullable(clusterName).ifPresent(value -> properties.put(PropertyKeyConst.CLUSTER_NAME, clusterName));
        String logFileName = nacosConf.getLogFileName();
        Optional.ofNullable(logFileName).ifPresent(value -> properties.put(UtilAndComs.NACOS_NAMING_LOG_NAME, logFileName));
        String logLevel = nacosConf.getLogLevel();
        Optional.ofNullable(logLevel).ifPresent(value -> properties.put(UtilAndComs.NACOS_NAMING_LOG_LEVEL, logLevel));
        Integer pollingThreadCount = nacosConf.getPollingThreadCount();
        Optional.ofNullable(pollingThreadCount).ifPresent(value -> properties.put(PropertyKeyConst.NAMING_POLLING_THREAD_COUNT, pollingThreadCount));
        String namespace = nacosConf.getNamespace();
        Optional.ofNullable(namespace).ifPresent(value -> properties.put(PropertyKeyConst.NAMESPACE, namespace));
        return properties;
    }

    @Override
    public void shutdown() throws RegistryException {
        if (this.namingService != null) {
            try {
                namingService.shutDown();
            } catch (NacosException e) {
                log.warn("shutdown nacos naming service fail", e);
            }
        }
    }

    @Override
    public void subscribe(RegistryListener listener, String serviceName) {
        lock.lock();
        try {
            ServiceInfo serviceInfo = ServiceInfo.fromKey(serviceName);
            Map<RegistryListener, EventListener> eventListenerMap = listeners.computeIfAbsent(serviceName, k -> new HashMap<>());
            if (eventListenerMap.containsKey(listener)) {
                log.warn("already use same listener subscribe service name {}" ,serviceName);
                return;
            }
            EventListener eventListener = listener::onChange;
            List<String> clusters ;
            if (serviceInfo.getClusters() == null || serviceInfo.getClusters().isEmpty()) {
                clusters = new ArrayList<>();
            } else {
                clusters = Arrays.stream(serviceInfo.getClusters().split(",")).collect(Collectors.toList());
            }
            namingService.subscribe(serviceInfo.getName(),serviceInfo.getGroupName(), clusters, eventListener);
            eventListenerMap.put(listener, eventListener);
        } catch (Exception e) {
            log.error("subscribe service name {} fail", serviceName, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unsubscribe(RegistryListener registryListener, String serviceName) {
        lock.lock();
        try {
            ServiceInfo serviceInfo = ServiceInfo.fromKey(serviceName);
            Map<RegistryListener, EventListener> map = listeners.get(serviceName);
            if (map == null) {
                return;
            }
            List<String> clusters ;
            if (serviceInfo.getClusters() == null || serviceInfo.getClusters().isEmpty()) {
                clusters = new ArrayList<>();
            } else {
                clusters = Arrays.stream(serviceInfo.getClusters().split(",")).collect(Collectors.toList());
            }
            EventListener eventListener = map.get(registryListener);
            namingService.unsubscribe(serviceInfo.getName(), serviceInfo.getGroupName(), clusters, eventListener);
            map.remove(registryListener);
        } catch (Exception e) {
            log.error("unsubscribe service name {} fail", serviceName, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<RegisterServerInfo> selectInstances(QueryInstances serverInfo) {
        return null;
    }

    @Override
    public boolean register(RegisterServerInfo eventMeshRegisterInfo) throws RegistryException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getAddress().split(":");
            if (ipPort.length < 2) {
                return false;
            }
            ServiceInfo serviceInfo = ServiceInfo.fromKey(eventMeshRegisterInfo.getServiceName());
            Instance instance = new Instance();
            instance.setClusterName(serviceInfo.getClusters());
            instance.setEnabled(true);
            instance.setEphemeral(true);
            instance.setHealthy(true);
            instance.setWeight(1.0);
            instance.setIp(ipPort[0]);
            instance.setPort(Integer.parseInt(ipPort[1]));
            instance.setMetadata(eventMeshRegisterInfo.getMetadata());
            namingService.registerInstance(eventMeshRegisterInfo.getEventMeshName(), GROUP_NAME, instance);
            return true;
        } catch (Exception e) {
            log.error("register instance service {} group {} cluster {} fail", eventMeshRegisterInfo.getEventMeshName(), GROUP_NAME, eventMeshRegisterInfo.getEventMeshClusterName(), e);
            return false;
        }
    }

    @Override
    public boolean unRegister(RegisterServerInfo eventMeshRegisterInfo) throws RegistryException {
        try {
            namingService.registerInstance(eventMeshRegisterInfo.getEventMeshName(), GROUP_NAME, new Instance());
            return true;
        } catch (Exception e) {
            log.error("register instance service {} group {} cluster {} fail", eventMeshRegisterInfo.getEventMeshName(), GROUP_NAME, eventMeshRegisterInfo.getEventMeshClusterName(), e);
            return false;
        }
    }
}
