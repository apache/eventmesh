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

package org.apache.eventmesh.connector.s3.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
public class S3SinkConnector implements SinkConnector {

    private S3Client s3;
    private String bucket;
    private Properties props;

    @Override
    public void init(Properties props) {
        this.props = props;
        bucket = props.getProperty("connector.bucket", "sink");
        s3 = S3Client.create();
    }

    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            String key = event.getId() != null ? event.getId() : String.valueOf(System.nanoTime());
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {

    }
}
