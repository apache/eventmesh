# EventMesh Metrics (OpenTelemetry and Prometheus)

## Introduction

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) is a dynamic cloud-native eventing infrastructure.

## An overview of OpenTelemetry

OpenTelemetry is a collection of tools, APIs, and SDKs. You can use it to instrument, generate, collect, and export telemetry data (metrics, logs, and traces) for analysis in order to understand your software's performance and behavior.

## An overview of  Prometheus

Power your metrics and alerting with a leading open-source monitoring solution.

- Dimensional data
- Powerful queries
- Great visualization
- Efficient storage
- Simple operation
- Precise alerting
- Many client libraries
- Many integrations

## Requirements

### Functional Requirements

| Requirement ID | Requirement Description                                      | Comments      |
| :------------- | ------------------------------------------------------------ | ------------- |
| F-1            | EventMesh users should be able to observe HTTP metrics from Prometheus | Functionality |
| F-2            | EventMesh users should be able to observe TCP metrics from Prometheus | Functionality |

## Design Details

use the meter instrument provided by OpenTelemetry to observe the metrics exist in EventMesh then export to Prometheus.

1、Initialize a meter instrument

2、set the Prometheus server

3、different metrics observer built

## Appendix

### References

<https://github.com/open-telemetry/docs-cn/blob/main/QUICKSTART.md#%E5%88%9B%E5%BB%BA%E5%9F%BA%E7%A1%80Span>
