# eventMesh-HTTP-trace-design

## Introduction

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) is a dynamic cloud-native eventing infrastructure.

## An overview of OpenTelemetry

OpenTelemetry is a collection of tools, APIs, and SDKs. You can use it to instrument, generate, collect, and export telemetry data (metrics, logs, and traces) for analysis in order to understand your software's performance and behavior.

## Requirements

- set tracer
- different exporter
- start and end span in server

## Design Details

* SpanProcessor:   BatchSpanProcessor

* Exporter:  log(default), would be changed from properties

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

1. When using the method 'init()' of the class "EventMeshHTTPServer", the class "AbstractHTTPServer” will get the tracer

```java
super.openTelemetryTraceFactory = new OpenTelemetryTraceFactory(eventMeshHttpConfiguration);
super.tracer = openTelemetryTraceFactory.getTracer(this.getClass().toString());
super.textMapPropagator = openTelemetryTraceFactory.getTextMapPropagator();
```

2. then the trace in class "AbstractHTTPServer” will work.

## Problem

How to set different exporter in class 'OpenTelemetryTraceFactory'?

After I get the exporter type from properties, how to deal with it.

The 'logExporter' only needs to new it.

But the 'zipkinExporter' needs to new and use the "getZipkinExporter()" method.

## Appendix

#### References

https://github.com/open-telemetry/docs-cn/blob/main/QUICKSTART.md

https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/netty
