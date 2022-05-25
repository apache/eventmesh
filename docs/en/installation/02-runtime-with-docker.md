# EventMesh Runtime (Docker)

The documentation introduces the steps to install the latest release of EventMesh Runtime with Docker and connect to Apache RocketMQ. It's recommended to use a Linux-based system with [Docker Engine](https://docs.docker.com/engine/install/). Please follow the [Docker tutorial](https://docs.docker.com/get-started/) to get familiar with the basic concepts (registry, volume, etc.) and commands of Docker.

## Pull EventMesh Image

Download the pre-built image of [`eventmesh`](https://hub.docker.com/r/eventmesh/eventmesh) from Docker Hub with `docker pull`:

```console
$ sudo docker pull eventmesh/eventmesh:v1.4.0
v1.4.0: Pulling from eventmesh/eventmesh
2d473b07cdd5: Pull complete
2b97b2e51c1a: Pull complete
ccef593d4fe7: Pull complete
70beb7ae51cd: Pull complete
0a2cf32321af: Pull complete
5d764ea8950d: Pull complete
d97f44e8825f: Pull complete
Digest: sha256:0edc758be313c61758c1b598ef3315e3b6f707b127ad649063caf67d8876cc45
Status: Downloaded newer image for eventmesh/eventmesh:v1.4.0
docker.io/eventmesh/eventmesh:v1.4.0
```

To verify that the `eventmesh/eventmesh` image is successfully installed, list the downloaded images with `docker images`:

```console
$ sudo docker images
eventmesh/eventmesh           v1.4.0    6e2964599c78   2 weeks ago    937MB
```

## Edit Configuration

Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime. To integrate RocketMQ as a connector, these two configuration files should be created: `eventmesh.properties` and `rocketmq-client.properties`.

```shell
sudo mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
sudo touch eventmesh.properties
sudo touch rocketmq-client.properties
```

### `eventmesh.properties`

The `eventmesh.properties` file contains the properties of EventMesh runtime environment and integrated plugins. Please refer to the [default configuration file](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-runtime/conf/eventmesh.properties) for the available configuration keys.

```shell
sudo vim eventmesh.properties
```

| Configuration Key | Default Value |  Description |
|-|-|-|
| `eventMesh.server.http.port` | 10105 | EventMesh HTTP server port |
| `eventMesh.server.tcp.port` | 10000 | EventMesh TCP server port  |
| `eventMesh.server.grpc.port` | 10205 | EventMesh gRPC server port |

### `rocketmq-client.properties`

The `rocketmq-client.properties` file contains the properties of the Apache RocketMQ nameserver.

```shell
sudo vim rocketmq-client.properties
```

Please refer to the [default configuration file](https://github.com/apache/incubator-eventmesh/blob/1.3.0/eventmesh-runtime/conf/rocketmq-client.properties) and change the value of `eventMesh.server.rocketmq.namesrvAddr` to the nameserver address of RocketMQ.

| Configuration Key | Default Value | Description |
|-|-|-|
| `eventMesh.server.rocketmq.namesrvAddr` | `127.0.0.1:9876;127.0.0.1:9876` | The address of RocketMQ nameserver |

## Run and Manage EventMesh Container

Run an EventMesh container from the `eventmesh/eventmesh` image with the `docker run` command. The `-p` option of the command binds the container port with the host machine port. The `-v` option of the command mounts the configuration files from files in the host machine.

```shell
sudo docker run -d -p 10000:10000 -p 10105:10105 \
-v /data/eventmesh/rocketmq/conf/eventmesh.properties:/data/app/eventmesh/conf/eventmesh.properties \
-v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties \
eventmesh/eventmesh:v1.4.0
```

The `docker ps` command lists the details (id, name, status, etc.) of the running containers. The container id is the unique identifier of the container.

```console
$ sudo docker ps
CONTAINER ID     IMAGE                        COMMAND                  CREATED              STATUS              PORTS                                                                                          NAMES
<container_id>   eventmesh/eventmesh:v1.4.0   "/bin/sh -c 'sh star…"   About a minute ago   Up About a minute   0.0.0.0:10000->10000/tcp, :::10000->10000/tcp, 0.0.0.0:10105->10105/tcp, :::10105->10105/tcp   <container_name>
```

To connect to the EventMesh container:

```shell
sudo docker exec -it [container id or name] /bin/bash
```

To read the log of the EventMesh container:

```shell
tail -f ../logs/eventmesh.out
```

To stop or remove the container:

```shell
sudo docker stop [container id or name]

sudo docker rm -f [container id or name]
```
