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

CC=gcc
RANLIB = ranlib

RMB_VERSION=$(shell grep "RMBVERSION" include/rmb_define.h|grep -v "RMBVERSIONFORBIZ"|awk -F " |\"" '{print $$4}')

INC = -I./include -I./src

DIR_THIRD_PARTY = ./third_party

DIR_CURL = $(DIR_THIRD_PARTY)/curl
INC_CURL = -I$(DIR_CURL)/include
LIB_A_CURL = $(DIR_THIRD_PARTY)/libcurl.a
LIB_CURL = -L$(DIR_THIRD_PARTY) -lcurl

DIR_JSONC = $(DIR_THIRD_PARTY)/json-c
DIR_BUILD_JSONC = $(DIR_JSONC)/cmake/build
INC_JSONC = -I$(DIR_JSONC) -I$(DIR_BUILD_JSONC)
LIB_A_JSONC = $(DIR_THIRD_PARTY)/libjson-c.a
LIB_JSONC = -L$(DIR_THIRD_PARTY) -ljson-c

LIB_UUID = -luuid

LIB_OPENSSL = -lssl -lcrypto

INC += $(INC_CURL) $(INC_JSONC)
LIB = $(LIB_CURL) $(LIB_JSONC) $(LIB_UUID) $(LIB_OPENSSL)

CFLAGS=-Wall -g -gstabs+ -ggdb -fPIC -Wunused-function

TARGET_STATIC = librmb.a
TARGET_DYNAMIC = librmb.so
TARGET = $(TARGET_STATIC) $(TARGET_DYNAMIC)

OBJ = $(patsubst %.c,%.o,$(wildcard ./src/*.c))

all:$(TARGET)

$(LIB_A_CURL):
	@echo
	@echo "Building $^ ==> $@..."
	@cd $(DIR_CURL) && autoreconf -fi && ./configure --without-ssl
	@cd $(DIR_CURL) && make
	@cp -f $(DIR_CURL)/./lib/.libs/libcurl.a $@

$(LIB_A_JSONC):
	@echo
	@echo "Building $^ ==> $@..."
	@mkdir -p $(DIR_BUILD_JSONC)
	@cd $(DIR_BUILD_JSONC) && cmake ../..
	@cd $(DIR_JSONC)/cmake/build && make
	@cp $(DIR_JSONC)/cmake/build/libjson-c.a $@

./tmp/libm.a: $(OBJ)
	@echo
	@echo "Building $^ ==> $@..."
	@-mkdir -p ./tmp
	$(AR) cq $@ $^
	$(RANLIB) $@

$(TARGET_STATIC): $(LIB_A_CURL) $(LIB_A_JSONC) ./tmp/libm.a
	@echo
	@echo "Building $^ ==> $@..."
	@cp -f $(LIB_A_CURL) ./tmp
	@cp -f $(LIB_A_JSONC) ./tmp
	@rm -f $@
	$(AR) cqT $@ ./tmp/*.a && echo -e 'create $@\naddlib $@\nsave\nend' | $(AR) -M

$(TARGET_DYNAMIC): $(LIB_A_CURL) $(LIB_A_JSONC) $(OBJ)
	@echo
	@echo "Building $^ ==> $@..."
	@rm -f $@
	$(CC) -shared -fPIC $(CFLAGS) -o $@ $(OBJ) $(LIB)

%.o: %.c
	$(CC) $(CFLAGS) $(INC) -c $^ -o $@

.PHONY: clean
clean:
	rm -rf $(TARGET) $(OBJ) ./tmp

demo: $(TARGET_STATIC)
	@echo
	@echo "Building $^ ==> $@..."
	$(CC) -o $@ examples/demo.c $(CCFLAGS) -I./include -L./ -l:librmb.a -luuid $(LIB_OPENSSL) -lz -pthread -lrt -dl -static-libgcc
