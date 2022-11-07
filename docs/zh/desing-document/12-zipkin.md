# 通过 Zipkin 观察 Trace

### 1、下载和运行Zipkin

请参考https://zipkin.io/pages/quickstart.html



### 2、运行eventmesh

运行eventmesh-starter(参考[eventmesh-runtime-quickstart](eventmesh-runtime-quickstart.md))

运行eventmesh-example(参考[eventmesh-sdk-java-quickstart](eventmesh-sdk-java-quickstart.md))



### 3、相关的设置

eventmesh-runtime/conf/eventmesh.properties中：

默认的exporter是log，需要手动改成Zipkin

```properties
#trace exporter
eventmesh.trace.exporter.type=Zipkin
```
下面是关于Zipkin的各种配置
```properties
#set the maximum batch size to use
eventmesh.trace.exporter.max.export.size=512
#set the queue size. This must be >= the export batch size
eventmesh.trace.exporter.max.queue.size=2048
#set the max amount of time an export can run before getting(TimeUnit=SECONDS)
eventmesh.trace.exporter.export.timeout=30
#set time between two different exports(TimeUnit=SECONDS)
eventmesh.trace.exporter.export.interval=5

#zipkin
eventmesh.trace.export.zipkin.ip=localhost
eventmesh.trace.export.zipkin.port=9411
```

以上都是相关的配置，如果你十分熟悉Zipkin的话可以自行修改。



### 4、观察

浏览器打开： **localhost:9411**
