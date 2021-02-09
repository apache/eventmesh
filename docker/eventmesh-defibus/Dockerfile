FROM centos7-jdk8:latest

MAINTAINER mikexue mike_xwm@126.com

WORKDIR /data
RUN mkdir /data/app
ADD eventmesh-runtime_1.2.0-SNAPSHOT.tar.gz /data/app/eventmesh
COPY eventmesh-connector-defibus-1.2.0-SNAPSHOT.jar /data/app/eventmesh/apps
WORKDIR /data/app/eventmesh/bin

EXPOSE 10000
EXPOSE 10105

ENV DOCKER true
CMD sh start.sh
