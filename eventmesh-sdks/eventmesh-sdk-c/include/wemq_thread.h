// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef _WEMQ_THREAD_
#define _WEMQ_THREAD_

#include <sys/epoll.h>
#include "rmb_define.h"
#define MAX_EVENT 10
#define TCP_PKG_LEN_BTYES 4

#ifdef _WEMQ_THREAD_DEBUG_
#define ASSERT(A) assert((A))
#else
#define ASSERT(A) ((void)(0))
#endif
enum
{
  THREAD_STATE_INIT = 0,
  THREAD_STATE_CONNECT,
  THREAD_STATE_REGI,
  THREAD_STATE_OK,
  THREAD_STATE_CLOSE,           //准备关闭状态
  THREAD_STATE_SERVER_BREAK,    //服务端退出状态
  THREAD_STATE_BREAK,
  THREAD_STATE_RECONNECT,
  THREAD_STATE_DESTROY,
  THREAD_STATE_EXIT
};

enum
{
  THREAD_MSG_CMD_BEAT = 0x01,
  THREAD_MSG_CMD_REGI = 0x02,
  THREAD_MSG_CMD_ADD_LISTEN = 0x03,
  THREAD_MSG_CMD_SEND_MSG = 0x04,
  THREAD_MSG_CMD_SEND_MSG_ACK = 0x05,
  THREAD_MSG_CMD_ADD_MANAGE = 0x06,
  THREAD_MSG_CMD_SEND_REQUEST = 0x07,
  THREAD_MSG_CMD_SEND_REQUEST_ASYNC = 0x08,
  THREAD_MSG_CMD_SEND_REPLY = 0x09,
  THREAD_MSG_CMD_START = 0x0a,
  THREAD_MSG_CMD_SEND_PUSH = 0x50,
  THREAD_MSG_CMD_SEND_CLIENT_GOODBYE = 0x0b,
  THREAD_MSG_CMD_SEND_LOG = 0x10,
  THREAD_MSG_CMD_RECV_MSG_ACK = 0x11
};
/*
typedef struct StWemqThreadMsg
{
	unsigned int m_iCmd;
	StWemqHeader m_stWemqHeader;
	void* m_stReq;
}StWemqThreadMsg;
*/
/*
typedef struct WemqThreadCtx
{
//	STRUCT_WEMQ_KFIFO(StWemqThreadMsg, 1024)* m_ptFifo; 
	stContextProxy* m_ptProxyContext;
	
	int m_iThreadId;	
	char* m_pRecvBuff;
	char* m_pSendBuff;

	//cache msg which from user thread;
	StWemqThreadMsg m_stWemqThreadMsg;
	int m_iWemqThreadMsgHandled;

	StRegisterReq m_stReqForRegister;
	StAddManageReq m_stReqForAddManage;
	StAddListenReq m_stReqForAddListen;

	StWemqHeader m_stWemqHeader;
	
	StTopicList* m_ptTopicList;

	//for epoll
	int m_iEpollFd;
	struct epoll_event m_stEv;
	struct epoll_event* m_ptEvents;

	int m_iSockFd;
	int m_iLastState;
	int m_iState;
	int m_contextType;
	
}WemqThreadCtx;
*/
#define GetWemqThreadMsgLen() (sizeof(StWemqThreadMsg))

int32_t wemq_thread_state_init (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_state_connect (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_state_regi (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_state_ok (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_state_break (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_state_reconnect (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_state_destory (WemqThreadCtx * pThreadCtx);
int32_t wemq_thread_run (WemqThreadCtx * pThreadCtx);
int32_t check_dyed_msg (StRmbMsg * rmbMsg);
#endif
