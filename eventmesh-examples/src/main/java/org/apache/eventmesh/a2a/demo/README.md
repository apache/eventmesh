# EventMesh A2A Gateway Demo

## 架构

```
  protocol-a2a 模块              runtime 模块                    examples 模块
  ┌─────────────────┐           ┌──────────────────┐           ┌──────────────┐
  │ A2AMessageTransport(接口)    │ A2AGatewayServer  │           │ A2AGatewayDemo│
  │ A2AClient (SDK)  │<──HTTP──>│ (main, Netty HTTP)│<──HTTP──>│ (纯客户端)    │
  │ AgentCard/Topic  │           │ InMemoryTransport │           └──────────────┘
  └─────────────────┘           │ GatewayService    │
                                │ TaskRegistry      │
                                └──────────────────┘
```

## 快速开始

### 1. 编译

```bash
cd eventmesh
./gradlew :eventmesh-protocol-plugin:eventmesh-protocol-a2a:compileJava \
          :eventmesh-runtime:compileJava \
          :eventmesh-examples:compileJava
```

### 2. 启动服务端

```bash
# 方式一：Gradle
./gradlew :eventmesh-runtime:run -PmainClass=org.apache.eventmesh.runtime.a2a.A2AGatewayServer

# 方式二：Java 命令行
java -cp <classpath> org.apache.eventmesh.runtime.a2a.A2AGatewayServer [port]
# 默认端口 10105
```

服务端启动后：
- Netty HTTP server 监听指定端口
- 预注册 mock `weather-agent`，自动响应 task 请求
- TTL 清理：已完成的 task 5 分钟后自动清理，agent card 60 秒无心跳自动过期

### 3. 运行客户端

```bash
# 方式一：Gradle
./gradlew :eventmesh-examples:run -PmainClass=org.apache.eventmesh.a2a.demo.gateway.A2AGatewayDemo

# 方式二：Java 命令行
java -cp <classpath> org.apache.eventmesh.a2a.demo.gateway.A2AGatewayDemo
```

客户端流程：
1. 注册自己的 AgentCard 到 Gateway
2. 列出已注册的 agents
3. 同步提交 task 到 weather-agent
4. 异步提交 task
5. 查询 task 状态

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/a2a/tasks?mode=sync` | 同步提交 task（等待结果） |
| POST | `/a2a/tasks?mode=async` | 异步提交 task（立即返回 taskId） |
| GET | `/a2a/tasks/{taskId}` | 查询 task 状态 |
| DELETE | `/a2a/tasks/{taskId}` | 取消 task |
| GET | `/a2a/tasks/{taskId}/wait` | 长轮询等待 task 结果 |
| GET | `/a2a/tasks/{taskId}/stream` | SSE 流式推送 task 状态更新 |
| GET | `/a2a/agents` | 列出所有已注册 agents |
| POST | `/a2a/heartbeat` | Agent 心跳 |
| GET | `/a2a/cards/list` | 列出所有 AgentCard |
| POST | `/a2a/cards/card/{org}/{unit}/{agent}` | 注册 AgentCard |

### 示例：curl 测试

```bash
# 同步 task
curl -X POST 'http://localhost:10105/a2a/tasks?mode=sync' \
  -H 'Content-Type: application/json' \
  -d '{"targetAgent":"weather-agent","message":"Beijing"}'

# 异步 task
curl -X POST 'http://localhost:10105/a2a/tasks?mode=async' \
  -H 'Content-Type: application/json' \
  -d '{"targetAgent":"weather-agent","message":"Shanghai"}'

# 查询状态
curl http://localhost:10105/a2a/tasks/{taskId}

# SSE 流
curl -N http://localhost:10105/a2a/tasks/{taskId}/stream

# 列出 agents
curl http://localhost:10105/a2a/agents

# 心跳
curl -X POST http://localhost:10105/a2a/heartbeat \
  -H 'Content-Type: application/json' \
  -d '{"orgId":"default","unitId":"default","agentId":"weather-agent"}'
```

## A2AClient SDK

```java
A2AClient client = A2AClient.builder()
    .gatewayUrl("http://localhost:10105")
    .namespace("global")
    .agentName("my-agent")
    .agentCard(validCard)
    .heartbeatInterval(30_000)
    .build();

client.start();

// 同步 task
TaskResult result = client.sendTaskSync("weather-agent", "Beijing", null);

// 异步 task
String taskId = client.sendTaskAsync("weather-agent", "Beijing", null);

// 查询状态
TaskResult status = client.getTaskStatus(taskId);

// 取消
boolean cancelled = client.cancelTask(taskId);

// 列出 agents
List<String> agents = client.listAgents();

client.shutdown();
```

## 测试

```bash
# 全部 A2A 测试
./gradlew :eventmesh-protocol-plugin:eventmesh-protocol-a2a:test \
          :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.a2a.*"

# 仅 HTTP 集成测试
./gradlew :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.a2a.A2AClientServerIntegrationTest"
```

测试覆盖：
- `A2ATopicFactoryTest` — Topic 生成/解析
- `TaskRegistryTest` — Task 状态机 + TTL 清理
- `InMemoryA2AMessageTransportTest` — 内存传输投递
- `A2AGatewayServiceTest` — Gateway 服务层
- `A2AGatewayEndToEndTest` — 进程内全链路
- `A2AClientServerIntegrationTest` — 真实 HTTP 客户端-服务端集成测试
