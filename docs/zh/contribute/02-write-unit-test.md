# 单元测试要求

- 每个单元测试都应该使用断言`assertions` 而不是 `System.out` 打印或者`if`语句
- 每个单元测试都不应该调用其他用例，或者依赖于执行顺序
- 每个单元测试都应该是可以循环执行的，并且单元测试不可以依赖外部环境，因为单元测试可能在持续集成环境中运行。
- 每个单元测试的测试范围应该足够小且精准，以便于定位方法级别的问题。

## 路径和命名规则

- 单元测试应该写在`src/test/java`目录下。
- 单元测试的配置文件应该放在`src/test/resources`目录下，如下示例：
  - 将被测试的类：`src/main/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBody.java`
  - 单元测试类：`src/test/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBodyTest.java`
  - 单元测试配置文件：`src/test/resources/configuration.properties`
- 单元测试类的包名应该和被测试的类的包名相同
-  单元测试的类的名字应该是`{被测试的类名+}Test`。 比如：
   - 将被测试的类是：`EventMeshUtil`
   - 单元测试类的类名就是： `EventMeshUtilTest`
- 每个单元测试的名字必须是 `test{+方法名}`，比如：
  - 将被测试的方法：`addProp(String key, String val)`
  - 单元测试的名字就是：`testAddProp`

## 断言的用法


### 常见的断言

| Methods | Instructions |
| :-------------- | :-------------- |
| `assertEquals`    | 确定两个对象或原语类型是否相等 |
| `assertNotEquals` | 确定两个对象或原语类型是否不相等 |
| `assertTrue`      | 确定给定的布尔值是否是 `true` |
| `assertFalse`    | 确定给定的布尔值是否是 `false` |
| `assertNull`      | 确定给定的对象是否是 `null` |
| `assertNotNull`   | 确定给定的对象是否不是 `null` |
| `assertAll`       | 如果同时处理多个逻辑，如果只有一个逻辑断言失败，整个测试将会失败|

### 示例

#### `assertEquals()`

```java
configuration.init();
Assert.assertEquals("value1", configuration.eventMeshEnv);
```

#### `assertTrue()`

```java
BaseResponseHeader header = BaseResponseHeader.buildHeader("200");
Assert.assertTrue(header.toMap().containsKey(ProtocolKey.REQUEST_CODE));
```

#### `assertFalse()`

```java
Class<NacosRegistryService> nacosRegistryServiceClass = NacosRegistryService.class;
Field initStatus = nacosRegistryServiceClass.getDeclaredField("INIT_STATUS");
initStatus.setAccessible(true);
Object initStatusField = initStatus.get(nacosRegistryService);
Assert.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
```

#### `assertNull()`

```java
DefaultFullHttpResponse response = httpCommand.httpResponse();
Assert.assertNull(response);
```

#### `assertNotNull()`

```java
Codec.Decoder cd = new Codec.Decoder();
ArrayList<Object> result = new ArrayList<>();
cd.decode(null, buf, result);
Assert.assertNotNull(result.get(0));
```
