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

package org.apache.eventmesh.runtime.transport.tcp;

import io.cloudevents.CloudEvent;

/**
 * Adapts a delivered CloudEvent into the legacy TCP {@code Package} body the old client expects
 * (egress direction). Production uses {@code MeshMessageProtocolAdaptor.fromCloudEvent(...)};
 * tests inject a stub (e.g. event → a Map the test can read back).
 */
@FunctionalInterface
public interface CloudEventToPackageBody {

    Object toBody(CloudEvent event);
}
