# EventMesh SPI

## 介绍

为了提高扩展性，EventMesh通过引入SPI（Service Provider Interface）机制，能够在运行时自动寻找扩展接口的具体实现类，动态加载。
在EventMesh中，一切扩展点都利用SPI采用插件的实现方式，用户可以通过实现扩展接口，开发自定义的插件，在运行时通过简单的配置，声明式的选择所需要运行的插件。

## eventmesh-spi模块

SPI相关的代码位于eventmesh-spi模块下，其中主要包括EventMeshExtensionFactory, EventMeshSPI, ExtensionClassLoader这三个类。

### EventMeshSPI

EventMeshSPI是SPI注解，所有需要采用SPI实现扩展的接口都需要使用@EventMeshSPI注解标记。

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

这么做的原因是可以通过注解的方式声明接口为SPI扩展接口，提高代码的可读性。同时，@EventMeshSPI注解中包含一个isSingleton属性，
用来声明该扩展接口是否采用单例的实现方式，如果为true，那么该接口的实现类将会使用单例的实现方式，在一个JVM进程中全局唯一。

### EventMeshExtensionFactory

EventMeshExtensionFactory是SPI实现类的获取工厂，包含一个静态方法`getExtension(Class<T> extensionType, String extensionName)`，
接收扩展接口字节码对象和扩展实例名称，用于获取扩展接口的具体实现类。

```java
public enum EventMeshExtensionFactory {
  ;
    /**
     * @param extensionType extension plugin class type
     * @param extensionName extension instance name
     * @param <T>           the type of the plugin
     * @return plugin instance
     */
    public static <T> T getExtension(Class<T> extensionType, String extensionName) {
    }
}
```

所有需要获取扩展实现的地方都应该通过EventMeshExtensionFactory获取。

### ExtensionClassLoader

ExtensionClassLoader是扩展接口实现类的加载接口，包含两个实现子类MetaInfExtensionClassLoader和JarExtensionClassLoader。

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

MetaInfExtensionClassLoader用于从classPath直接加载实现类，JarExtensionClassLoader用于从配置目录下通过加载Jar包的方式加载实现类，未来可能还会提供通过从Maven仓库下加载实现类。

## SPI使用示例

下面以eventmesh-connector-plugin为例，介绍SPI具体的使用过程。

首先定义一个eventmesh-connector-api模块，并且定义扩展接口MeshMQProducer。在MeshMQProducer接口上使用@EventMeshSPI注解进行声明，表明该接口是一个SPI扩展接口

```java
@EventMeshSPI(isSingleton = false)
public interface MeshMQProducer extends Producer {
...
}
```

eventmesh-connector-rocketmq模块中包含采用rocketmq的具体实现方式RocketMQProducerImpl。

```java
public class RocketMQProducerImpl implements MeshMQProducer {
...
}
```

同时，还需要在eventmesh-connector-rocketmq模块中resource/META-INF/eventmesh目录下创建文件名为SPI接口全限定名的文件
org.apache.eventmesh.api.producer.Producer

文件内容为扩展实例名和对应的实例全类名

```properties
rocketmq=org.apache.eventmesh.connector.rocketmq.producer.RocketMQProducerImpl
```

至此，一个SPI扩展模块就完成了。在使用的时候只需要通过EventMeshExtensionFactory.getExtension(MeshMQProducer.class, “rocketmq”)就可以获取RocketMQProducerImpl实现类。
