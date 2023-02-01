# 通过 Jaeger 观察 Trace

## Jaeger

[Jaeger](https://www.jaegertracing.io/) 是 [Uber](https://uber.github.io/) 开发的分布式跟踪系统，现已成为 [CNCF](https://cncf.io/) 开源项目，其灵感来源于 Google 的 [Dapper](https://research.google.com/pubs/pub36356.html) 和 Twitter 的 [Zipkin](https://zipkin.io/)，用于监控基于微服务的分布式系统。

Jaeger 的安装可以参考[官方文档](https://www.jaegertracing.io/docs/latest/getting-started/)，推荐使用官方的 Docker 镜像 `jaegertracing/all-in-one` 来快速搭建环境进行测试。

## 配置

为了启用 EventMesh Runtime 的 trace exporter，请将 `conf/eventmesh.properties` 文件中的 `eventMesh.server.trace.enabled` 字段设置为 true。

```conf
# Trace plugin
eventMesh.server.trace.enabled=true
eventMesh.trace.plugin=jaeger
```

为了定义 trace exporter 的行为，如超时时间或导出间隔，请编辑 `exporter.properties` 文件。

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

为了将导出的 trace 数据发送到 Jaeger，请编辑 `conf/jaeger.properties` 文件中的 `eventmesh.trace.jaeger.ip` 和 `eventmesh.trace.jaeger.port` 字段，来匹配 Jaeger 服务器的配置。

```conf
# Jaeger's IP and Port
eventmesh.trace.jaeger.ip=localhost
eventmesh.trace.jaeger.port=14250
```

## 从 Zipkin 迁移

Jaeger 采集器服务暴露了与 Zipkin 兼容的 REST API，`/api/v1/spans` 可以接收 Thrift 和 JSON，`/api/v2/spans` 可以接收 JSON 和 Proto。

因此你也可以使用 `eventmesh-trace-zipkin` 插件来通过 Jaeger 观察 trace，具体配置细节请参考 `eventmesh-trace-zipkin` 的文档。默认情况下这个特性在 Jaeger 中是关闭的，可以通过 `--collector.zipkin.host-port=:9411` 启用。