#!/usr/bin/env bash
#
# EventMesh unified runtime stop script.
# Finds the running EventMeshApplication process (via jps or pgrep) and sends SIGTERM for graceful
# shutdown (the JVM shutdown hook flushes offsets + closes storage). Falls back to SIGKILL after 10s.
#
set -euo pipefail

# Find the EventMeshApplication process by class name.
PID=""
if command -v jps &>/dev/null; then
    PID=$(jps -l 2>/dev/null | grep 'EventMeshApplication' | awk '{print $1}' | head -1)
fi
if [ -z "$PID" ] && command -v pgrep &>/dev/null; then
    PID=$(pgrep -f 'org.apache.eventmesh.runtime.boot.EventMeshApplication' | head -1)
fi

if [ -z "$PID" ]; then
    echo "EventMesh runtime is not running (no EventMeshApplication process found)."
    exit 0
fi

echo "Stopping EventMesh runtime (PID $PID)..."
kill "$PID" 2>/dev/null || true

# Wait up to 10s for graceful shutdown (offset flush + storage close).
for i in $(seq 1 10); do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "EventMesh runtime stopped."
        exit 0
    fi
    sleep 1
done

# Force kill if still running after 10s.
echo "Graceful shutdown timed out, force killing (PID $PID)..."
kill -9 "$PID" 2>/dev/null || true
echo "EventMesh runtime killed."
