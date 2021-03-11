FROM centos7-jdk8:latest

MAINTAINER mikexue mike_xwm@126.com

WORKDIR /data
RUN mkdir /data/app
ADD EventMesh_1.2.0-SNAPSHOT.tar.gz  /data/app/eventmesh
WORKDIR /data/app/eventmesh/bin

EXPOSE 10000
EXPOSE 10105

ENV DOCKER true

CMD sh start.sh

