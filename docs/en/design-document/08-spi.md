# Service Provider Interface

## Introduction

In order to improve scalability，EventMesh introduce the SPI（Service Provider Interface）mechanism, which can help to automatically find the concrete implementation
class of the extended interface at runtime and load it dynamically. In EventMesh, all extension modules are implemented by using plugin.
User can develop custom plugins by simply implementing extended interfaces, and select the plugin to be run at runtime by simply declare at configuration.

## eventmesh-spi module

The implementation of SPI is at eventmesh-spi module, there are three main classes `EventMeshSPI`, `EventMeshExtensionFactory` and `ExtensionClassLoader`.

### EventMeshSPI

EventMeshSPI is an SPI declaration annotation, all extended interface that want to be implemented should be declared by @EventMeshSPI.

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EventMeshSPI {
    /**
     * If true, the spi instance is singleton
     */
    boolean isSingleton() default false;
}
```

Use annotation to declare the interface is an SPI extended interface can improve the readability of the code.
On the other hand, @EventMeshSPI contains a isSingleton attribute which used to declare whether the extension instance is a singleton.
If this attribute is true, that means the instance of this interface will be singleton.

### EventMeshExtensionFactory

EventMeshExtensionFactory is a factory used to get the SPI extension instance which contains a static method `getExtension(Class<T> extensionType, String extensionName)`.

```java
public enum EventMeshExtensionFactory {
    /**
     * @param extensionType extension plugin class type
     * @param extensionName extension instance name
     * @param <T>           the type of the plugin
     * @return plugin instance
     */
    public static <T> T getExtension(Class<T> extensionType, String extensionName) {
        /* ... */
    }
}
```

If you want to get the extension instance, you should use EventMeshExtensionFactory.

### ExtensionClassLoader

ExtensionClassLoader is used to load extension instance classed, it has two subclass MetaInfExtensionClassLoader and JarExtensionClassLoader.

```java
/**
 * Load extension class
 * <ul>
 *     <li>{@link MetaInfExtensionClassLoader}</li>
 *     <li>{@link JarExtensionClassLoader}</li>
 * </ul>
 */
public interface ExtensionClassLoader {
    /**
     * load
     *
     * @param extensionType extension type class
     * @param <T>           extension type
     * @return extension instance name to extension instance class
     */
    <T> Map<String, Class<?>> loadExtensionClass(Class<T> extensionType);
}
```

MetaInfExtensionClassLoader used to load class from classPath, and JarExtensionClassLoader used to load class from extension jar on the plugin directory.
In the future, we might support the implementation to load from the maven repository.

## SPI use case

The following is an example of how to use the SPI to declare a plugin.

First, we create an eventmesh-connector-api module, and define the extension interface MeshMQProducer, and we use @EventMeshSPI on the MeshMQProducer,
which indicates the MeshMQProducer is an SPI interface.

```java
@EventMeshSPI(isSingleton = false)
public interface MeshMQProducer extends Producer {
    /* ... */
}
```

Then we create an eventmesh-connector-rocketmq module, which contains the concrete implementation named RocketMQProducerImpl.

```java
public class RocketMQProducerImpl implements MeshMQProducer {
    /* ... */
}
```

At the same time, we need to create a file with the full qualified name of the SPI interface under the resource/META-INF/eventmesh directory
in the eventmesh-connector-rocketmq module.

org.apache.eventmesh.api.producer.Producer

The content of the file is the extension instance name and the corresponding instance full class name

```properties
rocketmq=org.apache.eventmesh.connector.rocketmq.producer.RocketMQProducerImpl
```

At this point, an SPI expansion module is complete. We can use `EventMeshExtensionFactory.getExtension(MeshMQProducer.class, "rocketmq")` to get the `RocketMQProducerImpl` instance.
