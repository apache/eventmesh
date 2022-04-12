# Observe trace through Zipkin

## Download and run Zipkin

Reference:<https://zipkin.io/pages/quickstart.html>

## Run EventMesh

run eventmesh-starter(reference [eventmesh-runtime-quickstart](eventmesh-runtime-quickstart.md))

run eventmesh-example(reference [eventmesh-sdk-java-quickstart](eventmesh-sdk-java-quickstart.md))

## Related settings

In eventmesh-runtime/conf/eventmesh.properties：

The default exporter is log, which needs to be manually changed to Zipkin

```properties
#trace exporter
eventmesh.trace.exporter.type=Zipkin
```

Here are various configurations of Zipkin

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

The above are related configurations. If you are familiar with Zipkin, you can modify it yourself.

## Observe

Open browser access： **http://localhost:9411**
