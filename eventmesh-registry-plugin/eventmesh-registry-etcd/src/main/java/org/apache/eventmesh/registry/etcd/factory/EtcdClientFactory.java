/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.registry.etcd.factory;

import io.etcd.jetcd.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.registry.etcd.constant.EtcdConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class EtcdClientFactory {

	private static final Logger logger = LoggerFactory.getLogger(EtcdClientFactory.class);

	private static Map<String, EtcdLeaseId> etcdLeaseIdMap = new ConcurrentHashMap<>();


	public static Client createClient(Properties properties) {
		String serverAddr = properties.getProperty(EtcdConstant.SERVER_ADDR);
		String username = properties.getProperty(EtcdConstant.USERNAME);
		String password = properties.getProperty(EtcdConstant.PASSWORD);

		EtcdLeaseId etcdLeaseId = etcdLeaseIdMap.get(serverAddr);
		if (Objects.nonNull(etcdLeaseId)) {
			return etcdLeaseId.getClientWrapper();
		}
		ClientBuilder clientBuilder = Client.builder();
		String[] addresss = serverAddr.split(",");
		String[] httpAddress = new String[addresss.length];
		for (int i = 0; i < addresss.length; i++) {
			if (!addresss[i].startsWith(Constants.HTTP_PROTOCOL_PREFIX)) {
				httpAddress[i] = Constants.HTTP_PROTOCOL_PREFIX + addresss[i];
			}
		}
		etcdLeaseId = new EtcdLeaseId();
		try {
			etcdLeaseId.setUrl(serverAddr);
			etcdLeaseId.setClientBuilder(clientBuilder.endpoints(httpAddress));
			if (StringUtils.isNoneBlank(username)) {
				etcdLeaseId.getClientBuilder().user(ByteSequence.from(username.getBytes()));
			}
			if (StringUtils.isNoneBlank(password)) {
				etcdLeaseId.getClientBuilder().password(ByteSequence.from(password.getBytes()));
			}
			etcdLeaseId.setClientWrapper(new EtcdClientWrapper(etcdLeaseId.getClientBuilder().build()));
			EtcdClientWrapper client = etcdLeaseId.getClientWrapper();
			long leaseId = client.getLeaseClient().grant(EtcdConstant.TTL).get().getID();
			etcdLeaseId.setLeaseId(leaseId);
			EtcdStreamObserver etcdStreamObserver = new EtcdStreamObserver();
			etcdStreamObserver.setEtcdLeaseId(etcdLeaseId);
			etcdLeaseId.setEtcdStreamObserver(etcdStreamObserver);
			client.getLeaseClient().keepAlive(leaseId, etcdStreamObserver);

			etcdLeaseIdMap.put(serverAddr, etcdLeaseId);
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
			throw new RegistryException("createClient failed", e);
		}
		return etcdLeaseId.getClientWrapper();
	}

	public static Long getLeaseId(String url) {
		return etcdLeaseIdMap.get(url).getLeaseId();
	}

}
