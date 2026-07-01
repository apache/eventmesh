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

package org.apache.eventmesh.connector.mongodb.server;

import org.apache.eventmesh.connector.mongodb.config.MongodbServerConfig;
import org.apache.eventmesh.connector.mongodb.sink.connector.MongodbSinkConnector;
import org.apache.eventmesh.connector.mongodb.source.connector.MongodbSourceConnector;
import org.apache.eventmesh.openconnect.Application;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

public class MongodbConnectServer {

    public static void main(String[] args) throws Exception {

        MongodbServerConfig serverConfig = ConfigUtil.parse(MongodbServerConfig.class, "server-config.yml");

        if (serverConfig.isSourceEnable()) {
            Application mongodbSourceApp = new Application();
            mongodbSourceApp.run(MongodbSourceConnector.class);
        }

        if (serverConfig.isSinkEnable()) {
            Application mongodbSinkApp = new Application();
            mongodbSinkApp.run(MongodbSinkConnector.class);
        }
    }
}
