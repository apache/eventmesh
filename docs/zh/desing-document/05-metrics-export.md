# EventMesh 指标（OpenTelemetry 和 Prometheus

## 介绍

[EventMesh(incubating)](https://github.com/apache/incubator-eventmesh) 是一个动态的云原生事件基础设施.

## OpenTelemetry 概述

OpenTelemetry 是工具、API 和 SDK 的集合。您可以使用它来检测、生成、收集和导出遥测数据（指标、日志和跟踪）以进行分析，以便了解您的软件的性能和行为。

## 概述 Prometheus

使用领先的开源监控解决方案为您的指标和警报提供支持。

- 尺寸数据
- 强大的查询
- 伟大的可视化
- 高效存储
- 操作简单
- 精准预警
- 许多客户端库
- 许多集成

## 要求

### 功能要求

| Requirement ID | Requirement Description                                      | Comments      |
| :------------- | ------------------------------------------------------------ | ------------- |
| F-1            | EventMesh users should be able to observe HTTP metrics from Prometheus | Functionality |
| F-2            | EventMesh users should be able to observe TCP metrics from Prometheus | Functionality |

## 设计 细节

使用由提供的儀表儀器 OpenTelemetry 觀察指標存在於 EventMesh 然後導出到 Prometheus.

1、初始化儀表儀器

2、設置 Prometheus 服務器

3、创建了不同的指标观察者

## 附录

### 参考资料

<https://github.com/open-telemetry/docs-cn/blob/main/QUICKSTART.md#%E5%88%9B%E5%BB%BA%E5%9F%BA%E7%A1%80Span>
