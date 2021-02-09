FROM docker.io/centos:7

MAINTAINER mikexue <mike_xwm@126.com>

RUN yum update -y && yum install net-tools -y && yum install lrzsz -y && yum install vim -y
ADD jdk-8u281-linux-x64.tar.gz /usr/local/src/
RUN ln -s /usr/local/src/jdk1.8.0_281/ /usr/local/jdk

ENV JAVA_HOME /usr/local/jdk
ENV JRE_HOME $JAVA_HOME/jre
ENV CLASSPATH .:$JAVA_HOME/lib/:$JRE_HOME/lib/
ENV PATH $PATH:$JAVA_HOME/bin
