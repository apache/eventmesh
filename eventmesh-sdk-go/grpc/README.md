grpc
===
grpc client for evenemesh, support multiple type of loadbalancer

how to use
---
### send message

### subscribe message

setup with option
---
### 1. setup the logger
if you want to rewrite the log to other place, such as some promethus and so on, you need to implements the ```log.Logger```
interface, and set it with ```GRPCOption```
for example:
```GO
type selfLogger struct{
}

func (s *selfLogger) Infof(template string, args ...interface{}) {
	// todo
}
// ...
// other methods

cli, err := grpc.New(&conf.GRPCConfig{}, []Option{WithLogger(&selfLogger{})})
```
### 2. setup the idgen
in grpc client, we provide two kinds of id generator, uuid/flake, you can refers to ```commom/id``` for details.
and if you want to implement it yourself, just implement the ```id.Interface``` api, for example:
```GO
type selfIdg struct{}
func (s* selfIdg) Next() string {
	return "uniq id"
}

cli, err := grpc.New(&conf.GRPCConfig{}, []Option{WithID(&selfIdg{})})
```

TODO
---
use etcd as service discovery