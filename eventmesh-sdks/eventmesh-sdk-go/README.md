## EventMesh Go SDK

### Support api

1. **gRPC**
2. **HTTP**
3. **TCP**

### Makefile tip

#### 1. use golangci-lint static code check

install:

```shell
go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
make lint
```

#### 2. test code

```shell
make test
```

#### 3. test coverage

```shell
make coverage
```
