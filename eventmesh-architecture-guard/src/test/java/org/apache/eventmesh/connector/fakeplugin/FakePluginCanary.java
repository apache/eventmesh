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

package org.apache.eventmesh.connector.fakeplugin;

/**
 * Test canary for {@code ruleConnectorPluginsDependOnlyOnSpi}: a fake plugin class that
 * reaches into connector-runtime internals. The rule must flag it. Lives in test sources
 * so production code stays clean; ArchUnit imports it only when the guard test asks for
 * a classpath import that includes tests — the production rule uses
 * DO_NOT_INCLUDE_TESTS, so this canary is exercised via the focused unit test below.
 */
public class FakePluginCanary {
    public static void touch() {
        Class<?> c = org.apache.eventmesh.connector.ConnectorRuntime.class;
    }
}
