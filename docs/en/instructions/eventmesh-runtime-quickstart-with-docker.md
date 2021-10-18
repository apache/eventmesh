# Quick start Instruction

## 3 Run with Docker

### 3.1 Pull

execute `docker pull eventmesh/eventmesh-rocketmq:v1.2.0` , you will get EventMesh image like below

![image-20210309155255510](../../images/docker/docker-image.png)

### 3.2 Configuration

> **prerequisite** : may be you need download the source code from git first and use the contents of these files(eventMesh.properties and rocketmq-client.properties) as a reference for the following actions.

**3.2.1 Files to configure**

Before run the container you should configure some files.

**eventMesh.properties**

| Configuration Key      | Default Value | Remarks                    |
| ---------------------- | ------------- | -------------------------- |
| eventMesh.server.http.port | 10105         | EventMesh http server port |
| eventMesh.server.tcp.port  | 10000         | EventMesh tcp server port  |

**rocketmq-client.properties**

| Configuration Key                 | Default Value                 | Remarks                          |
| --------------------------------- | ----------------------------- | -------------------------------- |
| eventMesh.server.rocketmq.namesrvAddr | 127.0.0.1:9876;127.0.0.1:9876 | RocketMQ namesrv default address |

After pull the EventMesh image to your host machine, you can execute command below to configure **eventMesh.properties**
and **rocketmq-client.properties**

**3.2.2 Create Files**

```shell
mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
vi eventMesh.properties
vi rocketmq-client.properties
```

The contents of these files can reference
from [eventMesh.properties](https://github.com/WeBankFinTech/EventMesh/blob/develop/eventmesh-runtime/conf/eventMesh.properties)
and [rocketmq-client.properties](https://github.com/WeBankFinTech/EventMesh/blob/develop/eventmesh-runtime/conf/rocketmq-client.properties)

### 3.3 Run

**3.3.1 run**

execute command below to run container

```
docker run -d -p 10000:10000 -p 10105:10105 -v /data/eventmesh/rocketmq/conf/eventMesh.properties:/data/app/eventmesh/conf/eventMesh.properties -v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties docker.io/eventmesh/eventmesh-rocketmq:v1.2.0
```

> -p : binding the container port with host machine port
>
> -v : mount the container configuration files with host machine files

**3.3.2 check container**

execute `docker ps` to check the container health

![image-docker-ps](../../images/docker/docker-ps.png)

execute `docker logs [container id]` you will get following result:

![image-docker-logs](../../images/docker/docker-logs.png)

execute `docker exec -it [container id] /bin/bash` you will go into the container and see the details:

![image-docker-exec](../../images/docker/docker-exec.png)
