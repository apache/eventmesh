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

#include "wemq_proto.h"

unsigned long long htonll_z(unsigned long long host)
{
	union64HN u;
	u.src[0] = htonl(host >> 32);
	u.src[1] = htonl(host & 0xFFFFFFFF);
	return u.dest;
}

unsigned long long ntohll_z(unsigned long long net)
{
	union64HN u;
	u.src[0] = ntohl(net >> 32);
	u.src[1] = ntohl(net & 0xFFFFFFFF);
	return u.dest;
}

//***************************0x01 心跳**************************
//请求

int GetStHeartBeatReqBaseLegth()
{
	return sizeof(int) + sizeof(unsigned int) + sizeof(char);
}

inline int GetStHeartBeatReqLegth(const StHeartBeatReq *pReq)
{
	return GetStHeartBeatReqBaseLegth() + pReq->cReseveLength;
}

//return -1.空间不足
int EncodeStHeartBeatReq(char* buf, int* pBufLen, const StHeartBeatReq *pReq)
{
	if (*pBufLen < GetStHeartBeatReqLegth(pReq))
	{
		return -1;
	}
	*pBufLen = GetStHeartBeatReqLegth(pReq);
	ENCODE_INT(buf, pReq->pid);
	ENCODE_INT(buf, pReq->uiCount);
	ENCODE_CSTR_MEMCPY(buf, pReq->strReserve, pReq->cReseveLength);
	return 0;
}

//return -1,空间不足
//return -2,buf长度不够
int DecodeHeartBeatReq(StHeartBeatReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetStHeartBeatReqBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pReq->pid, buf, &iTmpLen);
	DECODE_INT(pReq->uiCount, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strReserve, pReq->cReseveLength, buf, &iTmpLen);
	return 0;
}

//回复

inline int GetStHeartBeatRspBaseLegth()
{
	return 3 * sizeof(unsigned int) + sizeof(char);
}

inline int GetStHeartBeatRspLegth(const StHeartBeatRsp *pRsp)
{
	return GetStHeartBeatRspBaseLegth() + pRsp->cReseveLength;
}

//return -1.空间不足
int EncodeStHeartBeatRsp(char* buf, int* pBufLen, const StHeartBeatRsp *pRsp)
{
	if (*pBufLen < GetStHeartBeatRspLegth(pRsp))
	{
		return -1;
	}
	*pBufLen = GetStHeartBeatRspLegth(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_INT(buf, pRsp->pid);
	ENCODE_INT(buf, pRsp->uiCount);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strReserve, pRsp->cReseveLength);
	return 0;
}

int DecodeHeartBeatRsp(StHeartBeatRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetStHeartBeatRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_INT(pRsp->pid, buf, &iTmpLen);
	DECODE_INT(pRsp->uiCount, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strReserve, pRsp->cReseveLength, buf, &iTmpLen);
	return 0;
}

//***************************0x02 注册,建立连接**************************
//请求

inline int GetRegisterReqBaseLegth()
{
	return sizeof(int) + 10 * sizeof(char);
}

inline int GetRegisterReqLegth(const StRegisterReq *pReq)
{
	return  GetRegisterReqBaseLegth() + pReq->cSolaceHostLen + pReq->cSolaceVpnLen + pReq->cSolaceUserLen + pReq->cSolacePwdLen + pReq->cConsumerIpLen \
		+ pReq->cConsumerSysIdLen + pReq->cConsumerDcnLen + pReq->cConsumerOrgIdLen + pReq->cConsumerVersionLen + pReq->cReseveLength;
}

//return -1.空间不足
int EncodeStRegisterReq(char* buf, int* pBufLen, const StRegisterReq *pReq)
{
	if (*pBufLen < GetRegisterReqLegth(pReq))
	{
		return -1;
	}
	*pBufLen = GetRegisterReqLegth(pReq);
	ENCODE_INT(buf, pReq->iPid);
	ENCODE_CSTR_MEMCPY(buf, pReq->strSolaceHost, pReq->cSolaceHostLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strSolaceVpn, pReq->cSolaceVpnLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strSolaceUser, pReq->cSolaceUserLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strSolacePwd, pReq->cSolacePwdLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strConsumerIp, pReq->cConsumerIpLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strConsumerSysId, pReq->cConsumerSysIdLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strConsumerDcn, pReq->cConsumerDcnLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strConsumerOrgId, pReq->cConsumerOrgIdLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strConsumerVersion, pReq->cConsumerVersionLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strReserve, pReq->cReseveLength);
	return 0;
}

int DecodeStRegisterReq(StRegisterReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetRegisterReqBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pReq->iPid, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strSolaceHost, pReq->cSolaceHostLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strSolaceVpn, pReq->cSolaceVpnLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strSolaceUser, pReq->cSolaceUserLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strSolacePwd, pReq->cSolacePwdLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strConsumerIp, pReq->cConsumerIpLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strConsumerSysId, pReq->cConsumerSysIdLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strConsumerDcn, pReq->cConsumerDcnLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strConsumerOrgId, pReq->cConsumerOrgIdLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strConsumerVersion, pReq->cConsumerVersionLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strReserve, pReq->cReseveLength, buf, &iTmpLen);
	return 0;
}

inline int GetRegisterRspBaseLegth()
{
	return sizeof(char) + 3 * sizeof(int);
}

inline int GetRegisterRsplength(const StRegisterRsp *pRsp)
{
	return  GetRegisterRspBaseLegth() + pRsp->cReseveLength;
}

int EncodeStRegisterRsp(char* buf, int* pBufLen, const StRegisterRsp *pRsp)
{
	if (*pBufLen < GetRegisterRsplength(pRsp))
	{
		return -1;
	}
	*pBufLen = GetRegisterRsplength(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_INT(buf, pRsp->uiCcdIndex);
	ENCODE_INT(buf, pRsp->uiCcdFlow);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strReserve, pRsp->cReseveLength);
	return 0;
}

int DecodeStRegisterRsp(StRegisterRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetRegisterRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_INT(pRsp->uiCcdIndex, buf, &iTmpLen);
	DECODE_INT(pRsp->uiCcdFlow, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strReserve, pRsp->cReseveLength, buf, &iTmpLen);
	return 0;
}

//***********************************0x06 注册接收连接***********************************
inline int GetRegisterReceiveReqBaseLegth()
{
	return 2 * sizeof(int) + sizeof(char);
}

inline int GetRegisterReceiveReqLegth(const StRegisterReceiveReq *pReq)
{
	return  GetRegisterReceiveReqBaseLegth() + pReq->cReseveLength;
}

//return -1.空间不足
int EncodeStRegisterReceiveReq(char* buf, int* pBufLen, const StRegisterReceiveReq *pReq)
{
	if (*pBufLen < GetRegisterReceiveReqLegth(pReq))
	{
		return -1;
	}
	*pBufLen = GetRegisterReceiveReqLegth(pReq);
	ENCODE_INT(buf, pReq->uiCcdIndex);
	ENCODE_INT(buf, pReq->uiCcdFlow);
	ENCODE_CSTR_MEMCPY(buf, pReq->strReserve, pReq->cReseveLength);
	return 0;
}

int DecodeStRegisterReceiveReq(StRegisterReceiveReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetRegisterReceiveReqBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pReq->uiCcdIndex, buf, &iTmpLen);
	DECODE_INT(pReq->uiCcdFlow, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strReserve, pReq->cReseveLength, buf, &iTmpLen);
	return 0;
}

inline int GetRegisterReceiveRspBaseLegth()
{
	return sizeof(char) + sizeof(int);
}

inline int GetRegisterReceiveRsplength(const StRegisterReceiveRsp *pRsp)
{
	return  GetRegisterReceiveRspBaseLegth() + pRsp->cReseveLength;
}

int EncodeStRegisterReceiveRsp(char* buf, int* pBufLen, const StRegisterReceiveRsp *pRsp)
{
	if (*pBufLen < GetRegisterReceiveRsplength(pRsp))
	{
		return -1;
	}
	*pBufLen = GetRegisterReceiveRsplength(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strReserve, pRsp->cReseveLength);
	return 0;
}

int DecodeStRegisterReceiveRsp(StRegisterRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetRegisterReceiveRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strReserve, pRsp->cReseveLength, buf, &iTmpLen);
	return 0;
}

inline int GetAddListenReqLegth(const StAddListenReq *pReq)
{
	return  sizeof(pReq->strSceneId) + sizeof(pReq->strServiceId);
}

//return -1.空间不足
int EncodeAddListenReq(char* buf, int* pBufLen, const StAddListenReq *pReq)
{
	if (*pBufLen < GetAddListenReqLegth(pReq))
	{
		return -1;
	}
	*pBufLen = GetAddListenReqLegth(pReq);
	memcpy(buf, pReq->strServiceId, sizeof(pReq->strServiceId));
	buf = buf + sizeof(pReq->strServiceId);
	memcpy(buf , pReq->strSceneId, sizeof(pReq->strSceneId));
	return 0;
}

int DecodeAddListenReq(StAddListenReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetAddListenReqLegth(pReq))
	{
		return -1;
	}
	memcpy(pReq->strServiceId, buf, sizeof(pReq->strServiceId));
	memcpy(pReq->strSceneId, buf + sizeof(pReq->strServiceId), sizeof(pReq->strSceneId));
	return 0;
}


inline int GetAddListenRspBaseLegth()
{
	return  sizeof(int);
}

inline int GetAddListenRspLegth(const StAddListenRsp *pRsp)
{
	return  GetAddListenRspBaseLegth() + sizeof(pRsp->strSceneId) + sizeof(pRsp->strServiceId);
}

//return -1.空间不足
int EncodeAddListenRsp(char* buf, int* pBufLen, const StAddListenRsp *pRsp)
{
	if (*pBufLen < GetAddListenRspLegth(pRsp))
	{
		return -1;
	}
	*pBufLen = GetAddListenRspLegth(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	memcpy(buf, pRsp->strServiceId, sizeof(pRsp->strServiceId));
	memcpy(buf, pRsp->strSceneId, sizeof(pRsp->strSceneId));
	return 0;
}

int DecodeAddListenRsp(StAddListenRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetAddListenRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	memcpy(pRsp->strServiceId, buf, sizeof(pRsp->strServiceId));
	memcpy(pRsp->strSceneId, buf, sizeof(pRsp->strSceneId));
	return 0;
}



//***************************0x06 增加监听**************************
inline int GetAddManageBaseLength()
{
	return sizeof(char);
}

inline int GetAddManageReqLegth(const StAddManageReq *pReq)
{
	return  GetAddManageBaseLength() + pReq->cManageTopicLength;
}

//return -1.空间不足
int EncodeAddManageReq(char* buf, int* pBufLen, const StAddManageReq *pReq)
{
	if (*pBufLen < GetAddManageReqLegth(pReq))
	{
		return -1;
	}
	*pBufLen = GetAddManageReqLegth(pReq);
	ENCODE_CSTR_MEMCPY(buf, pReq->strManageTopic, pReq->cManageTopicLength);
	return 0;
}

int DecodeAddManageReq(StAddManageReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetRegisterReceiveRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_CSTR_MEMCPY(pReq->strManageTopic, pReq->cManageTopicLength, buf, &iTmpLen);
	return 0;
}


inline int GetAddManageRspBaseLegth()
{
	return  sizeof(int) + sizeof(char);
}

inline int GetAddManageRspLegth(const StAddManageRsp *pRsp)
{
	return  GetAddManageRspBaseLegth() + pRsp->cManageTopicLength;
}

//return -1.空间不足
int EncodeAddManageRsp(char* buf, int* pBufLen, const StAddManageRsp *pRsp)
{
	if (*pBufLen < GetAddManageRspLegth(pRsp))
	{
		return -1;
	}
	*pBufLen = GetAddManageRspLegth(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strManageTopic, pRsp->cManageTopicLength);
	return 0;
}

int DecodeAddManageRsp(StAddManageRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetAddManageRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strManageTopic, pRsp->cManageTopicLength, buf, &iTmpLen);
	return 0;
}

//***************************0x04 worker发包**************************

inline int GetSendMsgReqBaseLength()
{
	return  3 * sizeof(int) + sizeof(char);
}

inline int GetSendMsgReqLength(const StSendMsgReq *pReq)
{
	return GetSendMsgReqBaseLength() + pReq->uiWemqMsgLen + pReq->cReseveLength;
}

//return -1.空间不足
int EncodeSendMsgReq(char* buf, int* pBufLen, const StSendMsgReq *pReq)
{
	if (*pBufLen < GetSendMsgReqLength(pReq))
	{
		return -1;
	}
	*pBufLen = GetSendMsgReqLength(pReq);
	ENCODE_INT(buf, pReq->uiMsgType);
	ENCODE_INT(buf, pReq->uiSendMsgSeq);
	ENCODE_DWSTR_MEMCPY(buf, pReq->strWemqMsg, pReq->uiWemqMsgLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strReserve, pReq->cReseveLength);
	return 0;
}


int DecodeSendMsgReq(StSendMsgReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetSendMsgReqBaseLength())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pReq->uiMsgType, buf, &iTmpLen);
	DECODE_INT(pReq->uiSendMsgSeq, buf, &iTmpLen);
	DECODE_DWSTR_MEMCPY(pReq->strWemqMsg, pReq->uiWemqMsgLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strReserve, pReq->cReseveLength, buf, &iTmpLen);
	return 0;
}

inline int GetSendMsgRspBaseLength()
{
	return  3 * sizeof(int) + sizeof(char);
}

inline int GetSendMsgRspLength(const StSendMsgRsp *pRsp)
{
	return GetSendMsgRspBaseLength() + pRsp->cUuidLen;
}

//return -1.空间不足
int EncodeSendMsgRsp(char* buf, int* pBufLen, const StSendMsgRsp *pRsp)
{
	if (*pBufLen < GetSendMsgRspLength(pRsp))
	{
		return -1;
	}
	*pBufLen = GetSendMsgRspLength(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_INT(buf, pRsp->uiMsgType);
	ENCODE_INT(buf, pRsp->uiRecvMsgSeq);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strUuid, pRsp->cUuidLen);
	return 0;
}


int DecodeSendMsgRsp(StSendMsgRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetSendMsgRspBaseLength())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_INT(pRsp->uiMsgType, buf, &iTmpLen);
	DECODE_INT(pRsp->uiRecvMsgSeq, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strUuid, pRsp->cUuidLen, buf, &iTmpLen);
	return 0;
}

//***************************0x05 worker发送消息ack**************************
//请求,为避免其他业务代码错误导致把其他人的msg ack掉, 因此这里将sessionId和sessionIndex flowIndex做核对

inline int GetAckMsgReqBaseLength()
{
	return  2 * sizeof(int) + 2 * sizeof(char) + sizeof(long);
}

inline int GetAckMsgReqLength(const StAckMsgReq *pReq)
{
	return GetAckMsgReqBaseLength() + pReq->cUuidLen + pReq->cReseveLength;
}

//return -1.空间不足
int EncodeAckMsgReq(char* buf, int* pBufLen, const StAckMsgReq *pReq)
{
	if (*pBufLen < GetAckMsgReqLength(pReq))
	{
		return -1;
	}
	*pBufLen = GetAckMsgReqLength(pReq);
	ENCODE_INT(buf, pReq->uiSessionIndex);
	ENCODE_INT(buf, pReq->uiFlowIndex);
	ENCODE_LONG(buf, pReq->ulMsgId);
	ENCODE_CSTR_MEMCPY(buf, pReq->strUuid, pReq->cUuidLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strReserve, pReq->cReseveLength);
	return 0;
}


int DecodeAckMsgReq(StAckMsgReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetAckMsgReqBaseLength())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pReq->uiSessionIndex, buf, &iTmpLen);
	DECODE_INT(pReq->uiFlowIndex, buf, &iTmpLen);
	DECODE_LONG(pReq->ulMsgId, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strUuid, pReq->cUuidLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strReserve, pReq->cReseveLength, buf, &iTmpLen);
	return 0;
}

//回包

inline int GetAckMsgRspBaseLength()
{
	return  3 * sizeof(int) + sizeof(char) + sizeof(unsigned long);
}

inline int GetAckMsgRspLength(const StAckMsgRsp *pReq)
{
	return GetAckMsgRspBaseLength() + pReq->cUuidLen;
}

//return -1.空间不足
int EncodeAckMsgRsp(char* buf, int* pBufLen, const StAckMsgRsp *pRsp)
{
	if (*pBufLen < GetAckMsgRspLength(pRsp))
	{
		return -1;
	}
	*pBufLen = GetAckMsgRspLength(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_INT(buf, pRsp->uiSessionIndex);
	ENCODE_INT(buf, pRsp->uiFlowIndex);
	ENCODE_LONG(buf, pRsp->ulMsgId);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strUuid, pRsp->cUuidLen);
	return 0;
}


int DecodeAckMsgRsp(StAckMsgRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetAckMsgRspBaseLength())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_INT(pRsp->uiSessionIndex, buf, &iTmpLen);
	DECODE_INT(pRsp->uiFlowIndex, buf, &iTmpLen);
	DECODE_LONG(pRsp->ulMsgId, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strUuid, pRsp->cUuidLen, buf, &iTmpLen);
	return 0;
}

//以下为proxy推送消息
//***************************0x50 proxy下发消息**************************
inline int GetPushMsgReqBaseLegth()
{
	return (2 * sizeof(int) + 2 * sizeof(char));
}

inline int GetPushMsgReqLegth(const StPushMsgReq *pReq)
{
	return GetPushMsgReqBaseLegth() + pReq->uiWemqMsgLen + pReq->cReseveLength;
}

//return -1.空间不足
int EncodePushMsgReq(char* buf, int* pBufLen, const StPushMsgReq *pReq)
{
	if (*pBufLen < GetPushMsgReqLegth(pReq))
	{
		return -1;
	}
	*pBufLen = GetPushMsgReqLegth(pReq);
	ENCODE_CHAR(buf, pReq->cWemqMsgType);
	ENCODE_INT(buf, pReq->uiSeq);
	ENCODE_DWSTR_MEMCPY(buf, pReq->strWemqMsg, pReq->uiWemqMsgLen);
	ENCODE_CSTR_MEMCPY(buf, pReq->strReserve, pReq->cReseveLength);
	return 0;
}

int DecodePushMsgReq(StPushMsgReq *pReq, const char* buf, const int bufLen)
{
	if (bufLen < GetPushMsgReqBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_CHAR(pReq->cWemqMsgType, buf, &iTmpLen);
	DECODE_INT(pReq->uiSeq, buf, &iTmpLen);
	DECODE_DWSTR_MEMCPY(pReq->strWemqMsg, pReq->uiWemqMsgLen, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pReq->strReserve, pReq->cReseveLength, buf, &iTmpLen);
	return 0;
}

//回包

inline int GetPushMsgRspBaseLegth()
{
	return sizeof(int) + sizeof(char);
}

inline int GetPushMsgRspLegth(const StPushMsgRsp *pRsp)
{
	return GetPushMsgRspBaseLegth() + pRsp->cUuidLen;
}

//return -1.空间不足
int EncodePushMsgRsp(char* buf, int* pBufLen, const StPushMsgRsp *pRsp)
{
	if (*pBufLen < GetPushMsgRspLegth(pRsp))
	{
		return -1;
	}
	*pBufLen = GetPushMsgRspLegth(pRsp);
	ENCODE_INT(buf, pRsp->uiResult);
	ENCODE_CSTR_MEMCPY(buf, pRsp->strUuid, pRsp->cUuidLen);
	return 0;
}

int DecodePushMsgRsp(StPushMsgRsp *pRsp, const char* buf, const int bufLen)
{
	if (bufLen < GetPushMsgRspBaseLegth())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pRsp->uiResult, buf, &iTmpLen);
	DECODE_CSTR_MEMCPY(pRsp->strUuid, pRsp->cUuidLen, buf, &iTmpLen);
	return 0;
}


//*************************************************wemq worker-proxy包格式*****************************
//msg的组成都是header + msgbuf, 其中header的头4个字节为长度
int GetWemqHeaderLen()
{
	return 5 * sizeof(int) + sizeof(unsigned short);
}

int EncodeWemqHeader(char* buf, int* pBufLen, const StWemqHeader *pHeader)
{
	if (*pBufLen < GetWemqHeaderLen())
	{
		return -1;
	}
	*pBufLen = GetWemqHeaderLen();

	ENCODE_INT(buf, pHeader->uiPkgLen);
	ENCODE_INT(buf, 0xFFFFFFFF);
	ENCODE_SHORT(buf, pHeader->usCmd);
	ENCODE_INT(buf, pHeader->uiSessionId);
	ENCODE_INT(buf, pHeader->uiSeq);
	ENCODE_INT(buf, pHeader->uiReserved);
	return 0;
}

int DecodeWemqHeader(StWemqHeader *pHeader, const char* buf, const int bufLen)
{
	if (bufLen < GetWemqHeaderLen())
	{
		return -1;
	}
	int iTmpLen = bufLen;
	DECODE_INT(pHeader->uiPkgLen, buf, &iTmpLen);
	DECODE_INT(pHeader->uiColorFlag, buf, &iTmpLen);
	DECODE_SHORT(pHeader->usCmd, buf, &iTmpLen);
	DECODE_INT(pHeader->uiSessionId, buf, &iTmpLen);
	DECODE_INT(pHeader->uiSeq, buf, &iTmpLen);
	DECODE_INT(pHeader->uiReserved, buf, &iTmpLen);
	return 0;
}

int DecodeWeMQMsg(StWeMQMSG* pMsg, const char* buf, const int bufLen)
{
	int iTmpLen = bufLen;
	DECODE_INT(pMsg->uiTotalLen, buf, &iTmpLen);
	DECODE_INT(pMsg->uiHeaderLen, buf, &iTmpLen);
/*
	DECODE_DWSTR_MEMCPY(pMsg->cStrJsonHeader,pMsg->uiHeaderLen,buf,&iTmpLen);
	pMsg->cStrJsonHeader[pMsg->uiHeaderLen] = '\0';
	DECODE_DWSTR_MEMCPY(pMsg->cStrJsonBody, pMsg->uiTotalLen - pMsg->uiHeaderLen - 8, buf, &iTmpLen);
	pMsg->cStrJsonHeader[pMsg->uiTotalLen - pMsg->uiHeaderLen - 8] = '\0';
*/
	return 0;
}
