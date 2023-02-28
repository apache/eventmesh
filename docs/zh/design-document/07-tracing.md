# 分布式追踪 

## OpenTelemetry概述 

OpenTelemetry是一组API和SDK的工具，您可以使用它来仪器化、生成、收集和导出遥测数据（指标、日志和追踪），以便进行分析，以了解您的软件性能和行为。

## 需求

- 设置追踪器
- 不同的导出器
- 在服务器中开始和结束跨度

## 设计细节

- 跨度处理器：BatchSpanProcessor

- 导出器：默认为日志，可以从属性中更改

```java
// Configure the batch spans processor. This span processor exports span in batches.
BatchSpanProcessor batchSpansProcessor =
    BatchSpanProcessor.builder(exporter)
        .setMaxExportBatchSize(512) // set the maximum batch size to use
        .setMaxQueueSize(2048) // set the queue size. This must be >= the export batch size
        .setExporterTimeout(
            30, TimeUnit.SECONDS) // set the max amount of time an export can run before getting
        // interrupted
        .setScheduleDelay(5, TimeUnit.SECONDS) // set time between two different exports
        .build();
OpenTelemetrySdk.builder()
    .setTracerProvider(
        SdkTracerProvider.builder().addSpanProcessor(batchSpansProcessor).build())
    .build();
```

1. 当使用`EventMeshHTTPServer`类的`init()`方法时，类`AbstractHTTPServer`将获取跟踪器。

```java
super.openTelemetryTraceFactory = new OpenTelemetryTraceFactory(eventMeshHttpConfiguration);
super.tracer = openTelemetryTraceFactory.getTracer(this.getClass().toString());
super.textMapPropagator = openTelemetryTraceFactory.getTextMapPropagator();
```

2. 然后，在类`AbstractHTTPServer`中的跟踪将起作用。

## 问题

### 如何在类“OpenTelemetryTraceFactory”中设置不同的导出器？（已解决）

在从属性中获取导出器类型之后，如何处理它。

`logExporter`只需要创建新实例即可。

但是，“zipkinExporter”需要新建并使用“getZipkinExporter()”方法。

## 解决方案

### 不同导出器的解决方案

使用反射获取导出器。

首先，不同的导出器必须实现接口“EventMeshExporter”。

然后，我们从配置中获取导出器名称，并反射到该类。

```java
//different spanExporter
String exporterName = configuration.eventMeshTraceExporterType;
//use reflection to get spanExporter
String className = String.format("org.apache.eventmesh.runtime.exporter.%sExporter",exporterName);
EventMeshExporter eventMeshExporter = (EventMeshExporter) Class.forName(className).newInstance();
spanExporter = eventMeshExporter.getSpanExporter(configuration);
```

另外，这将包含try catch。如果无法成功获取指定的导出器，则将使用默认的日志导出器。

#### 不同导出器的改进

SPI（待完成）

## 附录

### 参考资料

- <https://github.com/open-telemetry/docs-cn/blob/main/QUICKSTART.md>

- <https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/netty>
