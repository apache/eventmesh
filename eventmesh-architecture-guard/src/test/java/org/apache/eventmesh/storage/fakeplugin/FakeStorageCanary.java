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

package org.apache.eventmesh.storage.fakeplugin;

/**
 * Test canary for {@code ruleStoragePluginsIsolated}: a fake storage plugin
 * class that reaches into the rocketmq5 plugin's package. The rule must
 * flag it. Lives in test sources so production code stays clean; the
 * focused unit test
 * {@code ArchitectureRulesTest.ruleStoragePluginsIsolated_catches}
 * imports this class explicitly and asserts the rule fails with this
 * class named in the report.
 *
 * <p>Mirrors the {@code FakePluginCanary} pattern from
 * {@code org.apache.eventmesh.connector.fakeplugin} (issue #5328).
 */
public class FakeStorageCanary {
    public static void touch() {
        // Intentional violation: a kafka-style plugin must not depend on
        // the rocketmq5 plugin. Class-file references suffice; we never
        // instantiate the target.
        Class<?> c = org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin.class;
    }
}
