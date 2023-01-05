# Collect Trace with Zipkin

## Zipkin

Distributed tracing is a method used to profile and monitor applications built with microservices architecture. Distributed tracing helps pinpoint where failures occur and what causes poor performance.

[Zipkin](https://zipkin.io) is a distributed tracing system that helps collect timing data needed to troubleshoot latency problems in service architectures. EventMesh exposes a collection of trace data that could be collected and analyzed by Zipkin. Please follow [the "Zipkin Quickstart" tutorial](https://zipkin.io/pages/quickstart.html) to download and install the latest release of Zipkin.

## Configuration

To enable the trace exporter of EventMesh Runtime, set the `eventMesh.server.trace.enabled` field in the `conf/eventmesh.properties` file to `true`.

```conf
# Trace plugin
eventMesh.server.trace.enabled=true
eventMesh.trace.plugin=zipkin
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

To send the exported trace data to Zipkin, edit the `eventmesh.trace.zipkin.ip` and `eventmesh.trace.zipkin.port` fields in the `conf/zipkin.properties` file to match the configuration of the Zipkin server.

```conf
# Zipkin's IP and Port
eventmesh.trace.zipkin.ip=localhost
eventmesh.trace.zipkin.port=9411
```
