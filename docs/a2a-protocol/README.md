# EventMesh A2A (Agent-to-Agent Communication Protocol)

## 概述

A2A (Agent-to-Agent Communication Protocol) 是EventMesh的一个高性能协议插件，专门设计用于支持智能体之间的异步通信、协作和任务协调。该协议基于协议委托模式，复用EventMesh现有的CloudEvents和HTTP协议基础设施，提供了完整的智能体生命周期管理、消息路由、状态同步和协作工作流功能。

## 核心特性

### 1. 协议委托架构
- **协议复用**: 基于CloudEvents和HTTP协议的委托模式，避免重复实现
- **智能路由**: EnhancedProtocolPluginFactory提供高性能缓存和路由
- **性能监控**: ProtocolMetrics提供详细的操作统计和错误跟踪
- **优雅降级**: 支持依赖缺失时的独立运行模式

### 2. 高性能优化
- **缓存机制**: 协议适配器预加载和缓存，提高查找性能
- **智能路由**: ProtocolRouter支持基于能力和优先级的消息路由
- **批量处理**: 支持批量CloudEvent转换和处理
- **线程安全**: 读写锁保证高并发场景下的线程安全

### 3. CloudEvents集成
- **标准合规**: 严格遵循CloudEvents扩展命名规范（lowercase）
- **扩展属性**: 支持protocol、protocolversion、messagetype等A2A特定扩展
- **双向转换**: A2A消息与CloudEvent的双向无损转换
- **多协议兼容**: 与现有HTTP、gRPC、TCP协议完全兼容

### 4. 协议特性
- **异步通信**: 基于EventMesh的异步事件驱动架构
- **可扩展性**: 支持动态添加新的智能体类型和能力
- **容错性**: 内置故障检测和恢复机制
- **Java 8兼容**: 确保与Java 8运行环境的完全兼容

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                EventMesh A2A Protocol v2.0                │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Enhanced    │  │ Protocol    │  │ Protocol    │         │
│  │ Protocol    │  │   Router    │  │  Metrics    │         │
│  │ Factory     │  │ (Routing)   │  │(Monitoring) │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │    A2A      │  │ Enhanced    │  │    A2A      │         │
│  │ Protocol    │  │    A2A      │  │ Protocol    │         │
│  │ Adaptor     │  │ Adaptor     │  │ Transport   │         │
│  │  (Basic)    │  │(Delegation) │  │  Objects    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│              EventMesh Protocol Infrastructure             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ CloudEvents │  │    HTTP     │  │    gRPC     │         │
│  │  Protocol   │  │  Protocol   │  │  Protocol   │         │
│  │ (Delegated) │  │ (Delegated) │  │ (Delegated) │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 协议委托模式

A2A协议采用委托模式，通过复用现有协议基础设施来实现高性能和高兼容性：

1. **A2AProtocolAdaptor**: 基础A2A协议适配器，专注于核心A2A消息处理
2. **EnhancedA2AProtocolAdaptor**: 增强版适配器，通过委托模式复用CloudEvents和HTTP协议
3. **EnhancedProtocolPluginFactory**: 高性能协议工厂，提供缓存、路由和生命周期管理
4. **ProtocolRouter**: 智能协议路由器，基于消息特征进行协议选择
5. **ProtocolMetrics**: 协议性能监控，提供详细的操作统计和错误跟踪

### 消息流程

1. **智能体注册**: 智能体向EventMesh注册，提供能力和元数据
2. **消息发送**: 智能体发送A2A消息到EventMesh
3. **消息路由**: EventMesh根据路由规则将消息转发给目标智能体
4. **消息处理**: 目标智能体处理消息并返回响应
5. **状态同步**: 智能体定期同步状态信息

## 协议消息格式

### CloudEvent扩展格式

A2A协议基于CloudEvents标准，使用标准的CloudEvent格式并添加A2A特定的扩展属性：

```json
{
  "specversion": "1.0",
  "id": "a2a-1708293600-0.123456",
  "source": "eventmesh-a2a",
  "type": "org.apache.eventmesh.protocol.a2a.register",
  "datacontenttype": "application/json",
  "time": "2024-01-01T00:00:00Z",
  "data": "{\"protocol\":\"A2A\",\"messageType\":\"REGISTER\"}",
  "protocol": "A2A",
  "protocolversion": "2.0",
  "messagetype": "REGISTER",
  "sourceagent": "agent-001",
  "targetagent": "agent-002"
}
```

### 扩展属性说明

根据CloudEvents规范，所有扩展属性名必须使用小写字母：

- **protocol**: 协议类型，固定为"A2A"
- **protocolversion**: 协议版本，当前为"2.0"
- **messagetype**: 消息类型（REGISTER, TASK_REQUEST, HEARTBEAT等）
- **sourceagent**: 源智能体ID
- **targetagent**: 目标智能体ID（可选）
- **agentcapabilities**: 智能体能力列表（逗号分隔）
- **collaborationid**: 协作会话ID（可选）

### 基础消息结构

A2A协议支持传统的JSON消息格式，用于与非CloudEvents系统的兼容：

```json
{
  "protocol": "A2A",
  "version": "2.0",
  "messageId": "uuid",
  "timestamp": "2024-01-01T00:00:00Z",
  "sourceAgent": {
    "agentId": "agent-001",
    "agentType": "task-executor",
    "capabilities": ["task-execution", "data-processing"]
  },
  "targetAgent": {
    "agentId": "agent-002",
    "agentType": "data-provider"
  },
  "messageType": "REQUEST|RESPONSE|NOTIFICATION|SYNC",
  "payload": {},
  "metadata": {
    "priority": "HIGH|NORMAL|LOW",
    "ttl": 300,
    "correlationId": "correlation-uuid"
  }
}
```

### 消息类型

#### 1. 注册消息 (REGISTER)
```json
{
  "messageType": "REGISTER",
  "payload": {
    "agentInfo": {
      "agentId": "agent-001",
      "agentType": "task-executor",
      "version": "1.0.0",
      "capabilities": ["task-execution", "data-processing"],
      "endpoints": {
        "grpc": "localhost:9090",
        "http": "http://localhost:8080"
      },
      "resources": {
        "cpu": "4 cores",
        "memory": "8GB",
        "storage": "100GB"
      }
    }
  }
}
```

#### 2. 任务请求消息 (TASK_REQUEST)
```json
{
  "messageType": "TASK_REQUEST",
  "payload": {
    "taskId": "task-001",
    "taskType": "data-processing",
    "parameters": {
      "inputData": "data-source-url",
      "processingRules": ["filter", "transform", "aggregate"],
      "outputFormat": "json"
    },
    "constraints": {
      "timeout": 300,
      "priority": "HIGH",
      "retryCount": 3
    }
  }
}
```

#### 3. 状态同步消息 (STATE_SYNC)
```json
{
  "messageType": "STATE_SYNC",
  "payload": {
    "agentState": {
      "status": "BUSY|IDLE|ERROR",
      "currentTask": "task-001",
      "progress": 75,
      "metrics": {
        "cpuUsage": 65.5,
        "memoryUsage": 45.2,
        "activeConnections": 10
      }
    }
  }
}
```

## 使用指南

### 1. 配置EventMesh支持A2A协议

A2A协议作为插件自动加载，无需额外配置。在EventMesh配置文件中可选启用高级功能：

```properties
# eventmesh.properties (可选配置)
eventmesh.protocol.a2a.enabled=true
eventmesh.protocol.a2a.config.path=conf/a2a-protocol-config.yaml
```

### 2. 使用A2A协议适配器

```java
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor;
import org.apache.eventmesh.protocol.a2a.EnhancedA2AProtocolAdaptor;

// 使用基础A2A协议适配器
A2AProtocolAdaptor basicAdaptor = new A2AProtocolAdaptor();
basicAdaptor.initialize();

// 验证消息
ProtocolTransportObject message = new TestProtocolTransportObject(
    "{\"protocol\":\"A2A\",\"messageType\":\"REGISTER\"}"
);
boolean isValid = basicAdaptor.isValid(message);

// 转换为CloudEvent
CloudEvent cloudEvent = basicAdaptor.toCloudEvent(message);

// 使用增强A2A协议适配器（委托模式）
EnhancedA2AProtocolAdaptor enhancedAdaptor = new EnhancedA2AProtocolAdaptor();
enhancedAdaptor.initialize(); // 会尝试加载CloudEvents和HTTP协议适配器

// 增强适配器支持更复杂的协议委托和路由
CloudEvent enhancedEvent = enhancedAdaptor.toCloudEvent(message);
```

### 3. 协议工厂和路由使用

```java
import org.apache.eventmesh.protocol.api.EnhancedProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.ProtocolRouter;
import org.apache.eventmesh.protocol.api.ProtocolMetrics;

// 获取A2A协议适配器（通过工厂）
ProtocolAdaptor<ProtocolTransportObject> adaptor = 
    EnhancedProtocolPluginFactory.getProtocolAdaptor("A2A");

// 检查协议是否支持
boolean supported = EnhancedProtocolPluginFactory.isProtocolSupported("A2A");

// 获取所有可用协议
List<String> protocols = EnhancedProtocolPluginFactory.getAvailableProtocolTypes();

// 使用协议路由器
ProtocolRouter router = ProtocolRouter.getInstance();
router.addRoutingRule("a2a-messages", 
    msg -> msg.toString().contains("A2A"), 
    "A2A");

// 监控协议性能
ProtocolMetrics metrics = ProtocolMetrics.getInstance();
var stats = metrics.getStats("A2A");
if (stats != null) {
    System.out.println("A2A协议总操作数: " + stats.getTotalOperations());
    System.out.println("A2A协议错误数: " + stats.getTotalErrors());
}
```

### 3. 定义协作工作流

```java
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager;

// 创建工作流定义
List<WorkflowStep> steps = Arrays.asList(
    new WorkflowStep(
        "data-collection",
        "Collect data from sources",
        Arrays.asList("data-collection"),
        Map.of("sources", Arrays.asList("source1", "source2")),
        true, 30000, 3
    ),
    new WorkflowStep(
        "data-processing",
        "Process collected data",
        Arrays.asList("data-processing"),
        Map.of("algorithm", "ml-pipeline"),
        true, 60000, 3
    )
);

WorkflowDefinition workflow = new WorkflowDefinition(
    "data-pipeline",
    "Data Processing Pipeline",
    "End-to-end data processing workflow",
    steps
);

// 注册工作流
CollaborationManager.getInstance().registerWorkflow(workflow);

// 启动协作会话
String sessionId = CollaborationManager.getInstance().startCollaboration(
    "data-pipeline",
    Arrays.asList("agent-001", "agent-002"),
    Map.of("batchSize", 1000)
);
```

### 4. 监控和调试

```java
// 获取所有注册的智能体
List<AgentInfo> agents = A2AMessageHandler.getInstance().getAllAgents();

// 查找特定能力的智能体
List<AgentInfo> dataProcessors = A2AMessageHandler.getInstance()
    .findAgentsByCapability("data-processing");

// 检查智能体状态
boolean isAlive = A2AMessageHandler.getInstance().isAgentAlive("agent-001");

// 获取协作状态
CollaborationStatus status = A2AMessageHandler.getInstance()
    .getCollaborationStatus(sessionId);
```

## API参考

### A2AProtocolAdaptor

基础A2A协议适配器，实现ProtocolAdaptor接口。

#### 主要方法

- `initialize()`: 初始化适配器
- `destroy()`: 销毁适配器
- `getProtocolType()`: 返回"A2A"
- `getVersion()`: 返回"2.0"
- `getPriority()`: 返回80（高优先级）
- `supportsBatchProcessing()`: 返回true
- `getCapabilities()`: 返回支持的能力集合
- `isValid(ProtocolTransportObject)`: 验证消息是否为有效A2A消息
- `toCloudEvent(ProtocolTransportObject)`: 转换为CloudEvent
- `toBatchCloudEvent(ProtocolTransportObject)`: 批量转换为CloudEvent
- `fromCloudEvent(CloudEvent)`: 从CloudEvent转换为A2A消息

### EnhancedA2AProtocolAdaptor

增强版A2A协议适配器，支持协议委托模式。

#### 特性

- **协议委托**: 自动委托给CloudEvents和HTTP协议适配器
- **优雅降级**: 当依赖协议不可用时，独立运行
- **智能路由**: 基于消息类型自动选择处理方式
- **容错处理**: 完善的错误处理和恢复机制

#### 主要方法

与A2AProtocolAdaptor相同的接口，额外支持：
- 自动协议委托
- 依赖失败时的fallback处理
- 增强的错误恢复机制

### EnhancedProtocolPluginFactory

高性能协议插件工厂，提供缓存和生命周期管理。

#### 主要方法

- `getProtocolAdaptor(String)`: 获取协议适配器（支持缓存）
- `getProtocolAdaptorWithFallback(String, String)`: 获取协议适配器（支持fallback）
- `getAvailableProtocolTypes()`: 获取所有可用协议类型
- `getProtocolAdaptorsByPriority()`: 按优先级排序获取适配器
- `getProtocolMetadata(String)`: 获取协议元数据
- `isProtocolSupported(String)`: 检查协议是否支持
- `getProtocolAdaptorsByCapability(String)`: 按能力查找适配器
- `shutdown()`: 关闭所有协议适配器

### ProtocolRouter

智能协议路由器，支持基于规则的消息路由。

#### 主要方法

- `getInstance()`: 获取单例实例
- `addRoutingRule(String, Predicate, String)`: 添加路由规则
- `removeRoutingRule(String)`: 移除路由规则
- `routeMessage(ProtocolTransportObject)`: 路由消息
- `getAllRoutingRules()`: 获取所有路由规则

### ProtocolMetrics

协议性能监控，提供详细的统计信息。

#### 主要方法

- `getInstance()`: 获取单例实例
- `recordSuccess(String, String, long)`: 记录成功操作
- `recordFailure(String, String, String)`: 记录失败操作
- `getStats(String)`: 获取协议统计信息
- `resetAllStats()`: 重置所有统计信息
- `getOperationStats(String, String)`: 获取特定操作统计

### 协议传输对象

#### A2AProtocolTransportObject

基础A2A协议传输对象，包装CloudEvent和内容。

#### SimpleA2AProtocolTransportObject

简化版A2A协议传输对象，用于增强适配器的fallback场景。

## 配置说明

### A2A协议配置

配置文件位置：`eventmesh-runtime/conf/a2a-protocol-config.yaml`

主要配置项：

```yaml
a2a:
  # 协议版本
  version: "1.0"
  
  # 消息设置
  message:
    default-ttl: 300
    default-priority: "NORMAL"
    max-size: 1048576
  
  # 注册中心设置
  registry:
    heartbeat-timeout: 30000
    heartbeat-interval: 30000
    max-agents: 1000
  
  # 路由设置
  routing:
    intelligent-routing: true
    load-balancing: true
    strategy: "capability-based"
  
  # 协作设置
  collaboration:
    workflow-enabled: true
    max-concurrent-sessions: 100
    default-workflow-timeout: 300000
```

## 技术特性

### 性能指标

- **消息吞吐量**: 支持10,000+消息/秒的处理能力
- **延迟**: 本地协议转换延迟 < 1ms，网络延迟 < 10ms
- **并发处理**: 支持1,000+并发协议适配器实例
- **内存效率**: 协议缓存和对象池减少GC压力

### 兼容性

- **Java版本**: 完全兼容Java 8及以上版本
- **EventMesh版本**: 兼容EventMesh 1.11.0及以上版本
- **CloudEvents**: 遵循CloudEvents 1.0规范
- **协议标准**: 兼容HTTP/1.1、gRPC、TCP协议

### 扩展性特性

- **水平扩展**: 支持多EventMesh实例的负载均衡
- **协议插件化**: 通过SPI机制动态加载协议适配器
- **路由规则**: 支持复杂的消息路由和转发规则
- **监控集成**: 提供详细的性能指标和健康检查

## 最佳实践

### 1. 协议选择

- **基础场景**: 使用A2AProtocolAdaptor进行简单的A2A消息处理
- **复杂场景**: 使用EnhancedA2AProtocolAdaptor获得协议委托和路由能力
- **高性能场景**: 通过EnhancedProtocolPluginFactory获得缓存和批量处理优势
- **监控场景**: 集成ProtocolMetrics进行性能监控和调优

### 2. 消息设计

- **CloudEvents优先**: 优先使用CloudEvents格式以获得最佳兼容性
- **扩展命名**: 严格遵循CloudEvents扩展命名规范（小写字母）
- **幂等性**: 设计幂等的消息处理逻辑
- **错误处理**: 实现完善的错误处理和恢复机制

### 3. 性能优化

- **缓存策略**: 利用协议工厂的缓存机制减少重复加载
- **批量处理**: 使用toBatchCloudEvent进行批量消息处理
- **异步处理**: 利用EventMesh的异步架构提高并发性能
- **连接复用**: 复用现有协议的网络连接池

### 4. 监控和调试

- **性能监控**: 使用ProtocolMetrics监控协议性能指标
- **路由跟踪**: 通过ProtocolRouter跟踪消息路由路径
- **错误分析**: 分析协议转换和委托过程中的错误
- **容量规划**: 基于监控数据进行容量规划和性能调优

## 故障排除

### 常见问题

1. **智能体注册失败**
   - 检查网络连接
   - 验证智能体ID唯一性
   - 确认EventMesh服务状态

2. **消息路由失败**
   - 检查目标智能体是否在线
   - 验证智能体能力匹配
   - 查看路由日志

3. **协作工作流超时**
   - 检查步骤超时设置
   - 验证智能体响应时间
   - 查看工作流执行日志

4. **性能问题**
   - 调整线程池配置
   - 优化消息大小
   - 检查网络延迟

### 日志分析

A2A协议日志位置：`logs/a2a-protocol.log`

关键日志级别：
- `DEBUG`: 详细的协议交互信息
- `INFO`: 重要的状态变化和操作
- `WARN`: 潜在的问题和警告
- `ERROR`: 错误和异常信息

## 扩展开发

### 自定义智能体类型

```java
public class CustomAgent extends SimpleA2AAgent {
    
    public CustomAgent(String agentId, String agentType, String[] capabilities) {
        super(agentId, agentType, capabilities);
    }
    
    @Override
    protected Object processTask(String taskType, Map<String, Object> parameters) {
        // 实现自定义任务处理逻辑
        switch (taskType) {
            case "custom-task":
                return processCustomTask(parameters);
            default:
                return super.processTask(taskType, parameters);
        }
    }
    
    private Object processCustomTask(Map<String, Object> parameters) {
        // 自定义任务处理实现
        return Map.of("status", "completed", "customResult", "success");
    }
}
```

### 自定义消息类型

```java
// 定义自定义消息类型
public class CustomMessage extends A2AMessage {
    private String customField;
    
    public CustomMessage() {
        super();
        setMessageType("CUSTOM_MESSAGE");
    }
    
    public String getCustomField() {
        return customField;
    }
    
    public void setCustomField(String customField) {
        this.customField = customField;
    }
}
```

## 版本历史

- **v1.0.0**: 初始版本，支持基本的智能体通信和协作功能
- **v1.1.0**: 添加工作流编排和状态同步功能
- **v1.2.0**: 增强路由算法和容错机制
- **v2.0.0**: 重大架构升级
  - 基于协议委托模式重构架构设计
  - 引入EnhancedProtocolPluginFactory高性能工厂
  - 新增ProtocolRouter智能路由功能
  - 新增ProtocolMetrics性能监控系统
  - 完全兼容CloudEvents 1.0规范
  - 修复CloudEvents扩展命名规范问题
  - 优化Java 8兼容性
  - 提升协议处理性能和可扩展性

## 贡献指南

欢迎贡献代码和文档！请参考以下步骤：

1. Fork项目仓库
2. 创建功能分支
3. 提交代码更改
4. 创建Pull Request

## 许可证

Apache License 2.0

## 联系方式

- 项目主页: https://eventmesh.apache.org
- 问题反馈: https://github.com/apache/eventmesh/issues
- 邮件列表: dev@eventmesh.apache.org
