# Quick Start Instruction
This quick start instruction is a detailed guide of setting up EventMesh via docker. It takes RocketMQ as connector for example.

Alternative languages: [English version](eventmesh-runtime-quickstart-with-docker.md), [Chinese version](../../cn/instructions/eventmesh-runtime-quickstart-with-docker.md).

## Prerequisites
1. 64bit Linux is recommended.
2. Docker Engine is assumed installed. The installation procedure could be found on [docker docs](https://docs.docker.com/engine/install/).
3. Basic [docker concepts and commands](https://docs.docker.com/get-started/) are highly recommended acquiring first, including registry, volume, etc. However, it's not a necessity for going through this instruction because every commands are listed.
4. [RocketMQ is running successfully](https://rocketmq.apache.org/docs/quick-start/) and could be routed to through IP address.

## Get EventMesh Image
First, you could open a terminal and use the following ```pull``` command to download [latest EventMesh](https://eventmesh.apache.org/events/release-notes/v1.3.0/) from [Docker Hub - the default docker registry](https://registry.hub.docker.com/r/eventmesh/eventmesh/tags).
```shell
sudo docker pull eventmesh/eventmesh:v1.3.0
```
During and After downloading, the terminal will show the status such as:
```shell
ubuntu@VM-16-4-ubuntu:~$ sudo docker pull eventmesh/eventmesh:v1.3.0
v1.3.0: Pulling from eventmesh/eventmesh
2d473b07cdd5: Downloading [======>                                            ]  9.649MB/76.1MB
2b97b2e51c1a: Pulling fs layer 
ccef593d4fe7: Pulling fs layer 
70beb7ae51cd: Waiting 
0a2cf32321af: Waiting 
5d764ea8950d: Waiting 
71d02dcd996d: Waiting 
v1.3.0: Pulling from eventmesh/eventmesh
2d473b07cdd5: Pull complete 
2b97b2e51c1a: Pull complete 
ccef593d4fe7: Pull complete 
70beb7ae51cd: Pull complete 
0a2cf32321af: Pull complete 
5d764ea8950d: Pull complete 
71d02dcd996d: Pull complete 
Digest: sha256:267a93a761e999790f8bd132b09541f0ffab551e8618097a4adce8e3e66bbe4e
Status: Downloaded newer image for eventmesh/eventmesh:v1.3.0
docker.io/eventmesh/eventmesh:v1.3.0
```
Next, you could list and check local images on your machine using command:
```shell
sudo docker images
```
And, the terminal will print all local images such as the following content. It could be found that EventMesh image has been successfully downloaded.
```shell
ubuntu@VM-16-4-ubuntu:~$ sudo docker images
REPOSITORY               TAG         IMAGE ID       CREATED        SIZE
eventmesh/eventmesh      v1.3.0      da0008c1d03b   7 days ago     922MB
```

## Prepare Configuration Files
Before running the EventMesh container from downloaded image, you need to configure some files.

Here this instruction takes RocketMQ as connector for example, so that two configuration files should be created: ```eventMesh.properties``` and ```rocketmq-client.properties```.

First, you may need to create such files, using following commands:
```shell
sudo mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
sudo touch eventmesh.properties
sudo touch rocketmq-client.properties
```

### eventMesh.properties

It contains properties of  EventMesh runtime env and integrated plugins.

Use ```vi``` command to edit ```eventmesh.properties```:
```shell
sudo vi eventmesh.properties
```
In the quick start step, you could directly copy content in https://github.com/apache/incubator-eventmesh/blob/1.3.0/eventmesh-runtime/conf/eventmesh.properties .

Some default key-values are listed below:

| Configuration Key          | Default Value | Remarks                    |
|----------------------------|---------------|----------------------------|
| eventMesh.server.http.port | 10105         | EventMesh http server port |
| eventMesh.server.tcp.port  | 10000         | EventMesh tcp server port  |



### rocketmq-client.properties

It contains properties of running RocketMQ nameserver.

Use ```vi``` command to edit ```rocketmq-client.properties```:
```shell
sudo vi rocketmq-client.properties
```
In the quick start step, you could refer to https://github.com/apache/incubator-eventmesh/blob/1.3.0/eventmesh-runtime/conf/rocketmq-client.properties , and change the value to a running nameserver address.

The default key-value is listed below:

| Configuration Key                     | Default Value                 | Remarks                          |
|---------------------------------------|-------------------------------|----------------------------------|
| eventMesh.server.rocketmq.namesrvAddr | 127.0.0.1:9876;127.0.0.1:9876 | RocketMQ namesrv default address |


## Make EventMesh Run
Now you are at the step of running an EventMesh container from downloaded docker image.

The main command is ```docker run```, and two things need to be noted.
1. binding the container port with host machine port: use ```-p``` option of ```docker run```.
2. mount the configuration files with host machine files: use ```-v``` option of ```docker run```.

So that the command for running EventMesh is:
```shell
sudo docker run -d \
> -p 10000:10000 -p 10105:10105 \
> -v /data/eventmesh/rocketmq/conf/eventMesh.properties:/data/app/eventmesh/conf/eventMesh.properties \
> -v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties \
> eventmesh/eventmesh:v1.3.0
```
After you executing it and seeing a string below it, the container is running successfully.

Next, you could use below command to check the status of the EventMesh container:
```shell
sudo docker ps
```

Successfully, you could see the terminal presenting container status such as:
```shell
CONTAINER ID   IMAGE                        COMMAND                  CREATED              STATUS              PORTS                                                                                          NAMES
d1e1a335d4a9   eventmesh/eventmesh:v1.3.0   "/bin/sh -c 'sh star…"   About a minute ago   Up About a minute   0.0.0.0:10000->10000/tcp, :::10000->10000/tcp, 0.0.0.0:10105->10105/tcp, :::10105->10105/tcp   focused_bartik
```
It tells you that ```container id``` is ```d1e1a335d4a9``` and random ```name``` is ```focused_bartik```. They are the identifier to this container when managing it. Note that they may be different in your machine.

## Manage EventMesh Container
After correctly running EventMesh container, you could manage such container by entering container, checking logs, remove container, and so on.

**enter container** command example:
```shell
sudo docker exec -it [your container id or name] /bin/bash
```

**checking logs** command example inside container:
```shell
cd ../logs
tail -f eventmesh.out
```

**remove container** command example:
```shell
sudo docker rm -f [your container id or name]
```

## Explore More
Since EventMesh is running, now you can write your own client code referring [```eventmesh-examples```](https://github.com/apache/incubator-eventmesh/tree/master/eventmesh-examples).

Hope you enjoy and explore more on EventMesh!
