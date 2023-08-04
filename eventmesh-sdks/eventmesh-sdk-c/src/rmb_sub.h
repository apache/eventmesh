#ifndef RMB_SUB_H_
#define RMB_SUB_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <string.h>
#include "rmb_msg.h"
#include "rmb_context.h"
#include "rmb_define.h"
#include "rmb_udp.h"

typedef struct StRmbSub
{
	StContext *pStContext;
	unsigned int uiContextNum;
	//for rr reply topic
	char cRrReply[255];
	unsigned int uiInitFlag;
	//for filter unlisten receive msg
	//unsigned int uiFlagForFilter;  //rr异步与sub做区分
	//unsigned int uiFlagForSubType;
	st_rmb_queue_info *pQueueInfo;
	int iQueueNum;
	//char **cTopic;
	//int iTopicNum; 
	/*
	//for rev req
	int iReqPort;

	//for rev reply
	int iReplyPort;

	//for rev broadcast
	int iBroadcastPort;
	*/
}StRmbSub;


/**
 * Function: rmb_sub_init
 * Description: rmb sub对象初始化
 * Return:
 * 		见rmb_errno.h文件
 */
int rmb_sub_init(StRmbSub *stRmbSub);

int rmb_sub_init_python();


/**
 * Function: rmb_sub_add_reveive_req
 * Description: init, sub add receive queue request packet
 * Return:
 * 		见rmb_errno.h文件
 */
#define rmb_sub_add_reveive_req_by_udp rmb_sub_add_reveive_req
int rmb_sub_add_reveive_req(StRmbSub *stRmbSub, unsigned short usRevReqPort);

/*
* Function: rmb_sub_add_reveive_req_by_mq
* Description:初始化接收请求的操作（使用mq接收）
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_req_by_mq(StRmbSub *stRmbSub, rmb_callback_func func, void* func_argv);

/*
* Function: rmb_sub_add_reveive_req_by_mq_v2
* Description:初始化接收请求的操作（使用mq接收）,自定义mq的key
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_req_by_mq_v2(StRmbSub *stRmbSub, const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);

int rmb_sub_add_reveive_req_by_mq_python(const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);

/*
Function: rmb_sub_get_receve_req_mq
Description:获取接收请求的mq对象
* Return:
* 		见rmb_errno.h文件
*/
StMqInfo* rmb_sub_get_receve_req_mq(StRmbSub *stRmbSub);


/*
Function: rmb_sub_add_reveive_rsp
Description:初始化接收回包(使用UDP)
* Return:
* 		见rmb_errno.h文件
*/
#define rmb_sub_add_reveive_rsp_by_udp rmb_sub_add_reveive_rsp
int rmb_sub_add_reveive_rsp(StRmbSub *stRmbSub, unsigned short usRevRspPort);

/*
Function: rmb_sub_add_reveive_rsp_by_mq
Description:初始化接收回包(使用mq)
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_rsp_by_mq(StRmbSub *stRmbSub, rmb_callback_func func, void* func_argv);

/*
Function: rmb_sub_add_reveive_rsp_by_mq_v2
Description:初始化接收回包(使用UDP),自定义mq key
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_rsp_by_mq_v2(StRmbSub *stRmbSub, const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);

int rmb_sub_add_reveive_rsp_by_mq_python(const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);

/*
Function: rmb_sub_get_receve_rsp_mq
Description:获取接收回包的mq对象
* Return:
* 		见rmb_errno.h文件
*/
StMqInfo* rmb_sub_get_receve_rsp_mq(StRmbSub *stRmbSub);


/*
Function: rmb_sub_add_reveive_broadcast
Description:初始化接收广播(使用UDP)
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_broadcast(StRmbSub *stRmbSub, unsigned short usRevBroadcastPort);

/*
Function: rmb_sub_add_reveive_broadcast_by_mq
Description:初始化接收广播(使用mq)
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_broadcast_by_mq(StRmbSub *stRmbSub, rmb_callback_func func, void* func_argv);

/*
Function: rmb_sub_add_reveive_broadcast_by_mq_v2
Description:初始化接收回包(使用mq)，自定义mq key
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_add_reveive_broadcast_by_mq_v2(StRmbSub *stRmbSub, const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);
int rmb_sub_add_reveive_broadcast_by_mq_python(const char* strFifoPath, const unsigned int uiShmKey, const unsigned int uiShmSize, rmb_callback_func func, void* func_argv);

/*
Function: rmb_sub_get_receve_broadcast_mq
Description:获取接收回包mq对象
* Return:
* 		见rmb_errno.h文件
*/
StMqInfo* rmb_sub_get_receve_broadcast_mq(StRmbSub *stRmbSub);

/*
Function: rmb_sub_get_fd
Description:获取mq对象的fd
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_get_fd(StMqInfo* pMqInfo);

/*
Function: rmb_sub_receive_from_mq
Description:从mq中获取消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_receive_from_mq(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen);

/*
Function: rmb_sub_try_receive_from_mq
Description:尝试从mq中获取消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_try_receive_from_mq(StMqInfo* pMqInfo, char* buf, const unsigned int uiBufSize, unsigned int *pDataLen);

/*
Function: rmb_sub_add_listen
Description:听queue
* Return:
* 		见rmb_errno.h文件
*/
//int rmb_sub_add_listen(StRmbSub *pStRmbSub, const char *cOwnDcn, int iServiceOrEven, const char *cServiceId, const char *cScenario);
int rmb_sub_add_listen(StRmbSub *pStRmbSub, const st_rmb_queue_info *pQueueInfo, unsigned int uiQueueSize);



int rmb_sub_add_listen_python(const st_rmb_queue_info *pQueueInfo, unsigned int uiQueueSize);

/*
Function: rmb_sub_add_listen_broadcast
Description:听topic
* Return:
* 		见rmb_errno.h文件
*/
//int rmb_sub_add_listen_broadcast(StRmbSub *pStRmbSub, const char *cOwnDcn, const char *cServiceId, const char *cScenario);

/*
Function: rmb_sub_do_receive
Description:听queue
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_do_receive(StRmbSub *pStRmbSub, int iTimeout);

int rmb_sub_do_receive_python();

/*
Function: rmb_sub_reply_msg
Description:回复消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_reply_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg);

int rmb_sub_reply_msg_python(StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg);



/*
Function: rmb_sub_ack_msg
Description:ack消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_ack_msg(StRmbSub *pStRmbSub, StRmbMsg *pStReceiveMsg);


/*
Function: rmb_sub_close
Description:关闭rmb_sub对象
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_close(StRmbSub *pStRmbSub);
int rmb_sub_close_python();

/*
Function: rmb_sub_close_v2
Description:关闭rmb_sub对象,2.0.12后使用
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_close_v2(StRmbSub *pStRmbSub);

/*
Function: rmb_sub_stop_receive
Description:rmb_sub停止接受queue消息
* Return:
* 		见rmb_errno.h文件
*/
int rmb_sub_stop_receive(StRmbSub *pStRmbSub);
int rmb_sub_stop_receive_python();


/*
Function: rmb_sub_check_req_mq_is_null
Description:校验是否请求队列是否已经为空，如果为空，则返回0，非空，则返回1
* Return:
* 		空返回0，非空返回1
*/
int rmb_sub_check_req_mq_is_null(StRmbSub *pStRmbSub);


unsigned long rmb_get_topic_thread_id();

/**
 * 专用于监听wemq的旁路topic
 */
//int rmb_sub_add_listen_topic_bypass(StRmbSub *pStRmbSub, const char *cTopic);
int rmb_sub_add_listen_topic_bypass(StRmbSub *pStRmbSub, const char **cTopic, unsigned int uiTopicSize);

unsigned long rmb_get_topic_thread_id();

#ifdef __cplusplus
}
#endif

#endif
