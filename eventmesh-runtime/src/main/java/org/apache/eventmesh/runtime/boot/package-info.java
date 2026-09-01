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
 * Process bootstrap: wires UniRuntime, EventMeshApplication, lifecycle hooks.
 *
 * <p>Depends on: Depends on every runtime sub-package (it IS the wiring layer).
 *
 * <p>Policy: May not be depended on by ingress/delivery/tcp.internal -- those must use EventMeshApplication via interfaces, not direct refs.
 *
 * <p>Marked {@link org.apache.eventmesh.common.Internal @Internal} as a
 * whole package; public types must carry {@link org.apache.eventmesh.common.Public @Public}.
 */
@org.apache.eventmesh.common.Internal
package org.apache.eventmesh.runtime.boot;
