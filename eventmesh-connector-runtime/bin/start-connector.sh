#!/usr/bin/env bash
#
# EventMesh connector-runtime launcher (container & host).
#
# Assembles the classpath from $EVENTMESH_HOME/{conf,apps,lib} plus every connector plugin jar
# under plugin/connector/**/*.jar. ConnectorApplication loads connectors via Class.forName, so
# (unlike the runtime) connector jars MUST be on the -cp.
#
# Env vars (all optional):
#   EVENTMESH_RUNTIME_URL   EventMesh runtime URL          (default http://localhost:8080)
#   CONNECTOR_ADMIN_PORT    connector admin HTTP port      (default 0 = off)
#   CONNECTOR_OFFSET_MODE   remote | rocksdb | inmemory    (default remote)
#   CONNECTOR_OFFSET_PATH   rocksdb offset path            (default $EVENTMESH_HOME/data/connector-offset)
#   CONNECTOR_OPTS          connector -D flags (connector.class / connector.N.* / …)
#   JAVA_OPTS               extra -D flags (tls.*, …)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVENTMESH_HOME="${EVENTMESH_HOME:-$(cd "$SCRIPT_DIR/.." && pwd)}"
cd "$EVENTMESH_HOME"

EVENTMESH_RUNTIME_URL="${EVENTMESH_RUNTIME_URL:-http://localhost:8080}"
CONNECTOR_ADMIN_PORT="${CONNECTOR_ADMIN_PORT:-0}"
CONNECTOR_OFFSET_MODE="${CONNECTOR_OFFSET_MODE:-remote}"
CONNECTOR_OFFSET_PATH="${CONNECTOR_OFFSET_PATH:-$EVENTMESH_HOME/data/connector-offset}"
CONNECTOR_OPTS="${CONNECTOR_OPTS:-}"
JAVA_OPTS="${JAVA_OPTS:-}"

# Flatten every connector plugin jar onto the classpath (single classloader; ConnectorApplication
# picks which connector(s) to run via -Dconnector.class / -Dconnector.N.class).
PLUGIN_CP=""
if compgen -G "$EVENTMESH_HOME/plugin/connector/*/*.jar" > /dev/null; then
    PLUGIN_CP="$(find "$EVENTMESH_HOME/plugin/connector" -name '*.jar' | paste -sd ':' -)"
fi

exec java $JAVA_OPTS \
    -cp "conf:apps/*:lib/*${PLUGIN_CP:+:$PLUGIN_CP}" \
    -Deventmesh.runtime.url="${EVENTMESH_RUNTIME_URL}" \
    -Dconnector.admin.port="${CONNECTOR_ADMIN_PORT}" \
    -Dconnector.offset.mode="${CONNECTOR_OFFSET_MODE}" \
    -Dconnector.offset.path="${CONNECTOR_OFFSET_PATH}" \
    $CONNECTOR_OPTS \
    org.apache.eventmesh.connector.ConnectorApplication
