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
#

################################################################################
# Variables                                                                    #
################################################################################
export GO111MODULE ?= on
export GOPROXY ?= https://proxy.golang.org
export GOSUMDB ?= sum.golang.org

# By default, disable CGO_ENABLED. See the details on https://golang.org/cmd/cgo
CGO         ?= 0

LOCAL_ARCH := $(shell uname -m)
ifeq ($(LOCAL_ARCH),x86_64)
	TARGET_ARCH_LOCAL=amd64
else ifeq ($(shell echo $(LOCAL_ARCH) | head -c 5),armv8)
	TARGET_ARCH_LOCAL=arm64
else ifeq ($(shell echo $(LOCAL_ARCH) | head -c 4),armv)
	TARGET_ARCH_LOCAL=arm
else
	TARGET_ARCH_LOCAL=amd64
endif
export GOARCH ?= $(TARGET_ARCH_LOCAL)

LOCAL_OS := $(shell uname)
ifeq ($(LOCAL_OS),Linux)
   TARGET_OS_LOCAL = linux
else ifeq ($(LOCAL_OS),Darwin)
   TARGET_OS_LOCAL = darwin
else
   TARGET_OS_LOCAL ?= windows
endif
export GOOS ?= $(TARGET_OS_LOCAL)

ifeq ($(GOOS),windows)
BINARY_EXT_LOCAL:=.exe
GOLANGCI_LINT:=golangci-lint.exe
BUILDMODE:=-buildmode=exe
else
BINARY_EXT_LOCAL:=
GOLANGCI_LINT:=golangci-lint
endif

COVERAGE_OPTS = -covermode=set -coverprofile=cover_set.out


################################################################################
# Target: test                                                                 #
################################################################################
.PHONY: test
test:
	go test ./...

################################################################################
# Target: coverage                                                                 #
################################################################################
.PHONY: coverage
coverage:
	go test ./... $(COVERAGE_OPTS)

################################################################################
# Target: lint                                                                 #
################################################################################
.PHONY: lint
lint:
	$(GOLANGCI_LINT) run --timeout=20m

################################################################################
# Target: go.mod                                                               #
################################################################################
.PHONY: go.mod
go.mod:
	go mod tidy

################################################################################
# Target: check-diff                                                           #
################################################################################
.PHONY: check-diff
check-diff:
	git diff --exit-code ./go.mod # check no changes

################################################################################
# Target: clean                                                                #
################################################################################
.PHONY: clean
clean:
	@-rm -rf *.out