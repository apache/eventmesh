1.在eventmesh-emesher 中配置

```
proxy.properties(添加如下配置)
proxy.server.useTls.enabled=true   //默认值 false


config env varible
-Dssl.server.protocol=TLSv1.1   //默认值 TLSv1.1 
-Dssl.server.cer=sChat2.jks     //把文件放到启动脚本start.sh 指定的conPath目录下
-Dssl.server.pass=sNetty

```


2.在eventmesh-sdk-java 中配置
```
//创建producer
LiteClientConfig liteClientConfig = new liteClientConfig();
...

//设置开启TLS
liteClientConfig.setUseTls(true);
LiteProducer producer = new LiteProducer(liteClientConfig);


//配置环境变量
-Dssl.client.protocol=TLSv1.1   //默认值 TLSv1.1 
-Dssl.client.cer=sChat2.jks     //把文件放到应用指定的conPath目录下
-Dssl.client.pass=sNetty
```
