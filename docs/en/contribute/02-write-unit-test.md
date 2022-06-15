# Unit Test Requirement

- Each unit test case should use assertions instead of `System.out` output or `if` statement
- Each unit test case shouldn't call other cases or depend on the order of execution.
- Each unit test case should be repeatable and not depend on the external environment because the test might be executed in the continuous integration.
- The scope of each unit test should be small enough to help locate the problem at the method level.

## Location and Naming Rules

- The unit test should be placed in `src/test/java`.
- The unit test configuration file should be placed in `src/test/resources`. For example:
  - Class to be tested: `src/main/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBody.java`
  - Unit test: `src/test/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBodyTest.java`
  - Unit test configuration: `src/test/resources/configuration.properties`
- The package name of the unit test class should be identical to the class to be tested.
- The name of the unit test class should be `{class or interface to be tested}Test`. For example:
  - Class to be tested: `EventMeshUtil`
  - Unit test class: `EventMeshUtilTest`
- The name of each test case should be `test{method name}`. For example:
  - Method to be tested: `addProp(String key, String val)`
  - Unit test case: `testAddProp`

## Assertion Usage

### Common Assertion

| Methods | Instructions |
| :-------------- | :-------------- |
| `assertEquals`    | Determines whether two objects or primitive types are equal |
| `assertNotEquals` | Determines whether two objects or primitive types are not equal |
| `assertTrue`      | Determines whether the given Boolean value is `true` |
| `assertFalse`    | Determines whether the given Boolean value is `false` |
| `assertNull`      | Determines whether the given object reference is `null` |
| `assertNotNull`   | Determines whether the given object reference is not `null` |
| `assertAll`       | When multiple decision logic are processed together if only one error is reported, the whole test will fail |

### Example

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
