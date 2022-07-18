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
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.LeaseOption;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.registry.etcd.constant.EtcdConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class EtcdClientFactory {

	private static final Logger logger = LoggerFactory.getLogger(EtcdClientFactory.class);

	private Map<String, EtcdLeaseId> etcdLeaseIdMap = new ConcurrentHashMap<>();


	public Client createClient(Properties properties) {
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
			if (!addresss[i].startsWith("http://")) {
				httpAddress[i] = "http://" + addresss[i];
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
			etcdLeaseId.setClientWrapper(new ClientWrapper(etcdLeaseId.getClientBuilder().build()));
			ClientWrapper client = etcdLeaseId.getClientWrapper();
			long leaseId = client.getLeaseClient().grant(10L).get().getID();
			etcdLeaseId.setLeaseId(leaseId);
			EtcdStreamObserver etcdStreamObserver = new EtcdStreamObserver();
			etcdStreamObserver.etcdLeaseId = etcdLeaseId;
			etcdLeaseId.setEtcdStreamObserver(etcdStreamObserver);
			client.getLeaseClient().keepAlive(leaseId, etcdStreamObserver);

			etcdLeaseIdMap.put(serverAddr, etcdLeaseId);
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
		return etcdLeaseId.getClientWrapper();
	}

	public Long getLeaseId(String url) {
		return etcdLeaseIdMap.get(url).getLeaseId();
	}

	private void createLeaseId(EtcdLeaseId etcdLeaseId) {
		logger.info("renewal of contract. server url {}", etcdLeaseId.getUrl());
		Client client = etcdLeaseId.getClientWrapper();
		try {
			long ttl = client.getLeaseClient().timeToLive(etcdLeaseId.getLeaseId(), LeaseOption.DEFAULT).get().getTTl();
			if (ttl < 1) {
				long leaseId = client.getLeaseClient().grant(1L).get().getID();
				client.getLeaseClient().keepAlive(leaseId, etcdLeaseId.getEtcdStreamObserver());
				etcdLeaseId.setLeaseId(leaseId);
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
			client.getLeaseClient().keepAlive(System.currentTimeMillis(), etcdLeaseId.getEtcdStreamObserver());
		}
	}

	class EtcdLeaseId {

		private long leaseId;

		private AtomicBoolean renewalLock = new AtomicBoolean();

		private ClientWrapper clientWrapper;

		private ClientBuilder clientBuilder;

		private String url;

		private EtcdStreamObserver etcdStreamObserver;

		public AtomicBoolean getRenewalLock() {
			return renewalLock;
		}

		public void setRenewalLock(AtomicBoolean renewalLock) {
			this.renewalLock = renewalLock;
		}

		public ClientWrapper getClientWrapper() {
			return clientWrapper;
		}

		public void setClientWrapper(ClientWrapper clientWrapper) {
			this.clientWrapper = clientWrapper;
		}

		public ClientBuilder getClientBuilder() {
			return clientBuilder;
		}

		public void setClientBuilder(ClientBuilder clientBuilder) {
			this.clientBuilder = clientBuilder;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public EtcdStreamObserver getEtcdStreamObserver() {
			return etcdStreamObserver;
		}

		public void setEtcdStreamObserver(EtcdStreamObserver etcdStreamObserver) {
			this.etcdStreamObserver = etcdStreamObserver;
		}

		public long getLeaseId() {
			return leaseId;
		}

		public void setLeaseId(long leaseId) {
			this.leaseId = leaseId;
		}
	}

	class ClientWrapper implements Client {

		private volatile Client client;

		public ClientWrapper(Client client) {
			this.client = client;
		}

		@Override
		public Auth getAuthClient() {
			return client.getAuthClient();
		}

		@Override
		public KV getKVClient() {
			return client.getKVClient();
		}

		@Override
		public Cluster getClusterClient() {
			return client.getClusterClient();
		}

		@Override
		public Maintenance getMaintenanceClient() {
			return client.getMaintenanceClient();
		}

		@Override
		public Lease getLeaseClient() {
			return client.getLeaseClient();
		}

		@Override
		public Watch getWatchClient() {
			return client.getWatchClient();
		}

		@Override
		public Lock getLockClient() {
			return client.getLockClient();
		}
//
//		@Override
//		public Election getElectionClient() {
//			return client.getElectionClient();
//		}

		@Override
		public void close() {
			client.close();
		}

	}

	class EtcdStreamObserver implements StreamObserver<LeaseKeepAliveResponse> {

		private EtcdLeaseId etcdLeaseId;

		@Override
		public void onNext(LeaseKeepAliveResponse value) {
		}

		@Override
		public void onError(Throwable t) {
			logger.error("EtcdStreamObserver error {}", t.getMessage(), t);
			EtcdClientFactory.this.createLeaseId(etcdLeaseId);
		}

		@Override
		public void onCompleted() {
			EtcdClientFactory.this.createLeaseId(etcdLeaseId);
		}

	}
}
