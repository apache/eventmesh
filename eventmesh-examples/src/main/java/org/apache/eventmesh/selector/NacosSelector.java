package org.apache.eventmesh.selector;

import org.apache.eventmesh.client.selector.Selector;
import org.apache.eventmesh.client.selector.SelectorException;
import org.apache.eventmesh.client.selector.ServiceInstance;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.util.Utils;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

public class NacosSelector implements Selector {
    private static final Logger logger = LoggerFactory.getLogger(NacosSelector.class);
    private NamingService namingService;

    public void init() throws Exception {
        try {
            Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
            namingService = NamingFactory.createNamingService(properties.getProperty(ExampleConstants.EVENTMESH_SELECTOR_NACOS_ADDRESS));
        } catch (NacosException e) {
            logger.error("[NacosSelector][init] error", e);
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public ServiceInstance selectOne(String serviceName) throws SelectorException {
        try {
            Instance instance = namingService.selectOneHealthyInstance(serviceName);
            if (instance == null) {
                return null;
            }
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setHost(instance.getIp());
            serviceInstance.setPort(instance.getPort());
            serviceInstance.setHealthy(instance.isHealthy());
            serviceInstance.setMetadata(instance.getMetadata());
            return serviceInstance;
        } catch (NacosException e) {
            logger.error("[NacosSelector][selectOne] error", e);
            throw new SelectorException(e.getMessage());
        }
    }
}
