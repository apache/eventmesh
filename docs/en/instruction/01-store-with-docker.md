# Guidelines of eventmesh-store with Docker

## Dependencies

```
64-bit OS，we recommend Linux/Unix；
64-bit JDK 1.8+;
Gradle 7.0+, we recommend 7.0.*
4g+ available disk to deploy eventmesh-store
If you choose standalone mode, you could skip this file and go to the next step: Start Eventmesh-Runtime; if not, you could choose RocketMQ as the store layer.
```


## Download

Download the Binary code (recommended: 4.9.*) from [RocketMQ Official](https://rocketmq.apache.org/dowloading/releases/). Here we take 4.9.2 as an example.

```
unzip rocketmq-all-4.9.2-bin-release.zip
cd rocketmq-4.9.2/
```


## Deploy

- #### Start Name Server

```
nohup sh bin/mqnamesrv &
tail -f ~/logs/rocketmqlogs/namesrv.log
```

- #### Start Broker

```
nohup sh bin/mqbroker -n localhost:9876 &
tail -f ~/logs/rocketmqlogs/broker.log
```

The deployment of eventmesh-store has finished, please go to the next step: [Start Eventmesh-Runtime](02-runtime.md)



## Deploy
Pull RocketMQ image from Docker Hub：

```shell
#获取namesrv镜像
sudo docker pull rocketmqinc/rocketmq-namesrv:4.5.0-alpine
#获取broker镜像
sudo docker pull rocketmqinc/rocketmq-broker:4.5.0-alpine
```

Start namesrv  and broker

```shell
#运行namerv容器
sudo docker run -d -p 9876:9876 -v `pwd`/data/namesrv/logs:/root/logs -v `pwd`/data/namesrv/store:/root/store --name rmqnamesrv  rocketmqinc/rocketmq-namesrv:4.5.0-alpine sh mqnamesrv

#运行broker容器
sudo docker run -d -p 10911:10911 -p 10909:10909 -v `pwd`/data/broker/logs:/root/logs -v `pwd`/data/broker/store:/root/store --name rmqbroker --link rmqnamesrv:namesrv -e "NAMESRV_ADDR=namesrv:9876" rocketmqinc/rocketmq-broker:4.5.0-alpine sh mqbroker -c ../conf/broker.conf
```

Please note that the **rocketmq-broker ip** is **pod ip**. If you want to modify this ip, you can set it your custom value in **broker.conf**。


By now, the deployment of eventmesh-store has finished, please go to the next step: [Start Eventmesh-Runtime Using Docker](02-runtime-with-docker.md)


## Reference
For more details about RocketMQ，please refer to <https://rocketmq.apache.org/docs/quick-start/>
