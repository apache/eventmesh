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

//#ifndef RMB_CONTEXT_H_
//#define RMB_CONTEXT_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "rmb_define.h"

int rmb_context_init(StContext *pStContext);

int rmb_context_add_rsp_socket(StContext *pStContext,  const char* cLocalIp, unsigned short usReplyPort);

int rmb_context_add_req_socket(StContext *pStContext,  const char* cLocalIp, unsigned short usReqPort);

int rmb_context_add_broadcast_socket(StContext *pStContext, const char* cLocalIp, unsigned short usBroadcastPort);

int rmb_context_add_req_mq_fifo(StContext *pStContext, const char* strFiFoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);
int rmb_context_add_rr_rsp_mq_fifo(StContext *pStContext, const char* strFiFoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);
int rmb_context_add_broadcast_mq_fifo(StContext *pStContext, const char* strFiFoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);

int rmb_context_enqueue(StContext *pStContext, const enum RmbMqIndex uiMsgType, const char* data, unsigned int uiDataLen);

#ifdef __cplusplus
}
#endif

//#endif /* RMB_CONTEXT_H_ */
