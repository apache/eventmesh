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
