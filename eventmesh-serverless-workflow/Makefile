#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

lint:
	golangci-lint run --tests=false
build:
	CGO_ENABLED=0 go build -o bin/eventmesh-workflow
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