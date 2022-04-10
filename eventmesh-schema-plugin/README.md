# Schema Plugin
## api
1. 根据消息头部判断是否需要校验schema
2. 根据subject注册schema
3. 根据id（或者根据subject和版本号）获取schema
4. 根据schema校验消息body的格式
5. 根据校验格式的结果给出相应的回应
    1. 如果是producer发送过来的，那就告诉producer没有发送成功
    2. 如果是要发送给consumer的，那就log下来，并且在给consumer发送的消息当中加上校验不成功的通知

