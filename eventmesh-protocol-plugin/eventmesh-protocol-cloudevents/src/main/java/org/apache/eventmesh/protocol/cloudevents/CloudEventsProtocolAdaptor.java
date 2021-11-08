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

package org.apache.eventmesh.protocol.cloudevents;

import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;

import io.cloudevents.core.v1.CloudEventV1;

/**
 * CloudEvents protocol adaptor, used to transform CloudEvents message to CloudEvents message.
 *
 * @since 1.3.0
 */
public class CloudEventsProtocolAdaptor<T> implements ProtocolAdaptor<T> {

    @Override
    public CloudEventV1 toCloudEventV1(T cloudEvent) {

        if (cloudEvent instanceof Package){
            //todo:convert package to cloudevents
        }else if (cloudEvent instanceof HttpCommand){
            //todo:convert httpCommand to cloudevents
        }
        return null;
    }

    @Override
    public Package fromCloudEventV1(CloudEventV1 cloudEvent) {
        return null;
    }

    @Override
    public String getProtocolType() {
        return CloudEventsProtocolConstant.PROTOCOL_NAME;
    }
}
