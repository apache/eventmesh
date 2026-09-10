#
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

# Builder: Gradle 8.5 + JDK 21, matching the fork's local build setup. The
# gradle wrapper (8.7) is intentionally not used: its distribution is fetched
# from services.gradle.org, which is unreachable on this fork's network.
# Dependencies resolve against the Maven repositories configured in settings.gradle / build.gradle.
# Build-stage runs as root (default for the gradle base image); no shipped
# artifact is affected -- the runtime stage below drops to a non-root user.
FROM gradle:8.5.0-jdk21 AS builder
USER root
WORKDIR /build
COPY . .
# `dist` builds every subproject jar and assembles dist/{apps,lib,conf,bin};
# `installPlugin` lays out dist/plugin/<type>/<name>/ (storage + protocol only;
# connectors are filtered out — they ship in the connector image). Neither runs
# tests nor style gates (jar does not depend on check/test).
RUN gradle clean dist --no-daemon \
 && gradle installPlugin --no-daemon

# Runtime: EventMeshApplication boots traffic HTTP + admin HTTP (+ opt-in
# WebSocket) as one JVM. Storage/protocol plugins are discovered from ./plugin/
# by the SPI JarExtensionClassLoader, so only conf/apps/lib need the classpath.
# bin/start.sh assembles the classpath and maps ENV vars to -D flags.
FROM eclipse-temurin:21-jre
# procps ships pgrep (container introspection / debug). The base image already
# provides the C.UTF-8 locale, so no locales package / localedef is needed
# (issue #5350: the previous en_US.UTF-8 generation was never selected by any
# ENV, leaving the JVM on POSIX anyway).
RUN apt-get update && apt-get install -y --no-install-recommends procps \
 && rm -rf /var/lib/apt/lists/*
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

# Run the JVM as a dedicated non-root user (issue #5350). uid 10001, nologin;
# home points at the app dir only so `~` resolves sensibly in shells.
RUN groupadd --gid 10001 eventmesh \
 && useradd --uid 10001 --gid eventmesh --home-dir /data/app/eventmesh --shell /usr/sbin/nologin eventmesh
WORKDIR /data/app/eventmesh
COPY --from=builder --chown=eventmesh:eventmesh /build/dist ./
RUN chmod +x bin/*.sh

# 8080 = traffic HTTP (/events/* CloudEvents + legacy /eventmesh/*).
# 8081 = independent admin port (UniAdminServer /admin/*, incl. GET /admin/health).
# 8082 = WebSocket push transport (opt-in via -Deventmesh.ws.port in JAVA_OPTS).
# Note (#5380 deployment modes, see docs/feature/deployment.md, deployment modes): this image is the
# CORE Runtime. The A2A Gateway (A2AGatewayServer, EXPERIMENTAL) and the v2 streaming
# session layer are NOT started by bin/start.sh; they require a dedicated launcher /
# embedder wiring and are documented as separate deployment modes.
EXPOSE 8080 8081 8082

# Liveness probe against the admin health endpoint (issue #5350). Uses bash's
# /dev/tcp redirection so no curl/wget layer is added; any non-200 first line
# (connection refused, 5xx) fails the check.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${EVENTMESH_ADMIN_PORT:-8081} \
    && printf "GET /admin/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 \
    && head -n1 <&3 | grep -q " 200 "' || exit 1

ENV DOCKER=true
ENV EVENTMESH_HOME=/data/app/eventmesh
ENV EVENTMESH_LOG_HOME=/data/app/eventmesh/logs
ENV CONFPATH=/data/app/eventmesh/conf
# Only kafka/rocketmq storage plugins ship (standalone was removed). Override
# with -e EVENTMESH_STORAGE_TYPE=rocketmq. Storage bootstrap (namesrv) is read
# from conf/eventmesh.properties — mount a volume over conf/ to override.
ENV EVENTMESH_STORAGE_TYPE=kafka
ENV EVENTMESH_HTTP_PORT=8080
ENV EVENTMESH_ADMIN_PORT=8081
ENV EVENTMESH_OFFSET_PATH=/data/app/eventmesh/data/offset
# Extra -D flags: TLS keystore, ws.port, meta.type/meta.addr for clustering, …
# For container memory limits consider -XX:MaxRAMPercentage=70.
ENV JAVA_OPTS=""

# Persistent state: the RocksDB offset store under data/ holds the delivery
# progress that makes at-least-once survive restarts (issue #5350). Declaring
# VOLUME makes the persistence requirement visible and keeps forgotten mounts
# on an anonymous volume instead of the container's writable layer.
VOLUME ["/data/app/eventmesh/data"]

USER eventmesh
CMD ["bash", "bin/start.sh"]
