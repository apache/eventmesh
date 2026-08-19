#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# EventMesh unified runtime launcher (container & host).
#
# Assembles the classpath from $EVENTMESH_HOME/{conf,apps,lib} and starts
# org.apache.eventmesh.runtime.boot.EventMeshApplication. Storage/protocol plugins are discovered
# from ./plugin/ by the SPI JarExtensionClassLoader, so they are NOT on the -cp.
#
# Env vars (all optional):
#   EVENTMESH_STORAGE_TYPE  kafka | rocketmq | rocketmq5    (default kafka)
#   EVENTMESH_HTTP_PORT     traffic HTTP port              (default 8080)
#   EVENTMESH_ADMIN_PORT    admin HTTP port                (default 8081)
#   EVENTMESH_OFFSET_PATH   RocksDB offset dir             (default $EVENTMESH_HOME/data/offset)
#   JAVA_OPTS               extra -D flags (tls.*, ws.port, meta.*, …)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVENTMESH_HOME="${EVENTMESH_HOME:-$(cd "$SCRIPT_DIR/.." && pwd)}"
cd "$EVENTMESH_HOME"

EVENTMESH_STORAGE_TYPE="${EVENTMESH_STORAGE_TYPE:-kafka}"
EVENTMESH_HTTP_PORT="${EVENTMESH_HTTP_PORT:-8080}"
EVENTMESH_ADMIN_PORT="${EVENTMESH_ADMIN_PORT:-8081}"
EVENTMESH_OFFSET_PATH="${EVENTMESH_OFFSET_PATH:-$EVENTMESH_HOME/data/offset}"
JAVA_OPTS="${JAVA_OPTS:-}"

exec java $JAVA_OPTS \
    -cp "conf:apps/*:lib/*" \
    -Deventmesh.storage.type="${EVENTMESH_STORAGE_TYPE}" \
    -Deventmesh.http.port="${EVENTMESH_HTTP_PORT}" \
    -Deventmesh.admin.port="${EVENTMESH_ADMIN_PORT}" \
    -Deventmesh.offset.path="${EVENTMESH_OFFSET_PATH}" \
    org.apache.eventmesh.runtime.boot.EventMeshApplication
