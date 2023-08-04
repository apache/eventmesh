/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _TCP_SOCKET_FOR_WEMQ_H_
#define _TCP_SOCKET_FOR_WEMQ_H_
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int wemq_tcp_connect(const char *ip, uint16_t port, int timeout);
int wemq_tcp_send(int fd, void *msg, uint32_t totalLen, uint32_t headerLen, int iTimeOut);
int wemq_tcp_recv(int fd, void *msg, uint32_t *len, int iTimeout);

void wemq_getsockename(int fd, char* ip, uint32_t len, int* port);
void wemq_getpeername(int fd, char* ip, uint32_t len, int* port);

#ifdef __cplusplus
}
#endif

#endif
