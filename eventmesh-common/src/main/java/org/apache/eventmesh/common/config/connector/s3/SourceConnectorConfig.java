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

package org.apache.eventmesh.common.config.connector.s3;

import java.util.Map;

import lombok.Data;

@Data
public class SourceConnectorConfig {

    private String connectorName;

    private String region;

    private String bucket;

    private String accessKey;

    private String secretKey;

    private String fileName;

    /**
     * The schema for the data to be read from S3.
     */
    private Map<String/* column name */, Integer/* bytes */> schema;

    /**
     * The maximum number of records that should be returned in each batch poll.
     */
    private Integer batchSize = 20;

    /**
     * The maximum ms to wait for request futures to complete.
     */
    private long timeout = 3000;
}
