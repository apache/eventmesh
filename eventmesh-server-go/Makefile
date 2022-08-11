#VERSION=$(shell git describe --tags --always)

.PHONY: build
build:
	mkdir -p bin/ && go build -o ./bin ./cmd/...

test:
	go test ./...

coverage:
	 go test -covermode=set ./...
