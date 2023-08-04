#include "rmb_sub.h"
#include "rmb_udp.h"
#include <unistd.h>
#include <sys/types.h>
#include <pthread.h>
#include "string.h"
#include <pthread.h>
#include <unistd.h>
#include "rmb_common.h"
#include "rmb_errno.h"
#include <errno.h>
#include "rmb_pub.h"

#include "rmb_list.h"

#include "rmb_context.h"
#include "wemq_thread.h"

#define SRV_GOV_DATA_LVQ  "SRV_GOV_DATA_LVQ"
#define SRV_BROADCAST_LVQ	"BROADCAST_DATA_LVQ"

#define atomic_set(x, y)    __sync_lock_test_and_set((x), (y))

static StRmbPub *pRmbGlobalSub;


//****************************************private************************************************

//根据类型获取mq
StMqInfo* rmb_sub_get_mq_by_type(StRmbSub *stRmbSub, int iType);

void rmb_get_topic_group(void *args);

int rmb_sub_add_rr_topic_v2(StRmbSub *pStRmbSub);
int rmb_sub_add_listen_to_wemq(StRmbSub *pRmbSub, const st_rmb_queue_info *pQueueInfo, unsigned int uiQueueSize, const char *cDcn, const char *cSysId);
int wemq_sub_reply_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg);
int wemq_sub_ack_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg);

//int wemq_sub_add_listen_topic_bypass(StRmbSub *pStRmbSub, const char *cTopic);
int wemq_sub_add_listen_topic_bypass(StRmbSub *pStRmbSub, const char **cTopic, unsigned int uiTopicSize);

int wemq_sub_add_start_to_access(StRmbSub *pStRmbSub);


//*************************************************************************************************

static unsigned long ulGetTopicGroupPthreadId = 0;

static StRmbTopicInfo rmbTopicInfo[1024];
static volatile int rmbTopicIndex = -1;

unsigned long rmb_get_topic_thread_id()
{
	return ulGetTopicGroupPthreadId;
}

/**
 * Function: rmb_sub_init
 * Description: rmb sub initialize
 * Return:
 * 		0: success
 * 		other: failed
 */
int rmb_sub_init(StRmbSub *pStRmbSub)
{
	if (pStRmbSub == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub is null");
		return -1;
	}

	if (pStRmbSub->uiContextNum == 1) {
		LOGRMB(RMB_LOG_ERROR, " sub had inited. uiContextNum is :%u, max context is : %u", pStRmbSub->uiContextNum, 1);
		return 0;
	}
	
	pStRmbSub->pStContext = (StContext*)malloc(sizeof(StContext));
	if (pStRmbSub->pStContext == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub->pContext  malloc error!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -1;
	}
	pRmbStConfig->uiPid = (unsigned int)getpid();
	memset(pStRmbSub->pStContext, 0, sizeof(StContext));
    
	pStRmbSub->pQueueInfo = (st_rmb_queue_info *)malloc(sizeof(st_rmb_queue_info) * MAX_LISTEN_TOPIC_NUM);
	if (pStRmbSub->pQueueInfo == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub->pQueueInfo  malloc error!");
		return -1;
	}
	memset(pStRmbSub->pQueueInfo, 0x00, (sizeof(st_rmb_queue_info) * MAX_LISTEN_TOPIC_NUM));
    pStRmbSub->iQueueNum = 0;
    
	//pStRmbSub->uiFlagForFilter = RMB_FILTER_FLAG_FOR_RR_ASYNC;

	pStRmbSub->pStContext->contextType = RMB_CONTEXT_TYPE_SUB;

	//init context
	int iRet = rmb_context_init(pStRmbSub->pStContext);
	if (iRet < 0) {
		rmb_errno = RMB_ERROR_INIT_CONTEXT_FAIL;
		return -2;
	}

	//init notify, from init_context to here, 1.8.0
	iRet = rmb_notify_init(&(pStRmbSub->pStContext->fifoMq));
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_notify_init error!iRet=%u", iRet);
		return -3;
	}

	pStRmbSub->pStContext->pFather = (void*)pStRmbSub;

	pStRmbSub->uiContextNum = 1;

	return 0;
}


/**
 * Function: rmb_sub_init_python
 * Description: rmb sub initialize
 * Return:
 * 		0: success
 * 		-1: failed
 */
int rmb_sub_init_python(){

	pRmbGlobalSub = (StRmbSub* )calloc(1, sizeof(StRmbSub));
		if (pRmbGlobalSub == NULL) {
			LOGRMB(RMB_LOG_ERROR, "pRmbGlobalSub arg is null\n");
			rmb_errno=RMB_ERROR_ARGV_NULL;
			return -1;
		}
	rmb_sub_init(pRmbGlobalSub);
}
/**
 * Function: rmb_sub_add_reveive_rsp
 * Description: init, sub add receive RR async report packages
 * Return:
 * 		0: success
 * 		other: failed
 */
int rmb_sub_add_reveive_rsp(StRmbSub *pStRmbSub, unsigned short usRspPort)
{
	int iRet = 0;

	iRet =  rmb_context_add_rsp_socket(pStRmbSub->pStContext, pRmbStConfig->cHostIp, usRspPort);
	if (iRet != 0)
	{
		rmb_errno = RMB_ERROR_INIT_UDP_FAIL;
		return rmb_errno;
	}
	return 0;
}

int rmb_sub_add_reveive_rsp_by_mq(StRmbSub *pStRmbSub, rmb_callback_func func, void* func_argv)
{

	int iRet = 0;
	iRet = rmb_context_add_rr_rsp_mq_fifo(pStRmbSub->pStContext, pRmbStConfig->strFifoPathForRRrsp, pRmbStConfig->uiShmKeyForRRrsp, pRmbStConfig->uiShmSizeForRRrsp, func, func_argv);
	if (iRet != 0) {
		return rmb_errno;
	}
	return 0;
}



int rmb_sub_add_reveive_rsp_by_mq_v2(StRmbSub *pStRmbSub, const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{
	int iRet = 0;

	iRet = rmb_context_add_rr_rsp_mq_fifo(pStRmbSub->pStContext, strFifoPath, uiShmKey, uiShmSize, func, func_argv);
	if (iRet != 0)
	{
		return rmb_errno;
	}
	return 0;
}


int rmb_sub_add_reveive_rsp_by_mq_python(const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{
	int iRet = 0;
	StRmbSub *pStRmbSub = pRmbGlobalSub;

	iRet = rmb_context_add_rr_rsp_mq_fifo(pStRmbSub->pStContext, strFifoPath, uiShmKey, uiShmSize, func, func_argv);
	if (iRet != 0)
	{
		return rmb_errno;
	}
	return 0;
}



/**
 * Function: rmb_sub_add_reveive_req
 * Description: init, sub add receive queue request package
 * Return:
 * 		0: success
 * 		other: failed
 */
int rmb_sub_add_reveive_req(StRmbSub *pStRmbSub, unsigned short usReqPort)
{

	int iRet =  rmb_context_add_req_socket(pStRmbSub->pStContext, pRmbStConfig->cHostIp, usReqPort);
	if (iRet != 0)
	{
		rmb_errno = RMB_ERROR_INIT_UDP_FAIL;
		return rmb_errno;
	}
	return 0;
}

int rmb_sub_add_reveive_req_by_mq(StRmbSub *pStRmbSub, rmb_callback_func func, void* func_argv)
{


	int iRet = 0;

		iRet = rmb_context_add_req_mq_fifo(pStRmbSub->pStContext, pRmbStConfig->strFifoPathForReq, pRmbStConfig->uiShmKeyForReq, pRmbStConfig->uiShmSizeForReq, func, func_argv);
		if (iRet != 0) {
			return -1;
		}
	
	return 0;
}

int rmb_sub_add_reveive_req_by_mq_v2(StRmbSub *pStRmbSub, const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{


	int iRet = 0;

		iRet = rmb_context_add_req_mq_fifo(pStRmbSub->pStContext, strFifoPath, uiShmKey, uiShmSize, func, func_argv);
		if (iRet != 0) {
			return -1;
		}

	return 0;
}


int rmb_sub_add_reveive_req_by_mq_python(const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{


	int iRet = 0;
	StRmbSub *pStRmbSub = pRmbGlobalSub;
		iRet = rmb_context_add_req_mq_fifo(pStRmbSub->pStContext, strFifoPath, uiShmKey, uiShmSize, func, func_argv);
		if (iRet != 0) {
			return -1;
		}

	return 0;
}




int rmb_sub_add_reveive_broadcast(StRmbSub *pStRmbSub, unsigned short usRevBroadcastPort)
{

	int iRet = rmb_context_add_broadcast_socket(pStRmbSub->pStContext, pRmbStConfig->cHostIp, usRevBroadcastPort);
	if (iRet != 0)
	{
		rmb_errno = RMB_ERROR_INIT_UDP_FAIL;
		return rmb_errno;
	}
	return 0;
}

/**
 * Function: rmb_sub_add_reveive_broadcast_by_mq
 * Description: init, sub add receive broadcast package
 * Return:
 * 		0: success
 * 		other: failed
 */
int rmb_sub_add_reveive_broadcast_by_mq(StRmbSub *pStRmbSub, rmb_callback_func func, void* func_argv)
{


	int iRet = 0;

		iRet = rmb_context_add_broadcast_mq_fifo(pStRmbSub->pStContext, pRmbStConfig->strFifoPathForBroadcast, pRmbStConfig->uiShmKeyForBroadcast, pRmbStConfig->uiShmSizeForBroadcast, func, func_argv);
		if (iRet != 0) {
			return -1;
		}


	return 0;
}

int rmb_sub_add_reveive_broadcast_by_mq_v2(StRmbSub *pStRmbSub, const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{

	int iRet = 0;

		iRet = rmb_context_add_broadcast_mq_fifo(pStRmbSub->pStContext, strFifoPath, uiShmKey, uiShmSize, func, func_argv);
		if (iRet != 0) {
			return -1;
		}


	return 0;
}

int rmb_sub_add_reveive_broadcast_by_mq_python(const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv)
{

	int iRet = 0;
	StRmbSub *pStRmbSub = pRmbGlobalSub;
		iRet = rmb_context_add_broadcast_mq_fifo(pStRmbSub->pStContext, strFifoPath, uiShmKey, uiShmSize, func, func_argv);
		if (iRet != 0) {
			return -1;
		}


	return 0;
}



int rmb_sub_add_listen(StRmbSub *pStRmbSub, const st_rmb_queue_info *pQueueInfo, unsigned int uiQueueSize)
{
	if (pStRmbSub == NULL || pQueueInfo == NULL || uiQueueSize == 0) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub or pQueueInfo is null or uiQueueSize=%u", uiQueueSize);
		return -1;
	}
	st_rmb_queue_info *p = pQueueInfo;

	pStRmbSub->pQueueInfo = pQueueInfo;
    pStRmbSub->iQueueNum = uiQueueSize;
	//pStRmbSub->uiFlagForFilter = RMB_FILTER_FLAG_FOR_SUB;

	int i = 0;
	int iRet = 0;
    iRet = rmb_sub_add_listen_to_wemq(pStRmbSub, pQueueInfo, uiQueueSize, pRmbStConfig->cConsumerDcn, pRmbStConfig->cConsumerSysId);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "sub add listen to wemq failed, dcn=%s", pRmbStConfig->cConsumerDcn);
		return iRet;
	}

	return wemq_sub_add_start_to_access(pStRmbSub);
}


int rmb_sub_add_listen_python(const st_rmb_queue_info *pQueueInfo, unsigned int uiQueueSize)
{
	StRmbSub *pStRmbSub = pRmbGlobalSub;

	return rmb_sub_add_listen(pStRmbSub,pQueueInfo,uiQueueSize);
}


/**
 * Function: rmb_sub_do_receive
 * Description:	add epoll
 * Return:
 * 		0: success
 * 		other: failed
 */
int rmb_sub_do_receive(StRmbSub *pStRmbSub, int iTimeout)
{
	int iRet = rmb_notify_epoll(pStRmbSub, iTimeout);
	
	return iRet;

}


int rmb_sub_do_receive_python()
{
	StRmbSub *pStRmbSub = pRmbGlobalSub;
	int iRet = rmb_notify_epoll(pStRmbSub, 1);
	
	return iRet;

}


/**
 * Function: rmb_sub_reply_msg
 * Description: report message
 * Return:
 * 		0: success
 * 		-1: failed
 */
int rmb_sub_reply_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg)
{
	RMB_CHECK_POINT_NULL(pStRmbSub, "pStRmbSub");
	RMB_CHECK_POINT_NULL(pStReceiveMsg, "pStReceiveMsg");
	RMB_CHECK_POINT_NULL(pStReplyMsg, "pStReplyMsg");

	if (strlen(pStReceiveMsg->replyTo.cDestName) == 0) {
		LOGRMB(RMB_LOG_WARN, "pStReceiveMsg->replyTo.cDestName=%s", pStReceiveMsg->replyTo.cDestName);
		return 0;
	}

	return wemq_sub_reply_msg(pStRmbSub, pStReceiveMsg, pStReplyMsg);
	
}

int rmb_sub_reply_msg_python(StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg)
{
	StRmbSub *pStRmbSub = pRmbGlobalSub;
	
	return rmb_sub_reply_msg(pStRmbSub,pStReceiveMsg,pStReplyMsg);
}

/**
 * Function: rmb_sub_ack_msg
 * Description: ack received message
 * Return:
 * 		0: success
 * 		-1: failed
 */
int rmb_sub_ack_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg)
{
	RMB_CHECK_POINT_NULL(pStRmbSub, "pStRmbSub");
	RMB_CHECK_POINT_NULL(pStReceiveMsg, "pStReceiveMsg");

	return 0;
}


/**
 * Function: rmb_sub_close
 * Description:close sub
 */
int rmb_sub_close(StRmbSub *pStRmbSub)
{
	if (pStRmbSub == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub is null");
		return 0;
	}

	//wemq
	if (pRmbStConfig->iConnWemq == 1 || pRmbStConfig->iApiLogserverSwitch == 1) {
		if (pStRmbSub->pStContext != NULL && pStRmbSub->pStContext->pContextProxy != NULL) {
			stContextProxy *pContextProxy = pStRmbSub->pStContext->pContextProxy;

			if (rmb_sub_send_client_goodbye_to_wemq(pStRmbSub) != 0){
				LOGRMB(RMB_LOG_ERROR, "send sub client goodbye to wemq fail");
			}
			pContextProxy->iFlagForRun = 0;
			GetRmbNowLongTime();
			pContextProxy->ulGoodByeTime = pRmbStConfig->ulNowTtime;
			LOGRMB(RMB_LOG_DEBUG, "pthread_join mainThreadId");
			if (pContextProxy->mainThreadId != 0) {
				pthread_join(pContextProxy->mainThreadId, NULL);
			}

			LOGRMB(RMB_LOG_DEBUG, "pthread_join coThreadId");
			if (pContextProxy->coThreadId != 0) {
				pthread_join(pContextProxy->coThreadId, NULL);
			}

			rmb_msg_free(pStRmbSub->pStContext->pReceiveWemqMsg);
			rmb_msg_free(pStRmbSub->pStContext->pReceiveWemqMsgForRR);
			rmb_msg_free(pStRmbSub->pStContext->pReceiveWemqMsgForBroadCast);
			free(pStRmbSub->pStContext->pWemqPkg);
			free(pStRmbSub->pStContext->pWemqPkgForRRAsync);
			
		}
	}

	LOGRMB(RMB_LOG_DEBUG, "call rmb_sub_close for over");

	return 0;
}

int rmb_sub_close_python()
{
	StRmbSub *pStRmbSub = pRmbGlobalSub;
	while(rmb_sub_check_req_mq_is_null(pStRmbSub) != 0)  //本地的共享内存还有未处理完的消息
	{
			rmb_sub_do_receive(pStRmbSub, 1);
	}
	
	int ret = rmb_sub_close_v2(pStRmbSub);
	free(pStRmbSub);
	return ret;
}

/**
 * Function: rmb_sub_close_v2
 * Description:close sub
 */
int rmb_sub_close_v2(StRmbSub *pStRmbSub)
{
	if (pStRmbSub == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub is null");
		return 0;
	}

	//wemq
	if (pRmbStConfig->iConnWemq == 1 || pRmbStConfig->iApiLogserverSwitch == 1) {
		if (pStRmbSub->pStContext != NULL && pStRmbSub->pStContext->pContextProxy != NULL) {
			stContextProxy *pContextProxy = pStRmbSub->pStContext->pContextProxy;

			pContextProxy->iFlagForRun = 0;
			GetRmbNowLongTime();
			pContextProxy->ulGoodByeTime = pRmbStConfig->ulNowTtime;
			LOGRMB(RMB_LOG_DEBUG, "pthread_join mainThreadId");
			if (pContextProxy->mainThreadId != 0) {
				pthread_join(pContextProxy->mainThreadId, NULL);
			}

			LOGRMB(RMB_LOG_DEBUG, "pthread_join coThreadId");
			if (pContextProxy->coThreadId != 0) {
				pthread_join(pContextProxy->coThreadId, NULL);
			}

			rmb_msg_free(pStRmbSub->pStContext->pReceiveWemqMsg);
			rmb_msg_free(pStRmbSub->pStContext->pReceiveWemqMsgForRR);
			rmb_msg_free(pStRmbSub->pStContext->pReceiveWemqMsgForBroadCast);
			free(pStRmbSub->pStContext->pWemqPkg);
			free(pStRmbSub->pStContext->pWemqPkgForRRAsync);
		}
	}

	LOGRMB(RMB_LOG_DEBUG, "call rmb_sub_close for over");

	return 0;
}

int rmb_sub_send_client_goodbye_to_wemq(StRmbSub *pRmbSub)
{
	RMB_CHECK_POINT_NULL(pRmbSub, "pStRmbSub");

	stContextProxy *pContextProxy = pRmbSub->pStContext->pContextProxy;

	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_CLIENT_GOODBYE;

	WEMQJSON *jsonHeader = json_object_new_object();
	if (jsonHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object failed");
		return -2;
	}
	//add command
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(CLIENT_GOODBYE_REQUEST));
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
    pthread_mutex_lock(&pContextProxy->goodByeMutex);
	int iRet = wemq_kfifo_put(&pContextProxy->subFifo, stThreadMsg);
	if (iRet <= 0)
	{
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error,iRet=%d!\n", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		return rmb_errno;
	}
 
    struct timeval nowTimeVal;
	gettimeofday(&nowTimeVal, NULL);
	struct timespec timeout;
	timeout.tv_sec = nowTimeVal.tv_sec + (nowTimeVal.tv_usec / 1000 + pRmbStConfig->goodByeTimeOut) / 1000;
	timeout.tv_nsec = ((nowTimeVal.tv_usec / 1000 + pRmbStConfig->goodByeTimeOut) % 1000) * 1000 * 1000;

	pContextProxy->iFlagForGoodBye = 0;
	
	if (pContextProxy->iFlagForGoodBye == 0)
	{
		pthread_cond_timedwait(&pContextProxy->goodByeCond, &pContextProxy->goodByeMutex, &timeout);
	}
	pthread_mutex_unlock(&pContextProxy->goodByeMutex);

	if (pContextProxy->iFlagForGoodBye != 1)
	{
		LOGRMB(RMB_LOG_ERROR, "send sub client goodBye timeout!");
		rmb_errno = RMB_ERROR_CLIENT_GOODBYE_TIMEOUT;
		return rmb_errno;
	}

	LOGRMB(RMB_LOG_DEBUG, "send and recv sub client goodBye succ");  

	return 0;
}

/*
Function: rmb_sub_stop_receive
Description:rmb_sub停止接受queue消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_stop_receive(StRmbSub *pStRmbSub)
{
	int iRet = 0;
	if (pRmbStConfig->iConnWemq == 1 || pRmbStConfig->iApiLogserverSwitch == 1 ) {
		if (pStRmbSub->pStContext != NULL && pStRmbSub->pStContext->pContextProxy != NULL) {
			stContextProxy *pContextProxy = pStRmbSub->pStContext->pContextProxy;
			if (rmb_sub_send_client_goodbye_to_wemq(pStRmbSub) == 0){
			    //pContextProxy->iFlagForRun = 0;
				return 0;
			}
			else{
				LOGRMB(RMB_LOG_ERROR, "send client goodbye to wemq fail");
				//pContextProxy->iFlagForRun = 0;
				return -1;
			}
		}
		else{
			LOGRMB(RMB_LOG_ERROR, "pStRmbSub->pStContext null");
			return -2;
		}
	}
	return 0;
}


/*
Function: rmb_sub_stop_receive_python
Description:rmb_sub停止接受queue消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_stop_receive_python()
{
	StRmbSub *pStRmbSub = pRmbGlobalSub;

	return rmb_sub_stop_receive(pStRmbSub);
}



//add 2015-02-10
StMqInfo* rmb_sub_get_mq_by_type(StRmbSub *stRmbSub, int iType)
{
	return stRmbSub->pStContext->fifoMq.mqIndex[iType];
}

StMqInfo* rmb_sub_get_receve_req_mq(StRmbSub *stRmbSub)
{
	if (stRmbSub == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "stRmb==NULL");
		return NULL;
	}
	return rmb_sub_get_mq_by_type(stRmbSub, (int)req_mq_index);
}

StMqInfo* rmb_sub_get_rr_rsp_mq(StRmbSub *stRmbSub)
{
	if (stRmbSub == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "stRmb==NULL");
		return NULL;
	}
	return rmb_sub_get_mq_by_type(stRmbSub, (int)rr_rsp_mq_index);
}

StMqInfo* rmb_sub_get_broadcast_mq(StRmbSub *stRmbSub)
{
	if (stRmbSub == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "stRmb==NULL");
		return NULL;
	}
	return rmb_sub_get_mq_by_type(stRmbSub, (int)broadcast_mq_index);
}

StMqInfo* rmb_sub_get_receve_rsp_mq(StRmbSub *stRmbSub)
{
	if (stRmbSub == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "stRmb==NULL");
		return NULL;
	}
	return rmb_sub_get_mq_by_type(stRmbSub, (int)rr_rsp_mq_index);
}


StMqInfo* rmb_sub_get_receve_broadcast_mq(StRmbSub *stRmbSub)
{
	if (stRmbSub == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "stRmb==NULL");
		return NULL;
	}
	return rmb_sub_get_mq_by_type(stRmbSub, (int)broadcast_mq_index);
}

int rmb_sub_get_fd(StMqInfo* pMqInfo)
{
	RMB_CHECK_POINT_NULL(pMqInfo, "pMqInfo");

		return pMqInfo->fifo->iFd;
}

int rmb_sub_receive_from_mq(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen)
{
	RMB_CHECK_POINT_NULL(pMqInfo, "pMqInfo");

	return rmb_notify_dequeue(pMqInfo, buf, uiBufSize, pDataLen);
}

int rmb_sub_try_receive_from_mq(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen)
{
	RMB_CHECK_POINT_NULL(pMqInfo, "pMqInfo");

	return rmb_notify_try_dequeue(pMqInfo, buf, uiBufSize, pDataLen);
}

/*
Function: rmb_sub_check_mq_is_null
Description:校验所有队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int rmb_sub_check_mq_is_null(StRmbSub *pStRmbSub)
{
    if (pStRmbSub == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub==NULL");
		return NULL;
	}
	int result = rmb_sub_check_req_mq_is_null(pStRmbSub) && rmb_sub_check_rr_rsp_mq_is_null(pStRmbSub) && rmb_sub_check_broadcast_mq_is_null(pStRmbSub);
    return result;
}


/*
Function: rmb_sub_check_req_mq_is_null
Description:校验是否请求队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int rmb_sub_check_req_mq_is_null(StRmbSub *pStRmbSub)
{
	StMqInfo *p = rmb_sub_get_receve_req_mq(pStRmbSub);
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
Function: rmb_sub_check_rr_rsp_mq_is_null
Description:校验rr_rsp队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int rmb_sub_check_rr_rsp_mq_is_null(StRmbSub *pStRmbSub)
{
	StMqInfo *p = rmb_sub_get_rr_rsp_mq(pStRmbSub);
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
Function: rmb_sub_check_broadcast_mq_is_null
Description:校验broadcast队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int rmb_sub_check_broadcast_mq_is_null(StRmbSub *pStRmbSub)
{
	StMqInfo *p = rmb_sub_get_broadcast_mq(pStRmbSub);
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


///////////////////////////////////////////////////////////////////////////////////
/**
 * Description: add bind to wemq
 * Return:
 * 		0: success
 * 		-1: arg error
 * 		-2: error
 * json:
 *    send:
 * 	headerJson={"code":0,"seq":"1333580037","command":"SUBSCRIBE_REQUEST"}
 *   |bodyJson={"topicList":["PRX-e-10030002-05-3","PRX-e-10020002-02-2","PRX-s-10000002-01-0","PRX-e-10020002-06-2"]}
 * 	  	例如:
 * 	  	  success:	-- code为0
 *    headerJson={"code":0,"seq":"1333580037","command":"SUBSCRIBE_RESPONSE", "message":"ok"}|bodyJson=null
 * 	  	 failed:   -- code非0
      headerJson={"code":0,"seq":"1333580037","command":"SUBSCRIBE_RESPONSE", "message":"no topic route"}|bodyJson=null
 *    
 */
int rmb_sub_add_listen_to_wemq(StRmbSub *pRmbSub, const st_rmb_queue_info *pQueueInfo, unsigned int uiQueueSize, const char *cDcn, const char *cSysId)
{
	RMB_CHECK_POINT_NULL(pRmbSub, "pStRmbSub");
	RMB_CHECK_POINT_NULL(pRmbSub->pStContext, "pStRmbSub->pStContext");
	RMB_CHECK_POINT_NULL(pRmbSub->pStContext->pContextProxy, "pStRmbSub->pStContext->pContextProxy");
	RMB_CHECK_POINT_NULL(cDcn, "cDcn");
	RMB_CHECK_POINT_NULL(cSysId, "cSysId");

	stContextProxy *pContextProxy = pRmbSub->pStContext->pContextProxy;

	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_ADD_LISTEN;
    WEMQJSON *jsonTopicList = json_object_new_array();
    st_rmb_queue_info *p = pQueueInfo;
	int i;
	char cBroadcastDcn[10] = "000";
	for(i = 0; i < uiQueueSize; i++){
		char cTopic[200] = {0};
		char serviceOrEvent = (*( p->cServiceId + 3) == '0') ? 's' : 'e';
        snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", cDcn, serviceOrEvent, p->cServiceId, p->cScenarioId, *( p->cServiceId + 3));
		json_object_array_add(jsonTopicList, json_object_new_string(cTopic));
	    //自动监听广播topic
		if(serviceOrEvent == 'e'){
			memset(cTopic, 0x00, sizeof(cTopic));
			snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", cBroadcastDcn, serviceOrEvent, p->cServiceId, p->cScenarioId, *( p->cServiceId + 3));
		    json_object_array_add(jsonTopicList, json_object_new_string(cTopic));
		}
		p += 1;
	}

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

	LOGRMB(RMB_LOG_INFO, "Gen thread msg header succ, len=%d, %s", stThreadMsg.m_iHeaderLen, header_str);
	stThreadMsg.m_pHeader = (char*)malloc(stThreadMsg.m_iHeaderLen * sizeof(char) + 1);
	if (stThreadMsg.m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for m_pHeader failed, errno=%d", errno);
		json_object_put(jsonBody);
		json_object_put(jsonTopicList);
		json_object_put(jsonHeader);
		return -1;
	}
	strncpy(stThreadMsg.m_pHeader, header_str, stThreadMsg.m_iHeaderLen);
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
	pthread_mutex_lock(&pContextProxy->regMutex);
	int iRet = wemq_kfifo_put(&pContextProxy->subFifo, stThreadMsg);
	if (iRet <= 0)
	{
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error,iRet=%d!\n", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		return -3;
	}

	struct timeval nowTimeVal;
	gettimeofday(&nowTimeVal, NULL);
	struct timespec timeout;
	timeout.tv_sec = nowTimeVal.tv_sec + (nowTimeVal.tv_usec / 1000 + pRmbStConfig->iNormalTimeout) / 1000;
	timeout.tv_nsec = ((nowTimeVal.tv_usec / 1000 + pRmbStConfig->iNormalTimeout) % 1000) * 1000 * 1000;

	pContextProxy->iFlagForReg = 0;
	pContextProxy->iResultForReg = -1;
	if (pContextProxy->iFlagForReg == 0)
	{
		pthread_cond_timedwait(&pContextProxy->regCond, &pContextProxy->regMutex, &timeout);
	}
	pthread_mutex_unlock(&pContextProxy->regMutex);

	if (pContextProxy->iFlagForReg == 1 && pContextProxy->iResultForReg != 0) {
		LOGRMB(RMB_LOG_ERROR, "add listen failed,iRet=%d,dcn=%s", pContextProxy->iResultForReg,cDcn);
		rmb_errno = RMB_ERROR_WORKER_REGISTER_ERROR;
		return rmb_errno;
	}

	if (pContextProxy->iFlagForReg != 1)
	{
		LOGRMB(RMB_LOG_ERROR, "add listen timeout!dcn=%s", cDcn);
		rmb_errno = RMB_ERROR_WORKER_REGISTER_ERROR;
		return rmb_errno;
	}

	LOGRMB(RMB_LOG_DEBUG, "add listen succ!dcn=%s", cDcn);
	//cache sub topic;
	p = pQueueInfo;
	for(i = 0; i < uiQueueSize; i++){
        StWemqTopicProp stTopicProp;
	    memset(&stTopicProp, 0, sizeof(stTopicProp));
	    stTopicProp.flag = 0;
	    strncpy(stTopicProp.cServiceId, p->cServiceId, strlen(p->cServiceId));
	    stTopicProp.cServiceId[8] = '\0';
	    strncpy(stTopicProp.cScenario, p->cScenarioId, strlen(p->cScenarioId));
	    stTopicProp.cScenario[2] = '\0';
	    wemq_topic_list_add_node(&pContextProxy->stTopicList, &stTopicProp);
		p += 1;
	}
	

	return 0;
}


int wemq_sub_add_start_to_access(StRmbSub *pStRmbSub)
{
	if (pStRmbSub == NULL || pStRmbSub->pStContext == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub or pStRmbSub->pStContext is null");
		return -1;
	}

	stContextProxy *pContextProxy = pStRmbSub->pStContext->pContextProxy;
	if (pContextProxy == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmbSub->pStContext->pContextProxy is null");
		return -1;
	}

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
	LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%u, %s", stThreadMsg.m_iHeaderLen, header_str);
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

	int iRet = 0;
	struct timeval nowTimeVal;
	struct timespec timeout;
	pthread_mutex_lock(&pContextProxy->regMutex);
	iRet = wemq_kfifo_put(&pContextProxy->subFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put for listen comman error");
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		return -3;
	}

	gettimeofday(&nowTimeVal, NULL);
	timeout.tv_sec = nowTimeVal.tv_sec + (nowTimeVal.tv_usec / 1000 + pRmbStConfig->iNormalTimeout) / 1000;
	timeout.tv_nsec = ((nowTimeVal.tv_usec / 1000 + pRmbStConfig->iNormalTimeout) % 1000) * 1000 * 1000;

	pContextProxy->iResultForReg = -1;
	pContextProxy->iFlagForReg = 0;
	if (pContextProxy->iFlagForReg == 0) {
		pthread_cond_timedwait(&pContextProxy->regCond, &pContextProxy->regMutex, &timeout);
	}
	pthread_mutex_unlock(&pContextProxy->regMutex);

	switch(pContextProxy->iResultForReg)
	{
		case RMB_CODE_TIME_OUT:
			LOGRMB(RMB_LOG_ERROR, "send start command timeout");
			return -6;
		case RMB_CODE_SUSS:
			LOGRMB(RMB_LOG_INFO, "send start command to access succ");
			return 0;
		case RMB_CODE_OTHER_FAIL:
			LOGRMB(RMB_LOG_ERROR, "send start command failed,iRet=%d", pContextProxy->iResultForReg);
			return -4;
		case RMB_CODE_AUT_FAIL:
			LOGRMB(RMB_LOG_ERROR, "send start command authentication failed,iRet=%d", pContextProxy->iResultForReg);
			return -5;
		default:
			return 0;
	}


}

int wemq_sub_reply_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg)
{
	if (pStRmbSub == NULL || pStReceiveMsg == NULL || pStReplyMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmb or pStReceiveMsg or pStReplyMsg is null");
		return -1;
	}

	memcpy(&pStReplyMsg->sysHeader, &pStReceiveMsg->sysHeader, sizeof(pStReceiveMsg->sysHeader));
	memcpy(&pStReplyMsg->dest, &pStReceiveMsg->replyTo, sizeof(pStReceiveMsg->replyTo));
	if (strlen(pStReplyMsg->dest.cDestName) == 0) {
		LOGRMB(RMB_LOG_ERROR, "receiveMsg has no replyTo, can't reply!");
		return -2;
	}
	LOGRMB(RMB_LOG_DEBUG, "receive msg property: %s", pStReceiveMsg->sysHeader.cProperty);
	memcpy(&pStReplyMsg->sysHeader.cProperty, &pStReceiveMsg->sysHeader.cProperty, sizeof(pStReceiveMsg->sysHeader.cProperty));

	//serviceId
	memcpy(pStReplyMsg->strServiceId, pStReceiveMsg->strServiceId, sizeof(pStReceiveMsg->strServiceId));
	//scenarioId
	memcpy(pStReplyMsg->strScenarioId, pStReceiveMsg->strScenarioId, sizeof(pStReceiveMsg->strScenarioId));
	//dcn
	memcpy(pStReplyMsg->strTargetDcn, pStReceiveMsg->strTargetDcn, sizeof(pStReceiveMsg->strTargetDcn));
	//organization
	memcpy(pStReplyMsg->strTargetOrgId, pStReceiveMsg->strTargetOrgId, sizeof(pStReceiveMsg->strTargetOrgId));
	//ttl
	pStReplyMsg->ulMsgLiveTime = pStReceiveMsg->ulMsgLiveTime;

	strncpy(pStReplyMsg->cCorrId, pStReceiveMsg->cCorrId, sizeof(pStReceiveMsg->cCorrId));
	pStReplyMsg->iCorrLen = pStReceiveMsg->iCorrLen;
	pStReplyMsg->cApiType = C_TYPE_WEMQ;
	pStReplyMsg->cLogicType = RSP_PKG_OUT_WEMQ;
	GetRmbNowLongTime();
	pStReplyMsg->sysHeader.ulReplyTime = pRmbStConfig->ulNowTtime;

	StContext* pStContext = pStRmbSub->pStContext;
	pStContext->uiPkgLen = MAX_LENTH_IN_A_MSG;
	stContextProxy* pContextProxy = pStContext->pContextProxy;
	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_REPLY;
	int iRet = rmb_pub_encode_thread_msg(stThreadMsg.m_iCmd, &stThreadMsg, pStReplyMsg, 3000);
	//LOGRMB(RMB_LOG_DEBUG, "encode succ header %s, body %s\n",stThreadMsg.m_pHeader, stThreadMsg.m_pBody);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_thread_msg error!\n");
		return iRet;
	}

	iRet = wemq_kfifo_put(&pContextProxy->subFifo, stThreadMsg);
	if (iRet <= 0)
	{
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_WORKER_PUT_FIFO_ERROR, "sub reply wemq_kfifo_put error", pStReplyMsg);
		return rmb_errno;
	}

	return 0;
}

int wemq_sub_ack_msg(StRmbSub *pStRmbSub,StRmbMsg *pStAckMsg)
{
	if (pStRmbSub == NULL || pStAckMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStRmb or pStReplyMsg is null");
		return -1;
	}

	if (pStAckMsg->cPkgType == RR_TOPIC_PKG){
    	return 0;
    }

	LOGRMB(RMB_LOG_DEBUG, "receive msg property: %s", pStAckMsg->sysHeader.cProperty);

	StContext* pStContext = pStRmbSub->pStContext;
	pStContext->uiPkgLen = MAX_LENTH_IN_A_MSG;
	stContextProxy* pContextProxy = pStContext->pContextProxy;
	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_MSG_ACK;
	int iRet = rmb_pub_encode_thread_msg(stThreadMsg.m_iCmd, &stThreadMsg, pStAckMsg, 3000);
	//LOGRMB(RMB_LOG_DEBUG, "encode succ header %s, body %s\n",stThreadMsg.m_pHeader, stThreadMsg.m_pBody);

	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_thread_msg error!\n");
		return iRet;
	}

	iRet = wemq_kfifo_put(&pContextProxy->subFifo, stThreadMsg);
	if (iRet <= 0)
	{
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_WORKER_PUT_FIFO_ERROR, "sub ack wemq_kfifo_put error", pStAckMsg);
		return rmb_errno;
	}

	return 0;
}
