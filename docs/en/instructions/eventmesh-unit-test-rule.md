# Unit test guidelines

## Directory and naming rules

+ Unit test code must be placed in the project directory: src/test/java  
  The test configuration file must also be placed in: src/test/resources  
  Example:  
  >src/main/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBody.java
  >src/test/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBodyTest.java
+ The package name of the test class is the same as that of the tested class
+ Naming specifications for test classes:  
  Tested (class, interface) name + Test
+ Test case naming specification:  
  test + Method name,prefix the method name with test.

## Coding specification

+ Unit tests must use assertions for verification, and are not allowed to use `System.out` output and `if` for judgmental verification (you can use log to print critical log output)
+ Incremental code should ensure that the unit test passes
+ Unit tests should ensure that the test granularity is small enough to help position the problem, generally at the method level  
  Note: Only with small test granularity can we locate the wrong position as soon as possible.
+ Keep unit tests independent, to keep unit tests stable, reliable and maintainable, unit test cases should never call each other or depend on the order in which they are executed
+ Unit tests must be repeatable and not affected by the external environment  
  Note: Unit tests are usually placed in continuous integration, and if a single unit test is dependent on an external environment, it is easy to make the integration mechanism unavailable

## Use of assertions

**The result verification of all test cases must use the assertion pattern**

### General assertions

| Methods | Instructions | Note |
| :-------------- | :-------------- | -------------- |
| assertEquals    | Determines whether two objects or primitive types are equal |  |
| assertNotEquals | Determines whether two objects or primitive types are not equal |  |
| assertTrue      | Determines whether the given Boolean value is true |  |
| assertFalse     | Determines whether the given Boolean value is false |  |
| assertNull      | Determines whether the given object reference is null |  |
| assertNotNull   | Determines whether the given object reference is not null |  |
| assertAll       | When multiple decision logic are processed together, if only one error is reported, the whole test will fail |  |

### Assertion Usage Examples

+ assertEquals()
```
 configuration.init();
 Assert.assertEquals("value1", configuration.eventMeshEnv);
```

+ assertTrue()
```
 BaseResponseHeader header = BaseResponseHeader.buildHeader("200");
 Assert.assertTrue(header.toMap().containsKey(ProtocolKey.REQUEST_CODE));
```

+ assertFalse()
```
 Class<NacosRegistryService> nacosRegistryServiceClass = NacosRegistryService.class;
 Field initStatus = nacosRegistryServiceClass.getDeclaredField("INIT_STATUS");
 initStatus.setAccessible(true);
 Object initStatusField = initStatus.get(nacosRegistryService);
 Assert.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
```

+ assertNull()
```
 DefaultFullHttpResponse response = httpCommand.httpResponse();
 Assert.assertNull(response);
```

+ assertNotNull()
```
 Codec.Decoder cd = new Codec.Decoder();
 ArrayList<Object> result = new ArrayList<>();
 cd.decode(null, buf, result);
 Assert.assertNotNull(result.get(0));
```

+ Each object in the set result set needs to be asserted (using map as an example)
```
 Map<String, Object> headerParam = new HashMap<>();
 headerParam.put(ProtocolKey.REQUEST_CODE, 200);
 headerParam.put(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
 headerParam.put(ProtocolKey.VERSION, "1.0");
 headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, "default cluster");
 headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, "127.0.0.1");
 headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, "DEV");
 headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, "IDC");
 header = PushMessageRequestHeader.buildHeader(headerParam);
 Assert.assertThat(header.toMap().get(ProtocolKey.REQUEST_CODE), is(200));
 Assert.assertThat(header.toMap().get(ProtocolKey.LANGUAGE), is(Constants.LANGUAGE_JAVA));
 Assert.assertThat(header.toMap().get(ProtocolKey.VERSION), is(ProtocolVersion.V1));
 Assert.assertThat(header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER), is("default cluster"));
 Assert.assertThat(header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP), is("127.0.0.1"));
 Assert.assertThat(header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV), is("DEV"));
 Assert.assertThat(header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC), is("IDC"));
```
