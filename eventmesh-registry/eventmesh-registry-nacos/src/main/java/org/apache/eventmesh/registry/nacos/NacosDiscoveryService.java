package org.apache.eventmesh.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.registry.NotifyEvent;
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
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
public class NacosDiscoveryService implements RegistryService {

    private final AtomicBoolean initFlag = new AtomicBoolean(false);

    private NacosRegistryConfiguration nacosConf;

    private NamingService namingService;

    private final Map<String, Map<RegistryListener, EventListener>> listeners = new HashMap<>();

    private static final Executor notifyExecutor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
         new LinkedBlockingQueue<>(20), r -> {
             Thread t = new Thread(r);
             t.setName("org.apache.eventmesh.registry.nacos.executor");
             t.setDaemon(true);
             return t;
         }, new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private final Lock lock = new ReentrantLock();


    @Override
    public void init() throws RegistryException {
        if (!initFlag.compareAndSet(false, true)) {
            return;
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
        if (nacosConf == null) {
            return properties;
        }
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, nacosConf.getRegistryAddr());
        properties.setProperty(PropertyKeyConst.USERNAME, nacosConf.getEventMeshRegistryPluginUsername());
        properties.setProperty(PropertyKeyConst.PASSWORD, nacosConf.getEventMeshRegistryPluginPassword());

        String endpoint = nacosConf.getEndpoint();
        if (Objects.nonNull(endpoint) && endpoint.contains(":")) {
            int index = endpoint.indexOf(":");
            properties.put(PropertyKeyConst.ENDPOINT, endpoint.substring(0, index));
            properties.put(PropertyKeyConst.ENDPOINT_PORT, endpoint.substring(index + 1));
        } else {
            Optional.ofNullable(endpoint).ifPresent(value -> properties.put(PropertyKeyConst.ENDPOINT, endpoint));
            String endpointPort = nacosConf.getEndpointPort();
            Optional.ofNullable(endpointPort).ifPresent(value -> properties.put(PropertyKeyConst.ENDPOINT_PORT,
                    endpointPort));
        }
        String accessKey = nacosConf.getAccessKey();
        Optional.ofNullable(accessKey).ifPresent(value -> properties.put(PropertyKeyConst.ACCESS_KEY, accessKey));
        String secretKey = nacosConf.getSecretKey();
        Optional.ofNullable(secretKey).ifPresent(value -> properties.put(PropertyKeyConst.SECRET_KEY, secretKey));
        String clusterName = nacosConf.getClusterName();
        Optional.ofNullable(clusterName).ifPresent(value -> properties.put(PropertyKeyConst.CLUSTER_NAME, clusterName));
        String logFileName = nacosConf.getLogFileName();
        Optional.ofNullable(logFileName).ifPresent(value -> properties.put(UtilAndComs.NACOS_NAMING_LOG_NAME,
                logFileName));
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
            Map<RegistryListener, EventListener> eventListenerMap = listeners.computeIfAbsent(serviceName,
                    k -> new HashMap<>());
            if (eventListenerMap.containsKey(listener)) {
                log.warn("already use same listener subscribe service name {}", serviceName);
                return;
            }
            EventListener eventListener = new AbstractEventListener() {
                @Override
                public Executor getExecutor() {
                    return notifyExecutor;
                }

                @Override
                public void onEvent(Event event) {
                    if (!(event instanceof NamingEvent)) {
                        log.warn("received notify event type isn't not as expected");
                        return;
                    }
                    try {
                        NamingEvent namingEvent = (NamingEvent) event;
                        List<Instance> instances = namingEvent.getInstances();
                        List<RegisterServerInfo> list = new ArrayList<>();
                        if (instances != null) {
                            for (Instance instance : instances) {
                                RegisterServerInfo info = new RegisterServerInfo();
                                info.setAddress(instance.getIp() + ":" + instance.getPort());
                                info.setMetadata(instance.getMetadata());
                                info.setHealth(instance.isHealthy());
                                info.setServiceName(
                                        ServiceInfo.getKey(NamingUtils.getGroupedName(namingEvent.getServiceName(),
                                                        namingEvent.getGroupName()),
                                                namingEvent.getClusters()));
                                list.add(info);
                            }
                        }
                        listener.onChange(new NotifyEvent(list));
                    } catch (Exception e) {
                        log.warn("");
                    }
                }
            };
            List<String> clusters;
            if (serviceInfo.getClusters() == null || serviceInfo.getClusters().isEmpty()) {
                clusters = new ArrayList<>();
            } else {
                clusters = Arrays.stream(serviceInfo.getClusters().split(",")).collect(Collectors.toList());
            }
            namingService.subscribe(serviceInfo.getName(), serviceInfo.getGroupName(), clusters, eventListener);
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
            List<String> clusters;
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
    public List<RegisterServerInfo> selectInstances(QueryInstances queryInstances) {
        ArrayList<RegisterServerInfo> list = new ArrayList<>();
        try {
            ServiceInfo serviceInfo = ServiceInfo.fromKey(queryInstances.getServiceName());
            ArrayList<String> clusters = new ArrayList<>();
            if (StringUtils.isNotBlank(serviceInfo.getClusters())) {
                clusters.addAll(Arrays.asList(serviceInfo.getClusters().split(",")));
            }
            List<Instance> instances = namingService.selectInstances(serviceInfo.getName(),
                    serviceInfo.getGroupName(), clusters,
                    queryInstances.isHealth());
            if (instances != null) {
                instances.forEach(x -> {
                    RegisterServerInfo instanceInfo = new RegisterServerInfo();
                    instanceInfo.setMetadata(x.getMetadata());
                    instanceInfo.setHealth(x.isHealthy());
                    instanceInfo.setAddress(x.getIp() + ":" + x.getPort());
                    instanceInfo.setServiceName(
                            ServiceInfo.getKey(NamingUtils.getGroupedName(x.getServiceName(),
                                    serviceInfo.getGroupName()), x.getClusterName()));
                    list.add(instanceInfo);
                });
            }
            return list;
        } catch (Exception e) {
            log.error("select instance by query {} from nacos fail", queryInstances, e);
            return list;
        }
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
            instance.setHealthy(eventMeshRegisterInfo.isHealth());
            instance.setWeight(1.0);
            instance.setIp(ipPort[0]);
            instance.setPort(Integer.parseInt(ipPort[1]));
            instance.setMetadata(eventMeshRegisterInfo.getMetadata());
            namingService.registerInstance(serviceInfo.getName(), serviceInfo.getGroupName(), instance);
            return true;
        } catch (Exception e) {
            log.error("register instance service {} fail", eventMeshRegisterInfo, e);
            return false;
        }
    }

    @Override
    public boolean unRegister(RegisterServerInfo eventMeshRegisterInfo) throws RegistryException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getAddress().split(":");
            if (ipPort.length < 2) {
                return false;
            }
            ServiceInfo serviceInfo = ServiceInfo.fromKey(eventMeshRegisterInfo.getServiceName());
            namingService.deregisterInstance(serviceInfo.getName(), serviceInfo.getGroupName(), ipPort[0],
                    Integer.parseInt(ipPort[1]),
                    serviceInfo.getClusters());
            return true;
        } catch (Exception e) {
            log.error("unregister instance service {} fail", eventMeshRegisterInfo, e);
            return false;
        }
    }
}
