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

package org.apache.eventmesh.connector.storage.jdbc;

import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;
import org.apache.eventmesh.api.connector.storage.data.ConsumerGroupInfo;
import org.apache.eventmesh.api.connector.storage.data.TopicInfo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ResultSetTransformUtils {

    public static String transformTableName(ResultSet resultSet) throws SQLException {
        return resultSet.getString(1);
    }

    public static ConsumerGroupInfo transformConsumerGroup(ResultSet resultSet) {
        return new ConsumerGroupInfo();
    }

    public static CloudEventInfo transformCloudEvent(ResultSet resultSet) {
        return null;
    }


    public static TopicInfo transformTopicInfo(ResultSet resultSet) {
        return null;
    }
}
