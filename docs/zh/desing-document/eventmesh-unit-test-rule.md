# 单元测试准则

## 目录以及命名规则

+ 单元测试代码必须放在工程目录下:src/test/java  
  测试的配置文件也必须放在: src/test/resources  
  例:    
  业务类: `src/main/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBody.java`  
  对应被测试业务类: `src/test/java/org/apache/eventmesh/common/protocol/http/body/BaseResponseBodyTest.java`  
  测试配置文件: `src/test/resources/configuration.properties`
  
+ 测试类所在的包名与被测试类所在的包名一致(如上所示)
  
+ 测试类的命名规范:   
  被测试(类、接口)名 + Test  
  例:  
  业务类名: `EventMeshUtil`  
  对应被测试业务类名: `EventMeshUtilTest`
  
+ 测试类用例命名规范:  
  test + 方法名, 使用test作为方法名的前缀  
  例:  
  业务方法名:
  ```
    public EventMeshMessage addProp(String key, String val) {
        if (prop == null) {
            prop = new HashMap<>();
        }
        prop.put(key, val);
        return this;
    }
  ```
  对应被测试业务方法名:
  ```
    public void testAddProp() {
        EventMeshMessage message = createLiteMessage();
        message.addProp("key3", "value3");
        Assert.assertEquals(3L, message.getProp().size());
        Assert.assertEquals("value1", message.getProp("key1"));
    }
  ```

## 编码规范

+ 单元测试类中必须使用assert断言来进行验证, 不允许使用System.out, if判断验证来进行验证(可以使用log打印关键日志输出)
+ 增量代码要确保单元测试通过
+ 单元测试要保证测试粒度足够小, 以助于精确定位问题, 一般都是方法级别  
  注：测试粒度小才能尽快定位到错误位置
+ 保持单元测试之间的独立性, 为了保证单元测试稳定可靠且便于维护, 单元测试用例之间绝不能互相调用，也不能依赖执行的先后次序
+ 单元测试必须可以重复执行的, 不受外界环境影响  
  注：单元测试通常放在持续集成中, 如果单个单元测试依赖外部环境, 那么很容易导致集成机制不可用

## 断言的使用

**所有的测试用例的结果验证都必须使用断言模式**

### 常规断言

|       方法       |       说明       |      备注      |
| :-------------- | :---------------| ------------- |
| assertEquals    | 判断两个对象或者两个原始类型是否相等   |  |
| assertNotEquals | 判断两个对象或者两个原始类型是否不相等 |  |
| assertTrue      | 判断给定的布尔值是否为真 |  |
| assertFalse     | 判断给定的布尔值是否为假 |  |
| assertNull      | 判断给定的对象应用是否为空   |  |
| assertNotNull   | 判断给定的对象应用是否不为空 |  |
| assertAll       | 多个逻辑一起处理, 只要有一个报错, 整个测试就会失败 |  |

### 断言使用实例

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

+ 集合结果集中的每个对象都需要断言(以map为例)
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
