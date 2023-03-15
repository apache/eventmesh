分布式追踪
OpenTelemetry 概述
OpenTelemetry 是工具、API 和 SDK 的集合。 您可以使用它来检测、生成、收集和导出遥测数据（指标、日志和跟踪）以进行分析，以便了解您的软件的性能和行为。

要求
设置示踪剂
不同的出口商
服务器中的开始和结束跨度
设计细节
SpanProcessor：BatchSpanProcessor

导出器：日志（默认），将从属性中更改

// 配置批处理跨度处理器。 此跨度处理器批量导出跨度。
BatchSpanProcessor batchSpansProcessor =
     BatchSpanProcessor.builder（出口商）
         .setMaxExportBatchSize(512) // 设置要使用的最大批量大小
         .setMaxQueueSize(2048) // 设置队列大小。 这必须 >= 导出批量大小
         .setExporterTimeout(
             30, TimeUnit.SECONDS) // 设置导出在获取之前可以运行的最长时间
         // 中断
         .setScheduleDelay(5, TimeUnit.SECONDS) // 设置两次不同导出之间的时间
         。建造（）;
OpenTelemetrySdk.builder()
     .setTracerProvider(
         SdkTracerProvider.builder().addSpanProcessor(batchSpansProcessor).build())
     。建造（）;
当使用“EventMeshHTTPServer”类的“init()”方法时，“AbstractHTTPServer”类将获取跟踪器
super.openTelemetryTraceFactory = new OpenTelemetryTraceFactory(eventMeshHttpConfiguration);
super.tracer = openTelemetryTraceFactory.getTracer(this.getClass().toString());
super.textMapPropagator = openTelemetryTraceFactory.getTextMapPropagator();
那么“AbstractHTTPServer”类中的跟踪将起作用。
问题
如何在“OpenTelemetryTraceFactory”类中设置不同的导出器？ （解决了）
从属性中获取导出器类型后，如何处理它。

'logExporter' 只需要更新它。

但是“zipkinExporter”需要新建并使用“getZipkinExporter()”方法。

解决方案
不同出口商的解决方案
使用反射来获取导出器。

首先，不同的导出器必须实现接口“EventMeshExporter”。

然后我们从配置中获取导出器名称并反映到类中。

//不同的spanExporter
String exporterName = configuration.eventMeshTraceExporterType;
//使用反射获取spanExporter
String className = String.format("org.apache.eventmesh.runtime.exporter.%sExporter",exporterName);
EventMeshExporter eventMeshExporter = (EventMeshExporter) Class.forName(className).newInstance();
spanExporter = eventMeshExporter.getSpanExporter(配置);
另外，这将用try catch包围。如果无法成功获取指定的exporter，将使用默认的exporter日志代替

改进不同的出口商
SPI（待完成）

附录
参考
https://github.com/open-telemetry/docs-cn/blob/main/QUICKSTART.md

https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/netty
Fēnbù shì zhuīzōng
OpenTelemetry gàishù
OpenTelemetry shì gōngjù,API hé SDK de jíhé. Nín kěyǐ shǐyòng tā lái jiǎncè, shēngchéng, shōují hé dǎochū yáocè shùjù (zhǐbiāo, rìzhì hé gēnzōng) yǐ jìn háng fēnxī, yǐbiàn liǎojiě nín de ruǎnjiàn dì xìngnéng hé xíngwéi.

Yāoqiú
shèzhì shì zōng jì
bùtóng de chūkǒu shāng
fúwùqì zhōng de kāishǐ hé jiéshù kuàdù
shèjì xìjié
SpanProcessor:BatchSpanProcessor

dǎochū qì: Rìzhì (mòrèn), jiāng cóng shǔxìng zhòng gēnggǎi

// pèizhì pī chǔlǐ kuàdù chǔlǐ qì. Cǐ kuàdù chǔlǐ qì pīliàng dǎochū kuàdù.
BatchSpanProcessor batchSpansProcessor =
    BatchSpanProcessor.Builder(chūkǒu shāng)
        .SetMaxExportBatchSize(512)// shèzhì yào shǐyòng de zuìdà pīliàng dàxiǎo
        .SetMaxQueueSize(2048)// shèzhì duìliè dàxiǎo. Zhè bìxū >= dǎochū pīliàng dàxiǎo
        .SetExporterTimeout(
            30, TimeUnit.SECONDS)// shèzhì dǎochū zài huòqǔ zhīqián kěyǐ yùnxíng de zuì cháng shíjiān
        // zhōngduàn
        .SetScheduleDelay(5, TimeUnit.SECONDS)// shèzhì liǎng cì bùtóng dǎochū zhī jiān de shíjiān
        . Jiànzào ();
OpenTelemetrySdk.Builder()
    .SetTracerProvider(
        SdkTracerProvider.Builder().AddSpanProcessor(batchSpansProcessor).Build())
    . Jiànzào ();
dāng shǐyòng “EventMeshHTTPServer” lèi de “init()” fāngfǎ shí,“AbstractHTTPServer” lèi jiāng huòqǔ gēnzōng qì
super.OpenTelemetryTraceFactory = new OpenTelemetryTraceFactory(eventMeshHttpConfiguration);
super.Tracer = openTelemetryTraceFactory.GetTracer(this.GetClass().ToString());
super.TextMapPropagator = openTelemetryTraceFactory.GetTextMapPropagator();
nàme “AbstractHTTPServer” lèi zhōng de gēnzōng jiāng qǐ zuòyòng.
Wèntí
rúhé zài “OpenTelemetryTraceFactory” lèi zhōng shèzhì bùtóng de dǎochū qì? (Jiějuéle)
cóng shǔxìng zhòng huòqǔ dǎochū qì lèixíng hòu, rúhé chǔlǐ tā.

'LogExporter' zhǐ xūyào gēngxīn tā.

Dànshì “zipkinExporter” xūyào xīnjiàn bìng shǐyòng “getZipkinExporter()” fāngfǎ.

Jiějué fāng'àn
bùtóng chūkǒu shāng de jiějué fāng'àn
shǐyòng fǎnshè lái huòqǔ dǎochū qì.

Shǒuxiān, bùtóng de dǎochū qì bìxū shíxiàn jiēkǒu “EventMeshExporter”.

Ránhòu wǒmen cóng pèizhì zhōng huòqǔ dǎochū qì míngchēng bìng fǎnyìng dào lèi zhōng.

//Bùtóng de spanExporter
String exporterName = configuration.EventMeshTraceExporterType;
//shǐyòng fǎnshè huòqǔ spanExporter
String className = String.Format("org.Apache.Eventmesh.Runtime.Exporter.%SExporter",exporterName);
EventMeshExporter eventMeshExporter = (EventMeshExporter) Class.ForName(className).NewInstance();
spanExporter = eventMeshExporter.GetSpanExporter(pèizhì);
lìngwài, zhè jiāng yòng try catch bāowéi. Rúguǒ wúfǎ chénggōng huòqǔ zhǐdìng de exporter, jiāng shǐyòng mòrèn de exporter rìzhì dàitì

gǎijìn bùtóng de chūkǒu shāng
SPI(dài wánchéng)

fùlù
cānkǎo
https://Github.Com/open-telemetry/docs-cn/blob/main/QUICKSTART.Md

https://Github.Com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/netty
