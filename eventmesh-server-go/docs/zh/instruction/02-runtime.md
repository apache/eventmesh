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


### 1.3 本地启动

**1.3.1 项目结构说明：**

- eventmesh-common : eventmesh公共类与方法模块
- eventmesh-connector-api : eventmesh connector插件接口定义模块
- eventmesh-connector-plugin : eventmesh connector插件模块
- eventmesh-runtime : eventmesh运行时模块
- eventmesh-sdk-java : eventmesh java客户端sdk
- eventmesh-starter : eventmesh本地启动运行项目入口
- eventmesh-spi : eventmesh SPI加载模块

> 注：插件模块遵循 eventmesh 定义的SPI规范, 自定义的SPI接口需要使用注解 @EventMeshSPI 标识.
> 插件实例需要在对应模块中的 /main/resources/META-INF/eventmesh 下配置相关接口与实现类的映射文件,文件名为SPI接口全类名.
> 文件内容为插件实例名到插件实例的映射, 具体可以参考 eventmesh-connector-rocketmq 插件模块

**1.3.2 插件说明**

***1.3.2.1 安装插件***

有两种方式安装插件

- classpath加载：本地开发可以通过在 eventmesh-starter 模块 build.gradle 中进行声明，例如声明使用 rocketmq 插件

```gradle
   implementation project(":eventmesh-connector-plugin:eventmesh-connector-rocketmq")
```

- 文件加载：通过将插件安装到插件目录，EventMesh 在运行时会根据条件自动加载插件目录下的插件，可以通过执行以下命令安装插件

```shell
./gradlew clean jar dist && ./gradlew installPlugin
```

***1.3.2.2 使用插件***

EventMesh 会默认加载 dist/plugin 目录下的插件，可以通过`-DeventMeshPluginDir=your_plugin_directory`来改变插件目录。运行时需要使用的插件实例可以在
`confPath`目录下面的`eventmesh.properties`中进行配置。例如通过以下设置声明在运行时使用rocketmq插件。

```properties
#connector plugin
eventMesh.connector.plugin.type=rocketmq
```

**1.3.3 配置VM启动参数**

```properties
-Dlog4j.configurationFile=eventmesh-runtime/conf/log4j2.xml
-Deventmesh.log.home=eventmesh-runtime/logs
-Deventmesh.home=eventmesh-runtime
-DconfPath=eventmesh-runtime/conf
```

> 注：如果操作系统为Windows, 可能需要将文件分隔符换成\

**1.3.4 启动运行**

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


