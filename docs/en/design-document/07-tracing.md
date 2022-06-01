# Distributed Tracing

## Overview of OpenTelemetry

OpenTelemetry is a collection of tools, APIs, and SDKs. You can use it to instrument, generate, collect, and export telemetry data (metrics, logs, and traces) for analysis in order to understand your software's performance and behavior.

## Requirements

- set tracer
- different exporter
- start and end span in server

## Design Details

- SpanProcessor: BatchSpanProcessor

- Exporter: log(default), would be changed from properties

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

## Problems

### How to set different exporter in class 'OpenTelemetryTraceFactory'? (Solved)

After I get the exporter type from properties, how to deal with it.

The 'logExporter' only needs to new it.

But the 'zipkinExporter' needs to new and use the "getZipkinExporter()" method.

## Solutions

### Solution of different exporter

Use reflection to get an exporter.

First of all, different exporter must implement the interface 'EventMeshExporter'.

Then we get the exporter name from the configuration and reflect to the class.

```java
//different spanExporter
String exporterName = configuration.eventMeshTraceExporterType;
//use reflection to get spanExporter
String className = String.format("org.apache.eventmesh.runtime.exporter.%sExporter",exporterName);
EventMeshExporter eventMeshExporter = (EventMeshExporter) Class.forName(className).newInstance();
spanExporter = eventMeshExporter.getSpanExporter(configuration);
```

Additional, this will surround with try catch.If the specified exporter cannot be obtained successfully, the default exporter log will be used instead

#### Improvement of different exporter

SPI (To be completed)

## Appendix

### References

<https://github.com/open-telemetry/docs-cn/blob/main/QUICKSTART.md>

<https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/netty>
