#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

FROM docker.io/centos:7

MAINTAINER mikexue mike_xwm@126.com

RUN yum update -y && yum install net-tools -y && yum install lrzsz -y && yum install vim -y
ADD https://download.oracle.com/java/17/archive/jdk-17.0.1_linux-x64_bin.tar.gz /usr/local/src/
RUN ln -s /usr/local/src/jdk-17.0.1/ /usr/local/jdk

ENV JAVA_HOME /usr/local/jdk
ENV JRE_HOME $JAVA_HOME/jre
ENV CLASSPATH .:$JAVA_HOME/lib/:$JRE_HOME/lib/
ENV PATH $PATH:$JAVA_HOME/bin

WORKDIR /data
RUN mkdir -p /data/app/eventmesh
COPY ./* /data/app/eventmesh
WORKDIR /data/app/eventmesh/bin

EXPOSE 10000
EXPOSE 10105

ENV DOCKER true

CMD sh start.sh

