# eventmesh-store 快速入门说明

## 依赖

```
建议使用64位操作系统，建议使用Linux/Unix；
64位JDK 1.8+;
Gradle至少为7.0, 推荐7.0.*
4g+可用磁盘用于eventmesh-store服务器
eventmesh在非standalone模式下，依赖RocketMQ作为存储层；若采用standalone模式，则可跳过该步，直接进行runtime的部署
```

## 部署
在命令行输入如下命令直接从 docker hub 上获取 RocketMQ 镜像：

```shell
#获取 namesrv 镜像
sudo docker pull rocketmqinc/rocketmq-namesrv:4.5.0-alpine
#获取 broker 镜像
sudo docker pull rocketmqinc/rocketmq-broker:4.5.0-alpine
```

在命令行输入以下命令运行namerv容器和broker容器

```shell
#运行 namerv 容器
sudo docker run -d -p 9876:9876 -v `pwd`/data/namesrv/logs:/root/logs -v `pwd`/data/namesrv/store:/root/store --name rmqnamesrv  rocketmqinc/rocketmq-namesrv:4.5.0-alpine sh mqnamesrv

#运行 broker 容器
sudo docker run -d -p 10911:10911 -p 10909:10909 -v `pwd`/data/broker/logs:/root/logs -v `pwd`/data/broker/store:/root/store --name rmqbroker --link rmqnamesrv:namesrv -e "NAMESRV_ADDR=namesrv:9876" rocketmqinc/rocketmq-broker:4.5.0-alpine sh mqbroker -c ../conf/broker.conf
```

请注意 **rocketmq-broker ip** 是 **pod ip**, 如果你想修改这个ip, 可以通过挂载容器中 **broker.conf** 文件的方式并修改文件中的 **brokerIP1** 配置项为自定义值


至此eventmesh-store的部署已完成，请转至下一步完成 [eventmesh-runtime](https://github.com/apache/incubator-eventmesh/blob/master/docs/zh/instruction/02-runtime-with-docker.md) 的部署


## 参考
关于RocketMQ的其他更多资料，请参考 <https://rocketmq.apache.org/docs/quick-start/>
