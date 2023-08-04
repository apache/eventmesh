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

#include "rmb_mq.h"
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <assert.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
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
#include "rmb_cfg.h"
#include "rmb_log.h"
#include "rmb_define.h"
#include <sys/epoll.h>
#include "rmb_pub.h"

#define NO_CLEAR_FLAG 0
#define CLEAR_FLAG 1

int rmb_notify_get_fator(const int iCount)
{
	if (iCount < 5000)
	{
		return iCount/1000 + 1;
	}
	else if(iCount < 10000)
	{
		return 5;
	}
	else if (iCount < 20000)
	{
		return 8;
	}
	else if (iCount < 30000)
	{
		return 12;
	}
	else if (iCount < 50000)
	{
		return 20;
	}
	else if (iCount < 100000)
	{
		return 30;
	}
	return 40;
}

//get shared mem
int rmb_mq_get_shm(StRmbMq *pMq, const unsigned int shmKey, const unsigned int shmSize, const int flags, const int bReadOnly)
{
	long lRet;
	if ((pMq->ulShmId = shmget(shmKey, shmSize, flags)) == -1)
	{
		return -1;
	}
	if (1 == bReadOnly)
	{
		if ((lRet = (long)shmat(pMq->ulShmId, NULL, SHM_RDONLY)) == -1)
		{
			return -2;
		}
	}
	else
	{
		if ((lRet = (long)shmat(pMq->ulShmId, NULL, 0)) == -1)
		{
			return -3;
		}
	}
	pMq->pMqData = (char*)lRet;
	return 0;
}

//mq init
int rmb_mq_init(StRmbMq *pMq, const unsigned int shmKey, const unsigned int shmSize, const int bCreate, const int bReadOnly)
{
	if(shmSize <= C_RMB_MQ_HEAD_SIZE)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_mq_init shmSize if too small=%d", (int)shmSize);
		return -1;
	}
	pMq->uiShmkey = shmKey;
	pMq->uiShmSize = shmSize;

	int nFlag = 0666;
	//pMq->mqStat.ulShmId = 
	int iRet = rmb_mq_get_shm(pMq, shmKey, shmSize, nFlag, bReadOnly);
	if (iRet != 0)
	{
		if (bCreate)
		{
			nFlag = nFlag | IPC_CREAT;
			iRet = rmb_mq_get_shm(pMq, shmKey, shmSize, nFlag, bReadOnly);
			if (iRet != 0)
			{
				LOGRMB(RMB_LOG_ERROR, "rmb_mq_init get shm failed! has create,iRet=%d", iRet);
				return -2;
			}
			if (!bReadOnly)
			{
				//memset(pMq->pMqData, 0, pMq->uiShmSize);
				memset(pMq->pMqData, 0, C_RMB_MQ_HEAD_SIZE);	//	set head and tail
			}
		}
		else
		{
			LOGRMB(RMB_LOG_ERROR, "rmb_mq_init get shm failed! no create,iRet=%d", iRet);
			return -3;
		}
	}
	
	//init shm
	pMq->pHead = (unsigned int*)pMq->pMqData;
	pMq->pTail = (unsigned int*)(pMq->pMqData + sizeof(unsigned int));
	*(pMq->pHead) = 0;
	*(pMq->pTail) = 0;
	pMq->pBlock = (char*)(pMq->pTail+ 1);
	pMq->uiBlockSize = pMq->uiShmSize - C_RMB_MQ_HEAD_SIZE;
	return 0;
}

//mq_enqueue
//return -2: not enough space
int rmb_mq_enqueue(StRmbMq *pMq, const char* data, unsigned int uiDataLen, unsigned int uiFLow)
{
	if (pMq == NULL || data == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "enqueue failed!pMq==NULL or data==NULL");
		rmb_errno = RMB_ERROR_ARGV_NULL;
		return -1;
	}

	unsigned int uiHead = *pMq->pHead;
	unsigned int uiTail = *pMq->pTail;	
	unsigned int uiFreeLen = uiHead>uiTail ? uiHead-uiTail: uiHead + pMq->uiBlockSize - uiTail;
	unsigned int uiTailLen = pMq->uiBlockSize - uiTail;
	char* pBlock = pMq->pBlock;

	char sHead[C_RMB_MQ_PKG_HEAD_SIZE] = {0};
	unsigned int uiTotalLen = uiDataLen + C_RMB_MQ_PKG_HEAD_SIZE;

	//1.if no enough space
	if (uiFreeLen <= uiTotalLen)
	{
		return -2;
	}
	
	memcpy(sHead, &uiTotalLen, sizeof(unsigned int));
	memcpy(sHead+sizeof(unsigned int), &uiFLow, sizeof(unsigned int));

	//2.if tail space > 8+len
	//copy 8 byte, copy data
	if (uiTailLen >= uiTotalLen)
	{
		memcpy(pBlock + uiTail, sHead, C_RMB_MQ_PKG_HEAD_SIZE);
		memcpy(pBlock + uiTail + C_RMB_MQ_PKG_HEAD_SIZE, data, uiDataLen);
		//update tail
		*pMq->pTail += (uiDataLen + C_RMB_MQ_PKG_HEAD_SIZE);

	}
	//3.if tail space > 8 && < 8+len
	else if (uiTailLen >= C_RMB_MQ_PKG_HEAD_SIZE && uiTailLen < C_RMB_MQ_PKG_HEAD_SIZE + uiDataLen)
	{
		//	copy 8 byte
		memcpy(pBlock + uiTail, sHead, C_RMB_MQ_PKG_HEAD_SIZE);

		//	copy firstlen
		unsigned int uiFirstLen = uiTailLen - C_RMB_MQ_PKG_HEAD_SIZE;
		memcpy(pBlock + uiTail + C_RMB_MQ_PKG_HEAD_SIZE, data, uiFirstLen);

		//	copy secondlen
		unsigned int uiSecondLen = uiDataLen - uiFirstLen;
		memcpy(pBlock, data + uiFirstLen, uiSecondLen);
		
		//update tail
		unsigned int tail = *pMq->pTail + uiDataLen + C_RMB_MQ_PKG_HEAD_SIZE - pMq->uiBlockSize;
		*pMq->pTail = tail;
	}
	//4.if tail space < 8
	else
	{
		//copy tail byte
		memcpy(pBlock + uiTail, sHead, uiTailLen);

		//copy 8-tail byte
		unsigned int uiSecondLen = C_RMB_MQ_PKG_HEAD_SIZE - uiTailLen;
		memcpy(pBlock, sHead + uiTailLen, uiSecondLen);

		//copy data
		memcpy(pBlock + uiSecondLen, data, uiDataLen);
		
		//update tail
		*pMq->pTail = uiSecondLen + uiDataLen;
	}
	return 0;
}

//mq_dequeue
int rmb_mq_dequeue(StRmbMq *pMq, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId)
{
	RMB_CHECK_POINT_NULL(pMq, "pMq");
	RMB_CHECK_POINT_NULL(pMq, "buf");
	RMB_CHECK_POINT_NULL(pMq, "pDataLen");
	
	unsigned int uiHead = *pMq->pHead;
	unsigned int uiTail = *pMq->pTail;	
	const char* pBlock = pMq->pBlock;

	if (uiHead == uiTail)
	{
		*pDataLen = 0;
		return 0;
	}
	unsigned int uiUsedLen = uiTail > uiHead ? uiTail - uiHead : uiTail+ pMq->uiBlockSize -uiHead;
	char sHead[C_RMB_MQ_PKG_HEAD_SIZE];

	//1.get head
	if (uiHead + C_RMB_MQ_PKG_HEAD_SIZE > pMq->uiBlockSize)
	{
		unsigned int uiFirstSize = pMq->uiBlockSize - uiHead;
		unsigned int uiSecondSize = C_RMB_MQ_PKG_HEAD_SIZE - uiFirstSize;
		memcpy(sHead, pBlock + uiHead, uiFirstSize);
		memcpy(sHead + uiFirstSize, pBlock, uiSecondSize);
		uiHead = uiSecondSize;
	}
	else
	{
		memcpy(sHead, pBlock + uiHead, C_RMB_MQ_PKG_HEAD_SIZE);
		uiHead += C_RMB_MQ_PKG_HEAD_SIZE;
	}

	//2.get meta data len
	unsigned int uiTotalLen  = *((unsigned int*)sHead);
	if (pFlowId)
	{
		*pFlowId = *( (unsigned*)(sHead+sizeof(unsigned int)) );
	}
	
	if (uiTotalLen > uiUsedLen)
	{
		LOGRMB(RMB_LOG_ERROR, "dequeue error!uiTotallen=%u > usedLen=%u", uiTotalLen, uiUsedLen);
		return -1;
	}
	
	*pDataLen = uiTotalLen - C_RMB_MQ_PKG_HEAD_SIZE;
	if (*pDataLen > uiBufSize)
	{
		LOGRMB(RMB_LOG_ERROR, "dequeue error!buffer size=%u is not enough,at least=%u!", uiBufSize, *pDataLen);
		return -1;
	}
	
	//3.memcpy the meata data
	if (uiHead + *pDataLen > pMq->uiBlockSize)		
	{
		unsigned int uiFirstSize = pMq->uiBlockSize - uiHead;
		unsigned int uiSecondSize = *pDataLen - uiFirstSize;
		memcpy(buf, pBlock + uiHead, uiFirstSize);
		memcpy(buf + uiFirstSize, pBlock, uiSecondSize);
		//update the head
		*pMq->pHead = uiSecondSize;
	}
	else
	{
		memcpy(buf, pBlock + uiHead, *pDataLen);
		//update the head
		*pMq->pHead = uiHead + *pDataLen;
	}

	return 0;
}

//mq_try_deuque
int rmb_mq_try_dequeue(StRmbMq *pMq, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId)
{
	return rmb_mq_dequeue(pMq, buf, uiBufSize, pDataLen, pFlowId);
}

//mq_get_stat
int rmb_mq_get_stat(StRmbMq* pMq)
{
	unsigned int uiHead = *pMq->pHead;
	unsigned int uiTail = *pMq->pTail;	
	unsigned int uiFreeLen = uiHead>uiTail ? uiHead-uiTail: uiHead + pMq->uiBlockSize - uiTail;
	unsigned int uiUsedLen = uiTail > uiHead ? uiTail - uiHead : uiTail+ pMq->uiBlockSize -uiHead;
	LOGRMB(RMB_LOG_ERROR, "Mq Stat:[head=%u,tail=%u,freeLen=%u,usedLen=%u]",
						uiHead,
						uiTail,
						uiFreeLen,
						uiUsedLen
						);
	return 0;
}

//*****************************for rmb queue(private memory)************************************
//queue init
int rmb_queue_init(StRmbQueue *pQue, const unsigned int size)
{
	if (size <= C_RMB_MQ_HEAD_SIZE) {
		LOGRMB(RMB_LOG_ERROR, "rmb_queue_init size is too small=%u", size);
		return -1;
	}

	pQue->pData = (char *)malloc(sizeof(char) * size);
	if (pQue->pData == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_queue_init malloc for pQue->pData failed");
		return -2;
	}

	pQue->uiSize = size;

	pQue->pHead = (unsigned int *)pQue->pData;
	pQue->pTail = (unsigned int *)(pQue->pData + sizeof(unsigned int));
	*(pQue->pHead) = 0;
	*(pQue->pTail) = 0;
	pQue->pBlock = (char *)(pQue->pTail + 1);
	pQue->uiBlockSize = pQue->uiSize - C_RMB_MQ_HEAD_SIZE;

	return 0;
}

int rmb_queue_enqueue(StRmbQueue *pQue, const char* data, unsigned int uiDataLen, unsigned int uiFLow)
{
	RMB_CHECK_POINT_NULL(pQue, "StRmbQueue:pQue");
	RMB_CHECK_POINT_NULL(data, "StRmbQueue:data");

	unsigned int uiHead = *pQue->pHead;
	unsigned int uiTail = *pQue->pTail;
	unsigned int uiFreeLen = (uiHead > uiTail) ? (uiHead - uiTail) : (uiHead + pQue->uiBlockSize - uiTail);
	unsigned int uiTailLen = pQue->uiBlockSize - uiTail;
	char *pBlock = pQue->pBlock;

	char sHead[C_RMB_MQ_PKG_HEAD_SIZE] = {0};
	unsigned int uiTotalLen = uiDataLen + C_RMB_MQ_PKG_HEAD_SIZE;

	//1.if no enough space
	if (uiFreeLen <= uiTotalLen) {
		return -2;
	}

	memcpy(sHead, &uiTotalLen, sizeof(unsigned int));
	memcpy(sHead+sizeof(unsigned int), &uiFLow, sizeof(unsigned int));

	//2.if tail space > 8+len
	//copy 8 byte, copy data
	if (uiTailLen >= uiTotalLen) {
		memcpy(pBlock + uiTail, sHead, C_RMB_MQ_PKG_HEAD_SIZE);
		memcpy(pBlock + uiTail + C_RMB_MQ_PKG_HEAD_SIZE, data, uiDataLen);
		//update tail
		*pQue->pTail += (uiDataLen + C_RMB_MQ_PKG_HEAD_SIZE);
	}
	//3.if tail space > 8 && < 8+len
	else if (uiTailLen >= C_RMB_MQ_PKG_HEAD_SIZE && uiTailLen < C_RMB_MQ_PKG_HEAD_SIZE + uiDataLen) {
		//	copy 8 byte
		memcpy(pBlock + uiTail, sHead, C_RMB_MQ_PKG_HEAD_SIZE);

		//	copy firstlen
		unsigned int uiFirstLen = uiTailLen - C_RMB_MQ_PKG_HEAD_SIZE;
		memcpy(pBlock + uiTail + C_RMB_MQ_PKG_HEAD_SIZE, data, uiFirstLen);

		//	copy secondlen
		unsigned int uiSecondLen = uiDataLen - uiFirstLen;
		memcpy(pBlock, data + uiFirstLen, uiSecondLen);

		//update tail
		unsigned int tail = *pQue->pTail + uiDataLen + C_RMB_MQ_PKG_HEAD_SIZE - pQue->uiBlockSize;
		*pQue->pTail = tail;
	}
	//4.if tail space < 8
	else
	{
		//copy tail byte
		memcpy(pBlock + uiTail, sHead, uiTailLen);

		//copy 8-tail byte
		unsigned int uiSecondLen = C_RMB_MQ_PKG_HEAD_SIZE - uiTailLen;
		memcpy(pBlock, sHead + uiTailLen, uiSecondLen);

		//copy data
		memcpy(pBlock + uiSecondLen, data, uiDataLen);

		//update tail
		*pQue->pTail = uiSecondLen + uiDataLen;
	}

	return 0;
}

int rmb_queue_dequeue(StRmbQueue *pQue, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId)
{
	RMB_CHECK_POINT_NULL(pQue, "rmb_queue_dequeue:pQue");
	RMB_CHECK_POINT_NULL(buf, "rmb_queue_dequeue:buf");
	RMB_CHECK_POINT_NULL(pDataLen, "rmb_queue_dequeue:pDataLen");

	unsigned int uiHead = *pQue->pHead;
	unsigned int uiTail = *pQue->pTail;
	const char *pBlock = pQue->pBlock;

	if (uiHead == uiTail)
	{
		*pDataLen = 0;
		return 0;
	}
	unsigned int uiUsedLen = uiTail > uiHead ? uiTail - uiHead : uiTail + pQue->uiBlockSize - uiHead;
	char sHead[C_RMB_MQ_PKG_HEAD_SIZE];

	//1.get head
	if (uiHead + C_RMB_MQ_PKG_HEAD_SIZE > pQue->uiBlockSize)
	{
		unsigned int uiFirstSize = pQue->uiBlockSize - uiHead;
		unsigned int uiSecondSize = C_RMB_MQ_PKG_HEAD_SIZE - uiFirstSize;
		memcpy(sHead, pBlock + uiHead, uiFirstSize);
		memcpy(sHead + uiFirstSize, pBlock, uiSecondSize);
		uiHead = uiSecondSize;
	}
	else
	{
		memcpy(sHead, pBlock + uiHead, C_RMB_MQ_PKG_HEAD_SIZE);
		uiHead += C_RMB_MQ_PKG_HEAD_SIZE;
	}

	//2.get meta data len
	unsigned int uiTotalLen  = *((unsigned int*)sHead);
	if (pFlowId)
	{
		*pFlowId = *( (unsigned*)(sHead+sizeof(unsigned int)) );
	}

	if (uiTotalLen > uiUsedLen)
	{
		LOGRMB(RMB_LOG_ERROR, "dequeue error!uiTotallen=%u > usedLen=%u", uiTotalLen, uiUsedLen);
		return -1;
	}

	*pDataLen = uiTotalLen - C_RMB_MQ_PKG_HEAD_SIZE;
	if (*pDataLen > uiBufSize)
	{
		LOGRMB(RMB_LOG_ERROR, "dequeue error!buffer size=%u is not enough,at least=%u!", uiBufSize, *pDataLen);
		return -1;
	}

	//3.memcpy the meata data
	if (uiHead + *pDataLen > pQue->uiBlockSize)
	{
		unsigned int uiFirstSize = pQue->uiBlockSize - uiHead;
		unsigned int uiSecondSize = *pDataLen - uiFirstSize;
		memcpy(buf, pBlock + uiHead, uiFirstSize);
		memcpy(buf + uiFirstSize, pBlock, uiSecondSize);
		//update the head
		*pQue->pHead = uiSecondSize;
	}
	else
	{
		memcpy(buf, pBlock + uiHead, *pDataLen);
		//update the head
		*pQue->pHead = uiHead + *pDataLen;
	}

	return 0;
}

int rmb_queue_try_dequeue(StRmbQueue *pQue, char* buf, unsigned int uiBufSize, unsigned int *pDataLen, unsigned int *pFlowId)
{
	return rmb_queue_dequeue(pQue, buf, uiBufSize, pDataLen, pFlowId);
}

int rmb_queue_get_stat(StRmbQueue *pQue)
{
	unsigned int uiHead = *pQue->pHead;
	unsigned int uiTail = *pQue->pTail;
	unsigned int uiFreeLen = uiHead>uiTail ? uiHead-uiTail: uiHead + pQue->uiBlockSize - uiTail;
	unsigned int uiUsedLen = uiTail > uiHead ? uiTail - uiHead : uiTail+ pQue->uiBlockSize -uiHead;
	LOGRMB(RMB_LOG_ERROR, "queue Stat:[head=%u,tail=%u,freeLen=%u,usedLen=%u]", uiHead, uiTail, uiFreeLen, uiUsedLen);
	return 0;
}

//*****************************for fifo****************************************
//fifo init
//return -1 exist error
int rmb_fifo_init(StRmbFifo* pFifo, const char* strPath)
{
	errno = 0;
	int mode = 0666 | O_NONBLOCK | O_NDELAY;
	if ((mkfifo(strPath, mode)) < 0)
	{
		if (errno != EEXIST)
		{
			LOGRMB(RMB_LOG_ERROR, "fifo strPath=%s has exist", strPath);
			return -1;
		}
	}

	if (pFifo->iFd != -1 && pFifo->iFd != 0)
	{
		close(pFifo->iFd);
		pFifo->iFd = -1;
	}

	if ((pFifo->iFd = open(strPath, O_RDWR)) < 0)
	{
		return -2;
	}
	if (pFifo->iFd > 1024)
	{
		close(pFifo->iFd);
		return -3;
	}

	int val = fcntl(pFifo->iFd, F_GETFL, 0);

	if (val == -1)
	{
		return -4;
	}

	if (val & O_NONBLOCK)
	{
		return 0;
	}

	int iRet = fcntl(pFifo->iFd, F_SETFL, val | O_NONBLOCK | O_NDELAY);
	if (iRet < 0)
	{
		return -5;
	}
	return 0;
}

//notify
int rmb_fifo_send(StRmbFifo* pFifo)
{
	int iRet = write(pFifo->iFd, "\0", 1);
	//LOGRMB(RMB_LOG_DEBUG, "rmb_fifo_send succ!iRet=%d, fd=%d", iRet, pFifo->iFd);
	if (iRet != 0)
	{
		//return -1;
	}
	return 0;
}

//clear notify
int rmb_fifo_clear_flag(StRmbFifo* pFifo)
{
	static char buffer[1];
	read(pFifo->iFd, buffer, 1);
	//LOGRMB(RMB_LOG_DEBUG, "rmb_fifo_clear_flag succ!fd=%d", pFifo->iFd);
	return 0;
}

//*****************************for pipe****************************************
//pipe init
//return -1 exist error
int rmb_pipe_init(StRmbPipe* pPipe)
{
	RMB_CHECK_POINT_NULL(pPipe, "rmb_pipe_init:pPipe");

	if (pipe(pPipe->fd) < 0) {
		LOGRMB(RMB_LOG_ERROR, "pipe failed\n");
		return -1;
	}

	int val = fcntl(pPipe->fd[0], F_GETFL, 0);
	if (val == -1) {
		LOGRMB(RMB_LOG_ERROR, "fcntl get fd[0] failed\n");
		return -2;
	}

	int iRet = fcntl(pPipe->fd[0], F_SETFL, val | O_NONBLOCK | O_NDELAY);
	if (iRet < 0) {
		LOGRMB(RMB_LOG_ERROR, "fcntl set fd[0] failed\n");
		return -3;
	}

	val = fcntl(pPipe->fd[1], F_GETFL, 0);
	if (val == -1) {
		LOGRMB(RMB_LOG_ERROR, "fcntl get fd[1] failed\n");
		return -2;
	}

	iRet = fcntl(pPipe->fd[1], F_SETFL, val | O_NONBLOCK | O_NDELAY);
	if (iRet < 0) {
		LOGRMB(RMB_LOG_ERROR, "fcntl set fd[1] failed\n");
		return -3;
	}

	pPipe->r_fd = pPipe->fd[0];
	pPipe->w_fd = pPipe->fd[1];

	return 0;
}

//notify
int rmb_pipe_send(StRmbPipe* pPipe)
{
	RMB_CHECK_POINT_NULL(pPipe, "rmb_pipe_send:pPipe");

	write(pPipe->w_fd, "\0", 1);

	return 0;
}

//clear notify
int rmb_pipe_clear_flag(StRmbPipe* pPipe)
{
	RMB_CHECK_POINT_NULL(pPipe, "rmb_pipe_send:pPipe");

	static char buffer[1];
	read(pPipe->r_fd, buffer, 1);

	return 0;
}

//******************************for rmb mq notify*******************************
int rmb_notify_init(StRmbMqFifoNotify* pMqNotify)
{
	memset((char*)pMqNotify, 0, sizeof(StRmbMqFifoNotify));
	if (pMqNotify->pBuf == NULL)
	{
		pMqNotify->pBuf = (char*)calloc(MAX_MQ_PKG_SIZE, sizeof(char));
		if (pMqNotify->pBuf == NULL)
		{
			LOGRMB(RMB_LOG_ERROR,"rmb_notify_init error!calloc buf failed!");
			rmb_errno = RMB_ERROR_MALLOC_FAIL;
			return rmb_errno;
		}
	}

	//epoll init
	pMqNotify->iEpollFd = epoll_create(MAX_MQ_NUMS);
	if(pMqNotify->iEpollFd < 0)
	{
		LOGRMB(RMB_LOG_ERROR,"rmb_notify_init error!epoll_create failed!");
		rmb_errno = RMB_ERROR_INIT_EPOLL_FAIL;
		return rmb_errno;
	}
	return 0;
}

//notify add
int rmb_notify_add(StRmbMqFifoNotify* pMqNotify, StRmbMq* mq, StRmbFifo* fifo, const enum RmbMqIndex iMsgType, rmb_callback_func func, void* func_argv)
{
	if (pMqNotify->iMqNum >= MAX_MQ_NUMS)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_add error!mqCurNum=%d,maxNum=%d", pMqNotify->iMqNum, MAX_MQ_NUMS);
		rmb_errno = RMB_ERROR_MQ_NUMS_LIMIT;
		return -1;
	}

	if (pMqNotify->mqIndex[(int)iMsgType] != NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_add error!type:%d has add!!!", (int)iMsgType);
		return -1;
	}

	pMqNotify->vecMqInfo[pMqNotify->iMqNum].mq = mq;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].fifo = fifo;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].que = NULL;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].pipe = NULL;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].func = func;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].args = func_argv;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].iMsgType = iMsgType;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].iIndex = pMqNotify->iMqNum;
	//add init mutex
	pthread_mutex_init(&(pMqNotify->vecMqInfo[pMqNotify->iMqNum].queMutex), NULL);
	pMqNotify->mqIndex[(int)iMsgType] = &(pMqNotify->vecMqInfo[pMqNotify->iMqNum]);

	pMqNotify->vecMqInfo[pMqNotify->iMqNum].iMergeNotifyFLag = 1;
	if (func == NULL)
	{
		pMqNotify->vecMqInfo[pMqNotify->iMqNum].iMergeNotifyFLag = 0;
	}

	//for select
	FD_SET(fifo->iFd, &pMqNotify->readFd);
	if (fifo->iFd >= pMqNotify->iMaxFd)
	{
		pMqNotify->iMaxFd = fifo->iFd + 1;
	}

	//for epoll
	struct epoll_event ev;
	ev.events = EPOLLIN | EPOLLERR;
	ev.data.fd = fifo->iFd;
	ev.data.u32 = pMqNotify->iMqNum;
	epoll_ctl(pMqNotify->iEpollFd, EPOLL_CTL_ADD, fifo->iFd, &ev);
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].active = 0;

	pMqNotify->iMqNum += 1;
	return 0;
}

int rmb_wemq_notify_add(StRmbMqFifoNotify* pMqNotify, StRmbQueue* que, StRmbPipe* pipe, const enum RmbMqIndex iMsgType, rmb_callback_func func, void* func_argv)
{
	if (pMqNotify->iMqNum >= MAX_MQ_NUMS)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_add error!mqCurNum=%d,maxNum=%d", pMqNotify->iMqNum, MAX_MQ_NUMS);
		rmb_errno = RMB_ERROR_MQ_NUMS_LIMIT;
		return -1;
	}
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].mq = NULL;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].fifo = NULL;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].que = que;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].pipe = pipe;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].func = func;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].args = func_argv;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].iMsgType = iMsgType;
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].iIndex = pMqNotify->iMqNum;
	pMqNotify->mqIndex[(int)iMsgType] = &(pMqNotify->vecMqInfo[pMqNotify->iMqNum]);

	pMqNotify->vecMqInfo[pMqNotify->iMqNum].iMergeNotifyFLag = 1;
	if (func == NULL)
	{
		pMqNotify->vecMqInfo[pMqNotify->iMqNum].iMergeNotifyFLag = 0;
	}

	//for select
	FD_SET(pipe->r_fd, &pMqNotify->readFd);
	if (pipe->r_fd >= pMqNotify->iMaxFd) {
		pMqNotify->iMaxFd = pipe->r_fd + 1;
	}

	//for epoll
	struct epoll_event ev;
	ev.events = EPOLLIN | EPOLLERR;
	ev.data.fd = pipe->r_fd;
	ev.data.u32 = pMqNotify->iMqNum;
	epoll_ctl(pMqNotify->iEpollFd, EPOLL_CTL_ADD, pipe->r_fd, &ev);
	pMqNotify->vecMqInfo[pMqNotify->iMqNum].active = 0;

	pMqNotify->iMqNum += 1;
	return 0;
}

int rmb_notify_enqueue_by_type_for_wemq(StRmbMqFifoNotify* pMqNotify, const enum RmbMqIndex uiMsgType, const unsigned int uiCurTime, const char* data, unsigned int uiDataLen)
{
	StMqInfo *pMqInfo = pMqNotify->mqIndex[(int)uiMsgType];
	if (pMqInfo == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue_for_wemq error!pMqInfo is NULL");
		return -1;
	}

	return rmb_notify_enqueue_for_wemq(uiCurTime, pMqInfo, data, uiDataLen);
}

int rmb_notify_enqueue_for_wemq(const unsigned int uiCurTime, StMqInfo* pMqInfo, const char* data, unsigned int uiDataLen)
{
	if (pMqInfo == NULL || pMqInfo->que == NULL || pMqInfo->pipe == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue_for_wemq error!pMqInfo==NULL || pMqInfo->que==NULL || pMqInfo->pipe==NULL");
		return -1;
	}

	int iRet = rmb_queue_enqueue(pMqInfo->que, data, uiDataLen, pMqInfo->iMsgType);
	if (iRet == -2) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue_for_wemq failed!queue full!");
		rmb_errno = RMB_ERROR_ENQUEUE_MQ_FAIL;
		return -2;
	} else if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue_for_wemq failed!iRet=%d", iRet);
		return -3;
	}
	if (pMqInfo->iMergeNotifyFLag == 1) {
		if (uiCurTime >= pMqInfo->uiLastCheckTime + pRmbStConfig->iNotifyCheckSpan) {
			pMqInfo->uiLastCheckTime = uiCurTime;
			pMqInfo->iNotifyFactor = rmb_notify_get_fator(pMqInfo->iCount);
			pMqInfo->iCount = 0;
		} else {
			pMqInfo->iCount++;
		}
		if (pMqInfo->iCount % pMqInfo->iNotifyFactor == 0) {
			iRet = rmb_pipe_send(pMqInfo->pipe);
			if (iRet != 0) {
				LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue failed!rmb_pipe_send return iRet=%d", iRet);
				rmb_errno = RMB_ERROR_SEND_FIFO_NOTIFY_ERROR;
				return -4;
			}
		}
	} else {
		iRet = rmb_pipe_send(pMqInfo->pipe);
		if (iRet != 0) {
			LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue failed!rmb_pipe_send return iRet=%d", iRet);
			rmb_errno = RMB_ERROR_SEND_FIFO_NOTIFY_ERROR;
			return -5;
		}
	}

	return 0;
}

//enqueue
int rmb_notify_enqueue_by_type(StRmbMqFifoNotify* pMqNotify, const enum RmbMqIndex uiMsgType, const unsigned int uiCurTime, const char* data, unsigned int uiDataLen)
{
	StMqInfo* pMqInfo = pMqNotify->mqIndex[(int)uiMsgType];
	if (pMqInfo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR,"rmb_notify_enqueue error!pMqInfo == NULL");
		return -1;
	}
	return rmb_notify_enqueue(uiCurTime, pMqInfo, data, uiDataLen);
}

//enqueue
//-1:error argv
//-2:not enough space
//-3:enquque error
//-4:notify send failed
int rmb_notify_enqueue(const unsigned int uiCurTime, StMqInfo* pMqInfo, const char* data, unsigned int uiDataLen)
{
//	if (pMqInfo->iMsgType >= wemq_req_mq_index) {
//		return rmb_notify_enqueue_for_wemq(uiCurTime, pMqInfo, data, uiDataLen);
//	}

	pthread_mutex_lock(&pMqInfo->queMutex);

	//StMqInfo *pMqInfo = &(pMqNotify->vecMqInfo[(int)iOffset]);
	if (pMqInfo->mq == NULL || pMqInfo->fifo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR,"rmb_notify_enqueue error!pMqInfo->mq == NULL || pMqInfo->fifo == NULL");
		rmb_errno = RMB_ERROR_ARGV_NULL;
		pthread_mutex_unlock(&pMqInfo->queMutex);
		return -1;
	}

	int iRet = rmb_mq_enqueue(pMqInfo->mq, data, uiDataLen, pMqInfo->iMsgType);
	if (iRet == -2)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue error!queue full!");
		rmb_errno = RMB_ERROR_ENQUEUE_MQ_FAIL;
		pthread_mutex_unlock(&pMqInfo->queMutex);
		return -2;
	}
	else if(iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue error!rmb_mq_enqueue iRet=%d", iRet);
		rmb_errno = RMB_ERROR_ENQUEUE_MQ_FAIL;
		pthread_mutex_unlock(&pMqInfo->queMutex);
		return -3;
	}
	if (pMqInfo->iMergeNotifyFLag == 1)
	{
		if (uiCurTime >= pMqInfo->uiLastCheckTime + pRmbStConfig->iNotifyCheckSpan)
		{
			pMqInfo->uiLastCheckTime = uiCurTime;
			pMqInfo->iNotifyFactor = rmb_notify_get_fator(pMqInfo->iCount);
			pMqInfo->iCount = 0;
		}
		else
		{
			pMqInfo->iCount++;
		}
		if (pMqInfo->iCount % pMqInfo->iNotifyFactor == 0)
		{
			iRet = rmb_fifo_send(pMqInfo->fifo);
			if (iRet != 0)
			{
				LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue error!rmb_fifo_send iRet=%d", iRet);
				rmb_errno = RMB_ERROR_SEND_FIFO_NOTIFY_ERROR;
				pthread_mutex_unlock(&pMqInfo->queMutex);
				return -4;
			}
		}
	}
	else
	{
		iRet = rmb_fifo_send(pMqInfo->fifo);
		if (iRet != 0)
		{
			LOGRMB(RMB_LOG_ERROR, "rmb_notify_enqueue error!rmb_fifo_send iRet=%d", iRet);
			rmb_errno = RMB_ERROR_SEND_FIFO_NOTIFY_ERROR;
			pthread_mutex_unlock(&pMqInfo->queMutex);
			return -5;
		}
	}
	pthread_mutex_unlock(&pMqInfo->queMutex);
	return 0;
}

int rmb_notify_dequeue_for_wemq(StMqInfo *pMqInfo, char *buf, const unsigned int uiBufSize, unsigned int *pDataLen)
{
	if (pMqInfo->que == NULL || pMqInfo->pipe == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue_for_wemq error!pMqInfo->que == NULL || pMqInfo->pipe == NULL");
		return -1;
	}

	int iRet = rmb_queue_dequeue(pMqInfo->que, buf, uiBufSize, pDataLen, NULL);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_queue_dequeue error!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_DEQUEUE_MQ_FAIL;
		return -2;
	}

	if (pMqInfo->iMergeNotifyFLag == 0) {
		iRet = rmb_pipe_clear_flag(pMqInfo->pipe);
		if (iRet != 0) {
			LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue_for_wemq error!iRet=%d", iRet);
			rmb_errno = RMB_ERROR_DEQUEUE_MQ_FAIL;
			return -2;
		}
	}

	return 0;
}

//dequeue
int rmb_notify_dequeue(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen)
{
//	if (pMqInfo->iMsgType >= wemq_req_mq_index) {
//		return rmb_notify_dequeue_for_wemq(pMqInfo, buf, uiBufSize, pDataLen);
//	}

	pthread_mutex_lock(&pMqInfo->queMutex);
	//StMqInfo *pMqInfo = &(pMqNotify->vecMqInfo[(int)iOffset]);
	if (pMqInfo->mq == NULL || pMqInfo->fifo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!pMqInfo->mq == NULL || pMqInfo->fifo == NULL");
		rmb_errno = RMB_ERROR_ARGV_NULL;
		pthread_mutex_unlock(&pMqInfo->queMutex);
		return -1;
	}

	int iRet = rmb_mq_dequeue(pMqInfo->mq, buf, uiBufSize, pDataLen, NULL);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!rmb_mq_dequeue iRet=%d", iRet);
		rmb_errno = RMB_ERROR_DEQUEUE_MQ_FAIL;
		pthread_mutex_unlock(&pMqInfo->queMutex);
		return -2;
	}
	if (pMqInfo->iMergeNotifyFLag == 0)
	{
		iRet = rmb_fifo_clear_flag(pMqInfo->fifo);
		if (iRet != 0)
		{
			LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!rmb_fifo_clear_flag iRet=%d", iRet);
			//return -3;
			//return 0;
		}
	}
	pthread_mutex_unlock(&pMqInfo->queMutex);
	return 0;
}

int rmb_notify_try_dequeue_for_wemq(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen)
{
	if (pMqInfo == NULL || pMqInfo->que == NULL || pMqInfo->pipe == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_try_dequeue_for_wemq failed!pMqInfo==NULL or pMqInfo->que==NULL || pMqInfo->pipe==NULL");
		return -1;
	}

	int iRet = rmb_queue_try_dequeue(pMqInfo->que, buf, uiBufSize, pDataLen, NULL);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_queue_try_dequeue failed!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_DEQUEUE_MQ_FAIL;
		return -2;
	}

	if (*pDataLen == 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_try_dequeue_for_wemq failed!queue is empty");
		rmb_errno = RMB_ERROR_DEQUEUE_MQ_EMPTY;
		return -3;
	}

	return 0;
}

//try dequeue
int rmb_notify_try_dequeue(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen)
{
//	if (pMqInfo->iMsgType >= wemq_req_mq_index) {
//		return rmb_notify_try_dequeue_for_wemq(pMqInfo, buf, uiBufSize, pDataLen);
//	}
	//StMqInfo *pMqInfo = &(pMqNotify->vecMqInfo[(int)iOffset]);
	if (pMqInfo->mq == NULL || pMqInfo->fifo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!pMqInfo->mq == NULL || pMqInfo->fifo == NULL");
		rmb_errno = RMB_ERROR_ARGV_NULL;
		return -1;
	}

	pthread_mutex_lock(&pMqInfo->queMutex);
	int iRet = rmb_mq_try_dequeue(pMqInfo->mq, buf, uiBufSize, pDataLen, NULL);
	pthread_mutex_unlock(&pMqInfo->queMutex);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!rmb_mq_dequeue iRet=%d", iRet);
		rmb_errno = RMB_ERROR_DEQUEUE_MQ_FAIL;
		return -2;
	}
	if (*pDataLen == 0)
	{
		rmb_errno = RMB_ERROR_DEQUEUE_MQ_EMPTY;
		return -3;
	}
// 	iRet = rmb_fifo_clear_flag(pMqInfo->fifo);
// 	if (iRet != 0)
// 	{
// 		printf("rmb_notify_dequeue error!rmb_fifo_clear_flag iRet=%d", iRet);
// 		return -3;
// 	}
	return 0;
}

//**************select or epoll*************************
//select fifo fd
//return >0 the mq index which has msg
//		 =0 all mq has no msg
//		 <0 all mq has no msg
static int rmb_notify_select(StRmbMqFifoNotify* pMqNotify, unsigned int uiSec, unsigned int uiUsec)
{
	int i = 0;
	int j = 0;
	errno = 0;
	StMqInfo *pMqInfo;
	FD_ZERO(&(pMqNotify->tmpReadFd));
	pMqNotify->tmpReadFd = pMqNotify->readFd;
	pMqNotify->tv.tv_sec = uiSec;
	pMqNotify->tv.tv_usec = uiUsec;
	int iRet = select(pMqNotify->iMaxFd, &(pMqNotify->tmpReadFd), NULL, NULL, &(pMqNotify->tv));
	if(iRet > 0)
	{
		for (i = 0; i < pMqNotify->iMqNum; i++)
		{
			if(FD_ISSET( pMqNotify->vecMqInfo[i].fifo->iFd, &(pMqNotify->tmpReadFd) ))
			{
				pMqInfo = &(pMqNotify->vecMqInfo[i]);
				rmb_fifo_clear_flag(pMqInfo->fifo);

				for (j = 0; j < pRmbStConfig->iEveryTimeProcessNum; i++)
				{
					iRet = rmb_notify_dequeue(&(pMqNotify->vecMqInfo[i]), pMqNotify->pBuf, MAX_MQ_PKG_SIZE, &(pMqNotify->uiBufLen));
					if (iRet != 0)
					{
						LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!iRet=%d", iRet);
						break;;
					}
					if (pMqNotify->uiBufLen == 0)
					{
						break;
					}

					pMqNotify->pBuf[pMqNotify->uiBufLen] = '\0';
					//shift_buf_2_msg((StRmbMsg *)pMqInfo->pReceiveMsg, pMqNotify->pBuf, pMqNotify->uiBufLen);
					pMqInfo->func(pMqNotify->pBuf, pMqNotify->uiBufLen, pMqInfo->args);
				}
			}
		}
	}
	else
	{
		if (iRet != 0)
		{
			LOGRMB(RMB_LOG_ERROR, "select error!iRet=%d", iRet);
		}
		for (i = 0; i < pMqNotify->iMqNum; i++)
		{
			for (j = 0; j < pRmbStConfig->iEveryTimeProcessNum; i++)
			{
				iRet = rmb_notify_dequeue(&(pMqNotify->vecMqInfo[i]), pMqNotify->pBuf, MAX_MQ_PKG_SIZE, &(pMqNotify->uiBufLen));
				if (iRet != 0)
				{
					LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!iRet=%d", iRet);
					break;;
				}
				if (pMqNotify->uiBufLen == 0)
				{
					break;
				}

				pMqNotify->pBuf[pMqNotify->uiBufLen] = '\0';
				//shift_buf_2_msg((StRmbMsg *)pMqInfo->pReceiveMsg, pMqNotify->pBuf, pMqNotify->uiBufLen);
				pMqInfo = &pMqNotify->vecMqInfo[i];
				pMqInfo->func(pMqNotify->pBuf, pMqNotify->uiBufLen, pMqInfo->args);

				LOGRMB(RMB_LOG_ERROR, "try_dequee succ,j=%d!", j);
				//rmb_mq_get_stat(pMqNotify->vecMqInfo[i].mq);
			}
		}
	}
	return 0;
}

//***********************for epoll*************************
//epoll
int rmb_notify_epoll(StRmbSub *pStRmbSub, int iTimeout)
{
	StRmbMqFifoNotify* pMqNotify = &pStRmbSub->pStContext->fifoMq;
	static struct epoll_event epv[MAX_MQ_NUMS];
	int iEventNum = epoll_wait(pMqNotify->iEpollFd, epv, MAX_MQ_NUMS, iTimeout);
	int iRet = 0;
	StMqInfo *pMqInfo;
	int i = 0;
	int j = 0;
	StRmbMsg *pReceiveMsg = rmb_msg_malloc();
	for( ; i < iEventNum; ++i)
	{
		LOGRMB(RMB_LOG_DEBUG, "rmb_notify_epoll succ!num=%d,i=%d,eventNum=%d", epv[i].data.u32, i, iEventNum);
		pMqInfo = &(pMqNotify->vecMqInfo[epv[i].data.u32]);

		rmb_fifo_clear_flag(pMqInfo->fifo);

		for (j = 0; j < pRmbStConfig->iEveryTimeProcessNum; j++)
		{
			iRet = rmb_notify_dequeue(pMqInfo, pMqNotify->pBuf, MAX_MQ_PKG_SIZE, &(pMqNotify->uiBufLen));
			if (iRet != 0)
			{
				LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!iRet=%d", iRet);
				break;
			}
			if (pMqNotify->uiBufLen == 0)
			{
				break;
			}

			pMqNotify->pBuf[pMqNotify->uiBufLen] = '\0';
			//rmb_send_log_for_func(pMqNotify->pBuf, pMqNotify->uiBufLen, RMB_CONTEXT_TYPE_SUB, pMqInfo->iMsgType);
			shift_buf_2_msg(pReceiveMsg, pMqNotify->pBuf, pMqNotify->uiBufLen);
			//do not filter
            if (rmb_epoll_msg_filter(pStRmbSub, pReceiveMsg) == 0){
				pRmbStConfig->mqIsEmpty = MQ_IS_NOT_EMPTY;
                pMqInfo->func(pMqNotify->pBuf, pMqNotify->uiBufLen, pMqInfo->args);
				wemq_sub_ack_msg(pStRmbSub, pReceiveMsg);
				if(rmb_sub_check_mq_is_null(pStRmbSub) == 0)
				{
					pRmbStConfig->mqIsEmpty = MQ_IS_EMPTY;
				}
			    pMqInfo->active = 1;
			}
	
		}
	}
	
	//for sure write fd is ok!
	for(i = 0; i < pMqNotify->iMqNum; ++i) 
	{
		pMqInfo = &pMqNotify->vecMqInfo[i];
		if( pMqInfo->active == 0)
		{
			for (j = 0; j < pRmbStConfig->iEveryTimeProcessNum; j++)
			{
				iRet = rmb_notify_dequeue(pMqInfo, pMqNotify->pBuf, MAX_MQ_PKG_SIZE, &(pMqNotify->uiBufLen));
				if (iRet != 0)
				{
					LOGRMB(RMB_LOG_ERROR, "rmb_notify_dequeue error!iRet=%d", iRet);
					break;
				}
				if (pMqNotify->uiBufLen == 0)
				{
					break;
				}
				
				pMqNotify->pBuf[pMqNotify->uiBufLen] = '\0';
				//rmb_send_log_for_func(pMqNotify->pBuf, pMqNotify->uiBufLen, RMB_CONTEXT_TYPE_SUB, pMqInfo->iMsgType);
				shift_buf_2_msg(pReceiveMsg, pMqNotify->pBuf, pMqNotify->uiBufLen);
                if (rmb_epoll_msg_filter(pStRmbSub, pReceiveMsg) == 0){
					pRmbStConfig->mqIsEmpty = MQ_IS_NOT_EMPTY;
				    pMqInfo->func(pMqNotify->pBuf, pMqNotify->uiBufLen, pMqInfo->args);
					wemq_sub_ack_msg(pStRmbSub, pReceiveMsg);
					if(rmb_sub_check_mq_is_null(pStRmbSub) == 0)
					{
						pRmbStConfig->mqIsEmpty = MQ_IS_EMPTY;
					}
				}
			}
		}
		else
		{
			pMqInfo->active = 0;
		}	
	}
	rmb_msg_free(pReceiveMsg);

	return 0;
}

int rmb_epoll_msg_filter(StRmbSub *pStRmbSub, StRmbMsg *pReceiveMsg)
{
	RMB_CHECK_POINT_NULL(pStRmbSub, "pStRmbSub");
	RMB_CHECK_POINT_NULL(pReceiveMsg, "pReceiveMsg");
	//rr异步的包，直接返回
    if (pReceiveMsg->cPkgType == RR_TOPIC_PKG){
		return 0;
	}

    //监听模式下调用do_receive，过滤
	int iFlag = 1;
	int i;
	st_rmb_queue_info *p = pStRmbSub->pQueueInfo;
		for(i = 0; i < pStRmbSub->iQueueNum; i++){
		    if(strcmp(pReceiveMsg->strServiceId, p->cServiceId) == 0){
			    iFlag = 0;
			    break;
		    }
			p += 1;
	    }
	
	if (iFlag != 0) {
        LOGRMB(RMB_LOG_WARN, "pReceiveMsg bizseqno=%s not at listen topic!", pReceiveMsg->sysHeader.cBizSeqNo);
		return -2;
	}
    return 0;
}