#!/usr/bin/env bash
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
