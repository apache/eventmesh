使用 Prometheus 观察指标
普罗米修斯
Prometheus 是一个开源系统监控和警报工具包，用于收集指标并将其存储为时间序列数据。 EventMesh 公开了一组可以由 Prometheus 抓取和分析的指标数据。 请按照“使用 Prometheus 的第一步”教程下载并安装最新版本的 Prometheus。

编辑普罗米修斯配置
eventmesh-runtime/conf/prometheus.yml 配置文件指定指标 HTTP 端点的端口。 默认指标端口为 19090。

eventMesh.metrics.prometheus.port=19090
请参考 Prometheus 配置指南将 EventMesh 指标添加为配置文件中的抓取目标。 这是创建名为 eventmesh 和端点 http://localhost:19090 的作业的最低配置：

抓取配置：
   - job_name：“eventmesh”
     静态配置：
       - 目标：[“本地主机：19090”]
请导航到 Prometheus 仪表板（例如 http://localhost:9090）以查看 EventMesh 导出的指标列表，这些指标以 eventmesh_ 为前缀。
