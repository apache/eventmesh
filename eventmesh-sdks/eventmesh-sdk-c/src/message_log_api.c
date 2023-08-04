#include "message_log_api.h"
#include <unistd.h>
#include <sys/time.h>
#include <sys/syscall.h>
#include <errno.h>
#include "wemq_thread.h"

#define gettidv1() syscall(__NR_gettid)
#define gettidv2() syscall(SYS_gettid)


static int get_log_id(char* clogId, int size)
{
	if (0 != rmb_msg_random_uuid(clogId, size))
	{
		return -1;
	}

    int i = 0;
	for (i = 0; i < size; i++)
	{
		if (*(clogId + i) == '-')
		{
			*(clogId + i) = '0';
		}
	}

    return 0;
}

/*headerJson={“code”:0,”command”:”SYS_LOG_TO_LOGSERVER”}
    bodyJson={“id”:”839232”,”consumerId”:”5982”,”logName”:”test”,”logTimestamp”:0,
	”content”:”test”,”logType”:”sys”,”lang”:”c”,”level”:”debug”,”processId”:5176,”threadId”:0,”consumerSvrId”:”dsi”,”extFields”:{}}
*/

int rmb_send_sys_log_for_api(stContextProxy* pContextProxy, const char* iLogLevel, const char* cConsumerId, const char* cLogName, const char* cContent, const char* extFields)
{

    if(pContextProxy == NULL || NULL == extFields){
		LOGRMB(RMB_LOG_ERROR, "pContextProxy or extFields is null");
		return -1;
	}

	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_LOG;

    WEMQJSON *jsonHeader = json_object_new_object();
	if (jsonHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object for jsonHeader failed");
		return -1;
	}
	
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(SYS_LOG_TO_LOGSERVER));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));

	WEMQJSON* jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object for jsonBody failed");
		json_object_put(jsonHeader);
		return -1;
	}
	char cLogId[33] = {0};

	if (0 != get_log_id(cLogId, sizeof(cLogId)))
	{
		LOGRMB(RMB_LOG_ERROR, "get_log_id failed");
		return -1;
	}

	GetRmbNowLongTime();
	json_object_object_add(jsonBody, LOG_MSG_COM_ID, json_object_new_string(cLogId));
	json_object_object_add(jsonBody, LOG_MSG_COM_CONSUMERID, json_object_new_string(cConsumerId));
	json_object_object_add(jsonBody, LOG_MSG_COM_LOGNAME, json_object_new_string(cLogName));
	json_object_object_add(jsonBody, LOG_MSG_COM_TIMESTAMP, json_object_new_int64(pRmbStConfig->ulNowTtime));
	json_object_object_add(jsonBody, LOG_MSG_COM_CONTENT, json_object_new_string(cContent));
	json_object_object_add(jsonBody, LOG_MSG_COM_LOGTYPE, json_object_new_string("sys"));
	json_object_object_add(jsonBody, LOG_MSG_COM_LANG, json_object_new_string("c"));
	json_object_object_add(jsonBody, LOG_MSG_COM_PROCESSID, json_object_new_int((int)getpid()));
	json_object_object_add(jsonBody, LOG_MSG_COM_THREADID, json_object_new_int64((long int)gettidv1()));
	json_object_object_add(jsonBody, LOG_MSG_COM_CONSUMERSVRID, json_object_new_string(pRmbStConfig->cHostIp));
	json_object_object_add(jsonBody, LOG_MSG_COM_LEVEL, json_object_new_string(iLogLevel));
	
	WEMQJSON* jsonExtFields = json_tokener_parse(extFields);
	WEMQJSON* tmp = NULL;
	if (jsonExtFields == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "json_tokener_parse extFields failed");
		tmp = json_tokener_parse("{}");
		json_object_object_add(jsonBody, LOG_MSG_COM_EXTFIELDS, tmp);
	}
	else{
		json_object_object_add(jsonBody, LOG_MSG_COM_EXTFIELDS, jsonExtFields);
	}	
    const char* header_str = json_object_get_string(jsonHeader);
	
	if (header_str == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "json_object_get_string for header is null");
		json_object_put(jsonBody);
		json_object_put(jsonExtFields);
		json_object_put(jsonHeader);
		return -2;
	}
	stThreadMsg.m_iHeaderLen = strlen(header_str);

	LOGRMB(RMB_LOG_INFO, "Gen thread msg header succ, len=%d, %s", stThreadMsg.m_iHeaderLen, header_str);
	stThreadMsg.m_pHeader = (char*)malloc(stThreadMsg.m_iHeaderLen * sizeof(char) + 1);
	if (stThreadMsg.m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for m_pHeader failed, errno=%d", errno);
		json_object_put(jsonBody);
		json_object_put(jsonExtFields);
		json_object_put(jsonHeader);
		return -1;
	}
	strncpy(stThreadMsg.m_pHeader, header_str, stThreadMsg.m_iHeaderLen);
	stThreadMsg.m_pHeader[stThreadMsg.m_iHeaderLen] = '\0';

	const char* body_str = json_object_get_string(jsonBody);
	if (body_str == NULL)
    {
		json_object_put(jsonBody);
		json_object_put(jsonExtFields);
		json_object_put(jsonHeader);
        return -1;
    }

	stThreadMsg.m_iBodyLen = strlen(body_str);
	stThreadMsg.m_pBody = (char*)malloc(stThreadMsg.m_iBodyLen * sizeof(char) + 1);
	if(stThreadMsg.m_pBody == NULL){
		LOGRMB(RMB_LOG_ERROR, "malloc for hello body failed");
		json_object_put(jsonBody);
		json_object_put(jsonExtFields);
		json_object_put(jsonHeader);
    	return -1;
	}
	memcpy(stThreadMsg.m_pBody, body_str, stThreadMsg.m_iBodyLen);
    stThreadMsg.m_pBody[stThreadMsg.m_iBodyLen] = '\0';

    json_object_put(jsonBody);
	json_object_put(jsonExtFields);
	json_object_put(jsonHeader);
	json_object_put(tmp);

	int iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!,iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		return -5;
	}
	else{
		LOGRMB(RMB_LOG_DEBUG, "put sys log msg to fifo");
		return 0;
	}
}


/*
header=Header{cmd=TRACE_LOG_TO_LOGSERVER, code=0, msg='null', seq='null'}, body=RmbTraceLog{level=debug, logTimestamp=1525674893639, 
logPoint=LOG_ERROR_POINT, message='null', model='model', retCode='retCode', retMsg='retMsg', lang='c', extFields={key=value}}}
*/

int rmb_send_log_for_error(stContextProxy* pContextProxy, int errCode, char* errMsg, StRmbMsg* ptSendMsg)
{
	if (pRmbStConfig->iApiLogserverSwitch == 0) {
		return 0;
	}

    if(pContextProxy == NULL || NULL == ptSendMsg){
		LOGRMB(RMB_LOG_ERROR, "pContextProxy or ptSendMsg is null");
		return -1;
	}

	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_LOG;

   	int iRet = -1;
	WEMQJSON* jsonHeader = json_object_new_object();

	// 组装消息
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(TRACE_LOG_TO_LOGSERVER));
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));
	json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));

	WEMQJSON *jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
    WEMQJSON *jsonMessage = json_object_new_object();
	if (jsonMessage == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
   char cTopic[128];
   char serviceOrEvent = (*(ptSendMsg->strServiceId + 3) == '0') ? 's' : 'e';
	snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", ptSendMsg->strTargetDcn, serviceOrEvent, ptSendMsg->strServiceId, ptSendMsg->strScenarioId, *(ptSendMsg->strServiceId + 3));
    json_object_object_add(jsonMessage, MSG_BODY_TOPIC_STR, json_object_new_string(cTopic));

	WEMQJSON *jsonBodyProperty = rmb_pub_encode_property_for_wemq(THREAD_MSG_CMD_SEND_LOG, ptSendMsg);
    if (jsonBodyProperty == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_property_for_wemq return null");
	}

	json_object_object_add(jsonMessage, MSG_BODY_PROPERTY_JSON, jsonBodyProperty);

	WEMQJSON *jsonByteBody = rmb_pub_encode_byte_body_for_wemq(THREAD_MSG_CMD_SEND_LOG, ptSendMsg);
    if (jsonByteBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_byte_body_for_wemq return null");
		return -1;
	}
	const char *byteBodyStr = json_object_get_string(jsonByteBody);
	
	json_object_object_add(jsonMessage, MSG_BODY_BYTE_BODY_JSON, json_object_new_string(byteBodyStr));

	const char *message_str = json_object_get_string(jsonMessage);
	if (message_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonMessage);

		return -1;
	}

	json_object_object_add(jsonBody, "retCode", json_object_new_int(errCode));
    json_object_object_add(jsonBody, "retMsg", json_object_new_string(errMsg));
	json_object_object_add(jsonBody, "level", json_object_new_string("error"));
	json_object_object_add(jsonBody, "logPoint", json_object_new_string(LOG_ERROR_POINT));
	json_object_object_add(jsonBody, "model", json_object_new_string("model"));
	json_object_object_add(jsonBody, "lang", json_object_new_string("c"));
    json_object_object_add(jsonBody, "message", json_object_new_string(message_str));
		
    const char* header_str = json_object_get_string(jsonHeader);
	
	if (header_str == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "json_object_get_string for header is null");
		json_object_put(jsonHeader);
	    json_object_put(jsonMessage);
    	json_object_put(jsonBodyProperty);
	    json_object_put(jsonByteBody);
	    json_object_put(jsonBody);
		return -2;
	}

	const char *body_str = json_object_get_string(jsonBody);
	if (body_str == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonBody);

		return -1;
	}

	stThreadMsg.m_iHeaderLen = strlen(header_str);

	
	stThreadMsg.m_pHeader = (char*)malloc(stThreadMsg.m_iHeaderLen * sizeof(char) + 1);
	if (stThreadMsg.m_pHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for m_pHeader failed, errno=%d", errno);
		json_object_put(jsonHeader);
	    json_object_put(jsonMessage);
	    json_object_put(jsonBodyProperty);
	    json_object_put(jsonByteBody);
	    json_object_put(jsonBody);
		return -1;
	}
	strncpy(stThreadMsg.m_pHeader, header_str, stThreadMsg.m_iHeaderLen);
	stThreadMsg.m_pHeader[stThreadMsg.m_iHeaderLen] = '\0';

	stThreadMsg.m_iBodyLen = strlen(body_str);
	stThreadMsg.m_pBody = (char*)malloc(stThreadMsg.m_iBodyLen * sizeof(char) + 1);
	if(stThreadMsg.m_pBody == NULL){
		LOGRMB(RMB_LOG_ERROR, "malloc for hello body failed");
		json_object_put(jsonHeader);
	    json_object_put(jsonMessage);
	    json_object_put(jsonBodyProperty);
	    json_object_put(jsonByteBody);
	    json_object_put(jsonBody);
    	return -1;
	}
	memcpy(stThreadMsg.m_pBody, body_str, stThreadMsg.m_iBodyLen);
    stThreadMsg.m_pBody[stThreadMsg.m_iBodyLen] = '\0';
    LOGRMB(RMB_LOG_INFO, "Gen error log succ, header :%s, body:%s", header_str, body_str);
    json_object_put(jsonHeader);
	json_object_put(jsonMessage);
	json_object_put(jsonBodyProperty);
	json_object_put(jsonByteBody);
	json_object_put(jsonBody);

	iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!,iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		return -5;
	}
	else{
		LOGRMB(RMB_LOG_DEBUG, "error log msg  put into fofo" );
		return 0;
	}
}



/**
 * 提供给业务调用,用于业务主动上报logserver
 */
int rmb_log_for_common(StContext *pStContext, const char* iLogLevel, const char* cLogName, const char *content,const char *extFields)
{
	if (pRmbStConfig->iApiLogserverSwitch == 0) {
		return 0;
	}
    stContextProxy *pContextProxy = pStContext->pContextProxy;
	int iRet = rmb_send_sys_log_for_api(pContextProxy, iLogLevel, pRmbStConfig->cConsumerSysId, cLogName, content, extFields);
	return iRet;
}

