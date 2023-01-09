# Collect Trace with Jaeger

## Jaeger

[Jaeger](https://www.jaegertracing.io/), inspired by [Dapper](https://research.google.com/pubs/pub36356.html) and [OpenZipkin](https://zipkin.io/), is a distributed tracing platform created by [Uber Technologies](https://uber.github.io/) and donated to [Cloud Native Computing Foundation](https://cncf.io/). It can be used for monitoring microservices-based distributed systems. 

For the installation of Jaeger, you can refer to the [official documentation](https://www.jaegertracing.io/docs/latest/getting-started/) of Jaeger. It is recommended to use docker image `jaegertracing/all-in-one` to quickly build the environment for testing.

## Configuration

To enable the trace exporter of EventMesh Runtime, set the `eventMesh.server.trace.enabled` field in the `conf/eventmesh.properties` file to `true`.

```conf
# Trace plugin
eventMesh.server.trace.enabled=true
eventMesh.trace.plugin=jaeger
```

To customize the behavior of the trace exporter such as timeout or export interval, edit the `exporter.properties` file.

```conf
# Set the maximum batch size to use
eventmesh.trace.max.export.size=512
# Set the queue size. This must be >= the export batch size
eventmesh.trace.max.queue.size=2048
# Set the max amount of time an export can run before getting(TimeUnit=SECONDS)
eventmesh.trace.export.timeout=30
# Set time between two different exports (TimeUnit=SECONDS)
eventmesh.trace.export.interval=5
```

To send the exported trace data to Jaeger, edit the `eventmesh.trace.jaeger.ip` and `eventmesh.trace.jaeger.port` fields in the `conf/jaeger.properties` file to match the configuration of the Jaeger server.

```conf
# Jaeger's IP and Port
eventmesh.trace.jaeger.ip=localhost
eventmesh.trace.jaeger.port=14250
```

## Migrating from Zipkin

Collector service exposes Zipkin compatible REST API `/api/v1/spans` which accepts both Thrift and JSON. Also there is `/api/v2/spans` for JSON and Proto. 

So you can also use the `eventmesh-trace-zipkin` plugin to collect trace with Jaeger. Please refer to the `eventmesh-trace-zipkin` documentation for the specific configuration. By default this feature in Jaeger is disabled. It can be enabled with `--collector.zipkin.host-port=:9411`.