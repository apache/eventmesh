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

#ifndef RMB_MSG_H_
#define RMB_MSG_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "rmb_define.h"


int rmb_msg_clear(StRmbMsg *pStMsg);
int rmb_msg_init(StRmbMsg* pRmbMsg, StRmbConfig *pConfig, enum RMB_API_YPE type);

//set fields
//you must fill in under fields
int rmb_msg_set_bizSeqNo(StRmbMsg* pRmbMsg, const char* cBizSeqNo);

int rmb_msg_set_consumerSysVersion(StRmbMsg* pRmbMsg, const char* cConsumerSysVersion);

int rmb_msg_set_consumerSeqNo(StRmbMsg* pRmbMsg, const char* cConsumerSeqNo);

int rmb_msg_set_orgSysId(StRmbMsg* pRmbMsg, const char* cOrgSysId);

//dyed msg,optional
int rmb_msg_set_dyedMsg(StRmbMsg* pRmbMsg,const char* sign);

/*
Function: rmb_msg_set_dest
Description:设置发送目标
Retrun:见rmb_error.h
*/

int rmb_msg_set_dest(StRmbMsg* pRmbMsg, int desType, const char *cTargetDcn, int iServiceOrEven, const char *cServiceId, const char *cScenario);

int rmb_msg_set_dest_v2(StRmbMsg* pRmbMsg, const char *cTargetDcn, const char *cServiceId, const char *cScenario);

//指定法人
int rmb_msg_set_dest_v2_1(StRmbMsg* pRmbMsg, const char *cTargetDcn, const char *cServiceId, const char *cScenario, const char *cTargetOrgId);

int rmb_msg_set_live_time(StRmbMsg* pRmbMsg, unsigned long ulLiveTime);

int rmb_msg_set_app_header(StRmbMsg* pRmbMsg, const char* appHeader, unsigned int uiLen);

int rmb_msg_set_content(StRmbMsg* pRmbMsg,  const char* content, unsigned int uiLen);

const char* rmb_msg_print(StRmbMsg* pRmbMsg);
int  rmb_msg_print_v(StRmbMsg* pRmbMsg);

//获取msg的消息类型
/**
* get msg type
* 0: undefined type
* 1: request in queue
* 2: reply package in RR
* 3: broadcast
* see: C_RMB_PKG_TYPE
*/
int rmb_msg_get_msg_type(StRmbMsg* pRmbMsg);
//get cUniqueId
const char* rmb_msg_get_uniqueId_ptr(StRmbMsg* pRmbMsg);
//get cBizSeqNo
const char* rmb_msg_get_biz_seq_no_ptr(StRmbMsg* pRmbMsg);
//get cConsumerSeqNo
const char* rmb_msg_get_consumer_seq_no_ptr(StRmbMsg* pRmbMsg);
//get cConsumerDcn
const char* rmb_msg_get_consumer_dcn_ptr(StRmbMsg* pRmbMsg);
//get cOrgSysId
const char* rmb_msg_get_org_sys_id_ptr(StRmbMsg* pRmbMsg);
//get cOrgId
const char* rmb_msg_get_org_id_ptr(StRmbMsg* pRmbMsg);

//get dest.cDestName
int rmb_msg_get_dest(StRmbMsg *pRmbMsg, char *dest, unsigned int *uiLen);
const char* rmb_msg_get_dest_ptr(StRmbMsg* pRmbMsg);

//get cConsumerSysId
int rmb_msg_get_consumerSysId(StRmbMsg *pRmbMsg, char *cConsumerSysId, unsigned int *uiLen);
const char* rmb_msg_get_consumerSysId_ptr(StRmbMsg* pRmbMsg);

//cConsumerSvrId
int rmb_msg_get_consumerSvrId(StRmbMsg *pRmbMsg, char *cConsumerSvrId, unsigned int *uiLen);
const char* rmb_msg_get_consumerSvrId_ptr(StRmbMsg* pRmbMsg);

//获取appHeader
int rmb_msg_get_app_header(StRmbMsg* pRmbMsg, char* userHeader, unsigned int *pLen);

//获取appHeader指针
const char* rmb_msg_get_app_header_ptr(StRmbMsg* pRmbMsg, unsigned int *pLen);

//获取消息内容
int rmb_msg_get_content(StRmbMsg* pRmbMsg, char* content, unsigned int *pLen);

//获取消息内容指针
const char* rmb_msg_get_content_ptr(StRmbMsg* pRmbMsg, unsigned int *pLen);

//将2进制buf反序列化为rmbMsg
int shift_buf_2_msg(StRmbMsg *pStMsg, const char* cBuf, unsigned int uiLen);

//将rmbMsg序列化成2进制buf
int shift_msg_2_buf(char* cBuf, unsigned int *pLen, const StRmbMsg *pStMsg);

//rmbMsg各必填字段校验
int rmb_check_msg_valid(StRmbMsg *pStMsg);

//获取下一个合适的连接
int rmb_get_fit_size(const unsigned int uiLen, const unsigned int uiMaxLen);

//消息初始化
StRmbMsg* rmb_msg_malloc();

//消息释放
int rmb_msg_free(StRmbMsg* pRmbMsg);

//wemq json message to rmb msg
int trans_json_2_rmb_msg(StRmbMsg *pStMsg, const char *bodyJson, const char *command);

//set extfields something to rmb msg
int set_extfields_2_rmb_msg(StRmbMsg *pStMsg,const char* command,int iSeq);

//生成uuid
int rmb_msg_random_uuid(char* puuid, size_t size);

#ifdef __cplusplus
}
#endif

#endif
