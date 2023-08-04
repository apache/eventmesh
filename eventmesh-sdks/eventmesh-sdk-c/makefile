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


RMB_VERSION=$(shell grep "RMBVERSION" rmb_define.h|grep -v "RMBVERSIONFORBIZ"|awk -F " |\"" '{print $$4}')
CC=gcc
RANLIB = ranlib

INC = -I./libs/curl/include -I./libs/json/include -I./src -I./src/md5

LIB += -pthread -lrt -ldl
CFLAGS=-Wall -g -gstabs+ -ggdb -fPIC -Wunused-function

TARGET_DYNAMIC=librmb.so
TARGET_STATIC=librmb.a

BINARY = $(patsubst src/%.c,src/%.o,$(wildcard src/*.c))
BINARY += $(patsubst src/md5/%.c,src/md5/%.o,$(wildcard src/md5/*.c))


all:$(TARGET_DYNAMIC) $(TARGET_STATIC)

$(TARGET_DYNAMIC):$(BINARY)
	$(CC) $(CFLAGS) $^ -o $@  -shared -fPIC

$(TARGET_STATIC):$(BINARY)
	@echo
	@echo "Building $^ ==> $@..."
	@-mkdir -p rmb_lib
	$(AR) cq rmb_lib/libsrc.a $^
	$(RANLIB) rmb_lib/libsrc.a
	@-cp libs/json/lib/libjson-c.a  libs/curl/lib/libcurl.a ./rmb_lib/
	$(AR) cqT librmb.a rmb_lib/*.a && echo -e 'create $@\naddlib $@\nsave\nend' | $(AR) -M


%.o: %.c
	$(CC) $(CFLAGS) $(INC) -c $^ -o $@


clean:
	rm -f $(TARGET_DYNAMIC) $(TARGET_STATIC) $(BINARY)
	rm -rf rmb_lib

example: $(all)
	@echo
    @echo "Building $^ ==> $@..."
    $(CC) -o $@ example/test_em.c -I./example -I./src -l:librmb.a $(LIB)
