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

package org.apache.eventmesh.connector.s3.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
public class S3SourceConnector implements SourceConnector {

    private S3Client s3;
    private String bucket;
    private String prefix;
    private String lastKey = "";

    @Override
    public void init(Properties props) {
        bucket = props.getProperty("connector.bucket", "events");
        prefix = props.getProperty("connector.prefix", "");
        s3 = S3Client.create();
    }

    @Override
    public List<CloudEvent> poll() {
        List<CloudEvent> out = new ArrayList<>();
        try {
            ListObjectsV2Request req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).startAfter(lastKey).maxKeys(100).build();
            ListObjectsV2Response resp = s3.listObjectsV2(req);
            for (S3Object obj : resp.contents()) {
                GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(obj.key()).build();
                byte[] data = s3.getObjectAsBytes(getReq).asByteArray();
                out.add(CloudEventBuilder.v1().withId("s3-" + obj.key()).withSource(URI.create("s3"))
                    .withType("s3.object").withDataContentType("application/octet-stream").withData(data).build());
                lastKey = obj.key();
            }
        } catch (Exception e) {
            log.warn("s3 poll: {}", e.toString());
        }
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
