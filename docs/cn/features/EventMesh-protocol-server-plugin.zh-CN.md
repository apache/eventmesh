# EventMesh Protocol Server Plugin

## 介绍

EventMesh采用微内核的设计模式，通过插件的方式实现HTTP、TCP、GRPC(todo)等多种协议，用户可以通过在配置文件中声明的方式选择所需要运行的插件，用户也可以通过扩展protocol SPI接口实现自定义插件。

![eventmesh-protocol-design](../../images/features/eventmesh-protocol-plugin-design.png?raw=true)

## Protocol Server SPI 接口
Protocol Server SPI接口的代码位于eventmesh-protocol-api模块下，包含三个核心方法init(), start(), shutdown()。inti()方法用于服务启动前执行初始化，
start()方法用于启动服务，shutdown()用于关闭服务。
```java
@EventMeshSPI(isSingleton = false)
public interface EventMeshProtocolServer {

    /**
     * The init method
     *
     * @throws EventMeshProtocolException
     */
    void init() throws EventMeshProtocolException;

    /**
     * The start method
     *
     * @throws EventMeshProtocolException
     */
    void start() throws EventMeshProtocolException;

    /**
     * The shutdown method
     *
     * @throws EventMeshProtocolException
     */
    void shutdown() throws EventMeshProtocolException;
}
```
EventMesh runtime在启动的时候会根据配置文件eventmesh.yml中的配置项，加载Protocol插件启动Protocol Server
```java
public class EventMeshServer {
    private final List<EventMeshProtocolServer> eventMeshProtocolServers;
    public void init() {
        ...        
        for (EventMeshProtocolServer eventMeshProtocolServer : eventMeshProtocolServers) {
            eventMeshProtocolServer.init();
        }
        ...
    }

    public void start() {
        ...
        for (EventMeshProtocolServer eventMeshProtocolServer : eventMeshProtocolServers) {
            eventMeshProtocolServer.start();
        }
        ...
    }

    public void shutdown() {
        ...
        for (EventMeshProtocolServer eventMeshProtocolServer : eventMeshProtocolServers) {
            eventMeshProtocolServer.shutdown();
        }
        ...
    }
}
```

## Protocol HTTP Server

## Protocol TCP Server


