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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <stddef.h>
#include <sys/un.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <signal.h>
#include <sys/wait.h>
#include <pthread.h>

#include "rmb_sub.h"
#include "rmb_pub.h"
#include "rmb_msg.h"
#include "rmb_udp.h"
#include "rmb_cfg.h"
#include "rmb_log.h"
#include "rmb_errno.h"
#include "test_rmb_capi_for_jekins.h"

#define MAX_MSG_LEN (1024*1024*3+1)

static const char *version_test = "RMB_C_API_V2.13";

static const char *send_msg = "pubSendMsg";

pthread_mutex_t test_mutex;

StRmbSub *pRmbSub;
StRmbPub *pRmbPub;

static char StatLogLevel[6][10] = {"FATAL", "ERROR", "WARN ", "INFO", "DEBUG", "ALL"};

int log_print(int logLevel, const char *format, ...)
{
    va_list ap;
    struct timeval stLogTv;

    va_start(ap, format);
    gettimeofday(&stLogTv, NULL);
    fprintf(stdout, "[%s][%s %03d][%d]", StatLogLevel[logLevel],RmbGetDateTimeStr((const time_t *) &(stLogTv.tv_sec)), (int) ((stLogTv.tv_usec)/1000),getpid());
    //fprintf(stdout, "[%s][%d]", StatLogLevel[logLevel],getpid());
    vfprintf(stdout, format, ap);
    va_end(ap);
    fprintf(stdout, "\n");

    return 0;
}


void printfUsage(const char *name)
{
    printf("%s: version:%s\n\n", name, version_test);

    printf("%s: sub server端(非广播)\n", name);
    printf("	%s sub sleep_time log_control process_nums topic1 topic2 ...\n", name);
    printf("	sleep_time:睡眠时间，通常为0, log_control:测试内容大小时使用，1：打印，其他值为不打印  process_nums:进程数量，最小为1\n");
    printf("\n");

    printf("%s: sub server端(广播)\n", name);
    printf("	%s sub_broadcast process_nums log_control topic1 topic2 ...\n", name);
    printf("\n");

    printf("%s: pub client端(RR同步消息/单播/多播/广播消息发送):\n", name);
    printf("	%s pub pthread_nums log_control msg_nums msg_len ttl_time sleep_time topic1 topic2 ...\n", name);
    printf("	pthread_nums:线程数量，最小为1, log_control:测试内容大小时使用，1：打印，其他值为不打印 msg_nums:每个线程发送消息数量 msg:消息内容 msg_len：消息内容大小\n");
    printf("\n");

    printf("%s: pub端(RR异步):\n", name);
    printf("	%s rr_async_pub process_nums log_control msg_nums msg_len sleep_time topic\n", name);
    printf("	process_nums:进程数量，最小为1, msg_nums：每个进程发送消息数量  msg:消息内容\n");
    printf("\n");

    printf("////////////////////////////////////////////////////////\n");

    exit(0);
}

typedef struct StDemoArgv
{
    //建议字段
    StRmbPub* pRmbPub;
    StRmbSub* pRmbSub;
    StRmbMsg* pReceiveMsg;
    StRmbMsg* pReplyMsg;
    //其他字段
    unsigned int uiLog;
    unsigned long ulMsgTotal;
    unsigned int uiSleepTime;
    unsigned int uiLogServer;
    unsigned int uiFlag;
}StDemoArgv;

typedef struct tThreadArgs
{
    StRmbPub* pRmbPub;
    enum EVENT_OR_SERVICE_CALL event_or_service;
    unsigned int times;
    int iContent;
    char cDcn[100];
    char cServiceId[100];
    char cSenaId[100];
    ///////////////
    unsigned long ulMsgSucc;
    ///////////////
    int iLogNum;
    unsigned long ttl;
    const char *pMsg;
}tThreadArgs;



/**
 * return value:
 * 		0: success
 * 		1: topic check failed
 * 		<0: send error
 */
int pubOwnMessage(StRmbPub *pRmbPub, int iEventOrService, unsigned long ttl_time, const char *cDcn, const char *cServiceId,
        const char *cSenaId, unsigned int log_control, const char *strMsg)
{
    int iRet = 0;
    StRmbMsg *pSendMsg = rmb_msg_malloc();
    rmb_msg_clear(pSendMsg);
    static unsigned int uiSeq = 1;
    struct timeval tv;
    gettimeofday(&tv, NULL);
    unsigned long ulNowTime = tv.tv_sec * 1000 + tv.tv_usec / 1000;
    char cSeqNo[33];
    snprintf(cSeqNo, sizeof(cSeqNo), "%013lu%019u", ulNowTime, uiSeq++);
    rmb_msg_set_bizSeqNo(pSendMsg, cSeqNo);
    rmb_msg_set_consumerSeqNo(pSendMsg, cSeqNo);
    rmb_msg_set_orgSysId(pSendMsg, "9999");
    if (ttl_time > 0) {
        rmb_msg_set_live_time(pSendMsg, ttl_time);
    }
    if (iEventOrService == 0) {
        iRet = rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, RMB_EVENT_CALL, cServiceId, cSenaId);
    } else {
        iRet = rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, RMB_SERVICE_CALL, cServiceId, cSenaId);
    }
    /*	if (iRet < 0)
        return 1;*/

    rmb_msg_set_content(pSendMsg, strMsg, strlen(strMsg));
    char appHeader[100] = "{}";
    rmb_msg_set_app_header(pSendMsg, appHeader, strlen(appHeader));

    if (iEventOrService == 0) {
        iRet = rmb_pub_send_msg(pRmbPub, pSendMsg);
        if (log_control & 2) {
            if (iRet == 0) {
                LOG_PRINT(RMB_LOG_INFO,"send msg=%s OK",pSendMsg->cContent);
            } else {
                LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_msg error!iRet = %d",iRet);
            }
        }
    } else {
        StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        iRet = rmb_pub_send_and_receive(pRmbPub, pSendMsg, pReceiveMsg, 50000);
        if (iRet == 0) {
            char receiveBuf[MAX_MSG_LEN];
            unsigned int receiveLen = sizeof(receiveBuf);
            rmb_msg_get_content(pReceiveMsg, receiveBuf, &receiveLen);

            if (log_control == 1) {
                LOG_PRINT(RMB_LOG_INFO,"receive reply:len=%u, %s", receiveLen, receiveBuf);
            }
        } else {
            LOG_PRINT(RMB_LOG_INFO,"rmb_pub_send_and_receive error!iRet = %d,msg=%s",iRet, strMsg);
        }

        //sleep(1);
        rmb_msg_free(pReceiveMsg);
    }


    rmb_msg_free(pSendMsg);
    return iRet;
}


//////////////////////////////////////////////////

int pub_message(StRmbPub *pRmbPub, int iEventOrService, int first_msg_nums, int second_msg_nums,
        const char *cDcn, const char *cServiceId, const char *cSenaId)
{
    int iRet = 0;
    StRmbMsg *pSendMsg = rmb_msg_malloc();

    rmb_msg_set_bizSeqNo(pSendMsg, "12345678901234567890123456789012");
    rmb_msg_set_consumerSeqNo(pSendMsg, "22222678901234567890123456799999");
    rmb_msg_set_orgSysId(pSendMsg, "9999");
    if (iEventOrService == 0) {
        rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, RMB_EVENT_CALL, cServiceId, cSenaId);
    } else {
        rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, RMB_SERVICE_CALL, cServiceId, cSenaId);
    }
    rmb_msg_set_content(pSendMsg, send_msg, strlen(send_msg));
    char appHeader[100] = "{}";
    rmb_msg_set_app_header(pSendMsg, appHeader, strlen(appHeader));

    unsigned long ulNowTime = 0;
    unsigned long ulSleepTime = 0;
    struct timeval tv;

    if (iEventOrService == 0) {
        gettimeofday(&tv, NULL);
        ulNowTime = tv.tv_sec * 1000000 + tv.tv_usec;
        int i;
        for (i=0; i<first_msg_nums; i++) {
            iRet = rmb_pub_send_msg(pRmbPub, pSendMsg);
#ifdef DEBUG
            if (iRet == 0) {
                LOG_PRINT(RMB_LOG_INFO,"send msg=%s OK",pSendMsg->cContent);
            } else {
                LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_msg error!iRet = %d",iRet);
            }
#endif
        }

        gettimeofday(&tv, NULL);
        ulSleepTime = tv.tv_sec * 1000000 + tv.tv_usec - ulNowTime;
        printf("ulSleepTime = %lu\n", ulSleepTime);
        if (ulSleepTime < 1000000)
            usleep(ulSleepTime);

        for (i=0; i<second_msg_nums; i++) {
            iRet = rmb_pub_send_msg(pRmbPub, pSendMsg);
#ifdef DEBUG
            if (iRet == 0) {
                LOG_PRINT(RMB_LOG_INFO,"send msg=%s OK", pSendMsg->cContent);
            } else {
                LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_msg error!iRet = %d",iRet);
            }
#endif
        }
    } else {
        StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        gettimeofday(&tv, NULL);
        ulNowTime = tv.tv_sec * 1000000 + tv.tv_usec;
        int i;
        for (i=0; i<first_msg_nums; i++) {
            iRet = rmb_pub_send_and_receive(pRmbPub, pSendMsg, pReceiveMsg, 5000);
            if (iRet == 0) {
                char receiveBuf[1024];
                unsigned int receiveLen = sizeof(receiveBuf);
                rmb_msg_get_content(pReceiveMsg, receiveBuf, &receiveLen);
#ifdef DEBUG
                LOG_PRINT(RMB_LOG_INFO,"receive reply pkg=%s",receiveBuf);
            } else {
                LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_and_receive error!iRet = %d,msg=%s",iRet, strMsg);
            }
#else
            }
#endif
        }
        gettimeofday(&tv, NULL);
        ulSleepTime = tv.tv_sec * 1000000 + tv.tv_usec - ulNowTime;
        printf("ulSleepTime = %lu\n", ulSleepTime);
        if (ulSleepTime < 1000000)
            usleep(ulSleepTime);

        for (i=0; i<second_msg_nums; i++) {
            iRet = rmb_pub_send_and_receive(pRmbPub, pSendMsg, pReceiveMsg, 5000);
            if (iRet == 0) {
                char receiveBuf[1024];
                unsigned int receiveLen = sizeof(receiveBuf);
                rmb_msg_get_content(pReceiveMsg, receiveBuf, &receiveLen);
#ifdef DEBUG
                LOG_PRINT(RMB_LOG_INFO,"receive reply pkg=%s",receiveBuf);
            } else {
                LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_and_receive error!iRet = %d,msg=%s",iRet, strMsg);			}
#else
            }
#endif
        }

        rmb_msg_free(pReceiveMsg);
    }
    printf("%s: send msg:%d\n", __func__, first_msg_nums+second_msg_nums);

    rmb_msg_free(pSendMsg);
    return iRet;
}

static int pub_board_message(StRmbPub *pRmbPub, int iEventOrService, unsigned long ttl_time, const char *cDcn, const char *cServiceId,
                const char *cSenaId, const char *cOrgId, unsigned int log_control, const char *strMsg)
{
    int iRet = 0;
    StRmbMsg *pSendMsg = rmb_msg_malloc();
    rmb_msg_clear(pSendMsg);
    rmb_msg_set_bizSeqNo(pSendMsg, "12345678901234567890123456789012");
    rmb_msg_set_consumerSeqNo(pSendMsg, "22222678901234567890123456799999");
    rmb_msg_set_orgSysId(pSendMsg, "9999");
    //set ttl, millisecond(hao s)
    //printf("ttl = %lu\n", ttl_time);
    if (ttl_time > 0)
            rmb_msg_set_live_time(pSendMsg, ttl_time);

    if (iEventOrService != RMB_EVENT_CALL) {
            printf("type:%d is not event call\n", iEventOrService);
            return -1;
    }

    iRet = rmb_msg_set_dest_v2_1(pRmbPub, cDcn, cServiceId, cSenaId, cOrgId);
    if (iRet < 0) {
            printf("rmb_msg_set_dest_v2_1 return: %d\n", iRet);
            return iRet;
    }

    rmb_msg_set_content(pSendMsg, strMsg, strlen(strMsg));
    char appHeader[100] = "{}";
    rmb_msg_set_app_header(pSendMsg, appHeader, strlen(appHeader));
    if (iEventOrService == 0) {
            iRet = rmb_pub_send_msg(pRmbPub, pSendMsg);
            if (log_control & 2) {
                    if (iRet == 0) {
                            LOG_PRINT(RMB_LOG_INFO,"send msg=%s OK",pSendMsg->cContent);
                    } else {
                            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_msg error!iRet = %d",iRet);
                    }
            }
    }

    rmb_msg_free(pSendMsg);
    return iRet;
}

int mutil_thread_pub_message(StRmbPub *pRmbPub, int iEventOrService, unsigned long ttl, int iContentBase, int iOffset,
        const char* cDcn, const char* cServiceId, const char* cSenaId, const char *strMsg)
{
    int iRet = 0;
    StRmbMsg *pSendMsg = rmb_msg_malloc();

    rmb_msg_set_bizSeqNo(pSendMsg, "12345678901234567890123456789012");
    rmb_msg_set_consumerSeqNo(pSendMsg, "22222678901234567890123456799999");
    rmb_msg_set_orgSysId(pSendMsg, "9999");
    if (ttl > 0) {
        rmb_msg_set_live_time(pSendMsg, ttl);
    }
    if (iEventOrService == 0) {
        rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, RMB_EVENT_CALL, cServiceId, cSenaId);
    } else {
        rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, RMB_SERVICE_CALL, cServiceId, cSenaId);
    }

    rmb_msg_set_content(pSendMsg, strMsg, strlen(strMsg));
    char appHeader[100] = "{}";
    rmb_msg_set_app_header(pSendMsg, appHeader, strlen(appHeader));

    if (iEventOrService == 0) {
        iRet = rmb_pub_send_msg(pRmbPub, pSendMsg);
#ifdef DEBUG
        if (iRet == 0) {
            LOG_PRINT(RMB_LOG_INFO,"send msg=%s OK",pSendMsg->cContent);
        } else {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_msg error!iRet = %d",iRet);
        }
#endif
    } else {
        StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        iRet = rmb_pub_send_and_receive(pRmbPub, pSendMsg, pReceiveMsg, 5000);
        if (iRet == 0) {
//			char receiveBuf[1024];
            char receiveBuf[MAX_MSG_LEN];
            unsigned int receiveLen = sizeof(receiveBuf);
            rmb_msg_get_content(pReceiveMsg, receiveBuf, &receiveLen);
#ifdef DEBUG
            LOG_PRINT(RMB_LOG_INFO,"receive reply pkg=%s",receiveBuf);
        } else {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_and_receive error!iRet = %d,msg=%s",iRet, strMsg);
        }
#else
        }
#endif
        rmb_msg_free(pReceiveMsg);
    }

    rmb_msg_free(pSendMsg);
    return iRet;
}

void mutil_thread_for_pub(void *pArgs)
{
    tThreadArgs *pTmp = (tThreadArgs*)pArgs;

    int iRet = 0;
    unsigned int message_times = 0;
    int i;
    for (i=0; i < pTmp->times; ++i) {
        iRet = mutil_thread_pub_message(pTmp->pRmbPub, pTmp->event_or_service, pTmp->ttl, pTmp->iContent, i,
                pTmp->cDcn, pTmp->cServiceId, pTmp->cSenaId, pTmp->pMsg);
        if (iRet == 0) {
            message_times++;
            pTmp->ulMsgSucc++;
        }
    }
    if (pTmp->event_or_service == RMB_SERVICE_CALL) {
        LOG_PRINT(RMB_LOG_INFO,"threadid:%d -- pub send %u and recv msg:%u\n", (int)pthread_self(), pTmp->times, message_times);
    } else {
        LOG_PRINT(RMB_LOG_ERROR,"threadid:%d -- pub send %u and succ msg:%u\n", (int)pthread_self(), pTmp->times, message_times);
    }
}

//sub回调函数
void func_with_req(const char* buf, const int len, void* pAgv)
{
    if (pAgv == NULL) {
        printf("%s:%d-%s pAgv or pRevMsg is null\n", __FILE__, __LINE__, __func__);
        return;
    }
    StRmbMsg *pReceiveMsg = rmb_msg_malloc();
    if (pReceiveMsg == NULL) {
        printf("rmb_msg_malloc failed");
        return;
    }

    StDemoArgv *p = (StDemoArgv *)pAgv;

    shift_buf_2_msg(pReceiveMsg, buf, len);

    if (p->uiSleepTime > 0)
        sleep(p->uiSleepTime);

    p->uiFlag = 1;
    p->ulMsgTotal++;
    const char* pContent = NULL;
    unsigned int uiContentLen = 0;
    if ((pContent = rmb_msg_get_content_ptr(pReceiveMsg, &uiContentLen)) != NULL) {
        if (p->uiLog == 1) {
            printf("***get message len=%d,%s\n", uiContentLen, pContent);
        }
        if (strlen(pReceiveMsg->replyTo.cDestName) > 0) {
            char replyContent[MAX_MSG_LEN] = {0};
            unsigned int uiReplyLen = 0;
            memcpy(replyContent, pContent, uiContentLen);
            uiReplyLen += uiContentLen;
            memcpy(&replyContent[uiReplyLen], "_reply", strlen("_reply"));
            uiReplyLen += strlen("_reply");
            replyContent[uiReplyLen] = '\0';

            rmb_msg_set_content(p->pReplyMsg, replyContent, uiReplyLen);

            char appHeader[10] = "{}";
            rmb_msg_set_app_header(p->pReplyMsg, appHeader, strlen(appHeader));
            rmb_sub_reply_msg(p->pRmbSub, pReceiveMsg, p->pReplyMsg);
        }
        rmb_sub_ack_msg(p->pRmbSub, pReceiveMsg);
    }

    rmb_msg_free(pReceiveMsg);

    return;
}

//sub回调函数
void func_with_req_pub_reply(const char* buf, const int len, void* pAgv)
{
    if (pAgv == NULL) {
        printf("%s:%d-%s pAgv or pRevMsg is null\n", __FILE__, __LINE__, __func__);
        return;
    }
    StRmbMsg *pReceiveMsg = rmb_msg_malloc();
    if (pReceiveMsg == NULL) {
        printf("rmb_msg_malloc failed");
        return;
    }

    StDemoArgv *p = (StDemoArgv *)pAgv;

    shift_buf_2_msg(pReceiveMsg, buf, len);


    //test for get dest
    const char *pDest = NULL;
    if ((pDest = rmb_msg_get_dest_ptr(pReceiveMsg)) != NULL) {
        printf("get dest name is:%s\n", pDest);
    }

    //test for get ConsumerSysId
    const char *pConsumerSysId = NULL;
    if ((pConsumerSysId = rmb_msg_get_consumerSysId_ptr(pReceiveMsg)) != NULL) {
        printf("get consumer sys id is:%s\n", pConsumerSysId);
    }

    //test for get cConsumerSvrId
    const char *pConsumerSvrId = NULL;
    if ((pConsumerSvrId = rmb_msg_get_consumerSvrId_ptr(pReceiveMsg)) != NULL) {
        printf("get consumer svr id is:%s\n", pConsumerSvrId);
    }

    printf("get consumerSysVersion version:%s\n", pReceiveMsg->sysHeader.cConsumerSysVersion);

    p->ulMsgTotal++;
    const char* pContent = NULL;
    unsigned int uiContentLen = 0;
    if ((pContent = rmb_msg_get_content_ptr(pReceiveMsg, &uiContentLen)) != NULL) {
        if (p->uiLog == 1) {
            printf("***get message len=%d,%s\n", uiContentLen, pContent);
        }

        if (strlen(pReceiveMsg->replyTo.cDestName) > 0) {
            char replyContent[MAX_MSG_LEN] = {0};
            unsigned int uiReplyLen = 0;
            memcpy(replyContent, pContent, uiContentLen);
            uiReplyLen += uiContentLen;
            memcpy(&replyContent[uiReplyLen], "_reply", strlen("_reply"));
            uiReplyLen += strlen("_reply");
            replyContent[uiReplyLen] = '\0';

            rmb_msg_set_content(p->pReplyMsg, replyContent, uiReplyLen);

            char appHeader[10] = "{}";
            rmb_msg_set_app_header(p->pReplyMsg, appHeader, strlen(appHeader));
            rmb_pub_reply_msg(p->pRmbPub, pReceiveMsg, p->pReplyMsg);
        }
        rmb_sub_ack_msg(p->pRmbSub, pReceiveMsg);
    }

    rmb_msg_free(pReceiveMsg);

    return;
}

/**
 * broad cast sub callback
 */
void func_with_broadcast(const char* buf, const int len, void* pAgv)
//void func_with_broadcast(void *pRecvMsg, void* pAgv)
{
    if (pAgv == NULL) {
        printf("%s:%d--%s pAgv is null\n", __FILE__, __LINE__, __func__);
        return;
    }

    StDemoArgv* p = (StDemoArgv*)pAgv;
    StRmbMsg *pReceiveMsg = rmb_msg_malloc();
    if (pReceiveMsg == NULL) {
        printf("rmb_msg_malloc failed\n");
        return;
    }

    shift_buf_2_msg(pReceiveMsg, buf, len);

    p->ulMsgTotal++;

    const char* pContent = NULL;
    unsigned int uiContentLen = 0;
    if ((pContent = rmb_msg_get_content_ptr(pReceiveMsg, &uiContentLen)) != NULL) {
        if (p->uiLog == 1) {
            LOG_PRINT(RMB_LOG_INFO,"receive broadcase content len=%u, %s", uiContentLen, pContent);
        }
    }

    rmb_msg_free(pReceiveMsg);
}

//RR异步时，pub回调函数
void func_with_rr_rsp(const char* buf, const int len, void* pAgv)
{
    if (pAgv == NULL) {
        printf("pAgv or pRevMsg is null");
        return;
    }

    StDemoArgv* p = (StDemoArgv*)pAgv;

    shift_buf_2_msg(p->pReceiveMsg, buf, len);

    p->ulMsgTotal++;

    const char* pUniqueId = rmb_msg_get_uniqueId_ptr(p->pReceiveMsg);
    if (pUniqueId == NULL) {
        printf("%s:%d-%s ,get uniqueId error!\n",  __FILE__, __LINE__, __func__);
    }
    printf("receive Msg:uniqueId = %s\n", pUniqueId);

    const char* pContent = NULL;
    unsigned int uiContentLen = 0;

    if ((pContent = rmb_msg_get_content_ptr(p->pReceiveMsg, &uiContentLen)) != NULL) {

        LOG_PRINT(RMB_LOG_INFO,"recevie reply msg:len=%d,%s\n", uiContentLen, pContent);
    }

    return;
}


//key is topic, like: dcn-s-serviceid--scense_id  AB0-s-11000000-26-0
int get_dcn_service_scense(const char *key, char *dcn, char *event,char *consumerSysId, char *service_id, char *scense_id)
{
    if (key == NULL)
    {       
            printf("key is null");
            return -1;
    }

    char *p = (char *)key;
    char *p1 = NULL;

    //判断分隔符数量是否正确
    int  i = 0;

    do {
            p1 = strchr(p, '-');
            if (p1 == NULL)
            {
                    break;
            }
            i++;
            p = p1+1;
    } while (p != NULL);

    if (i != 4)
    {
            LOG_PRINT(RMB_LOG_ERROR,"key:%s is error",key);
            exit(1);
    }

    char temp[100];
    memset(temp, 0x00, sizeof(temp));
    strncpy(temp, key, sizeof(temp)-1);

    p = strrchr(temp, '-');
    *p='\0';
    //copy scense id
    p = strrchr(temp, '-');
    if (p == NULL || scense_id == NULL)
    {
            return -1;
    }
    strcpy(scense_id, p+1);
    *p = '\0';

    //copy service id
    p = strrchr(temp, '-');
    if (p == NULL || service_id == NULL)
    {
            return -1;
    }
    strcpy(service_id, p+1);
    *p = '\0';

    p = strrchr(temp, '-');
    if (p == NULL)
    {
            return -1;
    }
    if (event != NULL)
    {
            strcpy(event, p+1);
    }
    *p = '\0';

    if (dcn == NULL)
    {
            return -1;
    }
    strcpy(dcn, temp);

    return 0;
}



int pub_binary_message(StRmbPub *pRmbPub, const char* cDcn, const char* cServiceId, const char* cSenaId)
{
    int iRet = 0;
    StRmbMsg *pSendMsg = rmb_msg_malloc();
    rmb_msg_clear(pSendMsg);
    rmb_msg_set_bizSeqNo(pSendMsg, "12345678901234567890123456789012");
    rmb_msg_set_consumerSeqNo(pSendMsg, "22222678901234567890123456799999");
    rmb_msg_set_orgSysId(pSendMsg, "9999");

    int iEventOrService = *(cServiceId + 3) - '0';
    int flag = iEventOrService;
    printf("flag: %d\n", flag);
    rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, cDcn, iEventOrService, cServiceId, cSenaId);

    char *p = "This is test binary message";
    char content[1024];
    unsigned int uiConLen = 0;
    memset(content, 0x00, sizeof(content));
    content[0] = 0x02;
    content[1] = 0;
    memcpy(&content[2], p, strlen(p));
    uiConLen = 2 + strlen(p);

    rmb_msg_set_content(pSendMsg, content, uiConLen);
    char appHeader[100] = "{}";
    rmb_msg_set_app_header(pSendMsg, appHeader, strlen(appHeader));

    if (flag == 1) {
        iRet = rmb_pub_send_msg(pRmbPub, pSendMsg);
        if (iRet == 0) {
            LOG_PRINT(RMB_LOG_INFO,"send msg=%s OK",pSendMsg->cContent);
        } else {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_msg error!iRet = %d",iRet);
        }
        rmb_msg_free(pSendMsg);
        return 0;
    } else {
        StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        iRet = rmb_pub_send_and_receive(pRmbPub, pSendMsg, pReceiveMsg, 5000);
        if (iRet == 0)
        {
            char receiveBuf[1024];
            unsigned int receiveLen = sizeof(receiveBuf);
            rmb_msg_get_content(pReceiveMsg, receiveBuf, &receiveLen);
            LOG_PRINT(RMB_LOG_INFO,"**********receive reply pkg len=%d",receiveLen);
            int i = 0;
            for (i=0; i<receiveLen; i++) {
                printf("%c", receiveBuf[i]);
            }
            printf("\n***********");
        }
        else
        {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_send_and_receive error!iRet = %d",iRet);
        }
        rmb_msg_free(pSendMsg);
        rmb_msg_free(pReceiveMsg);
        return 0;
    }
    return 0;
}

void destroy(){
    if(pRmbSub != NULL){
        rmb_sub_close_v2(pRmbSub);
        //exit(0);
    }
    if (pRmbPub != NULL){
        rmb_pub_close_v2(pRmbPub);
        exit(0);
    }
}


void sigusr2_handle( int iSigVal )
{
        
    LOG_PRINT(RMB_LOG_INFO,"sub accept signal USR2");
    //先stop_receive
    if(rmb_sub_stop_receive(pRmbSub) != 0)
    {
        //没有成功停止接收，有异常
        destroy();
    }
    else 
    {
        while(rmb_sub_check_req_mq_is_null(pRmbSub) != 0)  //本地的共享内存还有未处理完的消息
        {
            rmb_sub_do_receive(pRmbSub, 1);
        }
        //处理完消息之后，退出
        destroy();
    }
}


int main(int argc, char* argv[])
{
    if (argc <= 1) {
        printfUsage(argv[0]);
    }

    int ret=rmb_load_config("./rmb.conf");
    if(ret != 0){
        printf("load rmb config file failed \n");
        return -1;
    }
    pRmbSub = (StRmbSub* )calloc(1, sizeof(StRmbSub));
    if (pRmbSub == NULL) {
        printf("%s:%d -- calloc for pRmbSub failed:%s\n", __func__, __LINE__, strerror(errno));
        return 0;
    }
    pRmbPub = (StRmbPub* )calloc(1, sizeof(StRmbPub));
    if (pRmbPub == NULL) {
        printf("%s:%d -- calloc for pRmbPub failed:%s\n", __func__, __LINE__, strerror(errno));
        return 0;
    }

    char event[10];
    char dcn[10];
    char service_id[10];
    char scenario_id[10];

    enum EVENT_OR_SERVICE_CALL event_or_serv = RMB_SERVICE_CALL;

    int iRet = 0;

    if (strcmp(argv[1], "sub") == 0) 
    {
        //./process_name sub sleep_time log_control process_nums queue1 queue2 ...
    // example: ./rmb_demo sub 1 1 1 FT0-s-98200001-01-1
        if (argc < 6)
            printfUsage(argv[0]);
            signal(SIGUSR2, sigusr2_handle);
        unsigned int uiShmKeyForReq = pRmbStConfig->uiShmKeyForReq;
        unsigned int uiShmSizeForReq = pRmbStConfig->uiShmSizeForReq;
        char strFifoPathForReq[128];
        memset(strFifoPathForReq, 0x00, sizeof(strFifoPathForReq));
        strcpy(strFifoPathForReq, pRmbStConfig->strFifoPathForReq);

        LOG_PRINT(RMB_LOG_INFO,"sub pid:%d   process nums:%d",getpid(),atoi(argv[4]));
        //多进程
        int i;
        for (i=1; i<atoi(argv[4]); i++) {
            if (fork() == 0) {
                uiShmKeyForReq += 4096*i;
                memset(strFifoPathForReq, 0x00, sizeof(strFifoPathForReq));
                snprintf(strFifoPathForReq, sizeof(strFifoPathForReq), "./tmp_req_%d.fifo", getpid());
                break;
            }
        }

        if ((iRet = rmb_sub_init(pRmbSub)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_sub_init error=%d",iRet);
            return -1;
        }

        StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        StRmbMsg *pReplyMsg = rmb_msg_malloc();
        StDemoArgv *pTmp = (StDemoArgv*)calloc(1, sizeof(StDemoArgv));
        if (pReceiveMsg == NULL || pReplyMsg == NULL || pTmp == NULL) {
            LOG_PRINT(RMB_LOG_ERROR,"pReceiveMsg is NULL or pReplyMsg is NULL or pTmp is NULL");
            return -1;
        }
        pTmp->pRmbPub = pRmbPub;
        pTmp->pRmbSub = pRmbSub;
        pTmp->pReplyMsg = pReplyMsg;
        pTmp->uiLog = (unsigned int)atoi(argv[3]);
        ///////////
        pTmp->ulMsgTotal = 0;
        pTmp->uiSleepTime = (unsigned int)atoi(argv[2]);
        pTmp->uiFlag = 0;

        //注册回调
        if ((iRet = rmb_sub_add_reveive_req_by_mq_v2(pRmbSub, strFifoPathForReq, uiShmKeyForReq, uiShmSizeForReq, func_with_req, pTmp)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_sub_add_reveive_req_by_mq error=%d",iRet);
            return -1;
        }

        st_rmb_queue_info *pQueueInfo;
        pQueueInfo = (st_rmb_queue_info *)malloc(sizeof(st_rmb_queue_info) * 100);
        if (pQueueInfo == NULL) {
            printf("malloc for pQueueInfo failed\n");
            return -1;
        }
        memset(pQueueInfo, 0x00, (sizeof(st_rmb_queue_info) * 100));
        st_rmb_queue_info *p = pQueueInfo;
        unsigned int uiQueneNums = 0;

        for (i=0; (i<argc-5) && (i < 100); i++) {
            memset(event, 0x00, sizeof(event));
            memset(dcn, 0x00, sizeof(dcn));
            memset(service_id, 0x00, sizeof(service_id));
            memset(scenario_id, 0x00, sizeof(scenario_id));
            if ((iRet = get_dcn_service_scense(argv[i+5], dcn, event, NULL, service_id, scenario_id)) < 0) {
                printf("parse queue:%s failed,iRet=%d\n", argv[i+5], iRet);
                continue;
            }
            if (strcmp(event, "e") == 0) {
                event_or_serv = RMB_EVENT_CALL;
            } else {
                event_or_serv = RMB_SERVICE_CALL;
            }

            LOG_PRINT(RMB_LOG_INFO,"sub:dcn:%s event:%d service_id:%s scenario_id:%s", dcn, (int)event_or_serv, service_id, scenario_id);

            strncpy(p->cDcn, dcn, strlen(dcn));
            strncpy(p->cServiceId, service_id, strlen(service_id));
            strncpy(p->cScenarioId, scenario_id, strlen(scenario_id));
            p += 1;
            uiQueneNums += 1;
        }

        if ((iRet = rmb_sub_add_listen(pRmbSub, pQueueInfo, uiQueneNums)) != 0) {
            printf("rmb_sub_add_listen failed, iRet = %d\n", iRet);
            return -2;
        }

        unsigned long last_msg_total = 0;
        unsigned long print_msg_ctrl = 0;
        for (;;) {
            //to do recevive
            rmb_sub_do_receive(pRmbSub, 1);

            print_msg_ctrl++;

            if (print_msg_ctrl > 5000) {
                if (last_msg_total != pTmp->ulMsgTotal) {
                    printf("%s:%d -- sub: pid:%d receive message total:%lu\n", __FILE__, __LINE__, getpid(), pTmp->ulMsgTotal);
                    print_msg_ctrl = 0;
                    last_msg_total = pTmp->ulMsgTotal;
                }
            }
        }
    }

    else if (strcmp(argv[1], "sub_broadcast") == 0) {
        //./process_name sub_broadcast process_nums log_control topic(queue)1 topic(queue)2 ...
        unsigned int uiShmKeyForBroadcastReq = pRmbStConfig->uiShmKeyForBroadcast;
        unsigned int uiShmSizeRorBroadcastReq = pRmbStConfig->uiShmSizeForBroadcast;
        char strFifoPathForBroadcastReq[128];

        snprintf(strFifoPathForBroadcastReq, sizeof(strFifoPathForBroadcastReq), "%s", pRmbStConfig->strFifoPathForReq);

        printf("sub_broadcast process nums:%d", atoi(argv[2]));
        //mutil-process
        int i;
        for (i=1; i<atoi(argv[2]); i++) {
            if (fork() == 0) {
                uiShmKeyForBroadcastReq += 4096*i;
                memset(uiShmSizeRorBroadcastReq, 0x00, sizeof(uiShmSizeRorBroadcastReq));
                snprintf(strFifoPathForBroadcastReq, sizeof(strFifoPathForBroadcastReq), "./tmp_req_%d.fifo", getpid());
                break;
            }
        }

        if ((iRet = rmb_sub_init(pRmbSub)) != 0) {
            printf("rmb_sub_init failed\n");
            return -1;
        }

        //StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        StDemoArgv *pTmp = (StDemoArgv *)calloc(1, sizeof(StDemoArgv));
        pTmp->pRmbPub = pRmbPub;
        pTmp->pRmbSub = pRmbSub;
        pTmp->pReplyMsg = NULL;
        pTmp->ulMsgTotal = 0;
        pTmp->uiLog = (unsigned int)atoi(argv[3]);

        //rmb_callback_func func, void *func_msg, void* func_argv
        if ((iRet = rmb_sub_add_reveive_broadcast_by_mq_v2(pRmbSub, strFifoPathForBroadcastReq, uiShmKeyForBroadcastReq, uiShmSizeRorBroadcastReq, func_with_broadcast, pTmp)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_sub_add_reveive_broadcast_by_mq_v2 failed!iRet=%d", iRet);
            return -1;
        }

        st_rmb_queue_info *pQueueInfo;
        pQueueInfo = (st_rmb_queue_info *)malloc(sizeof(st_rmb_queue_info) * 100);
        if (pQueueInfo == NULL) {
            printf("malloc for pQueueInfo failed\n");
            return -1;
        }
        memset(pQueueInfo, 0x00, (sizeof(st_rmb_queue_info) * 100));
        st_rmb_queue_info *p = pQueueInfo;
        unsigned int uiQueneNums = 0;

        for (i=0; (i<argc-4) && (i < 100); i++) {
            memset(dcn, 0x00, sizeof(dcn));
            memset(service_id, 0x00, sizeof(service_id));
            memset(scenario_id, 0x00, sizeof(scenario_id));

            if (get_dcn_service_scense(argv[i+4], dcn, NULL, NULL, service_id, scenario_id) < 0) {
                printf("parse:%s failed\n", argv[i+4]);
                continue;
            }
            LOG_PRINT(RMB_LOG_INFO,"board_sub: dcn:%s service_id:%s scense_id:%s", dcn, service_id, scenario_id);

            event_or_serv = RMB_EVENT_CALL;

            strncpy(p->cDcn, dcn, strlen(dcn));
            strncpy(p->cServiceId, service_id, strlen(service_id));
            strncpy(p->cScenarioId, scenario_id, strlen(scenario_id));
            p += 1;
            uiQueneNums += 1;
        }

        if ((iRet = rmb_sub_add_listen(pRmbSub, pQueueInfo, uiQueneNums)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_sub_add_listen failed, iRet = %d", iRet);
            return -2;
        }

        unsigned long print_msg_ctrl = 0;
        for (;;) {
            rmb_sub_do_receive(pRmbSub, 1);
            print_msg_ctrl++;

            if (print_msg_ctrl > 3000) {
                LOG_PRINT(RMB_LOG_INFO,"sub: pid:%d receive message total:%lu",getpid(), pTmp->ulMsgTotal);
                print_msg_ctrl = 0;
            }
        }

    } else if (strcmp(argv[1], "pub") == 0) {
        //./process_name pub pthread_nums log_control msg_nums msg_len ttl_time sleep_time queue1 queue2 ...
        /*
         * pub:指"rr_sync_pub" or "unicast_pub"
         * pthread_nums: 发送消息的线程数量
         * log_control: 是否打印收发消息的长度，这个是针对包大小测试加的
         * msg_nums： 每个线程发送消息的数量或单线程往每个queue上发送的消息数量
         * msg_len: 发送的消息体的总的长度，这个主要是针对测试包大小而加入的
         * queue1、queue2...: 发送消息使用的queue
         * 
         * example: ./rmb_demo pub 1 1 1 10 3000 1 FT0-s-98200001-01-1
         */
        if (argc < 9)
            printfUsage(argv[0]);

        if ((iRet = rmb_pub_init(pRmbPub)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_init error=%d",iRet);
            return -1;
        }

        int sleep_time = 0;
        if (atoi(argv[7]) > 0)
            sleep_time = atoi(argv[7]);
        char *msg = (char *)calloc(sizeof(char), atoi(argv[5])+1);
        int msg_len = strlen(send_msg);
        int copy_nums = atoi(argv[5]) / msg_len;

        char *p = msg;
        int i;
        for (i=0; i<copy_nums; i++) {
            memcpy(p, send_msg, msg_len);
            p += msg_len;
        }

        if (atoi(argv[5]) % msg_len != 0) {
            memcpy(p, send_msg, atoi(argv[5]) % msg_len);
        }

        if (strcmp(argv[3], "1") == 0) {
            LOG_PRINT(RMB_LOG_INFO,"pub send msg len:%d, content len:%lu", atoi(argv[5]), strlen(msg));
        }

        tMessage msg_info;
        msg_info.type = 0;
        msg_info.pid = getpid();
        msg_info.process_num = 1;
        msg_info.send_msg_num = 0;
        msg_info.recv_msg_num = 0;
        enum EVENT_OR_SERVICE_CALL event_or_serv = RMB_SERVICE_CALL;
        //queue numbers
        int iQueueNumbers = argc - 8;
        if (atoi(argv[2]) == 1) {    //single thread
            for (i=0; i<iQueueNumbers; i++) {
                char event[100];
                char dcn[100];
                char service_id[100];
                char scenario_id[100];

                memset(event, 0x00, sizeof(event));
                memset(dcn, 0x00, sizeof(dcn));
                memset(service_id, 0x00, sizeof(service_id));
                memset(scenario_id, 0x00, sizeof(scenario_id));

                if (get_dcn_service_scense(argv[i+8], dcn, event, NULL, service_id, scenario_id) < 0) {
                    continue;
                }

                if (strcmp(event, "e") == 0) {
                    event_or_serv = RMB_EVENT_CALL;
                }

                LOG_PRINT(RMB_LOG_INFO,"dcn:%s event:%d service_id:%s scenario_id:%s", dcn, (int)event_or_serv, service_id, scenario_id);

                unsigned long pub_send_msg = 0;
                int l;
                for (l=0; l<atoi(argv[4]); l++) {
                    iRet = pubOwnMessage(pRmbPub, event_or_serv, atol(argv[6]), dcn, service_id, scenario_id, (unsigned int)atoi(argv[3]), msg);
                    if (iRet == 0) {
                        pub_send_msg++;
                    }
                    if (sleep_time > 0)
                        sleep(sleep_time);
                }

                msg_info.send_msg_num += (unsigned int)atoi(argv[4]);
                msg_info.recv_msg_num += pub_send_msg;

                
                if (event_or_serv == RMB_EVENT_CALL) {
                    LOG_PRINT(RMB_LOG_INFO,"pid:%ld queue:%s pub send:%d and success:%lu\n", (long)getpid(), argv[i+8], atoi(argv[4]), pub_send_msg);
                } else {
                    LOG_PRINT(RMB_LOG_INFO,"pid:%ld queue:%s pub send:%d and recv:%lu\n", (long)getpid(), argv[i+8], atoi(argv[4]), pub_send_msg);
                }
            }
            rmb_pub_close(pRmbPub);
        } else if (atoi(argv[2]) > 1) {   //mutil thread
            //./process_name pub pthread_nums log_control msg_nums msg_len msg ttl_time queue1 queue2 ...
            pthread_t thVec[200];
            tThreadArgs tArgs[200];
            unsigned int thread_nums = (atoi(argv[2]) < 200 ? atoi(argv[2]) : 200);
            for (i=0; i<iQueueNumbers && i<200; ++i) {
                tArgs[i].pRmbPub = pRmbPub;
                tArgs[i].times = atoi(argv[4]);
                tArgs[i].iContent = i;
                tArgs[i].ulMsgSucc = 0;
                tArgs[i].ttl = atol(argv[6]);
                tArgs[i].pMsg = msg;

                char event[100];

                memset(event, 0x00, sizeof(event));
                memset(tArgs[i].cDcn, 0x00, sizeof(tArgs[i].cDcn));
                memset(tArgs[i].cServiceId, 0x00, sizeof(tArgs[i].cServiceId));
                memset(tArgs[i].cSenaId, 0x00, sizeof(tArgs[i].cSenaId));

                get_dcn_service_scense(argv[i+8], tArgs[i].cDcn, event, NULL, tArgs[i].cServiceId, tArgs[i].cSenaId);

                if (strcmp(event, "e") == 0) {
                    event_or_serv = RMB_EVENT_CALL;
                }
                tArgs[i].event_or_service = event_or_serv;
            }
            if (thread_nums > iQueueNumbers) {
                for (i=iQueueNumbers; i<thread_nums; i++) {
                    tArgs[i].pRmbPub = pRmbPub;
                    tArgs[i].times = atoi(argv[4]);
                    tArgs[i].event_or_service = tArgs[i%iQueueNumbers].event_or_service;
                    tArgs[i].iContent = i;
                    tArgs[i].pMsg = msg;

                    memset(tArgs[i].cDcn, 0x00, sizeof(tArgs[i].cDcn));
                    strcpy(tArgs[i].cDcn, tArgs[i%iQueueNumbers].cDcn);

                    memset(tArgs[i].cServiceId, 0x00, sizeof(tArgs[i].cServiceId));
                    strcpy(tArgs[i].cServiceId, tArgs[i%iQueueNumbers].cServiceId);

                    memset(tArgs[i].cSenaId, 0x00, sizeof(tArgs[i].cSenaId));
                    strcpy(tArgs[i].cSenaId, tArgs[i%iQueueNumbers].cSenaId);
                }
            }
            for (i=0; i<thread_nums; ++i) {
                if ((iRet = pthread_create(&thVec[i], NULL, (void*)&mutil_thread_for_pub, (void*)&tArgs[i])) != 0) {
                    printf("%d thread error!iRet=%d\n", i, iRet);
                }
            }

            unsigned long total_msg = 0;
            for (i=0; i<thread_nums; ++i) {
                if ((iRet = pthread_join(thVec[i], NULL)) != 0) {
                    printf("%d thread error!iRet=%d\n", i, iRet);
                } else {
                    total_msg += tArgs[i].ulMsgSucc;
                }
            }
            if (event_or_serv == RMB_SERVICE_CALL) {
                LOG_PRINT(RMB_LOG_INFO,"*****pub send msg:%d recv:%lu", thread_nums*atoi(argv[4]), total_msg);
            } else {
                LOG_PRINT(RMB_LOG_INFO,"*****pub send msg:%d success:%lu", thread_nums*atoi(argv[4]), total_msg);
            }

            msg_info.send_msg_num = thread_nums*atoi(argv[4]);
            msg_info.recv_msg_num = total_msg;
        }

        free(msg);
    } else if (strcmp(argv[1], "rr_async_pub") == 0) {
        //./test_rmb_capi rr_async_pub process_nums log_control msg_nums msg_len sleep_time timeout queue
        if (argc < 9)
            printfUsage(argv[0]);

        unsigned int uiShmKeyForRsq = pRmbStConfig->uiShmKeyForRRrsp;
        unsigned int uiShmSizeForRsq = pRmbStConfig->uiShmSizeForRRrsp;
        char strFifoPathForRsq[128];
        memset(strFifoPathForRsq, 0x00, sizeof(strFifoPathForRsq));
        strcpy(strFifoPathForRsq, pRmbStConfig->strFifoPathForRRrsp);

        LOG_PRINT(RMB_LOG_INFO,"***RR async, process total:%d, per process send message numbers:%d", atoi(argv[2]), atoi(argv[4]));

        int sleep_time = 0;
        if (atoi(argv[6]) > 0)
            sleep_time = atoi(argv[6]);
        int i;
        for (i=1; i<atoi(argv[2]); i++) {
            if (fork() == 0) {
                uiShmKeyForRsq += 4096 * i;
                memset(strFifoPathForRsq, 0x00, sizeof(strFifoPathForRsq));
                snprintf(strFifoPathForRsq, sizeof(strFifoPathForRsq), "./tmp_rsq_%d.fifo", getpid());
                break;
            }
        }

        if ((iRet = rmb_pub_init(pRmbPub)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_init error=%d",iRet);
            return -1;
        }

        if ((iRet = rmb_sub_init(pRmbSub)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_sub_init error=%d",iRet);
            return -1;
        }
        StRmbMsg *pReceiveMsg = rmb_msg_malloc();
        StRmbMsg *pReplyMsg = rmb_msg_malloc();
        StDemoArgv *pTmp = (StDemoArgv*)calloc(1, sizeof(StDemoArgv));
        pTmp->pRmbPub = pRmbPub;
        pTmp->pRmbSub = pRmbSub;
        pTmp->pReceiveMsg = pReceiveMsg;
        pTmp->pReplyMsg = pReplyMsg;
        pTmp->uiLog = (unsigned int)atoi(argv[3]);
        pTmp->ulMsgTotal = 0;

        if ((iRet = rmb_sub_add_reveive_rsp_by_mq_v2(pRmbSub, strFifoPathForRsq, uiShmKeyForRsq, uiShmSizeForRsq, func_with_rr_rsp, pTmp)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_sub_add_reveive_req_by_mq error=%d",iRet);
            return -1;
        }

        //./test_rmb_capi rr_async_pub process_nums log_control msg_nums msg_len msg queue
        char *msg = (char *)calloc(sizeof(char), atoi(argv[5])+1);
        int msg_len = strlen(send_msg);
        int copy_nums = atoi(argv[5]) / msg_len;

        char *p = msg;
        for (i=0; i<copy_nums; i++) {
            memcpy(p, send_msg, msg_len);
            p += msg_len;
        }

        if (atoi(argv[5]) % msg_len != 0) {
            memcpy(p, send_msg, atoi(argv[5]) % msg_len);
        }

        LOG_PRINT(RMB_LOG_INFO,"rr async pub send msg len:%d, content len:%lu", atoi(argv[5]), strlen(msg));

        StRmbMsg *pSendMsg = rmb_msg_malloc();

        static unsigned int uiSeq = 1;
        struct timeval tv;
        gettimeofday(&tv, NULL);
        unsigned long ulNowTime = tv.tv_sec * 1000 + tv.tv_usec / 1000;
        char cSeqNo[33];
        snprintf(cSeqNo, sizeof(cSeqNo), "%013lu%019u", ulNowTime, uiSeq++);
        rmb_msg_set_bizSeqNo(pSendMsg, cSeqNo);
        rmb_msg_set_consumerSeqNo(pSendMsg, cSeqNo);
        rmb_msg_set_orgSysId(pSendMsg, "9999");
        rmb_msg_set_content(pSendMsg, msg, strlen(msg));
        char appHeader[100] = "{}";
        rmb_msg_set_app_header(pSendMsg, appHeader, strlen(appHeader));

        //增加多个队列支持
        unsigned int send_msg = 0;
        for (i=0; i<argc-7; i++) {
            char dcn[100];
            char service_id[100];
            char scense_id[100];
            memset(dcn, 0x00, sizeof(dcn));
            memset(service_id, 0x00, sizeof(service_id));
            memset(scense_id, 0x00, sizeof(scense_id));
            if (get_dcn_service_scense(argv[i+8], dcn, NULL, NULL, service_id, scense_id) < 0) {
                printf("RR async pub queue error:%s\n", argv[i+6]);
                continue;
            }
            printf("dcn:%s service_id:%s scense_id:%s\n", dcn, service_id, scense_id);

            rmb_msg_set_dest(pSendMsg, RMB_DEST_TOPIC, dcn, RMB_SERVICE_CALL, service_id, scense_id);
            int j;
            for (j=0; j<atoi(argv[4]); j++) {
                iRet = rmb_pub_send_rr_msg_async(pRmbPub, pSendMsg,atoi(argv[7]));
                if (iRet != 0) {
                    printf("%s:%d-%s ,rmb_pub_send_and_receive error!iRet = %d\n", __FILE__, __LINE__, __func__, iRet);
                } else {
                    send_msg++;
                    if (sleep_time > 0)
                        sleep(sleep_time);
                }
            }
        }

        LOG_PRINT(RMB_LOG_INFO,"pid:%d rmb_pub_async_message success total: %u",(int)getpid(), send_msg);

        tMessage msg_info;
        msg_info.type = 0;
        msg_info.pid = getpid();
        msg_info.process_num = atoi(argv[4]);
        msg_info.send_msg_num = atoi(argv[4])*(argc-7);
        msg_info.recv_msg_num = 0;

        unsigned long last_msg_total = 0;
        unsigned long print_msg_ctrl = 0;
        for (;;) {
            //to do recevive
            rmb_sub_do_receive(pRmbSub, 1);
            print_msg_ctrl++;

            if (print_msg_ctrl > 10000) {
                print_msg_ctrl = 0;
                if (pTmp->ulMsgTotal != last_msg_total) {
                    LOG_PRINT(RMB_LOG_INFO,"pid:%d get_reply_msg_total:%lu", (int)getpid(), pTmp->ulMsgTotal);
                    last_msg_total = pTmp->ulMsgTotal;
                }
            }
        }
    } else if (strcmp(argv[1], "pub_board") == 0) {
        //./process_name pub_board message_nums message orgId queue
        //send board message
        if ((iRet = rmb_pub_init(pRmbPub)) != 0) {
            LOG_PRINT(RMB_LOG_ERROR,"rmb_pub_init error=%d",iRet);
            return -1;
        }

        enum EVENT_OR_SERVICE_CALL event_or_serv = RMB_SERVICE_CALL;

        char event[100];
        char dcn[100];
        char service_id[100];
        char scenario_id[100];

        memset(event, 0x00, sizeof(event));
        memset(dcn, 0x00, sizeof(dcn));
        memset(service_id, 0x00, sizeof(service_id));
        memset(scenario_id, 0x00, sizeof(scenario_id));

        iRet = get_dcn_service_scense(argv[5], dcn, event, NULL, service_id, scenario_id);

        if (iRet < 0) {
            printf("get_dcn_service_scense return:%d\n", iRet);
            return iRet;
        }

        if (strcmp(event, "e") == 0) {
            event_or_serv = RMB_EVENT_CALL;
        }
        LOG_PRINT(RMB_LOG_INFO,"dcn:%s event:%d service_id:%s scenario_id:%s", dcn, (int)event_or_serv, service_id, scenario_id);
        unsigned long pub_send_msg = 0;
        int i;
        for (i=0; i<atoi(argv[2]); i++) {
            iRet = pub_board_message(pRmbPub, event_or_serv, 0, dcn, service_id, scenario_id, argv[4], 1, argv[3]);
            if (iRet == 0) {
                pub_send_msg++;
            }
        }
    } 

    else {
        printf("unknown argv[1]:%s\n", argv[1]);
        printfUsage(argv[0]);
    }

    return 0;
}
