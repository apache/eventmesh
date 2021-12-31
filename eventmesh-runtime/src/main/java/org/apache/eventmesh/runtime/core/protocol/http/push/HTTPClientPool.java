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

package org.apache.eventmesh.runtime.core.protocol.http.push;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;

public class HTTPClientPool {

    private List<CloseableHttpClient> clients = Lists.newArrayList();

    private int core = 1;

    public HTTPClientPool(int core) {
        this.core = core;
    }

    public CloseableHttpClient getClient() {
        if (CollectionUtils.size(clients) < core) {
            CloseableHttpClient client = HttpClients.createDefault();
            clients.add(client);
            return client;
        }
        return clients.get(RandomUtils.nextInt(core, 2 * core) % core);
    }


    public void shutdown() throws Exception {
        Iterator<CloseableHttpClient> itr = clients.iterator();
        while (itr.hasNext()) {
            CloseableHttpClient client = itr.next();
            client.close();
            itr.remove();
        }
    }
}
