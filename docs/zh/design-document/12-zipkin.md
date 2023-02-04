使用 Zipkin 收集痕迹
齐普金
分布式跟踪是一种用于分析和监控使用微服务架构构建的应用程序的方法。 分布式跟踪有助于查明发生故障的位置以及导致性能不佳的原因。

Zipkin 是一种分布式跟踪系统，可帮助收集解决服务架构中的延迟问题所需的计时数据。 EventMesh 公开了可以由 Zipkin 收集和分析的跟踪数据集合。 请按照“Zipkin 快速入门”教程下载并安装最新版本的 Zipkin。

配置
要启用 EventMesh Runtime 的跟踪导出器，请将 conf/eventmesh.properties 文件中的 eventMesh.server.trace.enabled 字段设置为 true。

# 跟踪插件
eventMesh.server.trace.enabled=true
eventMesh.trace.plugin=zipkin
要自定义跟踪导出器的行为（例如超时或导出间隔），请编辑 exporter.properties 文件。

# 设置要使用的最大批量大小
eventmesh.trace.max.export.size=512
# 设置队列大小。 这必须 >= 导出批量大小
eventmesh.trace.max.queue.size=2048
# 设置导出在获取之前可以运行的最长时间(TimeUnit=SECONDS)
eventmesh.trace.export.timeout=30
# 设置两次不同导出之间的时间 (TimeUnit=SECONDS)
eventmesh.trace.export.interval=5
要将导出的跟踪数据发送到 Zipkin，请编辑 conf/zipkin.properties 文件中的 eventmesh.trace.zipkin.ip 和 eventmesh.trace.zipkin.port 字段以匹配 Zipkin 服务器的配置。

# Zipkin的IP和端口
eventmesh.trace.zipkin.ip=localhost
eventmesh.trace.zipkin.port=9411
