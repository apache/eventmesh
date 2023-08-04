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

/**
 * rmb_pub.h
 * ---rmb的发包管理，包括发送广播、RR模式等
 */
#ifndef RMB_PUB_H_
#define RMB_PUB_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include "rmb_define.h"
#include "rmb_msg.h"
#include "message_log_api.h"

typedef struct StRmbPub
{
	StContext* pContext;
	//StConfig config;
	//for rr send msg
	char cRrReplyTopic[200];
	int uiContextNum;				//是否已经初始化

	//for control send to wemq or solace
	unsigned long ulLastTime;
	unsigned int uiWeight;

	pthread_mutex_t pubMutex;		//临界区
	StRmbMsg *pSendMsg;
	StRmbMsg *pRcvMsg;
	char pkgBuf[MAX_GSL_REQ_BUF_SIZE];
	char printGslBuf[1000];
}StRmbPub;

/**
Function: rmb_pub_init
Description:initialize
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_init(StRmbPub *rmb_pub);

int rmb_pub_init_python();


/**
Function: rmb_pub_send_and_receive
Description:send message and wait for report
Retrun:
	0    --success
	-1	 --timeout
	-2   --error
*/
int rmb_pub_send_and_receive(StRmbPub *rmb_pub, StRmbMsg *pSendMsg, StRmbMsg *pRevMsg, unsigned int uiTimeOut);
int rmb_pub_send_and_receive_python(StRmbMsg *pSendMsg, StRmbMsg *pRevMsg, unsigned int uiTimeOut);


/**
Function: rmb_pub_send_msg
Description:send message
Retrun:
	0    --success
	-1	 --failed
	-2   --queue full
*/
int rmb_pub_send_msg(StRmbPub *rmb_pub, StRmbMsg *pStMsg);

int rmb_pub_send_msg_python(StRmbMsg *pStMsg);



/**
Function: rmb_pub_send_rr_msg
Description:send RR asynchronous message
Retrun:
	0    --success
	-1	 --failed
	-2   --queue full
*/
int rmb_pub_send_rr_msg_async(StRmbPub *rmb_pub, StRmbMsg *pStMsg,unsigned int uiTimeOut);
int rmb_pub_send_rr_msg_async_python(StRmbMsg *pStMsg,unsigned int uiTimeOut);


/**
Function: rmb_pub_reply_msg
Description:send report packet
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_reply_msg(StRmbPub *pRmbPub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg);



/**
Function: rmb_pub_close
Description:close pub
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_close(StRmbPub *pRmbPub);
int rmb_pub_close_python();


/**
Function: rmb_pub_close_v2
Description:close pub对象，2.0.12后使用
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_close_v2(StRmbPub *pRmbPub);

int rmb_pub_encode_thread_msg(unsigned int uiCmd, StWemqThreadMsg *ptThreadMsg, StRmbMsg *ptSendMsg, unsigned long ulTimeToAlive);

WEMQJSON* rmb_pub_encode_byte_body_for_wemq(unsigned int uiCmd, StRmbMsg *ptSendMsg);
WEMQJSON* rmb_pub_encode_property_for_wemq(unsigned int uiCmd, StRmbMsg *ptSendMsg);

/**
 * 给GSL发送染色消息
 */
int rmb_pub_send_dyed_msg_to_gsl(StRmbPub *pStPub);

//extern StRmbPub *pRmbGlobalPub;

/*
 *  send log to logserver by wemq
 *  when iLogPoint is RMB_LOG_ON_ERROR, need iErrCode, cErrMsg
 *  others, not need
 */
//int rmb_send_log_to_logserver(StRmbMsg *pStMsg, int iContextType, int iLogPoint, int iErrCode, char* cErrMsg);

#ifdef __cplusplus
}
#endif

#endif
