<h1>Eventmesh-runtime快速入门说明</h1>


##  1 远程部署

<h3>1.1 依赖</h3>

```
建议使用64位操作系统，建议使用Linux / Unix；
64位JDK 1.8+;
Gradle至少为5.6, 推荐 5.6.*
```

<h3>1.2 下载源码</h3> 

[https://github.com/WeBankFinTech/EventMesh](https://github.com/WeBankFinTech/EventMesh)
您将获得**EventMesh-master.zip**

<h3>1.3 构建源码</h3>

```$ xslt
unzip EventMesh-master.zip
cd / *您的部署路径* /EventMesh-master/eventmesh-runtime
gradle clean dist tar -x test
```
您将在目录/ *您的部署路径* /EventMesh-master/eventmesh-runtime/dist中获得**eventmesh-runtime_1.0.0.tar.gz**

<h3>1.4 部署</h3>

- 部署eventmesh-runtime


```$ xslt
upload eventmesh-runtime_1.0.0.tar.gz
tar -zxvf eventmesh-runtime_1.0.0.tar.gz
cd bin
配置 proxy.properties
cd ../bin
sh start.sh
```
如果看到"ProxyTCPServer[port=10000] started...."，则说明设置成功。



<h2>2 本地构建运行</h2>

<h3>2.1 同上述步骤 1.1</h3>

<h3>2.2 同上述步骤 1.2</h3>

<h3>2.3 本地启动</h3>

**2.3.1 项目结构说明：**

![](C:\Users\mikexue\AppData\Roaming\Typora\typora-user-images\image-20201229211217729.png)

- eventmesh-common : eventmesh公共类与方法模块
- eventmesh-connector-api : eventmesh插件接口定义模块
- eventmesh-connector-defibus : eventmesh defibus插件模块
- eventmesh-connector-rocketmq : eventmesh rocketmq插件模块
- eventmesh-runtime : eventmesh运行时模块
- eventmesh-sdk-java : eventmesh java客户端sdk
- eventmesh-starter : eventmesh本地启动运行项目入口

> 注：插件模块遵循java spi机制，需要在对应模块中的/main/resources/META-INF/services 下配置相关接口与实现类的映射文件

**2.3.2 配置VM启动参数**

```
-Dlog4j.configurationFile=..\eventmesh-runtime\conf\log4j2.xml
-Dproxy.log.home=..\eventmesh-runtime\logs
-Dproxy.home=..\eventmesh-runtime
-DconfPath=..\eventmesh-runtime\conf
```

**2.3.3 配置build.gradle文件**

通过修改dependencies，compile project 项来指定项目启动后加载的插件

> 默认加载eventmesh-connector-defibus插件

**2.3.4 启动运行**

```
运行com.webank.eventmesh.starter.StartUp的主要方法
```

