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

/**
 * Connector SPI — the stable, minimal contract between connector plugins and the
 * {@code eventmesh-connector-runtime} process.
 *
 * <p>Plugins implement {@link org.apache.eventmesh.connector.SourceConnector} /
 * {@link org.apache.eventmesh.connector.SinkConnector} and depend on this module (plus
 * {@code cloudevents-core}) only. The runtime side
 * ({@code EventMeshHttpEndpoint}, offset stores, {@code ConnectorRuntime} orchestration)
 * lives in {@code eventmesh-connector-runtime} and must not be referenced by plugins.</p>
 *
 * <p>The package name intentionally stays {@code org.apache.eventmesh.connector} so the
 * 23 existing plugins keep their imports unchanged; the module split is enforced by the
 * architecture guard (issue #5305) instead of the package name.</p>
 */
package org.apache.eventmesh.connector;
