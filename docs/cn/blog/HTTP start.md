# HTTP的启动

> 首先我们先将一下启动前的准备工作，也就是HTTPServer的初始化。
>
> 在EventMeshServer初始化的时候，会连带这初始化EventMeshHTTPServer，下面我就几点做出说明：



1. 在初始化的时候，会初始化多个线程池，这个线程池中会有许多的阻塞的线程队列，当某一种事件发生的时候，就会根据事件来使用线程池工厂来为事件创建线程。【下面是源代码】

```java
public void initThreadPool() throws Exception {
    
		// 批处理消息
        BlockingQueue<Runnable> batchMsgThreadPoolQueue = new LinkedBlockingQueue<Runnable>(eventMeshHttpConfiguration.eventMeshServerBatchBlockQSize);
        batchMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerBatchMsgThreadNum,
                eventMeshHttpConfiguration.eventMeshServerBatchMsgThreadNum, batchMsgThreadPoolQueue, "eventMesh-batchMsg-", true);
		// 发送消息，和上面的类似
        BlockingQueue<Runnable> sendMsgThreadPoolQueue=...
		// 推送消息，和上面的类似
        BlockingQueue<Runnable> pushMsgThreadPoolQueue =...
		// 客户端管理，和上面的类似
        BlockingQueue<Runnable> clientManageThreadPoolQueue =...
		// 管理线程池，和上面的类似
        BlockingQueue<Runnable> adminThreadPoolQueue =...
		// 回复消息，和上面的类似
        BlockingQueue<Runnable> replyMessageThreadPoolQueue =...
    }
```

2. 注册http请求处理器，这个处理器会针对http发过来的请求，获取到请求码，根据请求码注册对应的处理器，并且为这个事件用上面的线程池分配一个线程进行处理。【下面是源代码】

```java
 public void registerHTTPRequestProcessor() {
     	// 新建批量消息处理器
        BatchSendMessageProcessor batchSendMessageProcessor = new BatchSendMessageProcessor(this);
     	// 获取请求码，并且分配一个线程进行处理
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(),
                batchSendMessageProcessor, batchMsgExecutor);
		
        BatchSendMessageV2Processor batchSendMessageV2Processor =...
		// 同步消息处理器，和上面的类似
        SendSyncMessageProcessor sendSyncMessageProcessor =...
		// 异步消息处理器，和上面的类似
        SendAsyncMessageProcessor sendAsyncMessageProcessor =...
		// 管理指标处理器，和上面的类似
        AdminMetricsProcessor adminMetricsProcessor =...
		// 心跳处理器，和上面的类似
        HeartBeatProcessor heartProcessor =...
		// 订阅处理器，和上面的类似
        SubscribeProcessor subscribeProcessor =...
		// 和上面的类似
        UnSubscribeProcessor unSubscribeProcessor =...
		// 回复消息处理器，和上面的类似
        ReplyMessageProcessor replyMessageProcessor =...
    }
```

3. 这里就是http初始化的全部代码。

```java
public class EventMeshHTTPServer extends AbstractHTTPServer {
	...
  	//初始化
    public void init() throws Exception {
        logger.info("==================EventMeshHTTPServer Initialing==================");
        // 初始化线程组
        super.init("eventMesh-http");
		// 初始化线程池
        initThreadPool();
		// 对指标初始化，主要是把生成的指标用于后台数据处理
        metrics = new HTTPMetricsServer(this);
        metrics.init();
		// 消费者管理初始化，主要是把httpServer注册到事件总线上
        consumerManager = new ConsumerManager(this);
        consumerManager.init();
		
        producerManager = new ProducerManager(this);
        producerManager.init();
		// 重试
        httpRetryer = new HttpRetryer(this);
        httpRetryer.init();
		// 注册http处理器
        registerHTTPRequestProcessor();

        logger.info("--------------------------EventMeshHTTPServer inited");
    }
    ...
}
```



> 初始化完成之后，再启动http的服务器端，这里我也同样聊聊以下几点。

1. AbstractHTTPServer的启动，采用的是netty的异步模型框架搭建的。具体来讲，这里创建了两个线程池：`bossGroup`和`workerGroup`，前者是用来轮询`accept`事件并且和`client`建立连接的，后者是用来轮询`read`和`write`事件并且使用`handlers`处理`io`事件的。而且这里采用了回调机制，当调用发出后，并不一定立刻就能得到结果，而是在实际处理的时候调用这个组件完成后，通过状态、通知等回调告知调用者。

```java
public abstract class AbstractHTTPServer extends AbstractRemotingServer {
	...
    @Override
    public void start() throws Exception {
        super.start();
        Runnable r = () -> {
            // 创建服务器端启动的对象
            ServerBootstrap b = new ServerBootstrap();
            // 不进行加密通话？
            SSLContext sslContext = useTLS ? SSLContextFactory.getSslContext() : null;
            
            b.group(this.bossGroup, this.workerGroup)// 设置两个线程组
                    .channel(NioServerSocketChannel.class)//使用NioServerSocketChannel作为服务器的通道实现
                    .childHandler(new HttpsServerInitializer(sslContext))// 设置workerGroup的管道处理器
                    .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);// 保持连接状态
            try {
                httpServerLogger.info("HTTPServer[port={}] started......", this.port);
                // 这里就是对http的端口进行绑定，并且启动服务器端，采用了回调机制
                ChannelFuture future = b.bind(this.port).sync();
                //关闭通道事件进行监听
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                httpServerLogger.error("HTTPServer start Err!", e);
                try {
                    // 关闭资源
                    shutdown();
                } catch (Exception e1) {
                    httpServerLogger.error("HTTPServer shutdown Err!", e);
                }
                return;
            }
        };

        Thread t = new Thread(r, "eventMesh-http-server");
        t.start();
        started.compareAndSet(false, true);
    }

   ...
    
}

```

2. 设置管道处理器部分，当`channel`被注册之后，这个类中的`initChannel`方法就会被调用，就会实现在管道后面加入`handlers`。

```java
 class HttpsServerInitializer extends ChannelInitializer<SocketChannel> {

        private SSLContext sslContext;

        public HttpsServerInitializer(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
            ChannelPipeline pipeline = channel.pipeline();


            if (sslContext != null && useTLS) {
                SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(false);
                pipeline.addFirst("ssl", new SslHandler(sslEngine));
            }
            // 在管道后面加入handlers
            pipeline.addLast(new HttpRequestDecoder(),// 这个是对http解码
                    new HttpResponseEncoder(),// 这个是对http的响应编码
                    new HttpConnectionHandler(),// 这个是对http连接处理
                    new HttpObjectAggregator(Integer.MAX_VALUE),// 这个是http对象聚合
                    new HTTPHandler());// 这个是http的Handler的具体实现
        }
    }
}
```

3. 这里附上start的源码部分。

```java
public class EventMeshHTTPServer extends AbstractHTTPServer {  
    ...
    public void start() throws Exception {
        super.start();
        // 指标
        metrics.start();
        // 消费者管理
        consumerManager.start();
        // 生成者管理
        producerManager.start();
        // 重试
        httpRetryer.start();
        logger.info("--------------------------EventMeshHTTPServer started");
    }
    ...
}
```

