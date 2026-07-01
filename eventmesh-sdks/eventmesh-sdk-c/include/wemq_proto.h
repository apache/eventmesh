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

//wemq-c-api与access协议
#ifndef _WEMQ_PROXY_WORKER_PROTO_H_
#define _WEMQ_PROXY_WORKER_PROTO_H_

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <netinet/in.h>

//#pragma pack(1)
#ifdef __cplusplus
extern "C"
{
#endif

#define SERVICE_ID_LENGTH 9
#define SCENE_ID_LENGTH 3
#define MAX_LISTEN_SIZE 100

#define BYTES_LENGTH ((2<<8)-1)
#define WORD_LENGTH ((2<<16)-1)
#define DWORD_LENGTH ((2<<32)-1)

#define MAX_MSG_WEMQ_MSG_SIZE 2200000

/**
 * wemq建立监听topic时,在2.0.8版本之前的流程为：
 * 第一阶段 发送hello world, access会建立CLIENT Hello session, new出producer和consumer,耗时大约4s
 * 第二阶段发送订阅命令,access的subscribe会同时start和consumer,后续的第二次subscribe会有大量的topic not exist,命令如下:
 * 		--- subscribe(51, "订阅(不区分同步/异步) topic") ---
 * 		--- unsubscribe(50, "取消注册服务 --- topic") ---
 * 		--- subscribeTopic(53, "订阅服务(不区分同步/异步) -- 针对非类似生产的topic,例如ft-bq-bypass") ---
 * 		--- unsubscribeTopic(52, "取消注册服务 -- 针对非类似生产的topic, 例如ft-bq-bypass") ---
 *
 * 	在2.0.8版本为了解决access使用wemq的sub和start时序问题,修改为如下:
 * 	第一阶段 发送hello world, access会建立CLIENT Hello session, new出producer和consumer,耗时大约1s
 * 	第二阶段只subscribe, 如果客户端有多个topic,则循环发出sub命令, access收到,会sub在第一阶段建立的consumer上,耗时大约30ms
 * 	第三阶段api发送start命令给access, access会Listen,将ready好的带上了subscribe信息的consumer启动起来,正式开始收发消息, 耗时大约是3s
 * 	命令字如下:
 * 		--- subscribe(61, "订阅(不区分同步/异步) topic") ---
 * 		--- unsubscribe(60, "取消注册服务 --- topic") ---
 * 		--- subscribeTopic(63, "订阅服务(不区分同步/异步) -- 针对非类似生产的topic,例如ft-bq-bypass") ---
 * 		--- unsubscribeTopic(62, "取消注册服务 -- 针对非类似生产的topic, 例如ft-bq-bypass") ---
 * 		start命令字:
 * 		listen  request:
 *			decode|length=295|headerLength=291|bodyLength=0|header={"type":123,"time":1497331206231,"seq":8995349179,"status":0,"idc":"ft","ip":"10.39.84.50"}|body=null.
 *		listen response:
 *			encode|length=299|headerLength=291|bodyLength=0|header={"type":123,"time":1497331206231,"seq":8995349179,,"status":0,"idc":"ft","ip":"10.39.84.50"}|body=null.
 * 
 *  在2.0.12版本，采用新的wemq-access协议  http://rpddoc.weoa.com/index.php?s=/389&page_id=3967
 
enum CMD_FOR_WEMQ_PROXY_WORKER
{ 
	//proxy offer
	//client(worker)-->server(proxy)
	WEMQ_CMD_HELLO = 0,						//客户端上线通知
	WEMQ_CMD_GOODBYE = 88,					//客户端离线通知
	WEMQ_CMD_CLIENTGOODBYE = 881,           //客户端主动离线通知
	WEMQ_CMD_SERVERGOODBYE = 882,           //access端主动离线通知
	WEMQ_CMD_HEARTBEAT = 66,				//ECHO心跳包
	WEMQ_CMD_SYNCREQ = 10,					//同步请求消息
	WEMQ_CMD_SYNCRSP = 11,					//同步响应消息
	WEMQ_CMD_ASYNCRSP = 21,					//RR异步响应消息
	WEMQ_CMD_ASYNCREQ = 20,					//RR异步请求消息
	WEMQ_CMD_ASYNCEVENT_PUB = 30,			//发送事件消息(发送端)
	WEMQ_CMD_ASYNCEVENT_SUB = 31,			//接收事件(订阅端)
	WEMQ_CMD_ASYNCEVENT_LOG_PUB = 34,		//发送事件(发送logserver的命令字)
	WEMQ_CMD_BROADCAST_PUB = 40,			//广播消息发送(发送端)
	WEMQ_CMD_BROADCAST_SUB = 41,			//广播消息接收(订阅端)
	WEMQ_CMD_REDIRECT = 32,                 //重定向C客户端到新的ACCESS服务器
	WEMQ_CMD_SUB = 51,						//订阅服务(不区分同步/异步)
	WEMQ_UNSUBSCRIBE = 50,					//取消注册服务
	WEMQ_ACKNOWLEDGED = 100,				//ack消息
	WEMQ_UNACKNOWLEDGED = 59,				//unack消息
	//由于wemq针对旁边的topic是没有dcn、服务ID等的概念，故增加如下SUB指令
	WEMQ_CMD_SUB_TOPIC = 53,				//订阅topic
	WEMQ_CMD_UNSUB_TOPIC = 52,				//取消topic订阅
	//version 2.0.8 add, for access
	WEMQ_CMD_SUB_LISTEN = 61,				//订阅服务(不区分同步/异步)
	WEMQ_UNSUBSCRIBE_LISTEN = 60,			//取消注册服务
	//由于wemq针对旁边的topic是没有dcn、服务ID等的概念，故增加如下SUB指令
	WEMQ_CMD_SUB_TOPIC_LISTEN = 63,			//订阅topic
	WEMQ_CMD_UNSUB_TOPIC_LISTEN = 62,		//取消topic订阅
	WEMQ_CMD_SUB_START = 123,				//sub add listen之后,发送start命令
};
*/

  //心跳
#define HEARTBEAT_REQUEST "HEARTBEAT_REQUEST"   //client发给server的心跳包
#define HEARTBEAT_RESPONSE "HEARTBEAT_RESPONSE" //server回复client的心跳包
  //握手
#define HELLO_REQUEST "HELLO_REQUEST"   //client发给server的握手请求
#define HELLO_RESPONSE "HELLO_RESPONSE" //server回复client的握手请求
  //断连
#define CLIENT_GOODBYE_REQUEST "CLIENT_GOODBYE_REQUEST" //client主动断连时通知server
#define CLIENT_GOODBYE_RESPONSE "CLIENT_GOODBYE_RESPONSE"       //server回复client的主动断连通知
#define SERVER_GOODBYE_REQUEST "SERVER_GOODBYE_REQUEST" //server主动断连时通知client
#define SERVER_GOODBYE_RESPONSE "SERVER_GOODBYE_RESPONSE"       //client回复server的主动断连通知（client不会回复，准备好之后直接断连）
  //订阅管理
#define SUBSCRIBE_REQUEST "SUBSCRIBE_REQUEST"   //client发给server的订阅请求
#define SUBSCRIBE_RESPONSE "SUBSCRIBE_RESPONSE" //server回复client的订阅请求
#define UNSUBSCRIBE_REQUEST "UNSUBSCRIBE_REQUEST"       //client发给server的取消订阅请求
#define UNSUBSCRIBE_RESPONSE "UNSUBSCRIBE_RESPONSE"     //server回复client的取消订阅请求
  //监听
#define LISTEN_REQUEST "LISTEN_REQUEST" //client发给server的启动topic监听的请求
#define LISTEN_RESPONSE "LISTEN_RESPONSE"       //server回复client的监听请求

#define REQUEST_TO_SERVER  "REQUEST_TO_SERVER"  //client将RR请求发送给server
#define REQUEST_TO_CLIENT  "REQUEST_TO_CLIENT"  //server将RR请求推送给client
#define REQUEST_TO_CLIENT_ACK  "REQUEST_TO_CLIENT_ACK"  //client收到RR请求后回ack给server
#define RESPONSE_TO_SERVER  "RESPONSE_TO_SERVER"        //client将RR回包发送给server
#define RESPONSE_TO_CLIENT  "RESPONSE_TO_CLIENT"        //server将RR回包推送给client
#define RESPONSE_TO_CLIENT_ACK  "RESPONSE_TO_CLIENT_ACK"        //client收到RR回包后回ack给server

  //异步事件
#define ASYNC_MESSAGE_TO_SERVER  "ASYNC_MESSAGE_TO_SERVER"      //client将异步事件发送给server
#define ASYNC_MESSAGE_TO_SERVER_ACK  "ASYNC_MESSAGE_TO_SERVER_ACK"      //server收到异步事件后ACK给client
#define ASYNC_MESSAGE_TO_CLIENT  "ASYNC_MESSAGE_TO_CLIENT"      //server将异步事件推送给client
#define ASYNC_MESSAGE_TO_CLIENT_ACK  "ASYNC_MESSAGE_TO_CLIENT_ACK"      // client收到异步事件后回ack给server

  //广播
#define BROADCAST_MESSAGE_TO_SERVER  "BROADCAST_MESSAGE_TO_SERVER"      //client将广播消息发送给server
#define BROADCAST_MESSAGE_TO_SERVER_ACK  "BROADCAST_MESSAGE_TO_SERVER_ACK"      //server收到广播消息后ACK给client
#define BROADCAST_MESSAGE_TO_CLIENT  "BROADCAST_MESSAGE_TO_CLIENT"      //server将广播消息推送给client
#define BROADCAST_MESSAGE_TO_CLIENT_ACK  "BROADCAST_MESSAGE_TO_CLIENT_ACK"      //client收到异步事件后回ack给server
  //日志上报
#define TRACE_LOG_TO_LOGSERVER  "TRACE_LOG_TO_LOGSERVER"        //RMB跟踪日志上报，异常场景也要上报
#define SYS_LOG_TO_LOGSERVER  "SYS_LOG_TO_LOGSERVER"    //业务日志上报
  //重定向指令
#define REDIRECT_TO_CLIENT  "REDIRECT_TO_CLIENT"        //server将重定向指令推动给client

#define MSG_ACK "MSG_ACK"       //api 内部构造ack包命令字

//协议严格按照顺序，不要随便改变结构体的次序
//严格按照encode和decode函数进行序列化和反序列化

//for uint64
  typedef union union64HN
  {
    unsigned int src[2];
    unsigned long long dest;
  } union64HN;

#define WEMQ_PROXY_MEMCPY(buf,bufLen,p,pLen) \
if (bufLen > pLen) \
{ \
	memcpy(buf, p, pLen); \
	return 0; \
}\
else \
	return -1;

#define ENCODE_CHAR(buf,tmp)\
	*buf = tmp; \
	buf += sizeof(char);

#define ENCODE_SHORT(buf,tmp)\
	*((short*)buf) = htons(tmp); \
	buf += sizeof(short);

#define ENCODE_INT(buf,tmp)\
	*((int*)buf) = htonl(tmp); \
	buf += sizeof(int);

#define ENCODE_LONG(buf,tmp)\
	*((long*)buf) = htonll_z(tmp); \
	buf += sizeof(long);

#define DECODE_CHAR(tmp,buf,pBufLen) \
if (*pBufLen < sizeof(char)) \
	return -2; \
	tmp = *((char*)(buf)); \
	buf += sizeof(char); \
	*pBufLen -= sizeof(char);

#define DECODE_SHORT(tmp,buf,pBufLen)\
if (*pBufLen < sizeof(short))\
	return -2; \
	tmp = ntohs(*((short*)buf)); \
	buf += sizeof(short); \
	*pBufLen -= sizeof(short);

#define DECODE_INT(tmp,buf,pBufLen)\
if (*pBufLen < sizeof(int))\
	return -2; \
	tmp = ntohl(*((int*)buf)); \
	buf += sizeof(int); \
	*pBufLen -= sizeof(int);

#define DECODE_LONG(tmp,buf,pBufLen)\
if (*pBufLen < sizeof(long))\
	return -2; \
	tmp = ntohll_z(*((long*)buf)); \
	buf += sizeof(long); \
	*pBufLen -= sizeof(long);

#define ENCODE_CSTR_MEMCPY(buf, str, strLen) \
	ENCODE_CHAR(buf, strLen); \
	memcpy(buf, str, strLen); \
	buf += strLen;

#define ENCODE_WSTR_MEMCPY(buf, str, strLen) \
	ENCODE_SHORT(buf, strLen); \
	memcpy(buf, str, strLen); \
	buf += strLen;

/*
#define ENCODE_DWSTR_MEMCPY(buf, str, strLen) \
	ENCODE_INT(buf, strLen); \
	memcpy(buf, str, strLen); \
	buf += strLen;
*/

#define ENCODE_DWSTR_MEMCPY(buf, str, strLen) \
	memcpy(buf, str, strLen); \
	buf += strLen;

#define DECODE_CSTR_MEMCPY(str, strLen, buf, pBufLen) \
	DECODE_CHAR(strLen, buf, pBufLen); \
	if (*pBufLen < strLen) return -2; \
		memcpy(str, buf, strLen); \
		buf += strLen; \
		*pBufLen -= strLen;

#define DECODE_WSTR_MEMCPY(str, strLen, buf, pBufLen) \
	DECODE_SHORT(strLen, buf, pBufLen); \
	if (*pBufLen < strLen) return -2; \
	memcpy(str, buf, strLen);\
	buf += strLen; \
	*pBufLen -= strLen;
/*
#define DECODE_DWSTR_MEMCPY(str, strLen, buf, pBufLen) \
	DECODE_INT(strLen, buf, pBufLen); \
	if (*pBufLen < strLen) return -2; \
	memcpy(str, buf, strLen);\
	buf += strLen; \
	*pBufLen -= strLen;
*/

#define DECODE_DWSTR_MEMCPY(str, strLen, buf, pBufLen) \
	if (*pBufLen < strLen) return -2; \
	memcpy(str, buf, strLen);\
	buf += strLen; \
	*pBufLen -= strLen;

//***************************0x01 心跳**************************
//请求
  typedef struct StHeartBeatReq
  {
    int pid;                    //进程号
    unsigned int uiCount;       //心跳次数
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StHeartBeatReq;

  int EncodeStHeartBeatReq (char *buf, int *pBufLen,
                            const StHeartBeatReq * pReq);
  int DecodeHeartBeatReq (StHeartBeatReq * pReq, const char *buf,
                          const int bufLen);

//回复
  typedef struct StHeartBeatRsp
  {
    unsigned int uiResult;      //回复状态，0为ok，非0为不ok
    int pid;                    //进程号
    unsigned int uiCount;       //心跳次数
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StHeartBeatRsp;

  int EncodeStHeartBeatRsp (char *buf, int *pBufLen,
                            const StHeartBeatRsp * pRsp);
  int DecodeHeartBeatRsp (StHeartBeatRsp * pRsp, const char *buf,
                          const int bufLen);

//***************************0x02 注册，建立连接**************************
//请求
  typedef struct StRegisterReq
  {
    int iPid;
    char cSolaceHostLen;
    char strSolaceHost[BYTES_LENGTH];
    char cSolaceVpnLen;
    char strSolaceVpn[BYTES_LENGTH];
    char cSolaceUserLen;
    char strSolaceUser[BYTES_LENGTH];
    char cSolacePwdLen;
    char strSolacePwd[BYTES_LENGTH];
    char cConsumerIpLen;
    char strConsumerIp[BYTES_LENGTH];
    char cConsumerSysIdLen;
    char strConsumerSysId[BYTES_LENGTH];
    char cConsumerDcnLen;
    char strConsumerDcn[BYTES_LENGTH];
    char cConsumerOrgIdLen;
    char strConsumerOrgId[BYTES_LENGTH];
    char cConsumerVersionLen;
    char strConsumerVersion[BYTES_LENGTH];
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StRegisterReq;

  int EncodeStRegisterReq (char *buf, int *pBufLen,
                           const StRegisterReq * pReq);
  int DecodeStRegisterReq (StRegisterReq * pReq, const char *buf,
                           const int bufLen);

  typedef struct StRegisterRsp
  {
    unsigned int uiResult;
    unsigned int uiCcdIndex;
    unsigned int uiCcdFlow;
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StRegisterRsp;

  int EncodeStRegisterRsp (char *buf, int *pBufLen,
                           const StRegisterRsp * pRsp);
  int DecodeStRegisterRsp (StRegisterRsp * pRsp, const char *buf,
                           const int bufLen);

//***********************************0x06 注册接收连接***********************************
  typedef struct StRegisterReceiveReq
  {
    unsigned int uiCcdIndex;
    unsigned int uiCcdFlow;
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StRegisterReceiveReq;

  int EncodeStRegisterReceiveReq (char *buf, int *pBufLen,
                                  const StRegisterReceiveReq * pReq);
  int DecodeStRegisterReceiveReq (StRegisterReceiveReq * pReq,
                                  const char *buf, const int bufLen);

  typedef struct StRegisterReceiveRsp
  {
    unsigned int uiResult;
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StRegisterReceiveRsp;

  int EncodeStRegisterReceiveRsp (char *buf, int *pBufLen,
                                  const StRegisterReceiveRsp * pRsp);
  int DecodeStRegisterReceiveRsp (StRegisterRsp * pRsp, const char *buf,
                                  const int bufLen);

//请求
  typedef struct StAddListenReq
  {
    char strServiceId[SERVICE_ID_LENGTH];
    char strSceneId[SCENE_ID_LENGTH];
  } StAddListenReq;

  int EncodeAddListenReq (char *buf, int *pBufLen,
                          const StAddListenReq * pReq);
  int DecodeAddListenReq (StAddListenReq * pReq, const char *buf,
                          const int bufLen);

  typedef struct StAddListenRsp
  {
    unsigned int uiResult;      //见last_error
    char strServiceId[SERVICE_ID_LENGTH];
    char strSceneId[SCENE_ID_LENGTH];
  } StAddListenRsp;

  int EncodeAddListenRsp (char *buf, int *pBufLen,
                          const StAddListenRsp * pRsp);
  int DecodeAddListenRsp (StAddListenRsp * pRsp, const char *buf,
                          const int bufLen);

//***************************0x03 增加监听**************************

//请求
  typedef struct StAddManageReq
  {
    char cManageTopicLength;
    char strManageTopic[BYTES_LENGTH];
  } StAddManageReq;

  int EncodeAddManageReq (char *buf, int *pBufLen,
                          const StAddManageReq * pReq);
  int DecodeAddManageReq (StAddManageReq * pReq, const char *buf,
                          const int bufLen);

  typedef struct StAddManageRsp
  {
    unsigned int uiResult;      //见last_error
    char cManageTopicLength;
    char strManageTopic[BYTES_LENGTH];
  } StAddManageRsp;

  int EncodeAddManageRsp (char *buf, int *pBufLen,
                          const StAddManageRsp * pRsp);
  int DecodeAddManageRsp (StAddManageRsp * pRsp, const char *buf,
                          const int bufLen);

//***************************0x04 worker发包**************************
//请求
  enum WORKER_SEND_MSG_TYPE
  {
    SEND_EVENT_MSG_TYPE = 1,
    SEND_RR_MSG_TYPE = 2,
    SEND_ASYNC_RR_MSG_TYPE = 3,
    SEND_REPLY_MSG_TYPE = 4,
  };

  typedef struct StSendMsgReq
  {
    unsigned int uiMsgType;     //见WORKER_SEND_MSG_TYPE定义
    unsigned int uiWemqMsgLen;
    unsigned int uiSendMsgSeq;
    char strWemqMsg[MAX_MSG_WEMQ_MSG_SIZE];
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StSendMsgReq;

  int EncodeSendMsgReq (char *buf, int *pBufLen, const StSendMsgReq * pReq);
  int DecodeSendMsgReq (StSendMsgReq * pReq, const char *buf,
                        const int bufLen);

//回包
  typedef struct StSendMsgRsp
  {
    unsigned int uiResult;
    unsigned int uiMsgType;     //见WORKER_SEND_MSG_TYPE定义
    unsigned int uiRecvMsgSeq;  //Worker send window size;
    char cUuidLen;              //消息uuid长度
    char strUuid[BYTES_LENGTH]; //消息uuid
  } StSendMsgRsp;

//return -1.空间不足
  int EncodeSendMsgRsp (char *buf, int *pBufLen, const StSendMsgRsp * pRsp);
  int DecodeSendMsgRsp (StSendMsgRsp * pRsp, const char *buf,
                        const int bufLen);

//***************************0x05 worker发送消息ack**************************
//请求,为避免其他业务代码错误导致把其他人的msg ack掉。因此这里将sessionId和sessionIndex、flowIndex做核对。
  typedef struct StAckMsgReq
  {
    unsigned int uiSessionIndex;
    unsigned int uiFlowIndex;
    unsigned long ulMsgId;
    char cUuidLen;              //消息uuid长度
    char strUuid[BYTES_LENGTH]; //消息uuid
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StAckMsgReq;

  int EncodeAckMsgReq (char *buf, int *pBufLen, const StAckMsgReq * pReq);
  int DecodeAckMsgReq (StAckMsgReq * pReq, const char *buf, const int bufLen);

//回包
  typedef struct StAckMsgRsp
  {
    unsigned int uiResult;
    unsigned int uiSessionIndex;
    unsigned int uiFlowIndex;
    unsigned long ulMsgId;
    char cUuidLen;              //消息uuid长度
    char strUuid[BYTES_LENGTH]; //消息uuid
  } StAckMsgRsp;

  int EncodeAckMsgRsp (char *buf, int *pBufLen, const StAckMsgRsp * pRsp);
  int DecodeAckMsgRsp (StAckMsgRsp * pRsp, const char *buf, const int bufLen);

//以下为proxy推送消息
//***************************0x50 proxy下发消息**************************
//请求
  enum PUSH_MSG_TYPE
  {
    PUSH_QUEUE_MSG = 1,
    PUSH_BROADCAST_MSG = 2,
    PUSH_MANAGE_MSG = 3,
    PUSH_RR_REPLY_MSG = 4,
  };

  typedef struct StPushMsgReq
  {
    char cWemqMsgType;
    unsigned int uiSeq;
    unsigned int uiWemqMsgLen;
    char strWemqMsg[MAX_MSG_WEMQ_MSG_SIZE];
    char cReseveLength;         //一字节长度
    char strReserve[BYTES_LENGTH];      //保留字段
  } StPushMsgReq;

  int EncodePushMsgReq (char *buf, int *pBufLen, const StPushMsgReq * pReq);
  int DecodePushMsgReq (StPushMsgReq * pReq, const char *buf,
                        const int bufLen);

//回包
  typedef struct StPushMsgRsp
  {
    unsigned int uiResult;
    char cUuidLen;              //消息uuid长度
    char strUuid[BYTES_LENGTH]; //消息uuid
  } StPushMsgRsp;

  int EncodePushMsgRsp (char *buf, int *pBufLen, const StPushMsgRsp * pRsp);
  int DecodePushMsgRsp (StPushMsgRsp * pRsp, const char *buf,
                        const int bufLen);

//*************************************************wemq worker-proxy包格式*****************************
//msg的组成都是header + msgBuf,其中header的头4个字节为长度，
  typedef struct StWemqHeader
  {
    unsigned int uiPkgLen;
    unsigned int uiColorFlag;
    unsigned short usCmd;
    unsigned int uiSessionId;
    unsigned int uiSeq;
    unsigned int uiReserved;
  } StWemqHeader;

#define MAX_WEMQ_HEADER_LEN (4096)
#define MAX_WEMQ_BODY_LEN (3 * 1024 * 1024)
  typedef struct StWeMQMSG
  {
    unsigned int uiTotalLen;
    unsigned int uiHeaderLen;
    char cStrJsonHeader[MAX_WEMQ_HEADER_LEN];
    char cStrJsonBody[MAX_WEMQ_BODY_LEN];
  } StWeMQMSG;

  int GetWemqHeaderLen ();
  int EncodeWemqHeader (char *buf, int *pBufLen,
                        const StWemqHeader * pHeader);
  int DecodeWemqHeader (StWemqHeader * pHeader, const char *buf,
                        const int bufLen);
  int DecodeWeMQMsg (StWeMQMSG * pMsg, const char *buf, const int bufLen);

//��ʽ������������
  int GetAckMsgReqLength (const StAckMsgReq * pReq);
  int GetAddListenReqLegth (const StAddListenReq * pReq);
  int GetAddManageReqLegth (const StAddManageReq * pReq);
  int GetPushMsgReqLegth (const StPushMsgReq * pReq);
  int GetRegisterReceiveReqLegth (const StRegisterReceiveReq * pReq);
  int GetRegisterReqLegth (const StRegisterReq * pReq);
  int GetSendMsgReqLength (const StSendMsgReq * pReq);
  int GetStHeartBeatReqLegth (const StHeartBeatReq * pReq);

#ifdef __cplusplus
}
#endif
//#pragma pack()
#endif
