# EventMesh A2A (Agent-to-Agent Communication Protocol)

## 概述

A2A (Agent-to-Agent Communication Protocol) 是EventMesh的一个扩展协议，专门设计用于支持智能体之间的异步通信、协作和任务协调。该协议基于EventMesh的事件驱动架构，提供了完整的智能体生命周期管理、消息路由、状态同步和协作工作流功能。

## 核心特性

### 1. 智能体管理
- **自动注册与发现**: 智能体可以自动注册到EventMesh，并支持基于能力和类型的发现
- **心跳监控**: 实时监控智能体状态，自动检测离线智能体
- **元数据管理**: 维护智能体的能力、资源和使用情况信息

### 2. 消息路由
- **智能路由**: 基于智能体能力和负载的智能消息路由
- **容错机制**: 支持备用智能体路由和故障转移
- **负载均衡**: 在多个可用智能体之间分发任务

### 3. 协作管理
- **工作流编排**: 支持复杂的多智能体协作工作流
- **任务协调**: 自动协调智能体间的任务分配和执行
- **状态同步**: 实时同步智能体状态和协作进度

### 4. 协议特性
- **异步通信**: 基于EventMesh的异步事件驱动架构
- **可扩展性**: 支持动态添加新的智能体类型和能力
- **容错性**: 内置故障检测和恢复机制

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    EventMesh A2A Protocol                   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   Agent     │  │  Message    │  │Collaboration│         │
│  │  Registry   │  │   Router    │  │  Manager    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   A2A       │  │   A2A       │  │   A2A       │         │
│  │ Protocol    │  │  Message    │  │ Protocol    │         │
│  │  Adaptor    │  │  Handler    │  │ Processor   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                    EventMesh Core                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │    HTTP     │  │    gRPC     │  │   TCP       │         │
│  │  Protocol   │  │  Protocol   │  │  Protocol   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 消息流程

1. **智能体注册**: 智能体向EventMesh注册，提供能力和元数据
2. **消息发送**: 智能体发送A2A消息到EventMesh
3. **消息路由**: EventMesh根据路由规则将消息转发给目标智能体
4. **消息处理**: 目标智能体处理消息并返回响应
5. **状态同步**: 智能体定期同步状态信息

## 协议消息格式

### 基础消息结构

```json
{
  "protocol": "A2A",
  "version": "1.0",
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

在EventMesh配置文件中启用A2A协议：

```yaml
# eventmesh.properties
eventmesh.protocol.a2a.enabled=true
eventmesh.protocol.a2a.config.path=conf/a2a-protocol-config.yaml
```

### 2. 创建智能体客户端

```java
import org.apache.eventmesh.examples.a2a.SimpleA2AAgent;

// 创建智能体
SimpleA2AAgent agent = new SimpleA2AAgent(
    "my-agent-001",
    "task-executor",
    new String[]{"data-processing", "image-analysis"}
);

// 启动智能体
agent.start();

// 发送任务请求
agent.sendTaskRequest(
    "target-agent-002",
    "data-processing",
    Map.of("inputData", "data-url", "outputFormat", "json")
);

// 停止智能体
agent.stop();
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

### A2AProtocolProcessor

主要的协议处理器类，负责处理A2A消息。

#### 主要方法

- `processHttpMessage(RequestMessage)`: 处理HTTP A2A消息
- `processGrpcMessage(CloudEvent)`: 处理gRPC A2A消息
- `createRegistrationMessage(String, String, String[])`: 创建注册消息
- `createTaskRequestMessage(String, String, String, Map)`: 创建任务请求消息
- `createHeartbeatMessage(String)`: 创建心跳消息
- `createStateSyncMessage(String, Map)`: 创建状态同步消息

### AgentRegistry

智能体注册中心，管理智能体的注册、发现和元数据。

#### 主要方法

- `registerAgent(A2AMessage)`: 注册智能体
- `unregisterAgent(String)`: 注销智能体
- `getAgent(String)`: 获取智能体信息
- `getAllAgents()`: 获取所有智能体
- `findAgentsByType(String)`: 按类型查找智能体
- `findAgentsByCapability(String)`: 按能力查找智能体
- `isAgentAlive(String)`: 检查智能体是否在线

### MessageRouter

消息路由器，负责智能体间消息的路由和转发。

#### 主要方法

- `routeMessage(A2AMessage)`: 路由消息
- `registerHandler(String, Consumer)`: 注册消息处理器
- `unregisterHandler(String)`: 注销消息处理器

### CollaborationManager

协作管理器，处理智能体间的协作逻辑和工作流编排。

#### 主要方法

- `startCollaboration(String, List, Map)`: 启动协作会话
- `registerWorkflow(WorkflowDefinition)`: 注册工作流定义
- `getSessionStatus(String)`: 获取会话状态
- `cancelSession(String)`: 取消会话

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

## 最佳实践

### 1. 智能体设计

- **单一职责**: 每个智能体应该专注于特定的能力或任务类型
- **能力声明**: 准确声明智能体的能力和资源限制
- **错误处理**: 实现完善的错误处理和恢复机制
- **状态管理**: 定期同步状态信息，及时报告异常

### 2. 消息设计

- **幂等性**: 设计幂等的消息处理逻辑
- **超时设置**: 合理设置消息超时时间
- **优先级**: 根据业务重要性设置消息优先级
- **相关性**: 使用correlationId跟踪相关消息

### 3. 协作工作流

- **步骤设计**: 将复杂任务分解为可独立执行的步骤
- **容错处理**: 为每个步骤设置重试机制和超时处理
- **资源管理**: 合理分配和释放协作资源
- **监控告警**: 实现工作流执行监控和异常告警

### 4. 性能优化

- **连接池**: 使用连接池管理网络连接
- **批量处理**: 对大量消息进行批量处理
- **异步处理**: 使用异步处理提高并发性能
- **缓存策略**: 缓存频繁访问的智能体信息

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
