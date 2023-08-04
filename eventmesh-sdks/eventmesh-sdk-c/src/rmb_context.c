#include "rmb_context.h"
#include "rmb_udp.h"
#include "rmb_log.h"
#include "rmb_common.h"
#include "rmb_msg.h"
#include "rmb_mq.h"
#include "md5c.h"
#include "rmb_errno.h"
#include <errno.h>
#include <pthread.h>
#include "wemq_thread.h"

//char cManageTopic[30] = "rmb_c/manage";
char cManageTopic[30] = "rmb_c_api/manage";
char cQueueFullTopic[30] = "VPN_AD_MSG_SPOOL_TOPIC";
char cLogLevelTopic[30] = "OPEN_DEBUG_LOG_TOPIC";
char cPublishCheck[30] = "ALLOW_PUBLISH_MESSAGE_TOPIC";
#define TWO_STOP_TIME 300000

//add for period log thread
int g_iLogThreadInit = 0;
pthread_t g_stLogThreadId;

//////////////for wemq
static void _wemq_worker_thread_func(void *arg)
{
	StThreadArgs* pArg = (StThreadArgs*)arg;
	WemqThreadCtx stThreadCtx;
	stThreadCtx.m_ptProxyContext = pArg->pStContextProxy;
	stThreadCtx.m_contextType = pArg->contextType;
	stThreadCtx.m_iState = THREAD_STATE_INIT;
	stThreadCtx.m_iSockFdOld = -1;

	if (pArg->contextType == RMB_CONTEXT_TYPE_PUB) {
		stThreadCtx.m_ptTopicList = NULL;
		stThreadCtx.m_ptFifo = (void *)&(pArg->pStContextProxy->pubFifo);
	} else {
		stThreadCtx.m_ptTopicList = &(pArg->pStContextProxy->stTopicList);
		stThreadCtx.m_ptFifo = (void*)&(pArg->pStContextProxy->subFifo);
	}

	wemq_thread_run(&stThreadCtx);
	free(pArg);
}

static int _wemq_context_create_thread(int contextType, stContextProxy *pContextProxy)
{
	int iRet = -1;
	if (contextType == RMB_CONTEXT_TYPE_PUB) {
		//thread has been created
		if (pContextProxy->mainThreadId != 0) {
			return 0;
		}

		//create pub thread (main thread);
		StThreadArgs *pMainArgs = (StThreadArgs *)malloc(sizeof(StThreadArgs));
		if (pMainArgs == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pMainArgs failed!");
			return -1;
		}
		pMainArgs->pStContextProxy = pContextProxy;
		pMainArgs->contextType = RMB_CONTEXT_TYPE_PUB;

		iRet = pthread_create(&pContextProxy->mainThreadId, NULL, (void *)&_wemq_worker_thread_func, pMainArgs);
		if (iRet != 0) {
			LOGRMB(RMB_LOG_ERROR, "create main thread error!iRet=%d", iRet);
			rmb_errno = RMB_ERROR_CREATE_THREAD_FAIL;
			return rmb_errno;
		}

		struct timeval nowTimeVal;
		gettimeofday(&nowTimeVal, NULL);
		struct timespec timeout;
		timeout.tv_sec = nowTimeVal.tv_sec + pRmbStConfig->createConnectionTimeOut;
		timeout.tv_nsec = nowTimeVal.tv_usec * 1000;

		pContextProxy->iFlagForPub = 0;
		pthread_mutex_lock(&pContextProxy->pubMutex);
		if (pContextProxy->iFlagForPub == 0) {
			pthread_cond_timedwait(&pContextProxy->pubCond, &pContextProxy->pubMutex, &timeout);
		}
		pthread_mutex_unlock(&pContextProxy->pubMutex);
		if (pContextProxy->iFlagForPub != 1) {
			return -1;
		}
	} else if (contextType == RMB_CONTEXT_TYPE_SUB) {
		//thread has been created
		if (pContextProxy->coThreadId != 0) {
			return 0;
		}

		//create sub thread (co thread)
		StThreadArgs *pCoArgs = (StThreadArgs *)malloc(sizeof(StThreadArgs));
		if (pCoArgs == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pCoArgs failed!");
			return -2;
		}
		pCoArgs->pStContextProxy = pContextProxy;
		pCoArgs->contextType = RMB_CONTEXT_TYPE_SUB;
		iRet = pthread_create(&pContextProxy->coThreadId, NULL, (void *)&_wemq_worker_thread_func, pCoArgs);
		if (iRet != 0) {
			LOGRMB(RMB_LOG_ERROR, "create co thread error!iRet=%d", iRet);
			rmb_errno = RMB_ERROR_CREATE_THREAD_FAIL;
			return rmb_errno;
		}

		struct timeval nowTimeVal;
		gettimeofday(&nowTimeVal, NULL);
		struct timespec timeout;
		timeout.tv_sec = nowTimeVal.tv_sec +  pRmbStConfig->createConnectionTimeOut;
		timeout.tv_nsec = nowTimeVal.tv_usec * 1000;

		pContextProxy->iFlagForSub = 0;
		pthread_mutex_lock(&pContextProxy->subMutex);
		if (pContextProxy->iFlagForSub == 0) {
			pthread_cond_timedwait(&pContextProxy->subCond, &pContextProxy->subMutex, &timeout);
		}
		pthread_mutex_unlock(&pContextProxy->subMutex);
		if (pContextProxy->iFlagForSub != 1){
			return -1;
		}
	} else {
		LOGRMB(RMB_LOG_ERROR, "contextType is illegal(%d)!", contextType);
		rmb_errno = RMB_ERROR_CREATE_THREAD_FAIL;
		return rmb_errno;
	}
//	sleep(1);	//等待线程先执行
	return 0;
}

static void wemq_context_init_thread_sync_obj(stContextProxy* pContextProxy)
{
	pthread_mutex_init(&pContextProxy->rrMutex, NULL);
	pthread_cond_init(&pContextProxy->rrCond, NULL);
	pContextProxy->iFlagForRR = 0;

	pthread_mutex_init(&pContextProxy->regMutex, NULL);
	pthread_cond_init(&pContextProxy->regCond, NULL);
	pContextProxy->iFlagForReg = 0;

	pthread_mutex_init(&pContextProxy->pubMutex, NULL);
	pthread_cond_init(&pContextProxy->pubCond, NULL);
	pContextProxy->iFlagForPub = 0;

	pthread_mutex_init(&pContextProxy->subMutex, NULL);
	pthread_cond_init(&pContextProxy->subCond, NULL);
	pContextProxy->iFlagForSub = 0;

	pthread_mutex_init(&pContextProxy->eventMutex, NULL);
	pthread_cond_init(&pContextProxy->eventCond, NULL);
	pContextProxy->iFlagForEvent = 0;          //初始值设置为0，发送时设为-1，发送完ack再设为0。确保所有消息都回来的时候为0.相当于初始状态
	pContextProxy->iFlagForRRAsync = 0;


}


int wemq_context_init_add_manage_topic(stContextProxy* pContextProxy, int type)
{
	int iRet = -1;
	StWemqThreadMsg stThreadMsg;
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_ADD_MANAGE;

	if (type == 1)
	{
		iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
		if (iRet <= 0)
		{
			LOGRMB(RMB_LOG_ERROR, "send msg to worker thread by kfifo error\n");
		}
	}
	else
	{
		iRet = wemq_kfifo_put(&pContextProxy->subFifo, stThreadMsg);
		if (iRet <= 0)
		{
			LOGRMB(RMB_LOG_ERROR, "send msg to worker thread by kfifo error\n");
		}
	}

	struct timeval nowTimeVal;
	struct timespec timeout;
	gettimeofday(&nowTimeVal, NULL);
	timeout.tv_sec = nowTimeVal.tv_sec + (nowTimeVal.tv_usec / 1000 + pRmbStConfig->iNormalTimeout) / 1000;
	timeout.tv_nsec = ((nowTimeVal.tv_usec / 1000 + pRmbStConfig->iNormalTimeout) % 1000) * 1000 * 1000;

	pContextProxy->iFlagForReg = 0;
	pthread_mutex_lock(&pContextProxy->regMutex);
	if (pContextProxy->iFlagForReg == 0)
	{
		pthread_cond_timedwait(&pContextProxy->regCond, &pContextProxy->regMutex, &timeout);
	}
	pthread_mutex_unlock(&pContextProxy->regMutex);

	if (pContextProxy->iFlagForReg != 1)
	{
		LOGRMB(RMB_LOG_ERROR, "add manage topic timeout!\n");
		rmb_errno = RMB_ERROR_WORKER_REGISTER_ERROR;
		return rmb_errno;
	}

	/*
	if (pContextProxy->rspForAddManage.uiResult != 0)
	{
		LOGWEMQ(WEMQ_LOG_ERROR, "Failed to add magnage!manageTopic=%s\n", pContextProxy->rspForAddManage.strManageTopic);
		return -1;
	}
	*/
	return 0;
}

static int wemq_context_init_proxy_model(StContext *pStContext)
{
	stContextProxy *pContextProxy = NULL;

	//context proxy has been init;
	//get only one contextproxy;
	if (pRmbStConfig->iProxyContextNums == 1) {
		pStContext->pContextProxy = pRmbStConfig->pProxyContext;
		pContextProxy = pRmbStConfig->pProxyContext;
		if (pStContext->contextType == RMB_CONTEXT_TYPE_PUB) {
			pContextProxy->pubContext = (void *)pStContext;
		} else {
			pContextProxy->subContext = (void *)pStContext;
		}

		return _wemq_context_create_thread(pStContext->contextType, pContextProxy);
	}

	//create proxy context
	pContextProxy = (stContextProxy *)malloc(sizeof(stContextProxy));
	if (pContextProxy == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStContext->pContextProxy malloc error!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return rmb_errno;
	}
	memset(pContextProxy, 0, sizeof(stContextProxy));
	pContextProxy->iFlagForRun = 1;
	pContextProxy->ulGoodByeTime = 0;
	pContextProxy->ulLastClearRRAysncMsgTime = 0;

	pStContext->pContextProxy = pContextProxy;

	if (pStContext->contextType == RMB_CONTEXT_TYPE_PUB) {
		pContextProxy->pubContext = (void *)pStContext;
	} else {
		pContextProxy->subContext = (void *)pStContext;
	}

	INIT_WEMQ_KFIFO(pContextProxy->pubFifo);
	INIT_WEMQ_KFIFO(pContextProxy->subFifo);

	wemq_topic_list_init(&pContextProxy->stTopicList);

	wemq_context_init_thread_sync_obj(pContextProxy);

//	pContextProxy->rrHashTable = (myhast_t *)malloc(sizeof(myhash_t));
//	if (pContextProxy->rrHashTable == NULL ||
//			myhash_init(pContextProxy->rrHashTable, 10000000))
//	{
//		LOGRMB(RMB_LOG_ERROR, "pContextProxy->rrHashTable malloc or init error!\n");
//		rmb_errno = RMB_ERROR_MALLOC_FAIL;
//		return rmb_errno;
//	}

	pContextProxy->pReplyMsg = rmb_msg_malloc();

	//all buffer initialize
	pContextProxy->mPubRRBuf = (char *)malloc(TCP_BUF_SIZE * sizeof(char));
	if (pContextProxy->mPubRRBuf == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pContextProxy->mPubRRBuf malloc error!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return rmb_errno;
	}
	memset(pContextProxy->mPubRRBuf, 0x00, TCP_BUF_SIZE * sizeof(char));


    Init(&pContextProxy->pUniqueListForRRAsyncNew);
	Init(&pContextProxy->pUniqueListForRRAsyncOld);
	
	//pContextProxy->pUniqueListForRRAsyncNew = pContextProxy->stUniqueListForRRAsync;
	//pContextProxy->pUniqueListForRRAsyncOld = pContextProxy->stUniqueListForRRAsyncOld;
	//memset(pContextProxy->pUniqueListForRRAsyncNew,0x00,RMB_MAX_UNIQUE_NUMS * sizeof(StUniqueIdList));
	//memset(pContextProxy->pUniqueListForRRAsyncOld,0x00,RMB_MAX_UNIQUE_NUMS * sizeof(StUniqueIdList));

	// context proxy init ok;
	pRmbStConfig->pProxyContext = pContextProxy;
	pRmbStConfig->iProxyContextNums = 1;

	return _wemq_context_create_thread(pStContext->contextType, pContextProxy);
}


StContext* g_pStContextArry[MAX_RMB_CONTEXT] = {NULL};


int Log_Thread_Start(StContext *pStContext)
{
	LOGRMB(RMB_LOG_INFO, "call thread start StContext = 0x%p", pStContext);
    
	if (pStContext->contextType == RMB_CONTEXT_TYPE_PUB) {
		pRmbStConfig->iFlagForLoop = 1;
	}

	pthread_mutex_lock(&pRmbStConfig->configLog);
	if (g_iLogThreadInit == 1)
	{
	   if (g_pStContextArry[1] == NULL)
			g_pStContextArry[1] = pStContext;
	   pthread_mutex_unlock(&pRmbStConfig->configLog);
	   return 0;
   }

	g_iLogThreadInit = 1;
	pthread_mutex_unlock(&pRmbStConfig->configLog);

	g_pStContextArry[0] = pStContext;
	g_pStContextArry[1] = NULL;

 	int iRet = pthread_create(&g_stLogThreadId, NULL, (void*)&_Log_Thread_func, NULL);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "create Log thread error!iRet=%d\n", iRet);
	}
 	return iRet;
}

int rmb_context_init(StContext *pStContext)
{
	if (pStContext == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStContext is null!");
		rmb_errno = RMB_ERROR_ARGV_NULL;
		return -1;
	}
	if (pStContext->uiInitFlag == 1) {
		LOGRMB(RMB_LOG_ERROR, "pStContext has already init!");
		return 0;
	}

	/**
 * 由于当前使用的solace c api的版本为CCSMP Version 7.0.2.127 (Sep 12 2014 14:07:09)  Variant: Linux26-x86_64_opt - C SDK
 * 该版本重复init时，会导致出现close(fd)两次的core，故加入标识
 * rmb_msg_init生成uniqueId时需要使用solace api
 */
	if (pRmbStConfig->uiIsInitSolaceApi == 0) {
		solClient_initialize(SOLCLIENT_LOG_DEFAULT_FILTER, NULL);
		if (pRmbStConfig->iSwitchForSolaceLog == 1) {
			solClient_log_setFilterLevel(SOLCLIENT_LOG_CATEGORY_ALL, pRmbStConfig->iSolaceLogLevel);
			solClient_log_setFile(pRmbStConfig->strSolaceLog);
		}
		pRmbStConfig->uiIsInitSolaceApi = 1;
	}

	int iRet = 0;

		pStContext->pReceiveWemqMsg = NULL;
		pStContext->pReceiveWemqMsg = rmb_msg_malloc();
		if (pStContext->pReceiveWemqMsg == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pStContext->pReceiveWemqMsg error!");
			rmb_errno = RMB_ERROR_MALLOC_FAIL;
			return rmb_errno;
		}

		pStContext->pReceiveWemqMsgForRR = NULL;
		pStContext->pReceiveWemqMsgForRR = rmb_msg_malloc();
		if (pStContext->pReceiveWemqMsgForRR == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pStContext->pReceiveWemqMsgForRR error!");
			rmb_errno = RMB_ERROR_MALLOC_FAIL;
			return rmb_errno;
		}

		pStContext->pReceiveWemqMsgForBroadCast = NULL;
		pStContext->pReceiveWemqMsgForBroadCast = rmb_msg_malloc();
		if (pStContext->pReceiveWemqMsgForBroadCast == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pStContext->pReceiveWemqMsgForBroadCast failed!");
			rmb_errno = RMB_ERROR_MALLOC_FAIL;
			return rmb_errno;
		}

		pStContext->pWemqPkg = NULL;
		pStContext->pWemqPkg = (char *)malloc(MAX_LENTH_IN_A_MSG);
		if (pStContext->pWemqPkg == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pStContext->pWemqPkg error!");
			rmb_errno = RMB_ERROR_MALLOC_FAIL;
			return rmb_errno;
		}
		memset(pStContext->pWemqPkg, 0x00, MAX_LENTH_IN_A_MSG);

		pStContext->pWemqPkgForRRAsync = NULL;
		pStContext->pWemqPkgForRRAsync = (char *)malloc(MAX_LENTH_IN_A_MSG);
		if (pStContext->pWemqPkgForRRAsync == NULL) {
			LOGRMB(RMB_LOG_ERROR, "malloc for pStContext->pWemqPkgForRRAsync error!");
			rmb_errno = RMB_ERROR_MALLOC_FAIL;
			return rmb_errno;
		}
		memset(pStContext->pWemqPkgForRRAsync, 0x00, MAX_LENTH_IN_A_MSG);

		pStContext->uiInitFlag = 1;
//		int iRet = Log_Thread_Start(pStContext);
		iRet = wemq_context_init_proxy_model(pStContext);
		if (iRet != 0) {
			LOGRMB(RMB_LOG_ERROR, "wemq_context_init_proxy_model failed, iRet=%d", iRet);
			return iRet;
		}


	iRet = Log_Thread_Start(pStContext);

	return 0;
}

int rmb_context_add_rsp_socket(StContext *pStContext, const char* cLocalIp, unsigned short usRspPort)
{
	if (usRspPort != 0)
	{
		//init udp
		pStContext->iSocketForRsp = udp_get_socket("0.0.0.0", "0", NULL);
		if (pStContext->iSocketForRsp < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "context init error!because of udp socket init failed!port=%u", (unsigned int)usRspPort);
			rmb_errno = RMB_ERROR_INIT_UDP_FAIL;
			return -3;
		}
		tcp_nodelay(pStContext->iSocketForRsp);
		bzero(&pStContext->tmpReplyAddr, sizeof(pStContext->tmpReplyAddr));
		pStContext->tmpReplyAddr.sin_addr.s_addr = inet_addr(cLocalIp);
		pStContext->tmpReplyAddr.sin_port = htons(usRspPort);
		pRmbStConfig->iFlagForRRrsp = (int)MSG_IPC_UDP;
	}
	else
	{
		pStContext->iSocketForRsp = 0;
	}
	return 0;
}

int rmb_context_add_broadcast_socket(StContext *pStContext, const char* cLocalIp, unsigned short usBroadcastPort)
{
	if (usBroadcastPort != 0)
	{
		//init udp
		pStContext->iSocketForBroadcast = udp_get_socket("0.0.0.0", "0", NULL);
		if (pStContext->iSocketForBroadcast < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "context init error!because of udp socket init failed!port=%u", (unsigned int)usBroadcastPort);
			rmb_errno = RMB_ERROR_INIT_UDP_FAIL;
			return -3;
		}
		tcp_nodelay(pStContext->iSocketForBroadcast);
		bzero(&pStContext->tmpBroadcastAddr, sizeof(pStContext->tmpBroadcastAddr));
		pStContext->tmpBroadcastAddr.sin_addr.s_addr = inet_addr(cLocalIp);
		pStContext->tmpBroadcastAddr.sin_port = htons(usBroadcastPort);
		pRmbStConfig->iFlagForBroadCast = (int)MSG_IPC_UDP;
	}
	else
	{
		pStContext->iSocketForBroadcast = 0;
	}
	return 0;
}

int rmb_context_add_req_socket(StContext *pStContext, const char* cLocalIp, unsigned short usReqPort)
{
	if (usReqPort != 0)
	{
		//init udp
		pStContext->iSocketForReq = udp_get_socket("0.0.0.0", "0", NULL);
		if (pStContext->iSocketForReq < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "context init error!because of udp socket init failed!port=%u", (unsigned int)usReqPort);
			rmb_errno = RMB_ERROR_INIT_UDP_FAIL;
			return -3;
		}
		tcp_nodelay(pStContext->iSocketForReq);
		bzero(&pStContext->tmpReqAddr, sizeof(pStContext->tmpReqAddr));
		//pStContext->tmpReqAddr.sin_family = AF_INET;
		pStContext->tmpReqAddr.sin_addr.s_addr = inet_addr(cLocalIp);
		pStContext->tmpReqAddr.sin_port = htons(usReqPort);

		if (pRmbStConfig->iDebugSwitch)
		{
			LOGRMB(RMB_LOG_DEBUG, "add_req,socket=%u,ip=%s,port=%u", pStContext->iSocketForReq, cLocalIp, usReqPort);
		}
		pRmbStConfig->iFlagForReq = (int)MSG_IPC_UDP;
	}
	else
	{
		pStContext->iSocketForReq = 0;
	}
	return 0;
}

int rmb_context_add_req_mq_fifo(StContext *pStContext, const char* strFiFoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{
	int iRet = 0;
	if (strlen(strFiFoPath) == 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_req_mq error!strFiFoPath size=0!");
		rmb_errno = RMB_ERROR_FIFO_PARA_ERROR;
		return -1;
	}
	if (uiShmKey == 0 || uiShmSize == 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_req_mq error!shmKey=%u,shmSize=%u,fifoPath=%s", uiShmKey, uiShmSize, strFiFoPath);
		rmb_errno = RMB_ERROR_SHM_PARA_ERROR;
		return -1;
	}
	//init mq
	StRmbMq* pMq = (StRmbMq*)calloc(1, sizeof(StRmbMq));
	if (pMq == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "context init error!because of calloc mq failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	iRet = rmb_mq_init(pMq, uiShmKey, uiShmSize, true, false);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context init error!because of rmb_mq_init failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		rmb_errno = RMB_ERROR_INIT_MQ_FAIL;
		return -3;
	}

	//init fifo
	StRmbFifo* pFifo =  (StRmbFifo*)calloc(1, sizeof(StRmbFifo));
	if (pFifo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "context init error!because of calloc fifo failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -4;
	}
	iRet = rmb_fifo_init(pFifo, strFiFoPath);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context init error!because of rmb_fifo_init failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pFifo);
		free(pMq);
		rmb_errno = RMB_ERROR_INIT_FIFO_FAIL;
		return -5;
	}

	iRet = rmb_notify_add(&pStContext->fifoMq, pMq, pFifo, req_mq_index, func, func_argv);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context init error!because of rmb_notify_add failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		free(pFifo);
		return -6;
	}
	LOGRMB(RMB_LOG_INFO, "context_add_req_mq succ!");
	pRmbStConfig->iFlagForReq = (int)MSG_IPC_MQ;
	return 0;
}

int rmb_context_add_rr_rsp_mq_fifo(StContext *pStContext, const char* strFiFoPath, const unsigned int uiShmKey, const  unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{
	int iRet = 0;
	if (strlen(strFiFoPath) == 0)
	{
		LOGRMB(RMB_LOG_ERROR,  "context_add_rr_rsp_mq_fifo error!strFiFoPath size=0!");
		rmb_errno = RMB_ERROR_FIFO_PARA_ERROR;
		return -1;
	}
	if (uiShmKey == 0 || uiShmSize == 0)
	{
		LOGRMB(RMB_LOG_ERROR,  "context_add_rr_rsp_mq_fifo error!shmKey=%u,shmSize=%u,fifoPath=%s", uiShmKey, uiShmSize, strFiFoPath);
		rmb_errno = RMB_ERROR_SHM_PARA_ERROR;
		return -1;
	}
	//init mq
	StRmbMq* pMq = (StRmbMq*)calloc(1, sizeof(StRmbMq));
	if (pMq == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_rr_rsp_mq_fifo error!because of calloc mq failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	iRet = rmb_mq_init(pMq, uiShmKey, uiShmSize, true, false);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_rr_rsp_mq_fifo error!because of rmb_mq_init failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		rmb_errno = RMB_ERROR_INIT_MQ_FAIL;
		return -3;
	}

	//init fifo
	StRmbFifo* pFifo =  (StRmbFifo*)calloc(1, sizeof(StRmbFifo));
	if (pFifo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_rr_rsp_mq_fifo error!because of calloc fifo failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -4;
	}
	iRet = rmb_fifo_init(pFifo, strFiFoPath);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_rr_rsp_mq_fifo error!because of rmb_fifo_init failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pFifo);
		free(pMq);
		rmb_errno = RMB_ERROR_INIT_FIFO_FAIL;
		return -5;
	}

	iRet = rmb_notify_add(&pStContext->fifoMq, pMq, pFifo, rr_rsp_mq_index, func, func_argv);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "context_add_rr_rsp_mq_fifo!because of rmb_notify_add failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		free(pFifo);
		return -6;
	}
	pRmbStConfig->iFlagForRRrsp = (int)MSG_IPC_MQ;
	return 0;
}

int rmb_context_add_broadcast_mq_fifo(StContext *pStContext, const char* strFiFoPath,  const unsigned int uiShmKey,  const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{
	int iRet = 0;
	if (strlen(strFiFoPath) == 0)
	{
		LOGRMB(RMB_LOG_ERROR,  "fifo error!strFiFoPath size=0!");
		rmb_errno = RMB_ERROR_FIFO_PARA_ERROR;
		return -1;
	}
	if (uiShmKey == 0 || uiShmSize == 0)
	{
		LOGRMB(RMB_LOG_ERROR,  "shm error!shmKey=%u,shmSize=%u,fifoPath=%s", uiShmKey, uiShmSize, strFiFoPath);
		rmb_errno = RMB_ERROR_SHM_PARA_ERROR;
		return -1;
	}
	//init mq
	StRmbMq* pMq = (StRmbMq*)calloc(1, sizeof(StRmbMq));
	if (pMq == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "rmbMq error!because of calloc mq failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	iRet = rmb_mq_init(pMq, uiShmKey, uiShmSize, true, false);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "mq_init error!because of rmb_mq_init failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		rmb_errno = RMB_ERROR_INIT_MQ_FAIL;
		return -3;
	}

	//init fifo
	StRmbFifo* pFifo =  (StRmbFifo*)calloc(1, sizeof(StRmbFifo));
	if (pFifo == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "fifo null error!because of calloc fifo failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -4;
	}
	iRet = rmb_fifo_init(pFifo, strFiFoPath);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "fifo init error!because of rmb_fifo_init failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pFifo);
		free(pMq);
		rmb_errno = RMB_ERROR_INIT_FIFO_FAIL;
		return -5;
	}

	iRet = rmb_notify_add(&pStContext->fifoMq, pMq, pFifo, broadcast_mq_index, func, func_argv);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "notify add error!because of rmb_notify_add failed!iRet=%d,shmKey=%u,shmSize=%u,fifoPath=%s",
			iRet,
			uiShmKey,
			uiShmSize,
			strFiFoPath
			);
		free(pMq);
		free(pFifo);
		return -6;
	}
	LOGRMB(RMB_LOG_INFO, "context_add_broadcast_mq succ!");
	pRmbStConfig->iFlagForBroadCast = (int)MSG_IPC_MQ;
	return 0;
}

////////////////////////////////////////////////////////////////////////
static int rmb_context_add_queue_pipe(StContext *pStContext, const unsigned int uiSize, const enum RmbMqIndex iMsgType, rmb_callback_func func, void* func_argv)
{
	RMB_CHECK_POINT_NULL(pStContext, "rmb_context_add_queue_pipe:pStContext");

	int iRet = 0;
	if (uiSize == 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_context_add_queue_pipe error!uiSize=%u", uiSize);
		rmb_errno = RMB_ERROR_SHM_PARA_ERROR;
		return -1;
	}
	//init queue
	StRmbQueue *pQue = (StRmbQueue *)calloc(1, sizeof(StRmbQueue));
	if (pQue == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_context_add_queue_pipe error!because calloc for queue failed!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}

	iRet = rmb_queue_init(pQue, uiSize);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_context_add_queue_pipe error!because rmb_queue_init failed!");
		free(pQue);
		rmb_errno = RMB_ERROR_INIT_MQ_FAIL;
		return -3;
	}

	StRmbPipe *pPipe = (StRmbPipe *)calloc(1, sizeof(StRmbPipe));
	if (pPipe == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_context_add_queue_pipe error!because calloc for pipe failed!");
		free(pQue);
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -4;
	}

	iRet = rmb_pipe_init(pPipe);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_context_add_queue_pipe error!becaulse rmb_pipe_init failed!");
		free(pPipe);
		free(pQue);
		rmb_errno = RMB_ERROR_INIT_FIFO_FAIL;
		return -5;
	}

	iRet = rmb_wemq_notify_add(&pStContext->fifoMq, pQue, pPipe, iMsgType, func, func_argv);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "context init error!because of rmb_notify_add failed!");
		free(pPipe);
		free(pQue);
		return -6;
	}

	LOGRMB(RMB_LOG_INFO, "rmb_context_add_queue_pipe succ!");
	pRmbStConfig->iFlagForReq = (int)MSG_IPC_MQ;
	return 0;
}

//static int rmb_context_add_req_queue_pipe(StContext *pStContext, const unsigned int uiSize, rmb_callback_func func, void *func_msg, void* func_argv)
//{
//	return rmb_context_add_queue_pipe(pStContext, uiSize, wemq_req_mq_index, func, func_msg, func_argv);
//}
//
//static int rmb_context_add_rr_rsp_queue_pipe(StContext *pStContext, const unsigned int uiSize, rmb_callback_func func, void *func_msg, void* func_argv)
//{
//	return rmb_context_add_queue_pipe(pStContext, uiSize, wemq_rr_rsp_mq_index, func, func_msg, func_argv);
//}
//
//static int rmb_context_add_broadcast_queue_pipe(StContext *pStContext, const unsigned int uiSize, rmb_callback_func func, void *func_msg, void* func_argv)
//{
//	return rmb_context_add_queue_pipe(pStContext, uiSize, wemq_broadcast_mq_index, func, func_msg, func_argv);
//}
////////////////////////////////////////////////////////////////////////

int rmb_context_enqueue(StContext *pStContext, const enum RmbMqIndex uiMsgType, const char* data, unsigned int uiDataLen)
{
	pStContext->uiNowTime = time(NULL);
	return rmb_notify_enqueue_by_type(&(pStContext->fifoMq), uiMsgType, pStContext->uiNowTime, data, uiDataLen);
}


