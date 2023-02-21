# Guidelines of eventmesh-store

## Dependencies

```text
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

### Start Name Server

```console
nohup sh bin/mqnamesrv &
tail -f ~/logs/rocketmqlogs/namesrv.log
```

### Start Broker

```console
nohup sh bin/mqbroker -n localhost:9876 &
tail -f ~/logs/rocketmqlogs/broker.log
```

The deployment of eventmesh-store has finished, please go to the next step: [Start Eventmesh-Runtime](02-runtime.md)

## Reference

For more details about RocketMQ, please refer to <https://rocketmq.apache.org/docs/quick-start/>
