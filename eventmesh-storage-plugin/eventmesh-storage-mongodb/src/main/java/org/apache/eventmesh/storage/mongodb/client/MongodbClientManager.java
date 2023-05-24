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

package org.apache.eventmesh.storage.mongodb.client;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public class MongodbClientManager {
    /**
     * create mongodb client
     *
     * @param url url, like: mongodb://root:root@192.168.74.143:27018,192.168.74.143:27019
     * @return mongodb client
     */
    public static MongoClient createMongodbClient(String url) {
        ConnectionString connectionString = new ConnectionString(url);
        return MongoClients.create(connectionString);
    }

    /**
     * close mongodb client
     *
     * @param mongoClient mongodb client
     */
    public static void closeMongodbClient(MongoClient mongoClient) {
        mongoClient.close();
    }
}
