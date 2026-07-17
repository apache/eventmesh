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
RUN apt-get update && apt-get install -y --no-install-recommends locales procps \
 && rm -rf /var/lib/apt/lists/*
RUN localedef -i en_US -f UTF-8 en_US.UTF-8 --quiet
WORKDIR /data/app/eventmesh
COPY --from=builder /build/dist ./
RUN chmod +x bin/*.sh

# 8080 = traffic HTTP (/events/* CloudEvents + legacy /eventmesh/*).
# 8081 = independent admin port (UniAdminServer /admin/*).
# 8082 = WebSocket push transport (opt-in via -Deventmesh.ws.port in JAVA_OPTS).
EXPOSE 8080 8081 8082

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
ENV JAVA_OPTS=""

CMD ["bash", "bin/start.sh"]
