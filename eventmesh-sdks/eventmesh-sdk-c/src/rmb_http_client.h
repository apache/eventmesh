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

#ifndef __RMB_HTTP_CLIENT_H
#define __RMB_HTTP_CLIENT_H

#include "curl/curl.h"

#ifdef  __cplusplus
extern "C" {
#endif

struct rmb_http_buffer {
	size_t len;
    char*  data;
};

// timeout: ms
int rmb_http_easy_get(const char* url, void* buffer, long timeout);
//int rmb_http_easy_post(char* url, char* post_data, size_t len, void* buffer, size_t size, size_t* used, long timeout);

#ifdef  __cplusplus
}
#endif

#endif
