lint:
	golangci-lint run --tests=false
build:
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o ${SERVER}
# goimports 
# -e report all errors (not just the first 10 on different lines)
# -d display diffs instead of rewriting files
# -local put imports beginning with this string after 3rd-party packages; comma-separated list
# gofmt 
# -e report all errors (not just the first 10 on different lines)
# -d display diffs instead of rewriting files
# -s simplify code
# -w write result to (source) file instead of stdout
fmt:
	find . -name "*.go" | xargs goimports -e -d -local git.code.oa.com -w && \
    find . -name "*.go" | xargs gofmt -e -d -s -w
test:
	go test -v ./... -gcflags "all=-N -l"

cover:
	go test ./...  -gcflags "all=-N -l" --covermode=count -coverprofile=cover.out.tmp
	cat cover.out.tmp | grep -v "_mock.go" | grep -v ".pb.go" > cover.out
	rm cover.out.tmp
	go tool cover -html=cover.out