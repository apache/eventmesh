# Eventmesh-runtime 快速入门说明


## 1 本地构建运行

### 1.1 依赖

```
建议使用64位操作系统，建议使用Linux / Unix；
64位JDK 1.8+;
Gradle至少为7.0, 推荐 7.0.*
```

### 1.2 下载源码

在 [EventMesh download](https://eventmesh.apache.org/download) 页面选择1.5.0版本的 Source Code 进行下载并解压, 您将获得**apache-eventmesh-1.5.0-incubating-src**


### 2.3 本地启动

**2.3.1 项目结构说明：**

- eventmesh-common : eventmesh公共类与方法模块
- eventmesh-connector-api : eventmesh connector插件接口定义模块
- eventmesh-connector-plugin : eventmesh connector插件模块
- eventmesh-runtime : eventmesh运行时模块
- eventmesh-sdk-java : eventmesh java客户端sdk
- eventmesh-starter : eventmesh本地启动运行项目入口
- eventmesh-spi : eventmesh SPI加载模块

> 注：插件模块遵循eventmesh定义的SPI规范, 自定义的SPI接口需要使用注解@EventMeshSPI标识.
> 插件实例需要在对应模块中的/main/resources/META-INF/eventmesh 下配置相关接口与实现类的映射文件,文件名为SPI接口全类名.
> 文件内容为插件实例名到插件实例的映射, 具体可以参考eventmesh-connector-rocketmq插件模块

**2.3.2 插件说明**

***2.3.2.1 安装插件***

有两种方式安装插件

- classpath加载：本地开发可以通过在eventmesh-starter模块build.gradle中进行声明，例如声明使用rocketmq插件

```gradle
   implementation project(":eventmesh-connector-plugin:eventmesh-connector-rocketmq")
```

- 文件加载：通过将插件安装到插件目录，EventMesh在运行时会根据条件自动加载插件目录下的插件，可以通过执行以下命令安装插件

```shell
./gradlew clean jar dist && ./gradlew installPlugin
```

***2.3.2.2 使用插件***

EventMesh会默认加载dist/plugin目录下的插件，可以通过`-DeventMeshPluginDir=your_plugin_directory`来改变插件目录。运行时需要使用的插件实例可以在
`confPath`目录下面的`eventmesh.properties`中进行配置。例如通过以下设置声明在运行时使用rocketmq插件。

```properties
#connector plugin
eventMesh.connector.plugin.type=rocketmq
```

**2.3.3 配置VM启动参数**

```properties
-Dlog4j.configurationFile=eventmesh-runtime/conf/log4j2.xml
-Deventmesh.log.home=eventmesh-runtime/logs
-Deventmesh.home=eventmesh-runtime
-DconfPath=eventmesh-runtime/conf
```

> 注：如果操作系统为Windows, 可能需要将文件分隔符换成\

**2.3.4 启动运行**

```
运行org.apache.eventmesh.starter.StartUp的主要方法
```

## 2 远程部署

### 2.1 依赖

```
建议使用64位操作系统，建议使用Linux / Unix；
64位JDK 1.8+;
Gradle至少为7.0, 推荐 7.0.*
```

### 2.2 下载

在 [EventMesh download](https://eventmesh.apache.org/download) 页面选择1.5.0版本的 Binary Distribution 进行下载, 您将获得**apache-eventmesh-1.5.0-incubating-bin.tar.gz**


### 2.3 部署

- 部署eventmesh-runtime

```$ xslt
# 解压 apache-eventmesh-1.5.0-incubating-bin.tar.gz
tar -zxvf apache-eventmesh-1.5.0-incubating-bin.tar.gz
cd apache-eventmesh-1.5.0-incubating

# 配置 eventMesh.properties
vim conf/eventMesh.properties

# 启动EventMesh
cd bin
sh start.sh
```

如果看到"EventMeshTCPServer[port=10000] started...."，则说明设置成功。


## 3 Docker 运行

### 3.1 拉取镜像

执行 `docker pull eventmesh/eventmesh-rocketmq:v1.5.0` , 你将会获取到EventMesh的镜像，如下图所示：


### 3.2 配置

> **预先准备** : 你可能需要从github上下载源代码，并参考这两个文件(eventMesh.properties 和 rocketmq-client.properties)的内容来做下面的操作

**3.2.1 需要配置的文件**

在运行容器之前，你需要配置如下文件：

**eventMesh.properties**

| 配置项                 | 默认值 | 备注                    |
| ---------------------- | ------ | ----------------------- |
| eventMesh.server.http.port | 10105  | EventMesh http 服务端口 |
| eventMesh.server.tcp.port  | 10000  | EventMesh tcp 服务端口  |
| eventMesh.server.grpc.port  | 10205  | EventMesh grpc 服务端口  |

**rocketmq-client.properties**

| 配置项                            | 默认值                        | 备注                  |
| --------------------------------- | ----------------------------- | --------------------- |
| eventMesh.server.rocketmq.namesrvAddr | 127.0.0.1:9876;127.0.0.1:9876 | RocketMQ namesrv 地址 |

拉取了EventMesh镜像到你的宿主机后，你可以执行下面的命令来完成**eventMesh.properties**和**rocketmq-client.properties** 文件的配置

**3.2.2 创建文件**

```shell
mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
vi eventMesh.properties
vi rocketmq-client.properties
```

这两个文件内容可以参考 [eventMesh.properties](https://github.com/apache/incubator-eventmesh/blob/develop/eventmesh-runtime/conf/eventMesh.properties)
和 [rocketmq-client.properties](https://github.com/apache/incubator-eventmesh/blob/develop/eventmesh-runtime/conf/rocketmq-client.properties)

### 3.3 运行

**3.3.1 运行**

执行下面的命令来运行容器

```shell
docker run -d -p 10000:10000 -p 10105:10105 -v /data/eventmesh/rocketmq/conf/eventMesh.properties:/data/app/eventmesh/conf/eventMesh.properties -v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties docker.io/eventmesh/eventmesh-rocketmq:v1.5.0
```

> -p : 将容器内端口与宿主机端口绑定，容器的端口应与配置文件中的端口一致
>
> -v : 将容器内的配置文件挂载到宿主机下，需注意配置文件的路径

**3.3.2 检查容器的运行状况**

执行 `docker ps` 来检查容器的运行状况


执行 `docker logs [container id]` 可以查看容器的日志


执行 `docker exec -it [container id] /bin/bash` 可以进入到容器中并查看详细信息


