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

package org.apache.eventmesh.connector.knative.producer;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.connector.knative.cloudevent.KnativeMessageFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ProducerImpl extends AbstractProducer {

    public ProducerImpl(final Properties properties) throws IOException {
        super(properties);
    }

    public Properties attributes() {
        return properties;
    }

    public void sendOneway(CloudEvent cloudEvent) {
        // Get CloudEvent data:
        try {
            String data = KnativeMessageFactory.createReader(cloudEvent);
            super.getHttpUrlConnection().getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));

            // Send CloudEvent message:
            String s = "";
            int code = super.getHttpUrlConnection().getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(super.getHttpUrlConnection().getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    s += line + "\n";
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
