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

#ifndef RMB_ACCESS_CONFIG_H_
#define RMB_ACCESS_CONFIG_H_

#ifdef __cplusplus
extern "C" {
#endif

int rmb_get_wemq_proxy_list_num();

int rmb_get_wemq_proxy_list_used();

//int wemq_proxy_load_servers(char* url, long timeout, const char* path);
int wemq_proxy_load_servers(const char* url, long timeout);

int wemq_proxy_get_server(char* host, size_t size, unsigned int* port);

void wemq_proxy_goodbye(const char* host, unsigned int port);

void wemq_proxy_to_black_list(const char* host, unsigned int port);

int wemq_proxy_ip_is_connected();

void split_str(char *ips, char ipArray[][50], int *len);

#ifdef __cplusplus
}
#endif

#endif /* RMB_ACCESS_CONFIG_H_ */
