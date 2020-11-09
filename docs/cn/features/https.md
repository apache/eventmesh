1.eventmesh-emesher 配置

```
proxy.properties
proxy.server.useTls.enabled=true   //default value is false


config env varible
-Dssl.server.protocol=SSLv1.1   //default value is SSLv1.1
-Dssl.server.cer=sChat2.jks     //put the file in confPath which is configured in start.sh 
-Dssl.server.pass=sNetty

```


2.eventmesh-sdk-java 配置
```
//创建producer
LiteClientConfig liteClientConfig = new liteClientConfig();
...
liteClientConfig.setUseTls(true);
LiteProducer producer = new LiteProducer(liteClientConfig);


config env varible
-Dssl.client.protocol=SSLv1.1   //default value is SSLv1.1
-Dssl.client.cer=sChat2.jks     //put the file in confPath of your application
-Dssl.client.pass=sNetty
```
