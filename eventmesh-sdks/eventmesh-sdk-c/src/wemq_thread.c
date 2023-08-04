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

#include <sys/epoll.h>
#include <errno.h>
#include <limits.h>
#include <time.h>
#include "wemq_thread.h"
#include "rmb_common.h"
#include "rmb_errno.h"
#include "wemq_tcp.h"
#include "rmb_pub.h"
#include "rmb_sub.h"
#include "rmb_access_config.h"
#define MAX_EVENTS 100
#define HEART_BEAT_COUNT 300 
#define HEART_BEAT_PERIOD 15
#define CHECK_RR_EMPTY_PERIOD 10

static enum _wemq_message_ret{
	WEMQ_MESSAGE_RET_ERR = -1,
	WEMQ_MESSAGE_RET_GOODBYE = 88,
	WEMQ_MESSAGE_RET_CLIENTGOODBYE = 881,
	WEMQ_MESSAGE_RET_SERVERGOODBYE = 882,
	WEMQ_MESSAGE_RET_REDIRECT = 300,
};

extern const char* vecManageTopic[10];
unsigned long long ullNow = 0;
char *STATE_MAP[10] = 
{
	"THREAD_STATE_INIT",
	"THREAD_STATE_CONNECT",
	"THREAD_STATE_REGI",
	"THREAD_STATE_OK",
	"THREAD_STATE_CLOSE",
	"THREAD_STATE_SERVER_BREAK",        
	"THREAD_STATE_BREAK",
	"THREAD_STATE_RECONNECT",
	"THREAD_STATE_DESTORY",
	"THREAD_STATE_EXIT"
};

char * _wemq_cmd_map[] =
{
	"",
	"CMD_BEAT",
	"CMD_REGI",
	"CMD_ADD_LISTEN",
	"CMD_SEND_MSG",
	"CMD_SEND_MSG_ACK",
	"CMD_ADD_MANAGE",
	"CMD_SEND_REQUEST",
	"CMD_SEND_REQUEST_ASYNC",
	"CMD_SEND_REPLY",
	"CMD_SEND_PUSH",
	"CMD_SEND_START"
};

static int32_t _wemq_thread_do_send_sync(WemqThreadCtx* pThreadCtx, void *msg, uint32_t totalLen, uint32_t headerLen);

/*
unsigned long long  _wemq_thread_get_cur_time()
{
	struct timeval _curTimeVal;	
	gettimeofday(&_curTimeVal, NULL);
	ullNow = (unsigned long)_curTimeVal.tv_sec * (unsigned long)1000UL + (unsigned long)_curTimeVal.tv_usec / 1000UL;
	return ullNow;
}
*/

static int _wemq_get_executable_path( char* processdir, size_t len)
{
		char* path_end;
		if(readlink("/proc/self/exe", processdir,len) <=0)
				return -1;
		path_end = strrchr(processdir,	'/');
		if(path_end == NULL)
				return -1;
		return (int)(path_end - processdir);
}

static const char* _wemq_thread_get_cmd(unsigned int usCmd)
{
	const char* pRet = NULL;
//	if (usCmd <= 0x50)
//	{
//		pRet = _wemq_cmd_map[usCmd];
//	}
//	else
//	{
//		pRet = "CMD_PUSH";
//	}

	switch (usCmd){
		case THREAD_MSG_CMD_BEAT:
		case THREAD_MSG_CMD_REGI:
		case THREAD_MSG_CMD_ADD_LISTEN:
		case THREAD_MSG_CMD_SEND_MSG:
		case THREAD_MSG_CMD_SEND_MSG_ACK:
		case THREAD_MSG_CMD_ADD_MANAGE:
		case THREAD_MSG_CMD_SEND_REQUEST:
		case THREAD_MSG_CMD_SEND_REQUEST_ASYNC:
		case THREAD_MSG_CMD_SEND_REPLY:
		case THREAD_MSG_CMD_START:
			pRet = _wemq_cmd_map[usCmd];
			break;

        case THREAD_MSG_CMD_SEND_PUSH:
            pRet = "CMD_PUSH";
            break;

		default:
            pRet = "CMD_ERROR";
	}

	return pRet;	
}

static int32_t _wemq_thread_check_init(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_connect(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_regi(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_ok(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_close(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_reconnect(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_break(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

static int32_t _wemq_thread_check_destory(WemqThreadCtx* pThreadCtx)
{
	return 0;
}

/**
 * 心跳包：
 * 	cmd:HEARTBEAT_REQUEST
 *  headerJson={"code":0,"seq":"0160616463","command":"HEARTBEAT_REQUEST"}|bodyJson=null
 *
 */
static int32_t _wemq_thread_make_heart_beat_pkg(WemqThreadCtx* pThreadCtx)
{
	StWemqThreadMsg* pThreadMsg = &pThreadCtx->m_stHeartBeat;
	memset(pThreadMsg, 0, sizeof(StWemqThreadMsg));
	pThreadMsg->m_iCmd = THREAD_MSG_CMD_BEAT;
	WEMQJSON* jsonHeader = json_object_new_object();
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(HEARTBEAT_REQUEST));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}
	pThreadMsg->m_iHeaderLen = strlen(header_str);
	
	pThreadMsg->m_pHeader = (char*)malloc(pThreadMsg->m_iHeaderLen * sizeof(char) + 1);
	if (pThreadMsg->m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for heart beat header failed");
		json_object_put(jsonHeader);
		return -1;
	}
	memcpy(pThreadMsg->m_pHeader, header_str, pThreadMsg->m_iHeaderLen);
	pThreadMsg->m_pHeader[pThreadMsg->m_iHeaderLen] = '\0';
	
	json_object_put(jsonHeader);
	
	pThreadMsg->m_iBodyLen = 0;
	pThreadMsg->m_pBody = NULL;
	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] make heart beat pkg succ %s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadMsg->m_pHeader);
	return 0;
}

/**
 * 发送start listen命令：
 * 	cmd:LISTEN_REQUEST
 *  headerJson={"code":0,"seq":"7542688344","command":"LISTEN_REQUEST"}|bodyJson=null
 *
 */
static int32_t _wemq_thread_make_start_listen_pkg(WemqThreadCtx* pThreadCtx)
{
	StWemqThreadMsg* pThreadMsg = &pThreadCtx->m_stListen;
	memset(pThreadMsg, 0, sizeof(StWemqThreadMsg));
	pThreadMsg->m_iCmd = THREAD_MSG_CMD_START;
	WEMQJSON* jsonHeader = json_object_new_object();
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(LISTEN_REQUEST));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}
	pThreadMsg->m_iHeaderLen = strlen(header_str);
	
	pThreadMsg->m_pHeader = (char*)malloc(pThreadMsg->m_iHeaderLen * sizeof(char) + 1);
	if (pThreadMsg->m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for listen header failed");
		json_object_put(jsonHeader);
		return -1;
	}
	memcpy(pThreadMsg->m_pHeader, header_str, pThreadMsg->m_iHeaderLen);
	pThreadMsg->m_pHeader[pThreadMsg->m_iHeaderLen] = '\0';
	
	json_object_put(jsonHeader);
	
	pThreadMsg->m_iBodyLen = 0;
	pThreadMsg->m_pBody = NULL;
	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] make listen pkg succ %s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadMsg->m_pHeader);
	return 0;
}

/**
 * 发送SUBSCRIBE_REQUEST：
 * 	cmd:SUBSCRIBE_REQUEST
 *  headerJson={"code":0,"seq":"1333580037","command":"SUBSCRIBE_REQUEST"}|
 *  bodyJson={"topicList":["PRX-e-10030002-05-3","PRX-e-10020002-02-2","PRX-s-10000002-01-0","PRX-e-10020002-06-2"]}
 *
 */
static int32_t _wemq_thread_make_subscribe_pkg(WemqThreadCtx* pThreadCtx)
{
	StWemqThreadMsg* pThreadMsg = &pThreadCtx->m_stListen;
	memset(pThreadMsg, 0, sizeof(StWemqThreadMsg));
	pThreadMsg->m_iCmd = THREAD_MSG_CMD_ADD_LISTEN;
	WEMQJSON* jsonHeader = json_object_new_object();
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(LISTEN_REQUEST));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}
	pThreadMsg->m_iHeaderLen = strlen(header_str);
	
	pThreadMsg->m_pHeader = (char*)malloc(pThreadMsg->m_iHeaderLen * sizeof(char) + 1);
	if (pThreadMsg->m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for listen header failed");
		json_object_put(jsonHeader);
		return -1;
	}
	memcpy(pThreadMsg->m_pHeader, header_str, pThreadMsg->m_iHeaderLen);
	pThreadMsg->m_pHeader[pThreadMsg->m_iHeaderLen] = '\0';
	
	json_object_put(jsonHeader);
	
	pThreadMsg->m_iBodyLen = 0;
	pThreadMsg->m_pBody = NULL;
	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] make listen pkg succ %s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadMsg->m_pHeader);
	return 0;
}

/**
 * register package:
 * 	cmd:THREAD_STATE_REGI
 * headerJson={"code":0,"seq":"5160263626","command":"HELLO_REQUEST"}
 * bodyJson={"subsystem":"5023","dcn":"AC0","path":"/data/app/umg_proxy","pid":32893,"host":"127.0.0.1",
*  "port":8362,"version":"2.0.11","username":"PU4283","password":"dsaiubd"}
 */
static int32_t _wemq_thread_make_hello_pkg(WemqThreadCtx* pThreadCtx)
{
    StWemqThreadMsg* pThreadMsg = &pThreadCtx->m_stHelloWord;
    memset(pThreadMsg, 0, sizeof(StWemqThreadMsg));
    pThreadMsg->m_iCmd = THREAD_STATE_REGI;
    WEMQJSON* jsonHeader = json_object_new_object();
    
    json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));
    json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(HELLO_REQUEST));

    WEMQJSON* jsonBody = json_object_new_object();

    json_object_object_add(jsonBody, "subsystem", json_object_new_string(pRmbStConfig->cConsumerSysId));
    json_object_object_add(jsonBody, "dcn", json_object_new_string(pRmbStConfig->cConsumerDcn));
	json_object_object_add(jsonBody, "host", json_object_new_string(pRmbStConfig->cHostIp));
	json_object_object_add(jsonBody, "port", json_object_new_string(""));
	json_object_object_add(jsonBody, "version", json_object_new_string(RMBVERSION));
	json_object_object_add(jsonBody, "username", json_object_new_string(pRmbStConfig->cWemqUser));
	json_object_object_add(jsonBody, "password", json_object_new_string(pRmbStConfig->cWemqPasswd));
	json_object_object_add(jsonBody, "idc", json_object_new_string(pRmbStConfig->cRegion));
	json_object_object_add(jsonBody, "orgid", json_object_new_string(pRmbStConfig->strOrgId));
	
	if(pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB)
		json_object_object_add(jsonBody, "purpose", json_object_new_string("pub"));
	else
		json_object_object_add(jsonBody, "purpose", json_object_new_string("sub"));
	
    char processPath[1024];
	memset(processPath, 0x00, sizeof(processPath));
    _wemq_get_executable_path(processPath, 1024);
    json_object_object_add(jsonBody, "path", json_object_new_string(processPath));
	char pthread_id[32];
	snprintf(pthread_id, sizeof(pthread_id), "%u", pRmbStConfig->uiPid);
    json_object_object_add(jsonBody, "pid", json_object_new_string(pthread_id));

    const char* header_str = json_object_get_string(jsonHeader);
	const char* body_str = json_object_get_string(jsonBody);

    if (header_str == NULL)
    {
		json_object_put(jsonHeader);
        return -1;
    }
	if (body_str == NULL)
    {
		json_object_put(jsonBody);
        return -1;
    }

    pThreadMsg->m_iHeaderLen = strlen(header_str);

    pThreadMsg->m_pHeader = (char*)malloc(pThreadMsg->m_iHeaderLen * sizeof(char) + 1);
    if (pThreadMsg->m_pHeader == NULL) {
    	LOGRMB(RMB_LOG_ERROR, "malloc for hello header failed");
    	//json_object_put(jsonBody);
    	json_object_put(jsonHeader);
    	return -1;
    }
    memcpy(pThreadMsg->m_pHeader, header_str, pThreadMsg->m_iHeaderLen);
    pThreadMsg->m_pHeader[pThreadMsg->m_iHeaderLen] = '\0';

	pThreadMsg->m_iBodyLen = strlen(body_str);
	pThreadMsg->m_pBody = (char*)malloc(pThreadMsg->m_iBodyLen * sizeof(char) + 1);
	if(pThreadMsg->m_pBody == NULL){
		LOGRMB(RMB_LOG_ERROR, "malloc for hello body failed");
		json_object_put(jsonBody);
    	return -1;
	}
	memcpy(pThreadMsg->m_pBody, body_str, pThreadMsg->m_iBodyLen);
    pThreadMsg->m_pBody[pThreadMsg->m_iBodyLen] = '\0';

    json_object_put(jsonBody);
    json_object_put(jsonHeader);

    LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] make hello pkg succ, header = %s, body = %s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadMsg->m_pHeader,
			pThreadMsg->m_pBody);
    return 0;
}

static inline int32_t _wemq_thread_state_trans(WemqThreadCtx* pThreadCtx, int iState, int iNextState)
{
	ASSERT(pThreadCtx);

	LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] Thread State Trans to [%s]",
			STATE_MAP[iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			STATE_MAP[iNextState]);

	pThreadCtx->m_iState = iNextState;
	pThreadCtx->m_iLastState = iState;
	return 0;	
}

static int32_t _wemq_thread_set_fd_nonblock(WemqThreadCtx* pThreadCtx,int fd)
{
	int opts;
	opts = fcntl(fd, F_GETFL);
	if (opts < 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] set non block error:%d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				errno);
		return -1;
	}

	opts = opts|O_NONBLOCK;

	if (fcntl(fd, F_SETFL, opts) < 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] set non block error:%d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				errno);
		return -1;
	}
	return 0;
}

static int32_t _wemq_thread_add_fd(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	pThreadCtx->m_stEv.events = EPOLLIN;
	pThreadCtx->m_stEv.data.fd = pThreadCtx->m_iSockFd;	

	int iRet = -1;
	iRet = epoll_ctl(pThreadCtx->m_iEpollFd, EPOLL_CTL_ADD, pThreadCtx->m_iSockFd, &pThreadCtx->m_stEv);
	if (iRet == -1)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] epoll ctl add error:%d\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				errno);
		return -1;
	}

	return 0;
}

static int32_t _wemq_thread_del_fd(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	int iRet = -1;
	iRet = epoll_ctl(pThreadCtx->m_iEpollFd, EPOLL_CTL_DEL, pThreadCtx->m_iSockFd, &pThreadCtx->m_stEv);
	if (iRet == -1)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] epoll ctl del error: %d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				errno);
		return -1;
	}

	return 0;
}

static int32_t _wemq_thread_del_old_fd(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	int iRet = -1;
	iRet = epoll_ctl(pThreadCtx->m_iEpollFd, EPOLL_CTL_DEL, pThreadCtx->m_iSockFdOld, &pThreadCtx->m_stEv);
	if (iRet == -1)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] epoll ctl del error: %d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				errno);
		return -1;
	}

	return 0;
}


static int32_t _wemq_thread_reset_sockFd(WemqThreadCtx* pThreadCtx,bool isRecvNewConnect)
{
	if(isRecvNewConnect)
	{
		_wemq_thread_del_fd(pThreadCtx);
		close(pThreadCtx->m_iSockFd);
		pThreadCtx->m_iSockFd = -1;
		pThreadCtx->m_iSockFdNew = -1;
		
	}else{
		_wemq_thread_del_old_fd(pThreadCtx);
		close(pThreadCtx->m_iSockFdOld);
		pThreadCtx->m_iSockFdOld = -1;
		pThreadCtx->m_iSockFd = pThreadCtx->m_iSockFdNew;
		
	}

	return 0;
}



static inline void _wemq_thread_clear_thread_msg(StWemqThreadMsg* pStWemqThreadMsg)
{
	if (NULL != pStWemqThreadMsg->m_pHeader)
	{
		free(pStWemqThreadMsg->m_pHeader);
		pStWemqThreadMsg->m_pHeader = NULL;
	}
	if (NULL != pStWemqThreadMsg->m_pBody)
	{
		free(pStWemqThreadMsg->m_pBody);
		pStWemqThreadMsg->m_pBody = NULL;
	}
	pStWemqThreadMsg->m_iCmd = 0;
	pStWemqThreadMsg->m_iHeaderLen = 0;
	pStWemqThreadMsg->m_iBodyLen = 0;
}

static int32_t _wemq_thread_get_data_from_fifo(WemqThreadCtx* pThreadCtx)
{
	int32_t iRet = -1;

	if ((iRet = wemq_kfifo_is_empty(pThreadCtx->m_ptFifo)))
	{
		return 0;
	}
	if ((iRet = wemq_kfifo_get(pThreadCtx->m_ptFifo, &pThreadCtx->m_stWemqThreadMsg)) < 1)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] wemq_fifo is not empty, but get 0 bytes\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID);
		return -1;
	}

	ASSERT(iRet == 1);
	pThreadCtx->m_iWemqThreadMsgHandled = 0;
	return iRet;
}

static int32_t _wemq_thread_dyed_msg_ack_to_access(WemqThreadCtx* pThreadCtx, int seq, int status, char* msgType, StRmbMsg* ptSendMsg)
{
	char* buf = pThreadCtx->m_pSendBuff;
	int iRet = -1;
	WEMQJSON* jsonHeader = json_object_new_object();

	// 组装消息
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(msgType));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(seq));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(status));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}

	WEMQJSON *jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
	int wemqMsgType=0;
	if(strcmp(msgType,REQUEST_TO_CLIENT)==0||strcmp(msgType,ASYNC_MESSAGE_TO_CLIENT)||strcmp(msgType,BROADCAST_MESSAGE_TO_CLIENT)) 
	{
		wemqMsgType=THREAD_MSG_CMD_SEND_MSG_ACK;
	}
	else if(strcmp(msgType,RESPONSE_TO_CLIENT))
	{
		wemqMsgType=THREAD_MSG_CMD_RECV_MSG_ACK;
	}

   char cTopic[128];
   char serviceOrEvent = (*(ptSendMsg->strServiceId + 3) == '0') ? 's' : 'e';
	snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", ptSendMsg->strTargetDcn, serviceOrEvent, ptSendMsg->strServiceId, ptSendMsg->strScenarioId, *(ptSendMsg->strServiceId + 3));
    json_object_object_add(jsonBody, MSG_BODY_TOPIC_STR, json_object_new_string(cTopic));

	WEMQJSON *jsonBodyProperty = rmb_pub_encode_property_for_wemq(wemqMsgType, ptSendMsg);
    if (jsonBodyProperty == NULL) {
		json_object_put(jsonHeader);
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_property_for_wemq return null");
		return -1;
	}

	json_object_object_add(jsonBody, MSG_BODY_PROPERTY_JSON, jsonBodyProperty);

	WEMQJSON *jsonByteBody = rmb_pub_encode_byte_body_for_wemq(wemqMsgType, ptSendMsg);
    if (jsonByteBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_byte_body_for_wemq return null");
		return -1;
	}
	const char *byteBodyStr = json_object_get_string(jsonByteBody);
	
	json_object_object_add(jsonBody, MSG_BODY_BYTE_BODY_JSON, json_object_new_string(byteBodyStr));

	const char *body_str = json_object_get_string(jsonBody);
	if (body_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonBody);

		return -1;
	}

	int iHeaderLen = strlen(header_str);
	int iBodyLen = strlen(body_str);
	int iTotalLen = iHeaderLen +  iBodyLen + 8;

	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, header_str, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, body_str, iBodyLen);
//	json_object_put(jsonHeader);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send:header_str:%s   body_str:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			header_str,
			body_str);
	json_object_put(jsonHeader);
	json_object_put(jsonBody);
	json_object_put(jsonBodyProperty);
	json_object_put(jsonByteBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, iHeaderLen);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_resp_ack_to_access error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}

static int32_t _wemq_thread_dyed_msg_reply_to_access(WemqThreadCtx* pThreadCtx, int seq, int status, char* msgType, StRmbMsg* ptSendMsg)
{
	char* buf = pThreadCtx->m_pSendBuff;
	int iRet = -1;
	WEMQJSON* jsonHeader = json_object_new_object();

	// 组装消息
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(msgType));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(seq));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(status));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}

	WEMQJSON *jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
   char cTopic[128];
   char serviceOrEvent = (*(ptSendMsg->strServiceId + 3) == '0') ? 's' : 'e';
	snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", ptSendMsg->strTargetDcn, serviceOrEvent, ptSendMsg->strServiceId, ptSendMsg->strScenarioId, *(ptSendMsg->strServiceId + 3));
    json_object_object_add(jsonBody, MSG_BODY_TOPIC_STR, json_object_new_string(cTopic));

	WEMQJSON *jsonBodyProperty = rmb_pub_encode_property_for_wemq(THREAD_MSG_CMD_SEND_REPLY, ptSendMsg);
    if (jsonBodyProperty == NULL) {
		json_object_put(jsonHeader);
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_property_for_wemq return null");
		return -1;
	}

	json_object_object_add(jsonBody, MSG_BODY_PROPERTY_JSON, jsonBodyProperty);

	WEMQJSON *jsonByteBody = rmb_pub_encode_byte_body_for_wemq(THREAD_MSG_CMD_SEND_REPLY, ptSendMsg);
    if (jsonByteBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_byte_body_for_wemq return null");
		return -1;
	}
	const char *byteBodyStr = json_object_get_string(jsonByteBody);
	
	json_object_object_add(jsonBody, MSG_BODY_BYTE_BODY_JSON, json_object_new_string(byteBodyStr));

	const char *body_str = json_object_get_string(jsonBody);
	if (body_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonBody);

		return -1;
	}

	int iHeaderLen = strlen(header_str);
	int iBodyLen = strlen(body_str);
	int iTotalLen = iHeaderLen +  iBodyLen + 8;

	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, header_str, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, body_str, iBodyLen);
//	json_object_put(jsonHeader);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send:header_str:%s   body_str:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			header_str,
			body_str);
	json_object_put(jsonHeader);
	json_object_put(jsonBody);
	json_object_put(jsonBodyProperty);
	json_object_put(jsonByteBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, iHeaderLen);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_resp_ack_to_access error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}

static int32_t _wemq_thread_resp_ack_to_access(WemqThreadCtx* pThreadCtx, int seq, int status, char* msgType, StRmbMsg* ptSendMsg)
{
	char* buf = pThreadCtx->m_pSendBuff;
	int iRet = -1;
	WEMQJSON* jsonHeader = json_object_new_object();

	// 组装消息
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(msgType));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(seq));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(status));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}

	WEMQJSON *jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
   char cTopic[128];
   char serviceOrEvent = (*(ptSendMsg->strServiceId + 3) == '0') ? 's' : 'e';
	snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", ptSendMsg->strTargetDcn, serviceOrEvent, ptSendMsg->strServiceId, ptSendMsg->strScenarioId, *(ptSendMsg->strServiceId + 3));
    json_object_object_add(jsonBody, MSG_BODY_TOPIC_STR, json_object_new_string(cTopic));

	WEMQJSON *jsonBodyProperty = rmb_pub_encode_property_for_wemq(THREAD_MSG_CMD_RECV_MSG_ACK, ptSendMsg);
    if (jsonBodyProperty == NULL) {
		json_object_put(jsonHeader);
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_property_for_wemq return null");
		return -1;
	}

	json_object_object_add(jsonBody, MSG_BODY_PROPERTY_JSON, jsonBodyProperty);

	WEMQJSON *jsonByteBody = rmb_pub_encode_byte_body_for_wemq(THREAD_MSG_CMD_RECV_MSG_ACK, ptSendMsg);
    if (jsonByteBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_byte_body_for_wemq return null");
		return -1;
	}
	const char *byteBodyStr = json_object_get_string(jsonByteBody);
	
	json_object_object_add(jsonBody, MSG_BODY_BYTE_BODY_JSON, json_object_new_string(byteBodyStr));

	const char *body_str = json_object_get_string(jsonBody);
	if (body_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonBody);

		return -1;
	}

	int iHeaderLen = strlen(header_str);
	int iBodyLen = strlen(body_str);
	int iTotalLen = iHeaderLen +  iBodyLen + 8;

	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, header_str, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, body_str, iBodyLen);
//	json_object_put(jsonHeader);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send:header_str:%s   body_str:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			header_str,
			body_str);
	json_object_put(jsonHeader);
	json_object_put(jsonBody);
	json_object_put(jsonBodyProperty);
	json_object_put(jsonByteBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, iHeaderLen);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_resp_ack_to_access error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}

static int32_t _wemq_thread_send_error_log(WemqThreadCtx* pThreadCtx, int seq, int errCode, char* logPoint, char* errMsg, StRmbMsg* ptSendMsg)
{
	char* buf = pThreadCtx->m_pSendBuff;
	int iRet = -1;
	WEMQJSON* jsonHeader = json_object_new_object();

	// 组装消息
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(TRACE_LOG_TO_LOGSERVER));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(seq));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(errCode));

	const char* header_str = json_object_get_string(jsonHeader);
	if(header_str == NULL)
	{
		json_object_put(jsonHeader);
		return -1;
	}

	WEMQJSON *jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
    WEMQJSON *jsonMessage = json_object_new_object();
	if (jsonMessage == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
   char cTopic[128];
   char serviceOrEvent = (*(ptSendMsg->strServiceId + 3) == '0') ? 's' : 'e';
	snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", ptSendMsg->strTargetDcn, serviceOrEvent, ptSendMsg->strServiceId, ptSendMsg->strScenarioId, *(ptSendMsg->strServiceId + 3));
    json_object_object_add(jsonMessage, MSG_BODY_TOPIC_STR, json_object_new_string(cTopic));

	WEMQJSON *jsonBodyProperty = rmb_pub_encode_property_for_wemq(THREAD_MSG_CMD_RECV_MSG_ACK, ptSendMsg);
    if (jsonBodyProperty == NULL) {
		json_object_put(jsonHeader);
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_property_for_wemq return null");
		return -1;
	}

	json_object_object_add(jsonMessage, MSG_BODY_PROPERTY_JSON, jsonBodyProperty);

	WEMQJSON *jsonByteBody = rmb_pub_encode_byte_body_for_wemq(THREAD_MSG_CMD_RECV_MSG_ACK, ptSendMsg);
    if (jsonByteBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_byte_body_for_wemq return null");
		return -1;
	}
	const char *byteBodyStr = json_object_get_string(jsonByteBody);
	
	json_object_object_add(jsonMessage, MSG_BODY_BYTE_BODY_JSON, json_object_new_string(byteBodyStr));

	const char *message_str = json_object_get_string(jsonMessage);
	if (message_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonMessage);

		return -1;
	}

	json_object_object_add(jsonBody, "retCode", json_object_new_int(errCode));
    json_object_object_add(jsonBody, "retMsg", json_object_new_string(errMsg));
	json_object_object_add(jsonBody, "level", json_object_new_string("error"));
	json_object_object_add(jsonBody, "logPoint", json_object_new_string(logPoint));
	json_object_object_add(jsonBody, "model", json_object_new_string("model"));
	json_object_object_add(jsonBody, "lang", json_object_new_string("c"));
    json_object_object_add(jsonBody, "message", json_object_new_string(message_str));

	const char *body_str = json_object_get_string(jsonBody);
	if (body_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonBody);

		return -1;
	}

	int iHeaderLen = strlen(header_str);
	int iBodyLen = strlen(body_str);
	int iTotalLen = iHeaderLen +  iBodyLen + 8;

	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, header_str, iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, body_str, iBodyLen);
//	json_object_put(jsonHeader);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Send:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			body_str);
	json_object_put(jsonHeader);
	json_object_put(jsonMessage);
	json_object_put(jsonBodyProperty);
	json_object_put(jsonByteBody);
	json_object_put(jsonBody);
	
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, iHeaderLen);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_send log error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}


/**
 * access to api
 * 		headerJson={"code":0,"seq":"0160616463","command":"REDIRECT_TO_CLIENT"}|bodyJson={"ip":"10.255.34.118", "port":10000}
 */
static int32_t _wemq_thread_on_message_redirect(WemqThreadCtx* pThreadCtx, char* jsonBody)
{
	WEMQJSON* jsonRedirect = NULL;
	WEMQJSON* jsonTmp = NULL;
	int ret = 0;

	pThreadCtx->m_cRedirectIP[0] = '\0';
	pThreadCtx->m_iRedirectPort = 0;
	//json_object_object_get_ex(jsonHeader ,MSG_HEAD_REDIRECT_OBJ, &jsonRedirect);
	jsonRedirect = json_tokener_parse(jsonBody);
	if (jsonRedirect == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "get redirect from body failed");
		return WEMQ_MESSAGE_RET_ERR;
	}

	json_object_object_get_ex(jsonRedirect ,"ip", &jsonTmp);
	if (jsonTmp != NULL)
	{
		const char* ip = json_object_get_string(jsonTmp);
		if (ip != NULL)
		{
			snprintf(pThreadCtx->m_cRedirectIP, sizeof(pThreadCtx->m_cRedirectIP), "%s", ip);
			json_object_object_get_ex(jsonRedirect ,"port", &jsonTmp);
			if (jsonTmp != NULL)
			{
				pThreadCtx->m_iRedirectPort = json_object_get_int(jsonTmp);
				if (pThreadCtx->m_iRedirectPort > 0)
				{
//					pThreadCtx->m_lRedirect = true;
					pThreadCtx->m_lRedirect = 1;
					ret = WEMQ_MESSAGE_RET_REDIRECT;
				}
				else
				{
					pThreadCtx->m_cRedirectIP[0] = '\0';
					pThreadCtx->m_iRedirectPort = 0;
				}
			}
			else
			{
				pThreadCtx->m_cRedirectIP[0] = '\0';
			}
		}
	}

	json_object_put(jsonTmp);
	json_object_put(jsonRedirect);
	return ret;
}

static int32_t _wemq_thread_on_message(WemqThreadCtx* pThreadCtx,bool isRecvNewConnect)
{
	ASSERT(pThreadCtx);

	stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
	StWeMQMSG* pWemqHeader = &pThreadCtx->m_stWeMQMSG;
	WEMQJSON* jsonHeader = NULL;
	WEMQJSON* jsonTmp = NULL;
	const char *usCmd;
	int serRet = -1;
	int bodyLen = 0;
	int seq = -1;
	long time = 0;
	int ret = 0;
	char cMsg[RMB_MAX_ERR_MSG_FROM_ACCESS];
	
	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] |access2wemq_thread|Recv  header:%s  body:%s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pThreadCtx->m_cProxyIP,
			pThreadCtx->m_uiProxyPort,
			pWemqHeader->cStrJsonHeader,
			pWemqHeader->cStrJsonBody);

	bodyLen = pWemqHeader->uiTotalLen - pWemqHeader->uiHeaderLen - 8;
	jsonHeader = json_tokener_parse(pWemqHeader->cStrJsonHeader);
	if (jsonHeader == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] json_tokener_parse header error: %s",
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pWemqHeader->cStrJsonHeader)
		return -1;
	}
	//get command
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_COMMAND_STR, &jsonTmp);
	if (jsonTmp != NULL)
	{
		usCmd = json_object_get_string(jsonTmp);
	}
	//get code
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_CODE_INT, &jsonTmp);
	if (jsonTmp != NULL)
	{
		serRet = json_object_get_int(jsonTmp);
	}
	//get seq
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_SEQ_INT, &jsonTmp);
	if (jsonTmp != NULL)
	{
		seq = json_object_get_int(jsonTmp);
	}
	//get time
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_TIME_LINT, &jsonTmp);
	if (jsonTmp != NULL)
	{
		time = (long)json_object_get_int64(jsonTmp);
	}
	memset(cMsg, 0x00, sizeof(cMsg));
	json_object_object_get_ex(jsonHeader, MSG_HEAD_MSG_STR, &jsonTmp);
	if (jsonTmp != NULL) {
		strncpy(cMsg, json_object_get_string(jsonTmp), sizeof(cMsg) - 1);
	}
	if (strcmp(cMsg, "auth exception") == 0) {  // auth failed
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] Authentication error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort,
				seq,
				time);
		return -1;
	}
	if(serRet != RMB_CODE_SUSS){
	    LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Time:%ld] code from access !=0, cmd=%s, seq=%d, code=%d, msg=%s!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort,
				time,
				usCmd,
				seq,
				serRet,
				cMsg);
	}
		if(strcmp(usCmd, HEARTBEAT_RESPONSE) == 0)		//心跳
		{
			gettimeofday(&pThreadCtx->stTimeLastRecv, NULL);
			pThreadCtx->m_uiHeartBeatCurrent = 0;
			
		}
		else if(strcmp(usCmd, HELLO_RESPONSE) == 0)	
		{
			ret = serRet;
		
		}
		else if(strcmp(usCmd, CLIENT_GOODBYE_RESPONSE) == 0)	
        {
			ret = WEMQ_MESSAGE_RET_CLIENTGOODBYE;
		
        }
		else if(strcmp(usCmd, SERVER_GOODBYE_REQUEST) == 0)	
        {
			ret = WEMQ_MESSAGE_RET_SERVERGOODBYE;
			
        }
		else if(strcmp(usCmd, REDIRECT_TO_CLIENT) == 0)	
		{
			ret = _wemq_thread_on_message_redirect(pThreadCtx, pWemqHeader->cStrJsonBody);
			
		}
		else if(strcmp(usCmd, ASYNC_MESSAGE_TO_SERVER_ACK) == 0)			//ack 单播 from access
		{
			if (serRet == RMB_CODE_SUSS)
			{
				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] PublishAsyncMessage request success!",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);

				LOGRMB(RMB_LOG_DEBUG, "seq for event: %d",  pContextProxy->iSeqForEvent);
				pthread_mutex_lock(&pContextProxy->eventMutex);
				if(seq == pContextProxy->iSeqForEvent){
					pContextProxy->iFlagForEvent = serRet;
					pthread_cond_signal(&pContextProxy->eventCond);
				}
				pthread_mutex_unlock(&pContextProxy->eventMutex);
			}
			else if(serRet == RMB_CODE_AUT_FAIL)
			{
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] PublishAsyncMessage request Authentication fail!",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);

				pthread_mutex_lock(&pContextProxy->eventMutex);
				pContextProxy->iFlagForEvent = serRet;
				pthread_cond_signal(&pContextProxy->eventCond);
				pthread_mutex_unlock(&pContextProxy->eventMutex);

			}
			else
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] PublishAsyncMessage request fail!",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);
				
				pthread_mutex_lock(&pContextProxy->eventMutex);
				pContextProxy->iFlagForEvent = serRet;
				pthread_cond_signal(&pContextProxy->eventCond);
				pthread_mutex_unlock(&pContextProxy->eventMutex);
				
			}
			if (pContextProxy->iFlagForEvent == -1)
			{
				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld]  send event msg signal succ! iFlagForEvent=%d",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time,
						serRet);

			}
 			

		}
		else if(strcmp(usCmd, ASYNC_MESSAGE_TO_CLIENT) == 0)      //recv event message from proxy
		{
			if (serRet == 0)
			{
				StContext* pStContext;
				if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB)
				{
					pStContext = (StContext*)(pContextProxy->pubContext);
				}
				else
				{
					pStContext = (StContext*)(pContextProxy->subContext);
				}

				if (pStContext == NULL) {
					LOGRMB(RMB_LOG_ERROR, "pStContext is null");
					
				}

				int iRet = 0;
				if ((iRet = trans_json_2_rmb_msg(pStContext->pReceiveWemqMsg, pWemqHeader->cStrJsonBody, ASYNC_MESSAGE_TO_CLIENT)) != 0) {
					LOGRMB(RMB_LOG_ERROR, "trans_json_2_rmb_msg failed,buf is:%s", pWemqHeader->cStrJsonBody);
					_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT, "trans_json_2_rmb_msg failed",  pStContext->pReceiveWemqMsg);
					return -1;
				}				
				pStContext->pReceiveWemqMsg->iMsgMode = RMB_MSG_FROM_WEMQ;
				pStContext->pReceiveWemqMsg->cPkgType = QUEUE_PKG;

				pStContext->uiWemqPkgLen = MAX_LENTH_IN_A_MSG;

				set_extfields_2_rmb_msg(pStContext->pReceiveWemqMsg,ASYNC_MESSAGE_TO_CLIENT,seq);

				iRet = shift_msg_2_buf(pStContext->pWemqPkg, &pStContext->uiWemqPkgLen, pStContext->pReceiveWemqMsg);
				//LOGRMB(RMB_LOG_DEBUG, "appHeaderLen:%d", pStContext->pReceiveWemqMsg->iAppHeaderLen);

				//LOGRMB(RMB_LOG_DEBUG, "uiWemqPkgLen:%d", pStContext->uiWemqPkgLen);
				if (iRet < 0) {
					LOGRMB(RMB_LOG_ERROR, "shift_msg_2_buf error!iRet=%d, %d, %s/%s/%s,unique_id=%s,mode=%d,receive=%s",
								iRet,
								pStContext->pReceiveWemqMsg->iEventOrService,
								pStContext->pReceiveWemqMsg->strTargetDcn,
								pStContext->pReceiveWemqMsg->strServiceId,
								pStContext->pReceiveWemqMsg->strScenarioId,
								pStContext->pReceiveWemqMsg->sysHeader.cUniqueId,
								pStContext->pReceiveWemqMsg->iMsgMode,
								rmb_msg_print(pStContext->pReceiveWemqMsg)
								);
					
				}
				//染色消息直接返回
				if (check_dyed_msg(pStContext->pReceiveWemqMsg) > 0)
				{
					_wemq_thread_dyed_msg_ack_to_access(pThreadCtx, seq, 0, ASYNC_MESSAGE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsg);
					LOGRMB(RMB_LOG_DEBUG, "get dyed msg:%s", pWemqHeader->cStrJsonBody);
					return serRet;
				}	
				int iMqIndex = req_mq_index;
				LOGRMB(RMB_LOG_DEBUG, "WEMQ_CMD_ASYNCEVENT_SUB:Ready to enqueue");

				if (pRmbStConfig->iFlagForReq == (int)MSG_IPC_MQ)
				{
					//if (bodyLen >= pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize - C_RMB_MQ_PKG_HEAD_SIZE)
					if (pStContext->uiWemqPkgLen >= pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize - C_RMB_MQ_PKG_HEAD_SIZE)
					{
						LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] receive reqSize=%u bigger than shmSize=%u,discard",
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pStContext->uiWemqPkgLen,
								pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize);
						_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT, "receive reqSize bigger than shmSize",  pStContext->pReceiveWemqMsg);
					}
					else
					{
						while ((iRet = rmb_context_enqueue(pStContext, (const enum RmbMqIndex)iMqIndex,
								pStContext->pWemqPkg, pStContext->uiWemqPkgLen)) == -2)
						{
							// LOG
							LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] req queue full!wait!",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID);
							usleep(1000);
						}
						if (iRet != 0)
						{
							LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] req wemq_context_enqueue error!enqueue failed=%d!receive=%s",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									iRet,
									pWemqHeader->cStrJsonBody);
							_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"req wemq_context_enqueue error!enqueue failed",  pStContext->pReceiveWemqMsg);
							return 0;
						}
						else 
						{
							LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] Enqueue succ, msg %s",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									pWemqHeader->cStrJsonBody);
						}
					}
				}
				else
				{
					iRet = sendto(pStContext->iSocketForReq, pStContext->pWemqPkg, pStContext->uiWemqPkgLen, 0,
								(const struct sockaddr *)&pStContext->tmpReqAddr, sizeof(pStContext->tmpReqAddr));
					if (iRet < 0) {
						LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] sendto failed=%d,message is:%s",
								pThreadCtx->m_contextType, pThreadCtx->m_threadID, iRet, pWemqHeader->cStrJsonBody);
					}
				}
			}
		}

		else if(strcmp(usCmd, REQUEST_TO_CLIENT) == 0) 			//sub端收到RR请求消息
		{
			if (serRet == 0)
			{
				StContext* pStContext;
				if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB)
				{
					pStContext = (StContext*)(pContextProxy->pubContext);
				}
				else
				{
					pStContext = (StContext*)(pContextProxy->subContext);
				}

				if (pStContext == NULL) {
					LOGRMB(RMB_LOG_ERROR, "pStContext is null");
				}

				int iRet = 0;
				if ((iRet = trans_json_2_rmb_msg(pStContext->pReceiveWemqMsg, pWemqHeader->cStrJsonBody, REQUEST_TO_CLIENT)) != 0) {
					LOGRMB(RMB_LOG_ERROR, "trans_json_2_rmb_msg failed,buf is:%s", pWemqHeader->cStrJsonBody);
					_wemq_thread_send_error_log(pThreadCtx, seq,  -1, LOG_ERROR_POINT, "trans_json_2_rmb_msg failed", pStContext->pReceiveWemqMsg);
					return -1;
				}

				pStContext->pReceiveWemqMsg->iMsgMode = RMB_MSG_FROM_WEMQ;
				pStContext->pReceiveWemqMsg->cPkgType = QUEUE_PKG;

				pStContext->uiWemqPkgLen = MAX_LENTH_IN_A_MSG;

				set_extfields_2_rmb_msg(pStContext->pReceiveWemqMsg,REQUEST_TO_CLIENT,seq);
				
				iRet = shift_msg_2_buf(pStContext->pWemqPkg, &pStContext->uiWemqPkgLen, pStContext->pReceiveWemqMsg);
				//LOGRMB(RMB_LOG_DEBUG, "uiWemqPkgLen:%d", pStContext->uiWemqPkgLen);

				if (iRet < 0) {
					LOGRMB(RMB_LOG_ERROR, "shift_msg_2_buf error!iRet=%d, %d, %s/%s/%s,unique_id=%s,mode=%d,receive=%s",
								iRet,
								pStContext->pReceiveWemqMsg->iEventOrService,
								pStContext->pReceiveWemqMsg->strTargetDcn,
								pStContext->pReceiveWemqMsg->strServiceId,
								pStContext->pReceiveWemqMsg->strScenarioId,
								pStContext->pReceiveWemqMsg->sysHeader.cUniqueId,
								pStContext->pReceiveWemqMsg->iMsgMode,
								rmb_msg_print(pStContext->pReceiveWemqMsg)
								);
					_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT, "shift_msg_2_buf failed", pStContext->pReceiveWemqMsg);
					return -1;
				}

				int iMqIndex = req_mq_index;
				//染色消息直接返回
				if (check_dyed_msg(pStContext->pReceiveWemqMsg) > 0)
				{
					LOGRMB(RMB_LOG_DEBUG, "get dyed msg:%s", pWemqHeader->cStrJsonBody);
					_wemq_thread_dyed_msg_ack_to_access(pThreadCtx, seq, 0, REQUEST_TO_CLIENT_ACK, pStContext->pReceiveWemqMsg);
					_wemq_thread_dyed_msg_reply_to_access(pThreadCtx,seq,0,RESPONSE_TO_SERVER,pStContext->pReceiveWemqMsg);
					return serRet;
				}				
				LOGRMB(RMB_LOG_DEBUG, "WEMQ_CMD_SYNCREQ:Ready to en queue");

				if (pRmbStConfig->iFlagForReq == (int)MSG_IPC_MQ)
				{
					//if (bodyLen >= pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize - C_RMB_MQ_PKG_HEAD_SIZE)
					if (pStContext->uiWemqPkgLen >= pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize - C_RMB_MQ_PKG_HEAD_SIZE)
					{
						LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] receive reqSize=%u bigger than shmSize=%u,discard",
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pStContext->uiWemqPkgLen,
								pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize);
						_wemq_thread_send_error_log(pThreadCtx, seq,  -1, LOG_ERROR_POINT, "receive reqSize bigger than shmSize", pStContext->pReceiveWemqMsg);
					}
					else
					{
						while ((iRet = rmb_context_enqueue(pStContext, (const enum RmbMqIndex)iMqIndex,
								pStContext->pWemqPkg, pStContext->uiWemqPkgLen)) == -2)
						{
							// LOG
							LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] req queue full!wait!",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID);
							usleep(1000);
						}
						if (iRet != 0)
						{
							LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] req wemq_context_enqueue error!enqueue failed=%d!receive=%s",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									iRet,
									pWemqHeader->cStrJsonBody);
							_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"req wemq_context_enqueue error!enqueue failed", pStContext->pReceiveWemqMsg);
						}
						else
						{
							LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] Enqueue succ, msg: header  %s  body %s",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									pWemqHeader->cStrJsonHeader,
									pWemqHeader->cStrJsonBody);
						}
					}
				}
				else
				{
					iRet = sendto(pStContext->iSocketForReq, pStContext->pWemqPkg, pStContext->uiWemqPkgLen, 0,
								(const struct sockaddr *)&pStContext->tmpReqAddr, sizeof(pStContext->tmpReqAddr));
					if (iRet < 0) {
						LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] sendto failed=%d,message is:%s",
								pThreadCtx->m_contextType, pThreadCtx->m_threadID, iRet, pWemqHeader->cStrJsonBody);
					}
				}
			}
		}
		else if(strcmp(usCmd, RESPONSE_TO_CLIENT) == 0)			//rr请求端收到的回包
		{
			if (serRet == 0) //rsp succ
            {
				WEMQJSON *jsonByteBody = NULL;
				WEMQJSON *systemHeader = NULL;
				WEMQJSON *sysExtFields = NULL;
				WEMQJSON *jsonDecoder = NULL;
				int rrType = 0;
				WEMQJSON *jsonBody = json_tokener_parse(pWemqHeader->cStrJsonBody);
				if (jsonBody == NULL) {
				     LOGRMB(RMB_LOG_ERROR, "json_tokener_parse failed!,buf is:%s", pWemqHeader->cStrJsonBody);
				     return -1;
				}
				if (!json_object_object_get_ex(jsonBody, MSG_BODY_BYTE_BODY_JSON, &jsonByteBody)) {
				     LOGRMB(RMB_LOG_ERROR, "body json no byte body!");
				     return -1;
				}//byte body json

				jsonByteBody = json_tokener_parse(json_object_get_string(jsonByteBody));
				if (!json_object_object_get_ex(jsonByteBody, MSG_BODY_BYTE_BODY_SYSTEM_HEADER_CONTENT_JSON, &systemHeader)) {
				     LOGRMB(RMB_LOG_ERROR, "byte body json no system header content!");
				     return -1;
				}//system header json

				systemHeader = json_tokener_parse(json_object_get_string(systemHeader));
				if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_EXTFIELDS_STR, &sysExtFields)) {
				     sysExtFields = json_tokener_parse(json_object_get_string(sysExtFields));
				     if(json_object_object_get_ex(sysExtFields, MSG_BODY_SYSTEM_RRTYPE_INT, &jsonDecoder)){
						rrType = json_object_get_int(jsonDecoder);
					}
				}//system extFields RRtype
				if(0 == rrType)
				{
					if (bodyLen >= (TCP_BUF_SIZE * sizeof(char))) {
						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] get rsp too long,bodyLen=%d,buf_le=%d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								pThreadCtx->m_cProxyIP,
								pThreadCtx->m_uiProxyPort,
								seq,
								time,
								bodyLen,
								TCP_BUF_SIZE);
					}
					int i = 0;
					int recvFlag = 0;
					pthread_mutex_lock(&pContextProxy->rrMutex);
					if (pContextProxy->iFlagForRR == -1)
					{
						LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] signal succ! iFlagForRR=0",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								pThreadCtx->m_cProxyIP,
								pThreadCtx->m_uiProxyPort,
								seq,
								time);
						memcpy(pContextProxy->mPubRRBuf, pWemqHeader->cStrJsonBody, bodyLen);
						pContextProxy->mPubRRBuf[bodyLen] = '\0';
						trans_json_2_rmb_msg(pContextProxy->pReplyMsg, pContextProxy->mPubRRBuf, RESPONSE_TO_CLIENT);
						//LOGRMB(RMB_LOG_DEBUG, "destname :%s", pContextProxy->pReplyMsg->dest.cDestName);
						GetRmbNowLongTime();
		                pContextProxy->pReplyMsg->sysHeader.ulReplyReceiveTime = pRmbStConfig->ulNowTtime;
						set_extfields_2_rmb_msg(pContextProxy->pReplyMsg,RESPONSE_TO_CLIENT,seq);
						if (pContextProxy->stUnique.flag == 1 && strcmp(pContextProxy->stUnique.unique_id, pContextProxy->pReplyMsg->sysHeader.cUniqueId) == 0) {
						if (check_dyed_msg(pContextProxy->pReplyMsg) > 0) {
							LOGRMB(RMB_LOG_DEBUG, "get dyed msg:%s", pContextProxy->mPubRRBuf);
							pContextProxy->iFlagForRR = RMB_CODE_DYED_MSG;	
						}else{
							pContextProxy->iFlagForRR = serRet;
						}						
							pContextProxy->stUnique.flag = 0;
							pthread_cond_signal(&pContextProxy->rrCond);
							recvFlag = 1;

						}
					}
	 				pthread_mutex_unlock(&pContextProxy->rrMutex);
					if(recvFlag == 1){
                  	  _wemq_thread_resp_ack_to_access(pThreadCtx, seq, 0, RESPONSE_TO_CLIENT_ACK, pContextProxy->pReplyMsg);
                 	  //_wemq_thread_send_error_log(pThreadCtx, seq, 0, "ok",  pContextProxy->pReplyMsg);
                    }
				}
				else//case WEMQ_CMD_ASYNCRSP RR异步消息，服务请求方收到回包
				{
					StContext* pStContext = (StContext *)(pContextProxy->subContext);
					pContextProxy->iFlagForRRAsync = 0;
					if (pStContext == NULL) {
						LOGRMB(RMB_LOG_ERROR, "[TID:%lu] pStContext is null", pThreadCtx->m_threadID);
						return -1;
					}

					int iRet = 0;

					if ((iRet = trans_json_2_rmb_msg(pStContext->pReceiveWemqMsgForRR, pWemqHeader->cStrJsonBody, RESPONSE_TO_CLIENT)) != 0) {
						LOGRMB(RMB_LOG_ERROR, "trans_json_2_rmb_msg failed,buf is:%s", pWemqHeader->cStrJsonBody);
						_wemq_thread_resp_ack_to_access(pThreadCtx, seq, -1, RESPONSE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsgForRR);
						_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"trans_json_2_rmb_msg failed", pStContext->pReceiveWemqMsgForRR);
						return -1;
					}
					GetRmbNowLongTime();
		            pStContext->pReceiveWemqMsgForRR->sysHeader.ulReplyReceiveTime = pRmbStConfig->ulNowTtime;
					int i;
					set_extfields_2_rmb_msg(pStContext->pReceiveWemqMsgForRR,RESPONSE_TO_CLIENT,seq);
					int recvFlag = 0;
					pthread_mutex_lock(&pContextProxy->rrMutex);
					//因为在access goodbye的时候，消息可能在旧连接上，所以必须同时扫描新旧连接，不能只扫描新连接

					//if(isRecvNewConnect)
						//{
						for (i = 0; i < pContextProxy->pUniqueListForRRAsyncNew.get_array_size(&pContextProxy->pUniqueListForRRAsyncNew); i++) {								
							if (pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag == 1 && strcmp(pContextProxy->pUniqueListForRRAsyncNew.Data[i].unique_id, pStContext->pReceiveWemqMsgForRR->sysHeader.cUniqueId) == 0) {
								pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag = 0;
								recvFlag = 1;
								break;
							}
						}
			//		}else
				//	{
						for (i = 0; i < pContextProxy->pUniqueListForRRAsyncOld.get_array_size(&pContextProxy->pUniqueListForRRAsyncOld); i++) {
							if (pContextProxy->pUniqueListForRRAsyncOld.Data[i].flag == 1 && strcmp(pContextProxy->pUniqueListForRRAsyncOld.Data[i].unique_id, pStContext->pReceiveWemqMsgForRR->sysHeader.cUniqueId) == 0) {
								pContextProxy->pUniqueListForRRAsyncOld.Data[i].flag = 0;
								recvFlag = 1;
								break;
							}
						}
					pthread_mutex_unlock(&pContextProxy->rrMutex);	
					//}
					//已经超时，不回包给pub端
					if(recvFlag == 0 ){
						LOGRMB(RMB_LOG_WARN, "rr async response bizseq=%s, uniqueId=%s comes back, but request has timeout", pStContext->pReceiveWemqMsgForRR->sysHeader.cBizSeqNo, pStContext->pReceiveWemqMsgForRR->sysHeader.cUniqueId);
						return -1;
					}
					
					pStContext->pReceiveWemqMsgForRR->cPkgType = RR_TOPIC_PKG;
					pStContext->pReceiveWemqMsgForRR->iMsgMode = RMB_MSG_FROM_WEMQ;
					pStContext->uiWemqPkgForRRAsyncLen = MAX_LENTH_IN_A_MSG;
					iRet = shift_msg_2_buf(pStContext->pWemqPkgForRRAsync, &pStContext->uiWemqPkgForRRAsyncLen, pStContext->pReceiveWemqMsgForRR);
					if (iRet < 0) {
						LOGRMB(RMB_LOG_ERROR, "shift_msg_2_buf error!iRet=%d, %d, %s/%s/%s,unique_id=%s,mode=%d,receive=%s",
										iRet,
										pStContext->pReceiveWemqMsgForRR->iEventOrService,
										pStContext->pReceiveWemqMsgForRR->strTargetDcn,
										pStContext->pReceiveWemqMsgForRR->strServiceId,
										pStContext->pReceiveWemqMsgForRR->strScenarioId,
										pStContext->pReceiveWemqMsgForRR->sysHeader.cUniqueId,
										pStContext->pReceiveWemqMsgForRR->iMsgMode,
										rmb_msg_print(pStContext->pReceiveWemqMsgForRR));
						_wemq_thread_resp_ack_to_access(pThreadCtx, seq, -1, RESPONSE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsgForRR);
						_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"shift_msg_2_buf error", pStContext->pReceiveWemqMsgForRR);
						return -1;
					}
					if (check_dyed_msg(pStContext->pReceiveWemqMsgForRR) > 0)
					{
						LOGRMB(RMB_LOG_DEBUG, "get dyed msg:%s", pWemqHeader->cStrJsonBody);
						_wemq_thread_resp_ack_to_access(pThreadCtx, seq, 0, RESPONSE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsgForRR);
						return serRet;
					}
				
					int iMqIndex = rr_rsp_mq_index;
					LOGRMB(RMB_LOG_DEBUG, "WEMQ_CMD_ASYNCRSP:Recv rr rsp Ready to en queue");
					if (pRmbStConfig->iFlagForRRrsp == (int)MSG_IPC_MQ)
					{
						if (pStContext->uiWemqPkgForRRAsyncLen >= pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize - C_RMB_MQ_PKG_HEAD_SIZE)
						{
							LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] receive reqSize=%u bigger than shmSize=%u,discard",
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									pStContext->uiWemqPkgForRRAsyncLen,
									pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize);
							_wemq_thread_resp_ack_to_access(pThreadCtx, seq, -1, RESPONSE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsgForRR);
						    _wemq_thread_send_error_log(pThreadCtx, seq, -1,LOG_ERROR_POINT, "receive msg bigger than shmSize", pStContext->pReceiveWemqMsgForRR);
						}
						else
						{
							while ((iRet = rmb_context_enqueue(pStContext, (const enum RmbMqIndex)iMqIndex,
									pStContext->pWemqPkgForRRAsync, pStContext->uiWemqPkgForRRAsyncLen)) == -2)
							{
								// LOG
								LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] req queue full!wait!",
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID);
								usleep(1000);
							}
							if (iRet != 0)
							{
								LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] req wemq_context_enqueue error!enqueue failed=%d!receive=%s",
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID,
										iRet,
										pWemqHeader->cStrJsonBody);
								_wemq_thread_resp_ack_to_access(pThreadCtx, seq, -1, RESPONSE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsgForRR);
						        _wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"req wemq_context_enqueue error", pStContext->pReceiveWemqMsgForRR);
							}
							else 
							{
								LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] Enqueue succ, msg: header  %s  body %s",
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID,
										pWemqHeader->cStrJsonHeader,
										pWemqHeader->cStrJsonBody);
								_wemq_thread_resp_ack_to_access(pThreadCtx, seq, 0, RESPONSE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsgForRR);
							}
						}
					}
					else
					{
						iRet = sendto(pStContext->iSocketForRsp, pStContext->pWemqPkgForRRAsync, pStContext->uiWemqPkgForRRAsyncLen, 0,
									(const struct sockaddr *)&pStContext->tmpReplyAddr, sizeof(pStContext->tmpReplyAddr));
						if (iRet < 0) {
							LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] sendto failed=%d,message is:%s",
									pThreadCtx->m_contextType, pThreadCtx->m_threadID, iRet, pWemqHeader->cStrJsonBody);
						}
					}
				}
				json_object_put(jsonByteBody);
				json_object_put(systemHeader);
				json_object_put(sysExtFields);
				json_object_put(jsonDecoder);
				json_object_put(jsonBody);
			}
			else if(serRet == RMB_CODE_AUT_FAIL)
			{
		 		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] rr request ret Authentication fail",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);
				
				pthread_mutex_lock(&pContextProxy->rrMutex);
				pContextProxy->iFlagForRR = serRet;
				pContextProxy->stUnique.flag = 0;
				pthread_cond_signal(&pContextProxy->rrCond);
	 			pthread_mutex_unlock(&pContextProxy->rrMutex);
			}
			else
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] rr request ret fail",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);

				pthread_mutex_lock(&pContextProxy->rrMutex);
				pContextProxy->iFlagForRR = serRet;
				pContextProxy->stUnique.flag = 0;
				pthread_cond_signal(&pContextProxy->rrCond);
	 			pthread_mutex_unlock(&pContextProxy->rrMutex);
			}
		}
		else if(strcmp(usCmd, BROADCAST_MESSAGE_TO_SERVER_ACK) == 0)	//收到广播消息的ack
		{
			if (serRet == RMB_CODE_SUSS) {
				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] Publish Broadcast Message request success",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);
			   
			}
			else if(serRet == RMB_CODE_AUT_FAIL)
			{
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] Publish Broadcast Message request Authentication fail!",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);
			}
			else {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] Publish Broadcast Message request failed!",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time);

			}

			pthread_mutex_lock(&pContextProxy->eventMutex);
			if (pContextProxy->iFlagForEvent == -1)
			{
				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld]  send Broadcast msg signal succ! iFlagForEvent=%d",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						seq,
						time,
						serRet);
				LOGRMB(RMB_LOG_DEBUG, "seq for Broadcast: %d",  pContextProxy->iSeqForEvent);
                if(seq == pContextProxy->iSeqForEvent){
					pContextProxy->iFlagForEvent = serRet;
					pthread_cond_signal(&pContextProxy->eventCond);
				}
			}
 			pthread_mutex_unlock(&pContextProxy->eventMutex);
		}

		else if(strcmp(usCmd, BROADCAST_MESSAGE_TO_CLIENT) == 0)  //收到广播消息
		{
			if (serRet == 0) {
					StContext *pStContext = NULL;
				if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) {
					pStContext = (StContext *)(pContextProxy->pubContext);
				} else {
					pStContext = (StContext *)(pContextProxy->subContext);
				}

				if (pStContext == NULL) {
					LOGRMB(RMB_LOG_ERROR, "[TID:%lu] pStContext is null", pThreadCtx->m_threadID);
					return -1;
				}

				int iRet = 0;

				if ((iRet = trans_json_2_rmb_msg(pStContext->pReceiveWemqMsgForBroadCast, pWemqHeader->cStrJsonBody, BROADCAST_MESSAGE_TO_CLIENT)) != 0) {
					LOGRMB(RMB_LOG_ERROR, "trans_json_2_rmb_msg failed,buf is:%s", pWemqHeader->cStrJsonBody);
					return -1;
				}

				pStContext->pReceiveWemqMsgForBroadCast->cPkgType = BROADCAST_TOPIC_PKG;
				pStContext->pReceiveWemqMsgForBroadCast->iMsgMode = RMB_MSG_FROM_WEMQ;
				pStContext->uiWemqPkgLen = MAX_LENTH_IN_A_MSG;

				set_extfields_2_rmb_msg(pStContext->pReceiveWemqMsgForBroadCast,BROADCAST_MESSAGE_TO_CLIENT,seq);
				
				iRet = shift_msg_2_buf(pStContext->pWemqPkg, &pStContext->uiWemqPkgLen, pStContext->pReceiveWemqMsgForBroadCast);
				if (iRet < 0) {
					LOGRMB(RMB_LOG_ERROR, "shift_msg_2_buf error!iRet=%d, %d, %s/%s/%s,unique_id=%s,mode=%d,receive=%s",
									iRet,
									pStContext->pReceiveWemqMsgForBroadCast->iEventOrService,
									pStContext->pReceiveWemqMsgForBroadCast->strTargetDcn,
									pStContext->pReceiveWemqMsgForBroadCast->strServiceId,
									pStContext->pReceiveWemqMsgForBroadCast->strScenarioId,
									pStContext->pReceiveWemqMsgForBroadCast->sysHeader.cUniqueId,
									pStContext->pReceiveWemqMsgForBroadCast->iMsgMode,
									rmb_msg_print(pStContext->pReceiveWemqMsgForBroadCast));
					_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"shift_msg_2_buf error",  pStContext->pReceiveWemqMsgForBroadCast);
					
					return -1;
				}
				//染色消息直接返回
				if (check_dyed_msg(pStContext->pReceiveWemqMsgForBroadCast) > 0)
				{
					_wemq_thread_dyed_msg_ack_to_access(pThreadCtx, seq, 0, BROADCAST_MESSAGE_TO_CLIENT_ACK, pStContext->pReceiveWemqMsg);
					LOGRMB(RMB_LOG_DEBUG, "get dyed msg:%s", pWemqHeader->cStrJsonBody);
					return serRet;
				}	

				int iMqIndex = broadcast_mq_index;
				LOGRMB(RMB_LOG_DEBUG, "WEMQ_CMD_BROADCAST_SUB:Recv broadcast message ready to enqueue");
				if (pRmbStConfig->iFlagForBroadCast == (int)MSG_IPC_MQ) {
					if (pStContext->uiWemqPkgLen >= pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize - C_RMB_MQ_PKG_HEAD_SIZE) {
						LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] receive broadcast message reqSize=%u bigger than shmSize=%u,discard",
								pThreadCtx->m_contextType, pThreadCtx->m_threadID, pStContext->uiWemqPkgLen,
								pStContext->fifoMq.mqIndex[iMqIndex]->mq->uiBlockSize);
						_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"receive broadcast message reqSize bigger than shmSize",  pStContext->pReceiveWemqMsgForBroadCast);
					} else {
						while ((iRet = rmb_context_enqueue(pStContext, (const enum RmbMqIndex)iMqIndex,
								pStContext->pWemqPkg, pStContext->uiWemqPkgLen)) == -2) {
							//queue full
							LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] req queue full!wait!", pThreadCtx->m_contextType, pThreadCtx->m_threadID);
							usleep(1000);
						}
						if (iRet != 0) {
							LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] req rmb_context_enqueue error!iRet=%d!receive=%s",
									pThreadCtx->m_contextType, pThreadCtx->m_threadID, iRet, pWemqHeader->cStrJsonBody);
							_wemq_thread_send_error_log(pThreadCtx, seq, -1, LOG_ERROR_POINT,"req rmb_context_enqueue error",  pStContext->pReceiveWemqMsgForBroadCast);
						} else {
							LOGRMB(RMB_LOG_DEBUG, "[Type:%d] [TID:%lu] enqueue succ, msg: header  %s  body %s",
									pThreadCtx->m_contextType, pThreadCtx->m_threadID, pWemqHeader->cStrJsonHeader,pWemqHeader->cStrJsonBody);
						}
					}
				} else {
					iRet = sendto(pStContext->iSocketForBroadcast, pStContext->pWemqPkg, pStContext->uiWemqPkgLen, 0,
							(const struct sockaddr *)&pStContext->tmpBroadcastAddr, sizeof(pStContext->tmpBroadcastAddr));
					if (iRet < 0) {
						LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] udp error!sendto failed=%d!receive message:%s",
								pThreadCtx->m_contextType, pThreadCtx->m_threadID,
								iRet, pWemqHeader->cStrJsonBody);
					}
				}
			}
		}
		else if(strcmp(usCmd, SUBSCRIBE_RESPONSE) == 0)  // add subscribe response
		{
			if (serRet != 0) {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] add listen return failed, iRet=%d, errmsg=%s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						serRet,
						cMsg);
			}

			pthread_mutex_lock(&pContextProxy->regMutex);
			if (pContextProxy->iFlagForReg == 0) {
				pContextProxy->iFlagForReg = 1;
				pContextProxy->iResultForReg = serRet;
				pthread_cond_signal(&pContextProxy->regCond);
			}
			pthread_mutex_unlock(&pContextProxy->regMutex);
		}
		else if(strcmp(usCmd, LISTEN_RESPONSE) == 0)  // add listen start response
		{
			
			if (serRet == RMB_CODE_OTHER_FAIL) {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] send start to access failed,iRet=%d, errmsg=%s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						serRet,
						cMsg);
			}

			if (serRet == RMB_CODE_AUT_FAIL) {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] send start to access authentication failed,iRet=%d, errmsg=%s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						serRet,
						cMsg);
			}

			pthread_mutex_lock(&pContextProxy->regMutex);
			if (pContextProxy->iFlagForReg == 0) {
				pContextProxy->iFlagForReg = 1;
				pContextProxy->iResultForReg = serRet;
				pthread_cond_signal(&pContextProxy->regCond);
			}
			pthread_mutex_unlock(&pContextProxy->regMutex);

		}

		else{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] No Such Command:%s!",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					usCmd);
			ret = -1;
		}
	

	json_object_put(jsonTmp);
	json_object_put(jsonHeader);

	return ret;
}

static int32_t _wemq_thread_do_recv_sync(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	int timeout = pRmbStConfig->iWemqTcpSocketTimeout;

	unsigned int uiTimeOutTimes = 0;
	unsigned int uiClosedByPeerTimes = 0;
	while(1)
	{
		uint32_t iRecvLen;
		int iRet = wemq_tcp_recv(pThreadCtx->m_iSockFd, pThreadCtx->m_pRecvBuff, &iRecvLen, timeout);
		if (iRet == 0)
		{
			uiTimeOutTimes += 1;
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] TCP recv timeout!timeout_times=%u",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					uiTimeOutTimes);
			if (uiTimeOutTimes >= 12) {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] TCP continuity recv timeout times=%d, so close connect",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					uiTimeOutTimes);
				uiTimeOutTimes = 0;
				_wemq_thread_del_fd(pThreadCtx);
				wemq_tcp_close(pThreadCtx->m_iSockFd);
				pThreadCtx->m_iSockFd = -1;
				wemq_proxy_to_black_list(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
				return -2;
			}
			continue;
		}
		else if (iRet == -2)
		{
			uiClosedByPeerTimes += 1;
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] TCP conncet closed by peer(%d)",
					STATE_MAP[pThreadCtx->m_iState] ,
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					errno);
			if (uiClosedByPeerTimes >= 3) {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] TCP conncet continuity closed by peer times=%d, so close connect",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					uiClosedByPeerTimes);
				uiClosedByPeerTimes = 0;
				_wemq_thread_del_fd(pThreadCtx);
				wemq_tcp_close(pThreadCtx->m_iSockFd);
				pThreadCtx->m_iSockFd = -1;
				wemq_proxy_to_black_list(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
				return -2;
			}
			continue;
		}
		else if (iRet == -3)
		{
			// msg is not full
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] msg is not full",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			_wemq_thread_del_fd(pThreadCtx);
			wemq_tcp_close(pThreadCtx->m_iSockFd);
			pThreadCtx->m_iSockFd = -1;

			return 1;
		}
		else if (iRet < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] wemq_tcp_recv error",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			return -1;
		}

		LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] recv complete len %d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort,
				iRet);

		//memset(&pThreadCtx->m_stWeMQMSG, 0, sizeof(pThreadCtx->m_stWeMQMSG));
		memset(&pThreadCtx->m_stWeMQMSG, 0, (sizeof(int) * 2));
		DecodeWeMQMsg(&pThreadCtx->m_stWeMQMSG, pThreadCtx->m_pRecvBuff, iRet);
		if (pThreadCtx->m_stWeMQMSG.uiHeaderLen == 0 || pThreadCtx->m_stWeMQMSG.uiHeaderLen >= MAX_WEMQ_HEADER_LEN)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] recv header len %u is 0 or too long",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort,
							pThreadCtx->m_cProxyIP,
							pThreadCtx->m_uiProxyPort,
							pThreadCtx->m_stWeMQMSG.uiHeaderLen);
			return -1;
		}
		if (pThreadCtx->m_stWeMQMSG.uiHeaderLen > 0)
		{
			memcpy(pThreadCtx->m_stWeMQMSG.cStrJsonHeader, pThreadCtx->m_pRecvBuff + 8, pThreadCtx->m_stWeMQMSG.uiHeaderLen);
			pThreadCtx->m_stWeMQMSG.cStrJsonHeader[pThreadCtx->m_stWeMQMSG.uiHeaderLen] = '\0';
		}
		unsigned int uiTmpBodyLen = pThreadCtx->m_stWeMQMSG.uiTotalLen - pThreadCtx->m_stWeMQMSG.uiHeaderLen - 8;
		if (uiTmpBodyLen >= MAX_WEMQ_BODY_LEN)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] recv body len %d is too long",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								pThreadCtx->m_cProxyIP,
								pThreadCtx->m_uiProxyPort,
								uiTmpBodyLen);
			return -1;
		}
		if (uiTmpBodyLen == 0)
		{
			LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] recv body len is 0",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								pThreadCtx->m_cProxyIP,
								pThreadCtx->m_uiProxyPort);
		}
		//if (pThreadCtx->m_stWeMQMSG.uiTotalLen - pThreadCtx->m_stWeMQMSG.uiHeaderLen - 8 > 0)
		if (uiTmpBodyLen > 0)
		{
			memcpy(pThreadCtx->m_stWeMQMSG.cStrJsonBody,
				   pThreadCtx->m_pRecvBuff + 8 + pThreadCtx->m_stWeMQMSG.uiHeaderLen,
				   pThreadCtx->m_stWeMQMSG.uiTotalLen - pThreadCtx->m_stWeMQMSG.uiHeaderLen - 8);
			pThreadCtx->m_stWeMQMSG.cStrJsonBody[uiTmpBodyLen] = '\0';
		}
		LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d]  Decode Wemq Header complete,total len %d, header len %d,header %s",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_stWeMQMSG.uiTotalLen,
				pThreadCtx->m_stWeMQMSG.uiHeaderLen,
				pThreadCtx->m_stWeMQMSG.cStrJsonHeader);
		return 0;
	}
	return -1;
}

static int32_t _wemq_thread_do_recv_async(WemqThreadCtx* pThreadCtx,bool isRecvNewConnect)
{
	ASSERT(pThreadCtx);
	
	int nfds =  epoll_wait(pThreadCtx->m_iEpollFd, pThreadCtx->m_ptEvents, MAX_EVENTS, 1);
	if (nfds == -1)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] epoll wait error:%d!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				errno);
		return -1;
	}
	if (nfds == 0)
	{
		return 0;
	}

	unsigned long ulLastTime = 0;
	unsigned long ulNowTime = 0;
	struct timeval tv;

	int iTmp = 0;
	int iRecvd = 0;
	int iRemind = TCP_PKG_LEN_BTYES;

	

	for (iTmp = 0; iTmp < nfds; ++iTmp)
	{
		// 必须在此判断，如果新旧连接都有消息过来，后面会把当前fd改成新连接的fd，导致下次循环又会重新处理
	    if(!isRecvNewConnect)
	    {
		    pThreadCtx->m_iSockFdNew = pThreadCtx->m_iSockFd;
		    pThreadCtx->m_iSockFd = pThreadCtx->m_iSockFdOld;
	    }	
		if (pThreadCtx->m_ptEvents[iTmp].data.fd == pThreadCtx->m_iSockFd)
		{
			if (EPOLLIN == (pThreadCtx->m_ptEvents[iTmp].events & EPOLLIN))
			{
				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] EPOLLIN EVENT",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort);
				gettimeofday(&tv, NULL);
				ulLastTime = tv.tv_sec * 1000000 + tv.tv_usec;
				ulNowTime = ulLastTime;
				while (iRemind > 0)
				{
					int iRecv = recv(pThreadCtx->m_iSockFd,pThreadCtx->m_pRecvBuff + iRecvd, iRemind, 0);
					//if (iRecv < 0 && (errno != EAGAIN))
					if (iRecv < 0)
					{
						if (errno == EAGAIN) {
							gettimeofday(&tv, NULL);
							ulNowTime = tv.tv_sec * 1000000 + tv.tv_usec;
							if ((ulNowTime - ulLastTime) > (1 * 60 * 1000000)) {
								LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
										STATE_MAP[pThreadCtx->m_iState],
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID,
										pThreadCtx->m_iLocalPort,
										errno);
								_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);
								return -2;
							}

							usleep(100);
							continue;
						}

						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								errno);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);								
						return -2;
					}
					if (iRecv == 0)
					{
						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Peer close connect, fd close() ret=%d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								iRecv);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);								
						return -2;
					}
					if (iRecv > 0)
					{
						iRecvd += iRecv;
						iRemind -= iRecv;
					}
				}
				
				if (iRecvd != 4 || strcmp(pThreadCtx->m_pRecvBuff, "WEMQ") != 0) {
					if(!isRecvNewConnect){
						LOGRMB(RMB_LOG_ERROR, "IP: [old proxy ip:%s|old port:%d]", pThreadCtx->m_cProxyIPOld, pThreadCtx->m_uiProxyPortOld);
					}
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%d] recv header error, buf: %s, len: %d",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort,
							pThreadCtx->m_cProxyIP,
							pThreadCtx->m_uiProxyPort,
							pThreadCtx->m_pRecvBuff,
							iRecvd);
					_wemq_thread_reset_sockFd(pThreadCtx, isRecvNewConnect);							
					return -2;
				}

				gettimeofday(&tv, NULL);
				ulLastTime = tv.tv_sec * 1000000 + tv.tv_usec;
				ulNowTime = ulLastTime;
				iRecvd = 0;
				iRemind = TCP_PKG_LEN_BTYES;
				while (iRemind > 0)
				{
					int iRecv = recv(pThreadCtx->m_iSockFd,pThreadCtx->m_pRecvBuff + iRecvd, iRemind, 0);
					//if (iRecv < 0 && (errno != EAGAIN))
					if (iRecv < 0)
					{
						if (errno == EAGAIN) {
							gettimeofday(&tv, NULL);
							ulNowTime = tv.tv_sec * 1000000 + tv.tv_usec;
							if ((ulNowTime - ulLastTime) > (1 * 60 * 1000000)) {
								LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
										STATE_MAP[pThreadCtx->m_iState],
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID,
										pThreadCtx->m_iLocalPort,
										errno);
								_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);										
								return -2;
							}

							usleep(100);
							continue;
						}

						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								errno);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);			
						return -2;
					}
					if (iRecv == 0)
					{
						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Peer close connect, fd close() ret=%d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								iRecv);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);								
						return -2;
					}
					if (iRecv > 0)
					{
						iRecvd += iRecv;
						iRemind -= iRecv;
					}
				}
				
				if (iRecvd != 4) {
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%d] recv version error, version=%s",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort,
							pThreadCtx->m_cProxyIP,
							pThreadCtx->m_uiProxyPort,
							pThreadCtx->m_pRecvBuff);
					_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);							
					return -2;
				}

				gettimeofday(&tv, NULL);
				ulLastTime = tv.tv_sec * 1000000 + tv.tv_usec;
				ulNowTime = ulLastTime;

				iRecvd = 0;
				iRemind = TCP_PKG_LEN_BTYES;
				while (iRemind > 0)
				{
					int iRecv = recv(pThreadCtx->m_iSockFd, pThreadCtx->m_pRecvBuff + iRecvd, iRemind, 0);
					//if (iRecv < 0 && (errno != EAGAIN))
					if (iRecv < 0)
					{
						if (errno == EAGAIN) {
							gettimeofday(&tv, NULL);
							ulNowTime = tv.tv_sec * 1000000 + tv.tv_usec;
							if ((ulNowTime - ulLastTime) > (1 * 60 * 1000000)) {
								LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
										STATE_MAP[pThreadCtx->m_iState],
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID,
										pThreadCtx->m_iLocalPort,
										errno);
								_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);										
								return -2;
							}

							usleep(100);
							continue;
						}

						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								errno);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);								
						return -2;
					}
					if (iRecv == 0)
					{
						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Peer close connect, fd close() ret=%d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								iRecv);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);								
						return -2;
					}
					if (iRecv > 0)
					{
						iRecvd += iRecv;
						iRemind -= iRecv;
					}
				}
				//ASSERT (iRemind == 0);
				if (iRemind != 0 || iRecvd != TCP_PKG_LEN_BTYES) {
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] get msg length failed",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort,
							pThreadCtx->m_cProxyIP,
							pThreadCtx->m_uiProxyPort);
					_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);							
					return -2;
				}
				
				gettimeofday(&tv, NULL);
				ulLastTime = tv.tv_sec * 1000000 + tv.tv_usec;
				ulNowTime = ulLastTime;

				iRemind = ntohl(*(uint32_t *)pThreadCtx->m_pRecvBuff);
				iRemind -= TCP_PKG_LEN_BTYES;
				while (iRemind > 0)
				{
					int iRecv = recv(pThreadCtx->m_iSockFd, pThreadCtx->m_pRecvBuff + iRecvd, iRemind, 0);
					
					//if (iRecv < 0 && (errno != EAGAIN))
					if (iRecv < 0)
					{
						if (errno == EAGAIN) {
							gettimeofday(&tv, NULL);
							ulNowTime = tv.tv_sec * 1000000 + tv.tv_usec;
							if ((ulNowTime - ulLastTime) > (1 * 60 * 1000000)) {
								LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
										STATE_MAP[pThreadCtx->m_iState],
										pThreadCtx->m_contextType,
										pThreadCtx->m_threadID,
										pThreadCtx->m_iLocalPort,
										errno);
								_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);										
								return -2;
							}

							usleep(100);
							continue;
						}

						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv return < 0, errno %d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								errno);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);						
						return -2;
					}

					if (iRecv == 0)
					{
						LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Peer close connect, fd close() ret=%d",
								STATE_MAP[pThreadCtx->m_iState],
								pThreadCtx->m_contextType,
								pThreadCtx->m_threadID,
								pThreadCtx->m_iLocalPort,
								iRecv);
						_wemq_thread_reset_sockFd(pThreadCtx,isRecvNewConnect);								
						return -2;
					}
					if (iRecv > 0)
					{
						iRecvd += iRecv;
						iRemind -= iRecv;
					}
				}

				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] recv complete len %d",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						iRecvd);
				if(!isRecvNewConnect)
				{
					pThreadCtx->m_iSockFd = pThreadCtx->m_iSockFdNew;
				}	
				//memset(&pThreadCtx->m_stWeMQMSG, 0, sizeof(pThreadCtx->m_stWeMQMSG));
				memset(&pThreadCtx->m_stWeMQMSG, 0, (sizeof(int) * 2));
				int iRet = DecodeWeMQMsg(&pThreadCtx->m_stWeMQMSG, pThreadCtx->m_pRecvBuff,iRecvd);
				
				if (pThreadCtx->m_stWeMQMSG.uiHeaderLen == 0 || pThreadCtx->m_stWeMQMSG.uiHeaderLen >= MAX_WEMQ_HEADER_LEN)
				{
					LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Decode Wemq Header complete, header len %d is 0 or too long",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort,
							pThreadCtx->m_stWeMQMSG.uiHeaderLen
							);
					return -1;
				}
				if (pThreadCtx->m_stWeMQMSG.uiHeaderLen > 0)
				{
					memcpy(pThreadCtx->m_stWeMQMSG.cStrJsonHeader,pThreadCtx->m_pRecvBuff + 8,pThreadCtx->m_stWeMQMSG.uiHeaderLen);
					pThreadCtx->m_stWeMQMSG.cStrJsonHeader[pThreadCtx->m_stWeMQMSG.uiHeaderLen] = '\0';
				}
				LOGRMB(RMB_LOG_DEBUG, "cStrJsonHeader: %s", pThreadCtx->m_stWeMQMSG.cStrJsonHeader);
				unsigned int uiTmpBodyLen = pThreadCtx->m_stWeMQMSG.uiTotalLen - pThreadCtx->m_stWeMQMSG.uiHeaderLen - 8;
				if (uiTmpBodyLen >= MAX_WEMQ_BODY_LEN)
				{
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d]  Decode Wemq complete,body len %d is too long",
									STATE_MAP[pThreadCtx->m_iState],
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									pThreadCtx->m_iLocalPort,
									uiTmpBodyLen);
					return -1;
				}
				if (uiTmpBodyLen == 0)
				{
					LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d]  Decode Wemq complete,body len is 0",
														STATE_MAP[pThreadCtx->m_iState],
														pThreadCtx->m_contextType,
														pThreadCtx->m_threadID,
														pThreadCtx->m_iLocalPort);
				}
				//if (pThreadCtx->m_stWeMQMSG.uiTotalLen - pThreadCtx->m_stWeMQMSG.uiHeaderLen - 8 > 0)
				if (uiTmpBodyLen > 0)
				{
					//set message source
					pThreadCtx->m_stWeMQMSG.cStrJsonBody[0] = RMB_MSG_FROM_WEMQ;
					memcpy(pThreadCtx->m_stWeMQMSG.cStrJsonBody,
						   pThreadCtx->m_pRecvBuff + 8 + pThreadCtx->m_stWeMQMSG.uiHeaderLen,
						   pThreadCtx->m_stWeMQMSG.uiTotalLen - pThreadCtx->m_stWeMQMSG.uiHeaderLen - 8);
					pThreadCtx->m_stWeMQMSG.cStrJsonBody[uiTmpBodyLen] = '\0';
				}
				LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d]  Decode Wemq Header complete,total len %d, header len %d,header %s, body %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_stWeMQMSG.uiTotalLen,
						pThreadCtx->m_stWeMQMSG.uiHeaderLen,
						pThreadCtx->m_stWeMQMSG.cStrJsonHeader,
						pThreadCtx->m_stWeMQMSG.cStrJsonBody);
				if (iRet < 0)
				{
					LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Decode Wemq Header ERROR",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort);
					return -1;
				}
			}
			else
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] epoll events %d",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_ptEvents[iTmp].events);
			}


		}	
		if(!isRecvNewConnect)
		{
			pThreadCtx->m_iSockFd = pThreadCtx->m_iSockFdNew;
		}	
	}
	return iRecvd;
}

static int32_t _wemq_thread_do_send_sync(WemqThreadCtx* pThreadCtx, void *msg, uint32_t totalLen, uint32_t headerLen)
{
	int iRetry = 2;
	int timeout = pRmbStConfig->iWemqTcpSocketTimeout;

	while (iRetry > 0)
	{
		int iRet = wemq_tcp_send(pThreadCtx->m_iSockFd, msg, totalLen, headerLen, timeout);

		if (iRet == 0)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Retry: %d] TCP send timeout!",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					iRetry);
			iRetry--;
			continue;
		}
		else if (iRet == -2)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] TCP send error(%d)!",
					STATE_MAP[pThreadCtx->m_iState] ,
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					errno);
			_wemq_thread_del_fd(pThreadCtx);
			wemq_tcp_close(pThreadCtx->m_iSockFd);
			pThreadCtx->m_iSockFd = -1;
			return -1;
		}
		else if (iRet > 0)
		{
			LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] send complete len %d",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					iRet);
			return 0;
		}
		return -2;
	}

	// 重试2次失败
	return -3;
}


static int32_t _wemq_thread_do_cmd_add_listen_msg(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_ADD_LISTEN);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff; 	
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
    ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send header:%s, body:%s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader,
			pStWemqThreadMsg->m_pBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] _wemq_thread_do_send_sync error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort);
		return -2;
	}

	return 0;
}

static int32_t _wemq_thread_do_cmd_start_msg(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_START);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff;
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send:%s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] _wemq_thread_do_send_sync error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort);
		return -2;
	}

	return 0;
}

static int32_t _wemq_thread_do_cmd_client_goodbye_msg(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_CLIENT_GOODBYE);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff;
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send Client GoodBye:%s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] _wemq_thread_do_send_sync client goodbye error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort);
		return -2;
	}

	return 0;
}


static int32_t _wemq_thread_do_cmd_send_msg(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_MSG);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff; 	
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	if (NULL != pStWemqThreadMsg->m_pBody)
	{
		ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);
	}


	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send header:%s  body:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader,
			pStWemqThreadMsg->m_pBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}


	return 0;
}

static int32_t _wemq_thread_do_cmd_send_log(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_LOG);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff; 	
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	if (NULL != pStWemqThreadMsg->m_pBody)
	{
		ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);
	}


	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Send:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}


	return 0;
}
static int32_t _wemq_thread_do_cmd_send_async_request(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_REQUEST_ASYNC);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff;	
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send header:%s  body:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader,
			pStWemqThreadMsg->m_pBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}

static int32_t _wemq_thread_do_cmd_send_request(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_REQUEST);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff; 	
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send header:%s  body:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader,
			pStWemqThreadMsg->m_pBody);
	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}


	return 0;
}

static int32_t _wemq_thread_do_cmd_send_reply(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_REPLY);

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff; 	
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss|Send  header:%s  body:%s\n",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader,
			pStWemqThreadMsg->m_pBody);

	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!\n",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}


static int32_t _wemq_thread_do_cmd_send_msg_ack(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_SEND_MSG_ACK);
	int iRet = 0;	
	StRmbMsg *pSendMsg = rmb_msg_malloc();
    if ((iRet = trans_json_2_rmb_msg(pSendMsg, pStWemqThreadMsg->m_pBody, REQUEST_TO_CLIENT)) != 0) {
    	LOGRMB(RMB_LOG_ERROR, "trans_json_2_rmb_msg failed,buf is:%s", pStWemqThreadMsg->m_pBody);
        return -1;
    }
   	pSendMsg->iMsgMode = RMB_MSG_FROM_WEMQ;
    pSendMsg->cPkgType = QUEUE_PKG;

	WEMQJSON *jsonDecoder = NULL;
	WEMQJSON *sysExtFields = NULL;
	int ack_seq = 0;
	sysExtFields = json_tokener_parse(pSendMsg->sysHeader.cExtFields);

	if(json_object_object_get_ex(sysExtFields, MSG_BODY_SYSTEM_ACK_SEQ, &jsonDecoder)){
		ack_seq = json_object_get_int(jsonDecoder);
	}else{
		LOGRMB(RMB_LOG_ERROR, "get ack_seq failed!");
		return -1;
	}

	switch(*(pSendMsg->strServiceId+3))
	{
		case '0':
			iRet = _wemq_thread_resp_ack_to_access(pThreadCtx, ack_seq, 0, REQUEST_TO_CLIENT_ACK, pSendMsg);
			break;
		case '1':
			iRet = _wemq_thread_resp_ack_to_access(pThreadCtx, ack_seq, 0, ASYNC_MESSAGE_TO_CLIENT_ACK, pSendMsg);
			break;
		case '3':
			iRet = _wemq_thread_resp_ack_to_access(pThreadCtx, ack_seq, 0, BROADCAST_MESSAGE_TO_CLIENT_ACK, pSendMsg);
			break;
		case '4':
			iRet = _wemq_thread_resp_ack_to_access(pThreadCtx, ack_seq, 0, BROADCAST_MESSAGE_TO_CLIENT_ACK, pSendMsg);
			break;
		default:
			;
	}

	json_object_put(sysExtFields);
	json_object_put(jsonDecoder);
	rmb_msg_free(pSendMsg);
	
	return iRet;
}

static int32_t _wemq_thread_do_cmd_send_msg_reg(WemqThreadCtx* pThreadCtx)
{
	StWemqThreadMsg* pStWemqThreadMsg = &pThreadCtx->m_stHelloWord;
	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + pStWemqThreadMsg->m_iBodyLen + 8;

	char* buf = pThreadCtx->m_pSendBuff;
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] |wemq_thread2accesss| Send header:%s, body:%s, totalLen:%d, headerLen:%d",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pStWemqThreadMsg->m_pHeader,
			pStWemqThreadMsg->m_pBody,
			iTotalLen,
			pStWemqThreadMsg->m_iHeaderLen);

	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		//return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		return -1;
	}

	iRet = _wemq_thread_do_recv_sync(pThreadCtx);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_recv_sync error: %d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				iRet);
		return -2;
	}

	StWeMQMSG* pWemqHeader = &pThreadCtx->m_stWeMQMSG;
	WEMQJSON* jsonHeader = NULL;
	WEMQJSON* jsonTmp = NULL;
	int usCmd = -1;
	int serRet = -1;
	int seq = -1;
	long time = 0;
	char cMessage[100];

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] |access2wemq_thread|Recv: %s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pThreadCtx->m_cProxyIP,
			pThreadCtx->m_uiProxyPort,
			pWemqHeader->cStrJsonHeader);
	jsonHeader = json_tokener_parse(pWemqHeader->cStrJsonHeader);
	if (jsonHeader == NULL)
	{
		// 消息不完整, json解析失败
		LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] json_tokener_parse error: %s",
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pWemqHeader->cStrJsonHeader)
		return 1;
	}
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_COMMAND_STR, &jsonTmp);
	if (jsonTmp != NULL)
	{
		usCmd = json_object_get_int(jsonTmp);
	}
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_CODE_INT, &jsonTmp);
	if (jsonTmp != NULL)
	{
		serRet = json_object_get_int(jsonTmp);
	}
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_SEQ_INT, &jsonTmp);
	if (jsonTmp != NULL)
	{
		seq = json_object_get_int(jsonTmp);
	}
	json_object_object_get_ex(jsonHeader ,MSG_HEAD_TIME_LINT, &jsonTmp);
	if (jsonTmp != NULL)
	{
		time = (long)json_object_get_int64(jsonTmp);
	}
	json_object_object_get_ex(jsonHeader, MSG_HEAD_MSG_STR, &jsonTmp);
	if (jsonTmp != NULL) {
		memset(cMessage, 0x00, sizeof(cMessage));
		strncpy(cMessage, json_object_get_string(jsonTmp), sizeof(cMessage) - 1);
	}
	json_object_put(jsonTmp);
	json_object_put(jsonHeader);

	if (strcmp(cMessage, "auth exception") == 0) {  // wemq user/passwd error
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] Authentication error!user:%s, passwd:%s",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort,
				seq,
				time,
				pRmbStConfig->cWemqUser,
				pRmbStConfig->cWemqPasswd);
		return -1;
	}
	if (serRet == RMB_CODE_OTHER_FAIL)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] register proxy error:%d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort,
				seq,
				time,
				serRet);
		return -2;
	}

	if (serRet == RMB_CODE_AUT_FAIL)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] register proxy Authentication error:%d",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort,
				seq,
				time,
				serRet);
		return -3;
	}
	
	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [Time:%ld] register proxy success!",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pThreadCtx->m_cProxyIP,
			pThreadCtx->m_uiProxyPort,
			seq,
			time);

	//hello world之后，心跳包可以延期
	gettimeofday(&pThreadCtx->stTimeLast, NULL);
	gettimeofday(&pThreadCtx->stTimeLastRecv, NULL);

	return 0;

}

int wemq_thread_fifo_msg_is_empty(WemqThreadCtx *pThreadCtx)
{
	if (pThreadCtx == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx is null");
		return 0;
	}

	/**
	 * fix bug:rmb_sub_reply的消息也需要上传完毕
	 */
//	if ((pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) && (pThreadCtx->m_ptFifo != NULL)) {
	//check wemq thrad msg empty
	if (pThreadCtx->m_ptFifo != NULL) {
		if (wemq_kfifo_is_empty(pThreadCtx->m_ptFifo)) {
			LOGRMB(RMB_LOG_INFO, "pub:pThreadCtx->m_ptFifo is empty");
			return 0;
		} else {
			return 1;
		}
	}

	return 0;
}

int wemq_thread_mq_msg_is_empty(WemqThreadCtx *pThreadCtx)
{
    if (pThreadCtx == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx==NULL");
		return NULL;
	}
	int result = wemq_thread_check_req_mq_is_null(pThreadCtx) && wemq_thread_check_rr_rsp_mq_is_null(pThreadCtx) && wemq_thread_check_broadcast_mq_is_null(pThreadCtx);
    if(result == 0){
		LOGRMB(RMB_LOG_INFO, "wemq_thread_mq_msg_is_empty");
	}
	return result;
	
}

StMqInfo* wemq_thread_get_mq_by_type(WemqThreadCtx *pThreadCtx, int iType)
{
   stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
   StContext* pStContext;
   if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB)
	{
		pStContext = (StContext*)(pContextProxy->pubContext);
	}
	else
	{
	    pStContext = (StContext*)(pContextProxy->subContext);
	}

	return pStContext->fifoMq.mqIndex[iType];
}

StMqInfo* wemq_thread_get_receve_req_mq(WemqThreadCtx *pThreadCtx)
{
	if (pThreadCtx == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx==NULL");
		return NULL;
	}
	return wemq_thread_get_mq_by_type(pThreadCtx, (int)req_mq_index);
}

StMqInfo* wemq_thread_get_rr_rsp_mq(WemqThreadCtx *pThreadCtx)
{
	if (pThreadCtx == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx==NULL");
		return NULL;
	}
	return wemq_thread_get_mq_by_type(pThreadCtx, (int)rr_rsp_mq_index);
}

StMqInfo* wemq_thread_get_broadcast_mq(WemqThreadCtx *pThreadCtx)
{
	if (pThreadCtx == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx==NULL");
		return NULL;
	}
	return wemq_thread_get_mq_by_type(pThreadCtx, (int)broadcast_mq_index);
}

/*
Function: rmb_sub_check_req_mq_is_null
Description:校验是否请求队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int wemq_thread_check_req_mq_is_null(WemqThreadCtx *pThreadCtx)
{
	StMqInfo *p = wemq_thread_get_receve_req_mq(pThreadCtx);
	if (p == NULL)
	{
		return 0;
	}
	if (*(p->mq->pHead) != *(p->mq->pTail))
	{
		return 1;
	}
	usleep(1000);
	if (*(p->mq->pHead) != *(p->mq->pTail))
	{
		return 1;
	}
	return 0;
}

/*
Function: wemq_thread_check_rr_rsp_mq_is_null
Description:校验rr_rsp队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int wemq_thread_check_rr_rsp_mq_is_null(WemqThreadCtx *pThreadCtx)
{
	StMqInfo *p = wemq_thread_get_rr_rsp_mq(pThreadCtx);
	if (p == NULL)
	{
		return 0;
	}
	if (*(p->mq->pHead) != *(p->mq->pTail))
	{
		return 1;
	}
	usleep(1000);
	if (*(p->mq->pHead) != *(p->mq->pTail))
	{
		return 1;
	}
	return 0;
}

/*
Function: wemq_thread_check_broadcast_mq_is_null
Description:校验broadcast队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int wemq_thread_check_broadcast_mq_is_null(WemqThreadCtx *pThreadCtx)
{
	StMqInfo *p = wemq_thread_get_broadcast_mq(pThreadCtx);
	if (p == NULL)
	{
		return 0;
	}
	if (*(p->mq->pHead) != *(p->mq->pTail))
	{
		return 1;
	}
	usleep(1000);
	if (*(p->mq->pHead) != *(p->mq->pTail))
	{
		return 1;
	}
	return 0;
}


int wemq_rr_msg_is_empty(stContextProxy* pContextProxy){

	if (pContextProxy == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pContextProxy is null");
		return 0;
	}

	if (pContextProxy->stUnique.flag == 1 ) {      //有未回复的rr同步消息
		LOGRMB(RMB_LOG_DEBUG, "rr sync message is not response");
		return 1;
	}

	struct timeval tv_now;
	gettimeofday(&tv_now, NULL);
	unsigned long ulNowTime = tv_now.tv_sec * 1000 + tv_now.tv_usec / 1000;

	int i;
	for (i = 0; i < pContextProxy->pUniqueListForRRAsyncOld.get_array_size(&pContextProxy->pUniqueListForRRAsyncOld); i++)
	{
		if (pContextProxy->pUniqueListForRRAsyncOld.Data[i].flag == 1 ) {      //有未回复的rr异步消息			
			if(ulNowTime >= pContextProxy->ulLastPrintOldListIsEmpty +     1*1000 ){
		    	LOGRMB(RMB_LOG_DEBUG, "rr async is message not response");
				pContextProxy->ulLastPrintOldListIsEmpty = ulNowTime;
			}
            return 1;
		}
	}
	
	LOGRMB(RMB_LOG_DEBUG, "rr msg is empty");
	return 0;
}


int wemq_rr_all_msg_is_empty(stContextProxy* pContextProxy){

	if (pContextProxy == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pContextProxy is null");
		return 0;
	}

	if (pContextProxy->stUnique.flag == 1 ) {      //有未回复的rr同步消息
		    LOGRMB(RMB_LOG_DEBUG, "rr sync message is not response");
            return 1;
		}

	
	int i;

	for (i = 0; i < pContextProxy->pUniqueListForRRAsyncNew.get_array_size(&pContextProxy->pUniqueListForRRAsyncNew); i++) {
		
		if (pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag == 1 ) {      //有未回复的rr异步消息
			LOGRMB(RMB_LOG_DEBUG, "rr async message in new session is not response");
			return 1;
		}
	}
	for (i = 0; i < pContextProxy->pUniqueListForRRAsyncOld.get_array_size(&pContextProxy->pUniqueListForRRAsyncOld); i++) {
		
		if (pContextProxy->pUniqueListForRRAsyncOld.Data[i].flag == 1 ) {      //有未回复的rr异步消息
			LOGRMB(RMB_LOG_DEBUG, "rr async message in old session is not response");
			return 1;
		}
	}
	
	LOGRMB(RMB_LOG_DEBUG, "rr msg is empty");
	return 0;
}


int32_t _wemq_thread_do_cmd_send_heart_beat(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
//	ASSERT(pStWemqThreadMsg->m_iCmd == THREAD_MSG_CMD_BEAT);
	if (pThreadCtx == NULL || pStWemqThreadMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx or pStWemqThreadMsg is null");
		return -1;
	}

	if (pStWemqThreadMsg->m_iCmd != THREAD_MSG_CMD_BEAT) {
		LOGRMB(RMB_LOG_ERROR, "pStWemqThreadMsg->m_iCmd=%d, not THREAD_MSG_CMD_BEAT", pStWemqThreadMsg->m_iCmd);
		return -1;
	}

	int iRet = -1;
	int iTotalLen = pStWemqThreadMsg->m_iHeaderLen + 8;

	char* buf = pThreadCtx->m_pSendBuff;
	ENCODE_INT(buf, iTotalLen);
	ENCODE_INT(buf, pStWemqThreadMsg->m_iHeaderLen);
	ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pHeader, pStWemqThreadMsg->m_iHeaderLen);
	//ENCODE_DWSTR_MEMCPY(buf, pStWemqThreadMsg->m_pBody, pStWemqThreadMsg->m_iBodyLen);

	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] |wemq_thread2accesss|Send:%s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			pThreadCtx->m_iLocalPort,
			pThreadCtx->m_cProxyIP,
			pThreadCtx->m_uiProxyPort,
			pStWemqThreadMsg->m_pHeader);

	iRet = _wemq_thread_do_send_sync(pThreadCtx, pThreadCtx->m_pSendBuff, iTotalLen, pStWemqThreadMsg->m_iHeaderLen);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] _wemq_thread_do_send_sync error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort);
		return -2;
	}
	return 0;
}


static int32_t _wemq_thread_send_heart_beat(WemqThreadCtx* pThreadCtx)
{
	if (pThreadCtx == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pThreadCtx is null");
		//防止一直空转
		usleep(1000);
		return -1;
	}

//	if (pThreadCtx->m_iState == THREAD_STATE_INIT)
//	{
//		return 0;
//	}

	if (pThreadCtx->m_iState != THREAD_STATE_OK) {
		return 0;
	}

	if (pThreadCtx->m_iHeartBeatCount % HEART_BEAT_COUNT == 0)
	{
		pThreadCtx->m_iHeartBeatCount = 0;
		gettimeofday(&pThreadCtx->stTimeNow, NULL);

		int time_inter = pThreadCtx->stTimeNow.tv_sec - pThreadCtx->stTimeLast.tv_sec;
		int time_inter_recv = pThreadCtx->stTimeNow.tv_sec - pThreadCtx->stTimeLastRecv.tv_sec;

		//check heart beat time out
		if (time_inter_recv >= pRmbStConfig->heartBeatTimeout)
		{
			//防止心跳超时日志多次打印
			pThreadCtx->stTimeLastRecv.tv_sec = pThreadCtx->stTimeNow.tv_sec;
			LOGRMB(RMB_LOG_WARN, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] heart beat time out,current_times=%u!",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
					pThreadCtx->m_uiHeartBeatCurrent);
			if (pThreadCtx->m_uiHeartBeatCurrent > 2) {
				LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] send two heart beat,but no receive!",
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort);
				_wemq_thread_del_fd(pThreadCtx);
				wemq_tcp_close(pThreadCtx->m_iSockFd);
				pThreadCtx->m_iSockFd = -1;
				pThreadCtx->m_uiHeartBeatCurrent = 0;
				//add to black list
				wemq_proxy_to_black_list(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
				return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
			}
		}

		if (time_inter >= pRmbStConfig->heartBeatPeriod)
		{
			pThreadCtx->m_uiHeartBeatCurrent += 1;
			int iRet = _wemq_thread_do_cmd_send_heart_beat(pThreadCtx, &pThreadCtx->m_stHeartBeat);
			if (iRet == -2)
			{
				//_wemq_thread_del_fd(pThreadCtx);
				//pThreadCtx->m_iSockFd = -1;
				return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
			}
			pThreadCtx->stTimeLast.tv_sec = pThreadCtx->stTimeNow.tv_sec;
			pThreadCtx->stTimeLast.tv_usec = pThreadCtx->stTimeNow.tv_usec;
		}
	}
	pThreadCtx->m_iHeartBeatCount++;

	return 0;
}

static int32_t _wemq_thread_do_req(WemqThreadCtx *pThreadCtx, StWemqThreadMsg* pStWemqThreadMsg)
{
	int iRet = -1;
	switch (pStWemqThreadMsg->m_iCmd)
	{
		case THREAD_MSG_CMD_ADD_LISTEN:
		{
			iRet = _wemq_thread_do_cmd_add_listen_msg(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}
		case THREAD_MSG_CMD_START:
		{
			iRet = _wemq_thread_do_cmd_start_msg(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0) {
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			} else {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread START CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2) {
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}

			return iRet;
		}
		case THREAD_MSG_CMD_SEND_MSG:
		{
			iRet = _wemq_thread_do_cmd_send_msg(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}
        
		case THREAD_MSG_CMD_SEND_LOG:
		{
			iRet = _wemq_thread_do_cmd_send_log(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}

		case THREAD_MSG_CMD_SEND_CLIENT_GOODBYE:
		{
			iRet = _wemq_thread_do_cmd_client_goodbye_msg(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}
		case THREAD_MSG_CMD_SEND_REQUEST:
		{
			
			LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] Thread REQ CMD THREAD_MSG_CMD_SEND_REQUEST",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID);
			iRet = _wemq_thread_do_cmd_send_request(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}
		case THREAD_MSG_CMD_SEND_REQUEST_ASYNC:
		{
			LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] Thread REQ CMD THREAD_MSG_CMD_SEND_REQUEST",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID);
			iRet = _wemq_thread_do_cmd_send_async_request(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}
		case THREAD_MSG_CMD_SEND_REPLY:
		{
			
			iRet = _wemq_thread_do_cmd_send_reply(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}
			}
			return iRet;
		}
		case THREAD_MSG_CMD_SEND_MSG_ACK:
		{
			LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] Do App ACK",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID);

		/*
			iRet = _wemq_thread_do_cmd_send_msg_ack(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
		*/		
			iRet = _wemq_thread_do_cmd_send_msg_ack(pThreadCtx, pStWemqThreadMsg);
			if (iRet == 0)
			{
				_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
				pThreadCtx->m_iWemqThreadMsgHandled = 1;
			}
			else 
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Thread REQ CMD ERROR %s",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						_wemq_thread_get_cmd(pStWemqThreadMsg->m_iCmd));
				if (iRet == -2)
				{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
				}else{
					_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
					pThreadCtx->m_iWemqThreadMsgHandled = 1;
					LOGRMB(RMB_LOG_ERROR,"data processing ERR cause _wemq_thread_do_cmd_send_msg_ack failed.");
				}
			}
			return iRet;
		}
		default:
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] do req error no such type req cmd %d!",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pStWemqThreadMsg->m_iCmd);
			_wemq_thread_clear_thread_msg(pStWemqThreadMsg);
			pThreadCtx->m_iWemqThreadMsgHandled = 1;
			break;
	}
	return iRet;
}

static int32_t _wemq_thread_do_last_failure_req(WemqThreadCtx* pThreadCtx)
{
	if (pThreadCtx->m_iWemqThreadMsgHandled == 1)
	{
		return 0;
	}
	else
	{
		LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] do last failure req!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort);
		int iRet = _wemq_thread_do_req(pThreadCtx, &pThreadCtx->m_stWemqThreadMsg);
		if (iRet == -2)
		{
			_wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
			return iRet;
		}
	}
	return 0;
}

static int32_t _wemq_thread_do_connect(WemqThreadCtx* pThreadCtx)
{
	//随机sleep配置时间+0~9ms
	struct timeval tv;
    gettimeofday(&tv, NULL);
    long now_time = tv.tv_sec * 1000000 + tv.tv_usec;
    srand((unsigned int)now_time);
	int random_time = rand() % 10;
	int retry = pRmbStConfig->iWemqTcpConnectRetryNum;
	int sleep_time = pRmbStConfig->iWemqTcpConnectDelayTime * 1000;
	int timeout = pRmbStConfig->iWemqTcpConnectTimeout+random_time;

	//memset(pThreadCtx->m_cProxyIP, 0, sizeof(pThreadCtx->m_cProxyIP));
	pThreadCtx->m_cProxyIP[0] = '\0';
	pThreadCtx->m_uiProxyPort = 0;
	pThreadCtx->m_iLocalPort = 0;

	if (pThreadCtx->m_lRedirect == 0)
	{
		int iRet = 0;
		do {
			iRet = wemq_proxy_get_server(pThreadCtx->m_cProxyIP, sizeof(pThreadCtx->m_cProxyIP), &pThreadCtx->m_uiProxyPort);
			if (iRet == 2) {
				LOGRMB(RMB_LOG_ERROR, "get proxy ip/port failed");
				sleep(1);
			}
		} while (iRet == 2);
	}
	else
	{
		pThreadCtx->m_lRedirect = 0;
		snprintf(pThreadCtx->m_cProxyIP, sizeof(pThreadCtx->m_cProxyIP), "%s", pThreadCtx->m_cRedirectIP);
		pThreadCtx->m_uiProxyPort = pThreadCtx->m_iRedirectPort;
	}

	while (retry > 0)
	{
		int iSockFd = wemq_tcp_connect(pThreadCtx->m_cProxyIP, (uint16_t)pThreadCtx->m_uiProxyPort, timeout);
		if (iSockFd > 0)
		{
			pThreadCtx->m_iSockFd = iSockFd;
			wemq_getsockename(iSockFd, NULL, 0, &pThreadCtx->m_iLocalPort);
			if (_wemq_thread_set_fd_nonblock(pThreadCtx, pThreadCtx->m_iSockFd) != 0)
			{
				//exit(1);
				LOGRMB(RMB_LOG_ERROR, "wemq thread set pThreadCtx->m_iSockFd=%d to nonblock failed", pThreadCtx->m_iSockFd);
				wemq_tcp_close(pThreadCtx->m_iSockFd);
				pThreadCtx->m_iSockFd = -1;
				//return -1;
			}
			else
			{
				LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [host:%s|port:%u|local_port:%d] connect to proxy!",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort,
						pThreadCtx->m_iLocalPort);
				//add fd to epoll
				_wemq_thread_add_fd(pThreadCtx);
				return 0;
			}
		}

		LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] [retry: %d|host:%s|port:%u] Connect Error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				retry,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort);
		retry--;
		usleep(sleep_time);
		if (retry == 0)
		{
			wemq_proxy_goodbye(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [host:%s|port:%u] Connect Failed!",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			return -1;
		}

	}
	return -2;
}




int32_t wemq_thread_state_init(WemqThreadCtx* pThreadCtx)
{
//	ASSERT(pThreadCtx);
	if (pThreadCtx == NULL) {
		LOGRMB(RMB_LOG_ERROR, "wemq_thread_state_init: pThreadCtx is null");
		return -1;
	}
	pThreadCtx->m_iHeartBeatCount = 0;
	pThreadCtx->m_uiHeartBeatCurrent = 0;

	gettimeofday(&pThreadCtx->stTimeNow, NULL);
	gettimeofday(&pThreadCtx->stTimeLast, NULL);
	gettimeofday(&pThreadCtx->stTimeLastRecv, NULL);

	//pThreadCtx->m_iThreadId = pThreadCtx->m_contextType;
	pThreadCtx->m_iWemqThreadMsgHandled = 1;
	pThreadCtx->m_threadID = pthread_self();

//	pThreadCtx->m_lRedirect = false;
	pThreadCtx->m_lRedirect = 0;
	pThreadCtx->m_cRedirectIP[0] = '\0';
	pThreadCtx->m_iRedirectPort = 0;

	pThreadCtx->m_pRecvBuff = (char*)malloc(TCP_BUF_SIZE*sizeof(char));
	if (pThreadCtx->m_pRecvBuff == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] pThreadCtx->m_pRecvBuff malloc error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	memset(pThreadCtx->m_pRecvBuff, 0x00, sizeof(TCP_BUF_SIZE*sizeof(char)));

	pThreadCtx->m_pSendBuff = (char*)malloc(TCP_BUF_SIZE*sizeof(char));
	if (pThreadCtx->m_pSendBuff == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] pThreadCtx->m_pSendBuff malloc error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	memset(pThreadCtx->m_pSendBuff, 0x00, TCP_BUF_SIZE*sizeof(char));

	pThreadCtx->m_iEpollFd = epoll_create(1);
	if (pThreadCtx->m_iEpollFd < 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] Create Epoll fd error:%d!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				errno);
		return -3;
	}

	pThreadCtx->m_ptEvents = (struct epoll_event*)malloc(MAX_EVENT * sizeof(struct epoll_event));
	if (pThreadCtx->m_ptEvents == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] Create Malloc events error!",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}

	pThreadCtx->m_iSockFd = -1;

	/*
	pThreadCtx->m_stReqForHeartBeat.pid = CMD_HEART_BEAT;
	pThreadCtx->m_stReqForHeartBeat.uiCount = 0;
	*/	
	_wemq_thread_make_heart_beat_pkg(pThreadCtx);
    _wemq_thread_make_hello_pkg(pThreadCtx);
	return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_CONNECT);
}

int32_t wemq_thread_state_connect(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	ASSERT(pThreadCtx->m_iState == THREAD_STATE_CONNECT);

	int ret = _wemq_thread_do_connect(pThreadCtx);

	if (ret == 0)
	{
		return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_REGI);
	}
	else if (ret < 0)
	{
		/**
		 * 1. 使用default ip/port时,如果连接失败,则通知前端连接失败
		 * 2. 如果从配置中心获取ip list失败或者获取到的ip list数量小于2个,则通知前端连接失败
		 */
		if (pRmbStConfig->iWemqUseHttpCfg != 1 || ((ret = rmb_get_wemq_proxy_list_num()) <= 1)) {
			if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) {
				pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->pubMutex);
				if (pThreadCtx->m_ptProxyContext->iFlagForPub == 0) {
					pthread_cond_signal(&pThreadCtx->m_ptProxyContext->pubCond);
				}
				pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->pubMutex);
			} else if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_SUB) {
				pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->subMutex);
				if (pThreadCtx->m_ptProxyContext->iFlagForSub == 0) {
					pthread_cond_signal(&pThreadCtx->m_ptProxyContext->subCond);
				}
				pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->subMutex);
			}
		}

		return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
	}
	return ret;
}

int32_t wemq_thread_state_regi(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	ASSERT(pThreadCtx->m_iState == THREAD_STATE_REGI);
	int iRet = -1;

	iRet = _wemq_thread_do_cmd_send_msg_reg(pThreadCtx);
	if (iRet == 0)
	{
		if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) {
			pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->pubMutex);
			if (pThreadCtx->m_ptProxyContext->iFlagForPub == 0) {
				pThreadCtx->m_ptProxyContext->iFlagForPub = 1;
				pThreadCtx->m_ptProxyContext->iFlagForPublish = 1;
				pthread_cond_signal(&pThreadCtx->m_ptProxyContext->pubCond);
			}
			pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->pubMutex);
		} else if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_SUB) {
			pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->subMutex);
			if (pThreadCtx->m_ptProxyContext->iFlagForSub == 0) {
				pThreadCtx->m_ptProxyContext->iFlagForSub = 1;
				pthread_cond_signal(&pThreadCtx->m_ptProxyContext->subCond);
			}
			pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->subMutex);
		}

		return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_OK);
	}
	else if (iRet < 0)
	{
		wemq_proxy_to_black_list(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
		return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
	}
	return -1;
}

int32_t wemq_thread_state_ok(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	ASSERT(pThreadCtx->m_iState == THREAD_STATE_OK);
	
	int iRet = -1;
	
	if ((pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) && (pThreadCtx->m_ptProxyContext->iFlagForPublish == 0)) {
		pThreadCtx->m_ptProxyContext->iFlagForPublish = 1;
	}

	iRet = _wemq_thread_do_last_failure_req(pThreadCtx);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu]  CALL DO LAST FAILURE REQ ERROR",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID);
		return iRet;
	}

    //旧连接还在
	if(pThreadCtx->m_iSockFdOld >= 0){
	
	    wemq_thread_do_deal_with_old_connect(pThreadCtx);
	}

	int iMsgNum = 0;
	int iRecv = 0;
	iMsgNum = _wemq_thread_get_data_from_fifo(pThreadCtx);
	if (iMsgNum > 0)
	{
		iRet = _wemq_thread_do_req(pThreadCtx, &pThreadCtx->m_stWemqThreadMsg);
		if (iRet == -2)
		{
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}

	}

	iRecv = _wemq_thread_do_recv_async(pThreadCtx,true);
	if (iRecv > 0)
	{
		LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] RECV %d bytes",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				iRecv);
				iRet = _wemq_thread_on_message(pThreadCtx,true);
				if (iRet == WEMQ_MESSAGE_RET_GOODBYE)
        {
            LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] RECV byebye cmd",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
            wemq_proxy_goodbye(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
            _wemq_thread_del_fd(pThreadCtx);
            close(pThreadCtx->m_iSockFd);
            pThreadCtx->m_iSockFd = -1;
            return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
        }
		else if (iRet == WEMQ_MESSAGE_RET_REDIRECT)
		{
			LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] --> [redirect ip:%s|port:%d] RECV redirect cmd",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort,
			        pThreadCtx->m_cRedirectIP,
			        pThreadCtx->m_iRedirectPort);
			//_wemq_thread_del_fd(pThreadCtx);
			//close(pThreadCtx->m_iSockFd);
			//pThreadCtx->m_iSockFd = -1;
			//return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		       wemq_proxy_goodbye(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);	
                       return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_CLOSE);  //停止发消息;
		}
		else if (iRet == WEMQ_MESSAGE_RET_SERVERGOODBYE)      //access端主动离线
		 {
            LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] RECV server goodbye cmd",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			//stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
			
			wemq_proxy_goodbye(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
			//wemq_thread_rr_msg_is_empty(pThreadCtx);
           // _wemq_thread_del_fd(pThreadCtx);
           // close(pThreadCtx->m_iSockFd);
           // pThreadCtx->m_iSockFd = -1;

            return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_CLOSE);  //停止发消息;
        }
		else if (iRet == WEMQ_MESSAGE_RET_CLIENTGOODBYE)      //client端主动离线，收到回包
		 {
            LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] RECV client goodbye cmd rsp",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
			pContextProxy->iFlagForGoodBye = 1;
			pthread_cond_signal(&pContextProxy->goodByeCond);
			//
            //pContextProxy->iFlagForRun = 0;  //停止运行状态机            
            return 0;
        }
	}
	else 
	{	
		//LOGWEMQ(WEMQ_LOG_ERROR, "[%s],[TID:%d],ERROR RECV %d bytes\n", STATE_MAP[pThreadCtx->m_iState], pThreadCtx->m_iThreadId, iRecv); 
		if (iRecv == -1)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu]Thread on Message ERROR",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID);
		}
		if (iRecv == -2)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] connect closed by peer",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}
	}
	return 0;
}

int32_t wemq_thread_state_close(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	ASSERT(pThreadCtx->m_iState == THREAD_STATE_CLOSE);
	
	stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
	int iRet = -1;
	
	int iMsgNum = 0;
	int iRecv = 0;
	/*
	if (wemq_rr_msg_is_empty(pThreadCtx->m_ptProxyContext) == 0 && pThreadCtx->m_ptProxyContext->iFlagForEvent == 0)      //rr同步和异步消息和单播的ack都已全部回来
	{
        LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] RECV all rsp",
				STATE_MAP[pThreadCtx->m_iState],
				pThreadCtx->m_contextType,
				pThreadCtx->m_threadID,
				pThreadCtx->m_iLocalPort,
				pThreadCtx->m_cProxyIP,
				pThreadCtx->m_uiProxyPort);
			//stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
			
		wemq_proxy_goodbye(pThreadCtx->m_cProxyIP, pThreadCtx->m_uiProxyPort);
        _wemq_thread_del_fd(pThreadCtx);
        close(pThreadCtx->m_iSockFd);
        pThreadCtx->m_iSockFd = -1;
        return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);  //停止
	}
	*/

	if(pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB){
		pThreadCtx->m_iSockFdOld = pThreadCtx->m_iSockFd;
		memcpy(pThreadCtx->m_cProxyIPOld, pThreadCtx->m_cProxyIP, strlen(pThreadCtx->m_cProxyIP));
		pThreadCtx->m_uiProxyPortOld = pThreadCtx->m_uiProxyPort;

		Array pTempList = pContextProxy->pUniqueListForRRAsyncNew;
		pContextProxy->pUniqueListForRRAsyncNew = pContextProxy->pUniqueListForRRAsyncOld;
		pContextProxy->pUniqueListForRRAsyncOld = pTempList;

	 	pThreadCtx->m_iSockFd = -1;
	}else{
		struct timeval tv_now;
		gettimeofday(&tv_now, NULL);

		unsigned long ulNowTime = tv_now.tv_sec * 1000 + tv_now.tv_usec / 1000;
		unsigned long ulLastTime = tv_now.tv_sec * 1000 + tv_now.tv_usec / 1000;
		unsigned long timeout = 4 * 1000;
		
		while((ulNowTime - ulLastTime) < timeout){
			iMsgNum = _wemq_thread_get_data_from_fifo(pThreadCtx);
			if (iMsgNum > 0)
			{
				iRet = _wemq_thread_do_req(pThreadCtx, &pThreadCtx->m_stWemqThreadMsg);
			}else
				if(pRmbStConfig->mqIsEmpty == MQ_INIT || pRmbStConfig->mqIsEmpty == MQ_IS_EMPTY)
					break;
			gettimeofday(&tv_now, NULL);
			ulNowTime = tv_now.tv_sec * 1000 + tv_now.tv_usec / 1000;
		}

	}
	return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);  //停止


}

int32_t wemq_thread_state_break(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	ASSERT(pThreadCtx->m_iState == THREAD_STATE_BREAK);
	//ASSERT(pThreadCtx->m_iSockFd == -1);
	if (pThreadCtx->m_iSockFd >= 0) {
		_wemq_thread_del_fd(pThreadCtx);
		wemq_tcp_close(pThreadCtx->m_iSockFd);
		pThreadCtx->m_iSockFd = -1;
	}

	int ret = -1;

	if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) {
		pThreadCtx->m_ptProxyContext->iFlagForPublish = 0;
	}

	usleep(1000);
	if ((ret = _wemq_thread_do_connect(pThreadCtx)) == 0)
	{
		pThreadCtx->m_uiHeartBeatCurrent = 0;
		pThreadCtx->m_iHeartBeatCount = 0;
		gettimeofday(&pThreadCtx->stTimeNow, NULL);
		gettimeofday(&pThreadCtx->stTimeLast, NULL);
		gettimeofday(&pThreadCtx->stTimeLastRecv, NULL);
		return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_RECONNECT);
	}

	if (wemq_proxy_ip_is_connected() == 1) {  //所有iplist已经遍历过一次
		if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) {
			pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->pubMutex);
			if (pThreadCtx->m_ptProxyContext->iFlagForPub == 0) {
				pthread_cond_signal(&pThreadCtx->m_ptProxyContext->pubCond);
			}
			pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->pubMutex);
		} else if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_SUB) {
			pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->subMutex);
			if (pThreadCtx->m_ptProxyContext->iFlagForSub == 0) {
				pthread_cond_signal(&pThreadCtx->m_ptProxyContext->subCond);
			}
			pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->subMutex);
		}
	}

	return ret;
}

int32_t wemq_thread_state_reconnect(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	//TODO RECONNECT;
    //sleep(1);
	struct timeval tv;
    gettimeofday(&tv, NULL);
    long now_time = tv.tv_sec * 1000000 + tv.tv_usec;
    srand((unsigned int)now_time);
	//随机sleep 30~50ms ,usleep 单位是纳秒
    int sleep_time = rand() % 20 + 30;
	usleep(sleep_time * 1000);
	// hello msg
    {
		int iRet = -1;

		iRet = _wemq_thread_do_cmd_send_msg_reg(pThreadCtx);
		if (iRet > 0)
		{
			return -1;
		}
		else if (iRet < 0)
		{
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}
    }

    /**
     * 程序首次起来时,如果选择的第一个ip连接失败而第二个ip连接成功且hello world指令发送成功,则应该通知前端连接成功
     */
    if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_PUB) {
    	if (pThreadCtx->m_ptProxyContext->iFlagForPublish == 0) {
    		pThreadCtx->m_ptProxyContext->iFlagForPublish = 1;
    	}
		pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->pubMutex);
		if (pThreadCtx->m_ptProxyContext->iFlagForPub == 0) {
			pThreadCtx->m_ptProxyContext->iFlagForPub = 1;
			pthread_cond_signal(&pThreadCtx->m_ptProxyContext->pubCond);
		}
		pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->pubMutex);
	} else if (pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_SUB) {
		pthread_mutex_lock(&pThreadCtx->m_ptProxyContext->subMutex);
		if (pThreadCtx->m_ptProxyContext->iFlagForSub == 0) {
			pThreadCtx->m_ptProxyContext->iFlagForSub = 1;
			pthread_cond_signal(&pThreadCtx->m_ptProxyContext->subCond);
		}
		pthread_mutex_unlock(&pThreadCtx->m_ptProxyContext->subMutex);
	}


    int flag = 0;
    //regist topic list
	StWemqTopicProp* ptTopicProp = NULL;
	if (pThreadCtx->m_ptTopicList != NULL)
	{
		ptTopicProp = pThreadCtx->m_ptTopicList->next;
	}
	WEMQJSON *jsonTopicList = json_object_new_array();
	char cBroadcastDcn[10] = "000";
	while (ptTopicProp != NULL)
	{
		flag = 1;
		char cTopic[200];
		//char serviceOrEvent = (*(ptTopicProp->cServiceId + 3) == '0') ? 'e' : 's';
		char serviceOrEvent = (*(ptTopicProp->cServiceId + 3) == '0') ? 's' : 'e';
		snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", pRmbStConfig->cConsumerDcn, serviceOrEvent, ptTopicProp->cServiceId, ptTopicProp->cScenario, *(ptTopicProp->cServiceId + 3));
		json_object_array_add(jsonTopicList, json_object_new_string(cTopic));
		 //自动监听广播topic
		if(serviceOrEvent == 'e'){
			memset(cTopic, 0x00, sizeof(cTopic));
			snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", cBroadcastDcn, serviceOrEvent, ptTopicProp->cServiceId, ptTopicProp->cScenario, *( ptTopicProp->cServiceId + 3));
		    json_object_array_add(jsonTopicList, json_object_new_string(cTopic));
		}
		ptTopicProp = ptTopicProp->next;
	}
	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_ADD_LISTEN;

	WEMQJSON *jsonHeader = json_object_new_object();
	if (jsonHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object for jsonHeader failed");
		return -1;
	}
	
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(SUBSCRIBE_REQUEST));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));

	WEMQJSON* jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object for jsonBody failed");
		json_object_put(jsonHeader);
		json_object_put(jsonTopicList);
		return -1;
	}
	
	json_object_object_add(jsonBody, MSG_BODY_TOPIC_LIST_JSON, jsonTopicList);
				
	const char* header_str = json_object_get_string(jsonHeader);
	if (header_str == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "json_object_get_string for header is null");
		json_object_put(jsonBody);
		json_object_put(jsonTopicList);
		json_object_put(jsonHeader);
		return -2;
	}
	stThreadMsg.m_iHeaderLen = strlen(header_str);
		
	LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] Gen thread msg header succ, len %d, %s",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID,
			stThreadMsg.m_iHeaderLen,
			header_str);
	stThreadMsg.m_pHeader = (char*)malloc(stThreadMsg.m_iHeaderLen * sizeof(char) + 1);
	if (stThreadMsg.m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for header failed");
		json_object_put(jsonBody);
		json_object_put(jsonTopicList);
		json_object_put(jsonHeader);
		return -1;
	}
	memcpy(stThreadMsg.m_pHeader, header_str, stThreadMsg.m_iHeaderLen);
	stThreadMsg.m_pHeader[stThreadMsg.m_iHeaderLen] = '\0';
	json_object_put(jsonHeader);
		
	const char* body_str = json_object_get_string(jsonBody);
	if (body_str == NULL)
    {
		json_object_put(jsonTopicList);
		json_object_put(jsonBody);
        return -1;
    }

	stThreadMsg.m_iBodyLen = strlen(body_str);
	stThreadMsg.m_pBody = (char*)malloc(stThreadMsg.m_iBodyLen * sizeof(char) + 1);
	if(stThreadMsg.m_pBody == NULL){
		LOGRMB(RMB_LOG_ERROR, "malloc for hello body failed");
		json_object_put(jsonTopicList);
		json_object_put(jsonBody);
    	return -1;
	}
	memcpy(stThreadMsg.m_pBody, body_str, stThreadMsg.m_iBodyLen);
    stThreadMsg.m_pBody[stThreadMsg.m_iBodyLen] = '\0';

   	json_object_put(jsonBody);
	json_object_put(jsonTopicList);
	
	int iRet = _wemq_thread_do_cmd_add_listen_msg(pThreadCtx, &stThreadMsg);
	if (iRet == -2)
	{
		free(stThreadMsg.m_pHeader);
		free(stThreadMsg.m_pBody);
		return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
	}
		iRet = _wemq_thread_do_recv_sync(pThreadCtx);
		if (iRet != 0)
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] Decode Wemq Header ERROR",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort);
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}

		StWeMQMSG* pWemqHeader = &pThreadCtx->m_stWeMQMSG;
		jsonHeader = NULL;
		WEMQJSON* jsonTmp = NULL;
		char* usCmd;
		char* msg;
		int serRet = -1;
		int seq = -1;
		long time = 0;
		

		jsonHeader = json_tokener_parse(pWemqHeader->cStrJsonHeader);
		if (jsonHeader == NULL)
		{
			LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] [LocalPort:%d] json_tokener_parse error: %s",
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pWemqHeader->cStrJsonHeader)
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}
		json_object_object_get_ex(jsonHeader ,MSG_HEAD_COMMAND_STR, &jsonTmp);
		if (jsonTmp != NULL)
		{
			usCmd = json_object_get_string(jsonTmp);
		}
		json_object_object_get_ex(jsonHeader ,MSG_HEAD_CODE_INT, &jsonTmp);
		if (jsonTmp != NULL)
		{
			serRet = json_object_get_int(jsonTmp);
		}
		json_object_object_get_ex(jsonHeader ,MSG_HEAD_SEQ_INT, &jsonTmp);
		if (jsonTmp != NULL)
		{
			seq = json_object_get_int(jsonTmp);
		}
		json_object_object_get_ex(jsonHeader ,MSG_HEAD_MSG_STR, &jsonTmp);
		if (jsonTmp != NULL)
		{
			msg = json_object_get_string(jsonTmp);
		}

		json_object_put(jsonTmp);
		json_object_put(jsonHeader);

		if ((serRet == 0) && strcmp(usCmd, SUBSCRIBE_RESPONSE) == 0) {
			LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [msg:%s] [cmd:%s] register proxy success",
									STATE_MAP[pThreadCtx->m_iState],
									pThreadCtx->m_contextType,
									pThreadCtx->m_threadID,
									pThreadCtx->m_iLocalPort,
									pThreadCtx->m_cProxyIP,
									pThreadCtx->m_uiProxyPort,
									seq,
									msg,
									usCmd);
			
			
		} else {
			if (strcmp(usCmd, SUBSCRIBE_RESPONSE) == 0) {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [msg:%s] [cmd:%s] [ret:%d] register proxy failed, iRet=%d",
											STATE_MAP[pThreadCtx->m_iState],
											pThreadCtx->m_contextType,
											pThreadCtx->m_threadID,
											pThreadCtx->m_iLocalPort,
											pThreadCtx->m_cProxyIP,
											pThreadCtx->m_uiProxyPort,
											seq,
											msg,
											usCmd,
											serRet);
			} else {
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] [Seq:%d] [msg:%s] [cmd:%s]register proxy failed, unknown cmd",
							STATE_MAP[pThreadCtx->m_iState],
							pThreadCtx->m_contextType,
							pThreadCtx->m_threadID,
							pThreadCtx->m_iLocalPort,
							pThreadCtx->m_cProxyIP,
							pThreadCtx->m_uiProxyPort,
							seq,
							msg,
							usCmd
							);
			}
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}
	
	//send start command
	if (flag == 1) {
		StWemqThreadMsg stThreadMsg;
		memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
		stThreadMsg.m_iCmd = THREAD_MSG_CMD_START;

		WEMQJSON *jsonHeader = json_object_new_object();
		if (jsonHeader == NULL) {
			LOGRMB(RMB_LOG_ERROR, "json_object_new_object failed");
			return -2;
		}
		//add command
		json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(LISTEN_REQUEST));
		//add seq
		json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));
		//add code
		json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));

		const char *header_str = json_object_get_string(jsonHeader);
		if (header_str == NULL) {
			LOGRMB(RMB_LOG_ERROR, "header is null");
			json_object_put(jsonHeader);
			return -2;
		}

		stThreadMsg.m_iHeaderLen = strlen(header_str);
		LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%u, %s", stThreadMsg.m_iHeaderLen, header_str)
		stThreadMsg.m_pHeader = (char *)malloc((stThreadMsg.m_iHeaderLen + 1) * sizeof(char));
		if (stThreadMsg.m_pHeader == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for stThreadMsg.m_pHeader failed");
			json_object_put(jsonHeader);
			return -2;
		}
		memcpy(stThreadMsg.m_pHeader, header_str, stThreadMsg.m_iHeaderLen);
		stThreadMsg.m_pHeader[stThreadMsg.m_iHeaderLen] = '\0';

		json_object_put(jsonHeader);

		stThreadMsg.m_iBodyLen = 0;
		stThreadMsg.m_pBody = NULL;

		int iRet = _wemq_thread_do_cmd_start_msg(pThreadCtx, &stThreadMsg);
		if (iRet == -2) {
			_wemq_thread_clear_thread_msg(&stThreadMsg);
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}

		iRet = _wemq_thread_do_recv_sync(pThreadCtx);
		if (iRet != 0) {
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] Decode Wemq Header ERROR",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIP,
					pThreadCtx->m_uiProxyPort);
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}

		StWeMQMSG * pWemqHeader = &pThreadCtx->m_stWeMQMSG;
		jsonHeader = NULL;
		WEMQJSON *jsonTmp = NULL;
		char* usCmd;
		char* msg;
		int serRet = -1;
		int seq = -1;
		long time = 0;

		jsonHeader = json_tokener_parse(pWemqHeader->cStrJsonHeader);
		if (jsonHeader == NULL) {
			LOGRMB(RMB_LOG_ERROR, "[Type:%d] [TID:%lu] [LocalPort:%d] json_tokener_parse error:%s",
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pWemqHeader->cStrJsonHeader);
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}

		json_object_object_get_ex(jsonHeader, MSG_HEAD_COMMAND_STR, &jsonTmp);
		if (jsonTmp != NULL) {
			usCmd = json_object_get_string(jsonTmp);
		}

		json_object_object_get_ex(jsonHeader ,MSG_HEAD_CODE_INT, &jsonTmp);
		if (jsonTmp != NULL)
		{
			serRet = json_object_get_int(jsonTmp);
		}
		json_object_object_get_ex(jsonHeader ,MSG_HEAD_SEQ_INT, &jsonTmp);
		if (jsonTmp != NULL)
		{
			seq = json_object_get_int(jsonTmp);
		}
		json_object_object_get_ex(jsonHeader ,MSG_HEAD_MSG_STR, &jsonTmp);
		if (jsonTmp != NULL)
		{
			msg = json_object_get_string(jsonTmp);
		}

		json_object_put(jsonTmp);
		json_object_put(jsonHeader);

		if (serRet != 0 || (strcmp(usCmd, LISTEN_RESPONSE) != 0))
		{
			LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [Seq:%d] [msg:%s] [CMD:%d] reconnect send start listen error:%d",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					seq,
					msg,
					usCmd,
					serRet);
			return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_BREAK);
		}
	}

	return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_OK);
}

int32_t wemq_thread_state_destory(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	free(pThreadCtx->m_pRecvBuff);
	free(pThreadCtx->m_pSendBuff);
	free(pThreadCtx->m_ptEvents);

	if(pThreadCtx->m_iSockFd != -1)
	{
		_wemq_thread_del_fd(pThreadCtx);
		close(pThreadCtx->m_iSockFd);
		pThreadCtx->m_iSockFd = -1;
	}	
	return _wemq_thread_state_trans(pThreadCtx, pThreadCtx->m_iState, THREAD_STATE_EXIT);
}

void wemq_thread_clear_timeout_rr_async_request(WemqThreadCtx* pThreadCtx)
{
	if(pThreadCtx->m_contextType == RMB_CONTEXT_TYPE_SUB)
		return;
    stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;
	struct timeval tv_now;
	gettimeofday(&tv_now, NULL);
	unsigned long ulNowTime = tv_now.tv_sec * 1000 + tv_now.tv_usec / 1000;
    //int timeout = pRmbStConfig->rrAsyncTimeOut;
	if(pThreadCtx->m_ptProxyContext->ulLastClearRRAysncMsgTime == 0){
		pThreadCtx->m_ptProxyContext->ulLastClearRRAysncMsgTime = ulNowTime;
	}
	// 1s 清除一次
	if(ulNowTime >= pThreadCtx->m_ptProxyContext->ulLastClearRRAysncMsgTime + 1 * 1000 ){
	    int i;	
		pthread_mutex_lock(&pContextProxy->rrMutex);
		for (i = 0; i < pContextProxy->pUniqueListForRRAsyncNew.get_array_size(&pContextProxy->pUniqueListForRRAsyncNew); i++){
		    if (pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag == 1 && ulNowTime >= (pContextProxy->pUniqueListForRRAsyncNew.Data[i].timeStamp + pContextProxy->pUniqueListForRRAsyncNew.Data[i].timeout)) {      //有超时的rr异步消息
				LOGRMB(RMB_LOG_WARN, "rr async bizSeq:%s ,unique_id:%s time out, remove!",pContextProxy->pUniqueListForRRAsyncNew.Data[i].biz_seq, pContextProxy->pUniqueListForRRAsyncNew.Data[i].unique_id);
			    pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag = 0;
		    }
		}
		for (i = 0; i < pContextProxy->pUniqueListForRRAsyncOld.get_array_size(&pContextProxy->pUniqueListForRRAsyncOld); i++){
		    if (pContextProxy->pUniqueListForRRAsyncOld.Data[i].flag == 1 && ulNowTime >= (pContextProxy->pUniqueListForRRAsyncOld.Data[i].timeStamp + pContextProxy->pUniqueListForRRAsyncOld.Data[i].timeout)) {      //有超时的rr异步消息
				LOGRMB(RMB_LOG_WARN, "rr old async bizSeq:%s ,unique_id:%s time out, remove!", pContextProxy->pUniqueListForRRAsyncOld.Data[i].biz_seq, pContextProxy->pUniqueListForRRAsyncOld.Data[i].unique_id);
			    pContextProxy->pUniqueListForRRAsyncOld.Data[i].flag = 0;
		    }
 	    }
	    pthread_mutex_unlock(&pContextProxy->rrMutex);
		pThreadCtx->m_ptProxyContext->ulLastClearRRAysncMsgTime = ulNowTime;
	}
}

void wemq_thread_do_deal_with_old_connect(WemqThreadCtx* pThreadCtx)
{
	ASSERT(pThreadCtx);
	int iRecv = 0;	
	int iRet = -1;
	if(pThreadCtx->m_iSockFdOld >= 0)
	{
		if (wemq_rr_msg_is_empty(pThreadCtx->m_ptProxyContext) == 0 && pThreadCtx->m_ptProxyContext->iFlagForEvent == 0)      //rr同步和异步消息和单播的ack都已全部回来
		{
        	LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] RECV all rsp",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					pThreadCtx->m_iLocalPort,
					pThreadCtx->m_cProxyIPOld,
					pThreadCtx->m_uiProxyPortOld);
				//stContextProxy* pContextProxy = pThreadCtx->m_ptProxyContext;

			wemq_proxy_goodbye(pThreadCtx->m_cProxyIPOld, pThreadCtx->m_uiProxyPortOld);
        	_wemq_thread_del_old_fd(pThreadCtx);
        	close(pThreadCtx->m_iSockFdOld);
        	pThreadCtx->m_iSockFdOld = -1;
			return;
		}
		
		iRecv = _wemq_thread_do_recv_async(pThreadCtx,false);
		if (iRecv > 0)
		{
			LOGRMB(RMB_LOG_DEBUG, "[%s] [Type:%d] [TID:%lu] RECV %d bytes",
					STATE_MAP[pThreadCtx->m_iState],
					pThreadCtx->m_contextType,
					pThreadCtx->m_threadID,
					iRecv);
			iRet = _wemq_thread_on_message(pThreadCtx,false);

		}else {	
			//LOGWEMQ(WEMQ_LOG_ERROR, "[%s],[TID:%d],ERROR RECV %d bytes\n", STATE_MAP[pThreadCtx->m_iState], pThreadCtx->m_iThreadId, iRecv); 
			if (iRecv == -1)
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu]Thread on Message ERROR",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID);
			}
			if (iRecv == -2)
			{
				LOGRMB(RMB_LOG_ERROR, "[%s] [Type:%d] [TID:%lu] [LocalPort:%d] [proxy ip:%s|port:%u] connect closed by peer",
						STATE_MAP[pThreadCtx->m_iState],
						pThreadCtx->m_contextType,
						pThreadCtx->m_threadID,
						pThreadCtx->m_iLocalPort,
						pThreadCtx->m_cProxyIP,
						pThreadCtx->m_uiProxyPort);
				wemq_proxy_goodbye(pThreadCtx->m_cProxyIPOld, pThreadCtx->m_uiProxyPortOld);
	        	_wemq_thread_del_old_fd(pThreadCtx);
	        	close(pThreadCtx->m_iSockFdOld);
	        	pThreadCtx->m_iSockFdOld = -1;

			}
		}
	}
}



int32_t wemq_thread_run(WemqThreadCtx* pThreadCtx)
{
//	ASSERT (pThreadCtx);
	if (pThreadCtx == NULL) {
		LOGRMB(RMB_LOG_ERROR, "wemq_thread_run:pThreadCtx is null");
		return -1;
	}
	int iRet = 0;
	int iRunFlag = 1;
	int countDown = 0;
	while (iRunFlag)
//	while (pThreadCtx->m_ptProxyContext->iFlagForRun)
	{
		_wemq_thread_send_heart_beat(pThreadCtx);
		switch(pThreadCtx->m_iState)
		{
			case THREAD_STATE_INIT:
			{
				_wemq_thread_check_init(pThreadCtx);
				iRet = wemq_thread_state_init(pThreadCtx);
				if (iRet < 0) {
					LOGRMB(RMB_LOG_ERROR, "wemq_thread_state_init failed,iRet=%d", iRet);
					return -1;
				}
				break;
			}
			case THREAD_STATE_CONNECT:
			{
				_wemq_thread_check_connect(pThreadCtx);
				wemq_thread_state_connect(pThreadCtx);
				break;
			}
			case THREAD_STATE_REGI:
			{
				_wemq_thread_check_regi(pThreadCtx);
				wemq_thread_state_regi(pThreadCtx);
				break;
			}
			case THREAD_STATE_OK:
			{
				_wemq_thread_check_ok(pThreadCtx);
				wemq_thread_state_ok(pThreadCtx);
				break;
			}
			case THREAD_STATE_CLOSE:
			{
				_wemq_thread_check_close(pThreadCtx);
				wemq_thread_state_close(pThreadCtx);
				break;
			}

			case THREAD_STATE_BREAK:
			{
				_wemq_thread_check_break(pThreadCtx);
				wemq_thread_state_break(pThreadCtx);
				break;
			}
			case THREAD_STATE_RECONNECT:
			{
				_wemq_thread_check_reconnect(pThreadCtx);
				wemq_thread_state_reconnect(pThreadCtx);
				break;
			}
			case THREAD_STATE_DESTROY:
			{
				_wemq_thread_check_destory(pThreadCtx);
				wemq_thread_state_destory(pThreadCtx);
				break;
			}
			default:
				iRunFlag = 0;
				break;
		}
		wemq_thread_clear_timeout_rr_async_request(pThreadCtx);
		if (pThreadCtx->m_ptProxyContext->iFlagForRun == 0) {
			if (wemq_thread_fifo_msg_is_empty(pThreadCtx) == 0 && wemq_rr_all_msg_is_empty(pThreadCtx->m_ptProxyContext) == 0) {
				iRunFlag = 0;
			}
			else{
				GetRmbNowLongTime();
				unsigned long timeout = pRmbStConfig->ulExitTimeOut ;  //default:30s
				//超过timeout直接退出
				if(pRmbStConfig->ulNowTtime >= pThreadCtx->m_ptProxyContext->ulGoodByeTime + timeout){
					iRunFlag = 0;
				}
			}
		}
	}
	LOGRMB(RMB_LOG_INFO, "[%s] [Type:%d] [TID:%lu] THREAD EXIT!!!!",
			STATE_MAP[pThreadCtx->m_iState],
			pThreadCtx->m_contextType,
			pThreadCtx->m_threadID);

	_wemq_thread_del_fd(pThreadCtx);
	wemq_tcp_close(pThreadCtx->m_iSockFd);
	pThreadCtx->m_iSockFd = -1;

	return 0;
}

int32_t check_dyed_msg(StRmbMsg *rmbMsg) 
{
	if (rmbMsg == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "rmbMsg is null");
		return -1;		
	}
	if(strcmp(rmbMsg->isDyedMsg,"true") == 0)
	{
		return 1;
	}
	else
	{
		return 0;
	}
}