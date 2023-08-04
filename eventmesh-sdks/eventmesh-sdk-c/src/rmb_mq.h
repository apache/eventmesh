//author:jasonrdzhou
//date:2015-02-02
//content:mq for rmb

#ifndef RMB_MQ_H_
#define RMB_MQ_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <netinet/in_systm.h> 
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <net/if.h>
#include <sys/shm.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <stdio.h> 
#include <string.h> 
#include <stdlib.h> 
#include <netdb.h> 
#include <sys/types.h> 
#include <sys/socket.h> 
#include <arpa/inet.h> 

#include "rmb_define.h"
#include "rmb_msg.h"
#include "rmb_sub.h"

//typedef void (*rmb_callback_func)(const char*, const int, const int type, void*);
//typedef int(*rmb_callback_func_v2)(const char*, const int, void*);
//#define MAX_MQ_NUMS 10
//#define MAX_FIFO_PATH_NAME_LEN 200
//#define MAX_MQ_PKG_SIZE 10000000
////static const unsigned int C_RMB_MQ_HEAD_SIZE = 2 * sizeof(unsigned int);
//#define C_RMB_MQ_HEAD_SIZE  2 * sizeof(unsigned int)
//#define C_RMB_MQ_PKG_HEAD_SIZE 2 * sizeof(unsigned int)
////static const unsigned int C_RMB_MQ_PKG_HEAD_SIZE = 2 * sizeof(unsigned int);

//*************************for mq*****************************
//typedef struct StRmbMq
//{
//	unsigned int uiShmkey;
//	unsigned long ulShmId;
//	unsigned int uiShmSize;
//
//	//head + tail +  real data
//	char* pMqData;
//	unsigned int *pHead;
//	unsigned int *pTail;
//
//	//real data
//	char *pBlock;
//	unsigned int uiBlockSize;
//
//}StRmbMq;

//mq init
int rmb_mq_init(StRmbMq *pMq, const unsigned int shmKey, const unsigned int shmSize, const int bCreate, const int bReadOnly);

//mq_enqueue
int rmb_mq_enqueue(StRmbMq *pMq, const char* data, unsigned int uiDataLen, unsigned int uiFLow);

//mq_dequeue
int rmb_mq_dequeue(StRmbMq *pMq, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId);

//mq_try_deuque
int rmb_mq_try_dequeue(StRmbMq *pMq, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId);

//mq_get_stat
int rmb_mq_get_stat(StRmbMq* pMq);


//*************************for fifo*****************************
//typedef struct StRmbFifo
//{
//	int iFd;
//	char strPath[MAX_FIFO_PATH_NAME_LEN];
//}StRmbFifo;

//fifo init
//return -1 exist error
int rmb_fifo_init(StRmbFifo* pFifo, const char* pPath);

//fifo
int rmb_fifo_send(StRmbFifo* pFifo);

//clear notify
int rmb_fifo_clear_flag(StRmbFifo* pFifo);

//*************************for queue******************************
//typedef struct StRmbQueue
//{
//	unsigned int uiSize;
//
//	//head + tail +  real data
//	char* pData;
//	unsigned int *pHead;
//	unsigned int *pTail;
//
//	//real data
//	char *pBlock;
//	unsigned int uiBlockSize;
//}StRmbQueue;

//queue init
int rmb_queue_init(StRmbQueue *pQue, const unsigned int size);

//enqueue
int rmb_queue_enqueue(StRmbQueue *pQue, const char* data, unsigned int uiDataLen, unsigned int uiFLow);

//dequeue
int rmb_queue_dequeue(StRmbQueue *pQue, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId);

//try deuque
int rmb_queue_try_dequeue(StRmbQueue *pQue, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId);

//get stat
int rmb_queue_get_stat(StRmbQueue *pQue);

//*************************for pipe*****************************
//typedef struct StRmbPipe
//{
//	int fd[2];
//	int r_fd;
//	int w_fd;
//}StRmbPipe;

//pipe init
//return -1 exist error
int rmb_pipe_init(StRmbPipe* pPipe);

//pipe
int rmb_pipe_send(StRmbPipe* pPipe);

//clear notify
int rmb_pipe_clear_flag(StRmbPipe* pPipe);

//*************************for mq & notify***********************
//typedef struct StMqInfo
//{
//	StRmbMq* mq;
//	StRmbFifo* fifo;
//
//	//for wemq
//	StRmbQueue *que;
//	StRmbPipe *pipe;
//
//	int iIndex;		//offset in vector
//	int iMsgType;	//iPkgType,1 queue msg;2 rr topic msg;3 broadcast msg;4 manage msg
//
//	rmb_callback_func func;
//	rmb_callback_func_v2 funcForNew;
//	void* args;
//
//	//for epoll
//	int active;
//
//	//for notify num
//	int iCount;
//	unsigned int uiLastCheckTime;
//
//	int iMergeNotifyFLag; //0：not marge 1:marge
//	int iNotifyFactor;   //the factor of notify
//}StMqInfo;


//enum RmbMqIndex
//{
//	req_mq_index = 1,
//	rr_rsp_mq_index = 2,
//	broadcast_mq_index = 3,
//	manage_mq_index = 4,
//
//	wemq_req_mq_index = 5,
//	wemq_rr_rsp_mq_index = 6,
//	wemq_broadcast_mq_index = 7,
//	wemq_manage_mq_index = 8,
//};
//
////for mq notify
//typedef struct StRmbMqFifoNotify
//{
//	StMqInfo vecMqInfo[MAX_MQ_NUMS];
//	int iMqNum;
//
//	StMqInfo* mqIndex[MAX_MQ_NUMS]; //see define of RmbMqIndex
//	//for select
//	fd_set readFd;
//	fd_set tmpReadFd;
//	struct timeval tv;
//	int iMaxFd;
//
//	//for epoll
//	int iEpollFd;
//
//	char* pBuf;
//	unsigned int uiBufLen;
//	int type;
//
//}StRmbMqFifoNotify;

//******************************************************function******************************s


int rmb_notify_init(StRmbMqFifoNotify* pMqNotify);

//notify add
int rmb_notify_add(StRmbMqFifoNotify* pMqNotify, StRmbMq* mq, StRmbFifo* fifo, const enum RmbMqIndex iMsgType, rmb_callback_func func, void* func_argv);

//enqueue
//-1:error argv
//-2:not enough space
//-3:enquque error
//-4:notify send failed
int rmb_notify_enqueue_by_type(StRmbMqFifoNotify* pMqNotify,  const enum RmbMqIndex uiMsgType, const unsigned int uiCurTime,  const char* data, unsigned int uiDataLen);

//enqueue
//-1:error argv
//-2:not enough space
//-3:enquque error
//-4:notify send failed
int rmb_notify_enqueue(const unsigned int uiCurTime, StMqInfo* pMqInfo, const char* data, unsigned int uiDataLen);

//dequeue
int rmb_notify_dequeue(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen);

//try dequeue
int rmb_notify_try_dequeue(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen);

//**************select or epoll*************************
//select fifo fd
//return >0 the mq index which has msg
//		 =0 all mq has no msg
//		 <0 all mq has no msg
//int rmb_notify_select(StRmbMqFifoNotify* pMqNotify, unsigned int uiSec, unsigned int uiUsec);

//epoll mq
int rmb_notify_epoll(StRmbSub* pStRmbSub, int iTimeout);

//filter rmb msg 
int rmb_epoll_msg_filter(StRmbSub* pStRmbSub, StRmbMsg* pReceiveMsg);

#ifdef __cplusplus
}
#endif

#endif
