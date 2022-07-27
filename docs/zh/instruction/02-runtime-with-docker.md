# 使用 Docker 快速入门 EventMesh（暂时只支持到1.4.0版本）

本篇快速入门将详细介绍使用 docker 部署 EventMesh，以 RocketMQ 作为对接的中间件。

可选语言: [英文版本](../../en/instructions/eventmesh-runtime-quickstart-with-docker.md)，[中文版本](02-runtime-with-docker.md)。

## 前提

1. 建议使用64位的 linux 系统；
2. 请预先安装 Docker Engine。 Docker 的安装过程可以参考 [docker 官方文档](https://docs.docker.com/engine/install/)；
3. 建议掌握基础的 docker 概念和命令行，例如注册中心、挂载等等。不过这不是必须的，因为本次操作所需的命令都已为您列出；
4. 若您选择非standalone模式，请确保 [RocketMQ 已成功启动](https://rocketmq.apache.org/docs/quick-start/) 并且可以使用 ip 地址访问到；若您选择standalone模式，则无需启动 RocketMQ 。

## 获取 EventMesh 镜像

首先，你可以打开一个命令行，并且使用下面的 ```pull``` 命令从 [Docker Hub](https://registry.hub.docker.com/r/eventmesh/eventmesh/tags) 中下载[最新发布的 EventMesh](https://eventmesh.apache.org/events/release-notes/v1.3.0/) 。

```shell
sudo docker pull eventmesh/eventmesh:v1.4.0
```

您可以使用以下命令列出并查看本地已有的镜像。

```shell
sudo docker images
```

如果终端显示如下所示的镜像信息，则说明 EventMesh 镜像已经成功下载到本地。

```shell
REPOSITORY               TAG           IMAGE ID           CREATED          SIZE
eventmesh/eventmesh      v1.4.0	       6e2964599c78	  3 months ago     936.73 MB
```

## 创建配置文件

在根据 EventMesh 镜像运行对应容器之前，你需要创建两个配置文件，分别是：```eventMesh.properties``` 和 ```rocketmq-client.properties```。

首先，你需要使用下面的命令创建这两个文件。

```shell
sudo mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
sudo touch eventmesh.properties
sudo touch rocketmq-client.properties
```

### 配置 eventMesh.properties

这个配置文件中包含 EventMesh 运行时环境和集成进来的其他插件所需的参数。

使用下面的 ```vim``` 命令编辑 ```eventmesh.properties```。

```shell
sudo vim eventmesh.properties
```

你可以直接将 GitHub 仓库中的对应配置文件中的内容复制过来，链接为：<https://github.com/apache/incubator-eventmesh/blob/1.3.0/eventmesh-runtime/conf/eventmesh.properties> 。

请检查配置文件里的默认端口是否已被占用，如果被占用请修改成未被占用的端口：

| 属性                         | 默认值   | 备注                         |
|----------------------------|-------|----------------------------|
| eventMesh.server.http.port | 10105 | EventMesh http server port |
| eventMesh.server.tcp.port  | 10000 | EventMesh tcp server port  |
| eventMesh.server.grpc.port | 10205 | EventMesh grpc server port |

### 配置 rocketmq-client.properties

这个配置文件中包含 RocketMQ nameserver 的信息。

使用下面的 ```vim``` 命令编辑 ```rocketmq-client.properties```。

```shell
sudo vim rocketmq-client.properties
```

你可以直接将 GitHub 仓库中的对应配置文件中的内容复制过来，链接为：<https://github.com/apache/incubator-eventmesh/blob/1.3.0/eventmesh-runtime/conf/rocketmq-client.properties> 。请注意，如果您正在运行的 namesetver 地址不是配置文件中的默认值，请将其修改为实际正在运行的nameserver地址。

请检查配置文件里的默认namesrvAddr是否已被占用，如果被占用请修改成未被占用的地址：

| 属性                                    | 默认值                           | 备注                               |
|---------------------------------------|-------------------------------|----------------------------------|
| eventMesh.server.rocketmq.namesrvAddr | 127.0.0.1:9876;127.0.0.1:9876 | RocketMQ namesrv default address |

## 运行 EventMesh

现在你就可以开始根据下载好的 EventMesh 镜像运行容器了。

使用到的命令是 ```docker run```，有以下两点内容需要格外注意。

1. 绑定容器端口和宿主机端口：使用 ```docker run``` 的 ```-p``` 选项。
2. 将宿主机中的两份配置文件挂在到容器中：使用 ```docker run``` 的 ```-v``` 选项。

综合一下，对应的启动命令为：

```shell
sudo docker run -d \
    -p 10000:10000 -p 10105:10105 \
    -v /data/eventmesh/rocketmq/conf/eventMesh.properties:/data/app/eventmesh/conf/eventMesh.properties \
    -v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties \
    eventmesh/eventmesh:v1.4.0
```

如果运行命令之后看到新输出一行字符串，那么运行 EventMesh 镜像的容器就启动成功了。

接下来，你可以使用下面的命令查看容器的状态。

```shell
sudo docker ps
```

如果成功的话，你会看到终端打印出了如下所示容器的信息，其中就有运行 EventMesh 镜像的容器。

```shell
CONTAINER ID   IMAGE                        COMMAND                  CREATED              STATUS              PORTS                                                                                          NAMES
d1e1a335d4a9   eventmesh/eventmesh:v1.4.0   "/bin/sh -c 'sh star…"   About a minute ago   Up About a minute   0.0.0.0:10000->10000/tcp, :::10000->10000/tcp, 0.0.0.0:10105->10105/tcp, :::10105->10105/tcp   focused_bartik
```

从这个信息中可以看出，```container id``` 是 ```d1e1a335d4a9```，```name``` 是 ```focused_bartik```，它们都可以用来唯一标识这个容器。**注意**：在你的电脑中，它们的值可能跟这里的不同。

## 管理 EventMesh 容器

在成功的运行了 EventMesh 容器后，你可以通过进入容器、查看日志、删除容器等方式管理容器。

**进入容器** 命令示例：

```shell
sudo docker exec -it [your container id or name] /bin/bash
```

在容器中 **查看日志** 命令示例：

```shell
cd ../logs
tail -f eventmesh.out
```

**删除容器** 命令示例:

```shell
sudo docker rm -f [your container id or name]
```

## 探索更多

既然 EventMesh 已经通过容器运行了，现在你可以参考 [```eventmesh-examples``` 模块](https://github.com/apache/incubator-eventmesh/tree/master/eventmesh-examples) 编写并测试自己的代码了。

希望你享受这个过程并获得更多收获！
