#include "rmb_pub.h"
#include "rmb_context.h"
#include "rmb_udp.h"
#include <unistd.h>
#include <sys/types.h>
#include <pthread.h>
#include "rmb_common.h"
#include "rmb_errno.h"
#include <string.h>

#include "wemq_thread.h"

static int g_iSendReq = 0;
static unsigned long g_iSendReqForEvent = 100000000;
static StRmbPub *pRmbGlobalPub;

unsigned int g_uiSendMsgSeq = 0;
unsigned int g_uiRecvMsgSeq = 0;
unsigned int DEFAULT_WINDOW_SIZE = 100;


typedef struct gsl_err {
	char result;
	const char *err_msg;
}gsl_err;

gsl_err gsl_error[] = {
		{0x01, NULL},
		{0x11, "query ckv null"},
		{0x21, "chvvalue.orgid_list_size() = 0"},
		{0x31, "orgid for query rules is null"},
		{0x41, "no available rules for service"},
		{0x51, "ckvvalue.org.dcn_list_size() = 0"},
		{0x61, "target dcn is null according to query rules for event"},
		{0x71, NULL},
		{0x81, NULL},
		{0x91, NULL},
		{0xA1, NULL},
		{0xB1, NULL},
		{0xC1, NULL},
		{0xD1, NULL},
		{0xE1, NULL},
		{0xF1, NULL},
		{0x02, NULL},
		{0x12, "CommonOrgid points to SingleOrgid, service cannot be deployed in more than one ADM or C-DCN"},
		{0x22, "CommonOrgid points to SingleOrgid, service cannot be found in either ADM or C-DCN"},
		{0x32, "public service id is deployed in multiple regions causing conflicts"},
		{0x42, "service is poly-active in one area(R-DCN/C-DCN/ADM/Common), but dcn matching IDC of clientDcn not found"},
		{0x52, "event subscriptors belong to one area(CS/DMZ/ECN), but dcn matching IDC of clientDcn not found"},
		{0x62, NULL},
		{0x72, NULL},
		{0x82, NULL},
		{0x92, NULL},
		{0xA2, NULL},
		{0xB2, NULL},
		{0xC2, NULL},
		{0xD2, NULL},
		{0xE2, NULL},
		{0xF2, NULL},
		{0x03, NULL},
		{0x13, "DecodeBuf error or ParseFromString error"},
		{0x23, "query ckv error"},
		{0x33, NULL},
		{0x43, NULL},
		{0x53, NULL},
		{0x63, NULL},
		{0x73, NULL},
		{0x83, NULL},
		{0x93, NULL},
		{0xA3, NULL},
		{0xB3, NULL},
		{0xC3, NULL},
		{0xD3, NULL},
		{0xE3, NULL},
		{0xF3, NULL}
};

//#define TCP_BUF_SIZE 5<<01
static int rmb_pub_encode_header_for_wemq(unsigned int uiCmd, StWemqThreadMsg* ptThreadMsg, StRmbMsg* ptSendMsg)
{
	if (ptThreadMsg == NULL || ptSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptThreadMsg is null or ptSendMsg is null");
		return -1;
	}

	switch (uiCmd) {
		case THREAD_MSG_CMD_SEND_MSG:
		{
			ptThreadMsg->m_iCmd = THREAD_MSG_CMD_SEND_MSG;

			WEMQJSON *jsonHeader = json_object_new_object();

      // ptSendMsg->strServiceId[3] == '3' 多播使用ASYNC_MESSAGE_TO_SERVER
			if (ptSendMsg->strServiceId[3] == '4') {
				json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(BROADCAST_MESSAGE_TO_SERVER));
			} else {
				json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(ASYNC_MESSAGE_TO_SERVER));
			}
			json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(++g_iSendReqForEvent));
			LOGRMB(RMB_LOG_DEBUG, "put seq:%ld in pkg", g_iSendReqForEvent);
			json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
			const char *header_str = json_object_get_string(jsonHeader);
			if (header_str == NULL) {
				LOGRMB(RMB_LOG_ERROR, "get thread msg header failed");
				json_object_put(jsonHeader);
				return -1;
			}
			ptThreadMsg->m_iHeaderLen = strlen(header_str);

			LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%d,%s\n", ptThreadMsg->m_iHeaderLen, header_str);
			ptThreadMsg->m_pHeader = (char *)malloc((ptThreadMsg->m_iHeaderLen + 1) * sizeof(char));
			if (ptThreadMsg->m_pHeader == NULL) {
				LOGRMB(RMB_LOG_ERROR, "malloc for ptThreadMsg->m_pHeader failed");
				json_object_put(jsonHeader);
				return -2;
			}
			memcpy(ptThreadMsg->m_pHeader, header_str, ptThreadMsg->m_iHeaderLen);
			ptThreadMsg->m_pHeader[ptThreadMsg->m_iHeaderLen] = '\0';

			json_object_put(jsonHeader);
			return 0;
		}
		case THREAD_MSG_CMD_SEND_REQUEST:
		{
			ptThreadMsg->m_iCmd = THREAD_MSG_CMD_SEND_REQUEST;

			WEMQJSON *jsonHeader = json_object_new_object();
			json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(REQUEST_TO_SERVER));
			json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(g_iSendReq++));
			json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
			const char *header_str = json_object_get_string(jsonHeader);
			if (header_str == NULL) {
				LOGRMB(RMB_LOG_ERROR, "Get thread msg header failed");
				json_object_put(jsonHeader);
				return -1;
			}
			ptThreadMsg->m_iHeaderLen = strlen(header_str);

			LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%d,%s\n", ptThreadMsg->m_iHeaderLen, header_str);
			ptThreadMsg->m_pHeader = (char *)malloc((ptThreadMsg->m_iHeaderLen + 1) * sizeof(char));
			if (ptThreadMsg->m_pHeader == NULL) {
				LOGRMB(RMB_LOG_ERROR, "malloc for ptThreadMsg->m_pHeader failed\n");
				json_object_put(jsonHeader);
				return -2;
			}
			memcpy(ptThreadMsg->m_pHeader, header_str, ptThreadMsg->m_iHeaderLen);
			ptThreadMsg->m_pHeader[ptThreadMsg->m_iHeaderLen] = '\0';
			json_object_put(jsonHeader);
			return 0;
		}
		case THREAD_MSG_CMD_SEND_REQUEST_ASYNC:
		{
			ptThreadMsg->m_iCmd = THREAD_MSG_CMD_SEND_REQUEST_ASYNC;

			WEMQJSON *jsonHeader = json_object_new_object();
			json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(REQUEST_TO_SERVER));
			json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(g_iSendReq++));
			json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
			const char *header_str = json_object_get_string(jsonHeader);
			if (header_str == NULL) {
				LOGRMB(RMB_LOG_ERROR, "Get thread msg header failed");
				json_object_put(jsonHeader);
				return -1;
			}
			ptThreadMsg->m_iHeaderLen = strlen(header_str);

			LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%d,%s\n", ptThreadMsg->m_iHeaderLen, header_str);
			ptThreadMsg->m_pHeader = (char *)malloc((ptThreadMsg->m_iHeaderLen + 1) * sizeof(char));
			if (ptThreadMsg->m_pHeader == NULL) {
				LOGRMB(RMB_LOG_ERROR, "malloc for ptThreadMsg->m_pHeader failed\n");
				json_object_put(jsonHeader);
				return -2;
			}
			memcpy(ptThreadMsg->m_pHeader, header_str, ptThreadMsg->m_iHeaderLen);
			ptThreadMsg->m_pHeader[ptThreadMsg->m_iHeaderLen] = '\0';
			json_object_put(jsonHeader);
			return 0;
		}
		case THREAD_MSG_CMD_SEND_REPLY:
		{
			ptThreadMsg->m_iCmd = THREAD_MSG_CMD_SEND_REPLY;

			WEMQJSON *jsonHeader = json_object_new_object();
			json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(RESPONSE_TO_SERVER));
			json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
			json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(g_iSendReq++));

			const char *header_str = json_object_get_string(jsonHeader);
			if (header_str == NULL) {
				LOGRMB(RMB_LOG_ERROR, "Get thread msg header failed");
				json_object_put(jsonHeader);
				return -1;
			}
			ptThreadMsg->m_iHeaderLen = strlen(header_str);

			//LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%d,%s\n", ptThreadMsg->m_iHeaderLen, header_str);
			ptThreadMsg->m_pHeader = (char *)malloc((ptThreadMsg->m_iHeaderLen + 1) * sizeof(char));
			if (ptThreadMsg->m_pHeader == NULL) {
				LOGRMB(RMB_LOG_ERROR, "malloc for ptThreadMsg->m_pHeader failed\n");
				json_object_put(jsonHeader);
				return -2;
			}
			memcpy(ptThreadMsg->m_pHeader, header_str, ptThreadMsg->m_iHeaderLen);
			ptThreadMsg->m_pHeader[ptThreadMsg->m_iHeaderLen] = '\0';

			json_object_put(jsonHeader);
			return 0;
		}
		case THREAD_MSG_CMD_SEND_MSG_ACK:
		{
			ptThreadMsg->m_iCmd = THREAD_MSG_CMD_SEND_MSG_ACK;

			WEMQJSON *jsonHeader = json_object_new_object();
			json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(MSG_ACK));
			json_object_object_add(jsonHeader, MSG_HEAD_CODE_INT, json_object_new_int(0));
			json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(g_iSendReq++));

			const char *header_str = json_object_get_string(jsonHeader);
			if (header_str == NULL) {
				LOGRMB(RMB_LOG_ERROR, "Get thread msg header failed");
				json_object_put(jsonHeader);
				return -1;
			}
			ptThreadMsg->m_iHeaderLen = strlen(header_str);

			//LOGRMB(RMB_LOG_DEBUG, "Get thread msg header succ, len=%d,%s\n", ptThreadMsg->m_iHeaderLen, header_str);
			ptThreadMsg->m_pHeader = (char *)malloc((ptThreadMsg->m_iHeaderLen + 1) * sizeof(char));
			if (ptThreadMsg->m_pHeader == NULL) {
				LOGRMB(RMB_LOG_ERROR, "malloc for ptThreadMsg->m_pHeader failed\n");
				json_object_put(jsonHeader);
				return -2;
			}
			memcpy(ptThreadMsg->m_pHeader, header_str, ptThreadMsg->m_iHeaderLen);
			ptThreadMsg->m_pHeader[ptThreadMsg->m_iHeaderLen] = '\0';

			json_object_put(jsonHeader);
			return 0;
		}
		default:
			LOGRMB(RMB_LOG_ERROR, "unknown cmd:%u", uiCmd);
			return -1;
	}

	return 0;
}

static WEMQJSON* rmb_pub_encode_system_header_for_wemq(unsigned int uiCmd, StRmbMsg *ptSendMsg)
{
	if (ptSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptSendMsg is null");
		return NULL;
	}

	WEMQJSON *jsonSystem = json_object_new_object();

	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_BIZ_STR, json_object_new_string(ptSendMsg->sysHeader.cBizSeqNo));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_SEQNO_STR, json_object_new_string(ptSendMsg->sysHeader.cConsumerSeqNo));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_SVRID_STR, json_object_new_string(ptSendMsg->sysHeader.cConsumerSvrId));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_ORGSYS_STR, json_object_new_string(ptSendMsg->sysHeader.cOrgSysId));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_CSMID_STR, json_object_new_string(ptSendMsg->sysHeader.cConsumerSysId));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_TIME_LINT, json_object_new_int64(ptSendMsg->sysHeader.ulTranTimeStamp));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_CSMDCN_STR, json_object_new_string(ptSendMsg->sysHeader.cConsumerDcn));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_ORGSVR_STR, json_object_new_string(ptSendMsg->sysHeader.cOrgSvrId));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_ORGID_STR, json_object_new_string(ptSendMsg->sysHeader.cOrgId));
	//发送消息时，version为consumerSysVersion，且必须为1.0.0
	//json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_VER_STR, json_object_new_string("weq_c_1_0_0"));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_VER_STR, json_object_new_string(ptSendMsg->sysHeader.cConsumerSysVersion));
	//add rmb api version
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_API_VERSION, json_object_new_string(RMBVERSION));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_UNIID_STR, json_object_new_string(ptSendMsg->sysHeader.cUniqueId));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_CONLEN_INT, json_object_new_int(ptSendMsg->sysHeader.iContentLength));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_MSGTYPE_INT, json_object_new_int(1));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_RECVTYPE_INT, json_object_new_int(1));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_SENDTIME_LINT, json_object_new_int64(ptSendMsg->sysHeader.ulSendTime));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_RECVTIME_LINT, json_object_new_int64(ptSendMsg->sysHeader.ulReceiveTime));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_REPLYTIME_LINT, json_object_new_int64(ptSendMsg->sysHeader.ulReplyTime));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_REPLYRECEIVETIME_LINT, json_object_new_int64(ptSendMsg->sysHeader.ulReplyReceiveTime));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_APITYPE_INT, json_object_new_int(ptSendMsg->cApiType));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_LOGICTYPE_INT, json_object_new_int((int32_t)ptSendMsg->cLogicType));
	json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_SOCOID_STR, json_object_new_string("#C"));
    
	if (uiCmd == THREAD_MSG_CMD_SEND_REPLY || uiCmd == THREAD_MSG_CMD_RECV_MSG_ACK || uiCmd == THREAD_MSG_CMD_SEND_MSG_ACK)  //一般发回包时候、单播回ack，需要rsp_ip
	{
		WEMQJSON *extFields = json_tokener_parse(ptSendMsg->sysHeader.cExtFields);
		if(extFields == NULL){
			extFields = json_object_new_object();
		}
		json_object_object_add(extFields, MSG_BODY_SYSTEM_RSP_IP, json_object_new_string(pRmbStConfig->cHostIp));
		json_object_object_add(extFields, MSG_BODY_SYSTEM_RSP_SYS, json_object_new_string(pRmbStConfig->cConsumerSysId));
		json_object_object_add(extFields, MSG_BODY_SYSTEM_RSP_DCN, json_object_new_string(pRmbStConfig->cConsumerDcn));
		json_object_object_add(extFields, MSG_BODY_SYSTEM_RSP_IDC, json_object_new_string(pRmbStConfig->cRegion));
		json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_EXTFIELDS_STR, extFields);
	    const char* systemStr = json_object_get_string(jsonSystem);
	    WEMQJSON *jsonSystemHeader = json_tokener_parse(systemStr);
	    json_object_put(extFields);
	    json_object_put(jsonSystem);
		return jsonSystemHeader;
	}else       //发包时候
	{
	     WEMQJSON *extFields = json_tokener_parse(ptSendMsg->sysHeader.cExtFields);
    	 if(extFields == NULL){
    			extFields = json_object_new_object();
    		}
		json_object_object_add(extFields, MSG_BODY_SYSTEM_REQ_IP, json_object_new_string(pRmbStConfig->cHostIp));
		json_object_object_add(extFields, MSG_BODY_SYSTEM_REQ_SYS, json_object_new_string(pRmbStConfig->cConsumerSysId));
		json_object_object_add(extFields, MSG_BODY_SYSTEM_REQ_DCN, json_object_new_string(pRmbStConfig->cConsumerDcn));
		json_object_object_add(extFields, MSG_BODY_SYSTEM_REQ_IDC, json_object_new_string(pRmbStConfig->cRegion));
		if (strcmp(ptSendMsg->isDyedMsg,"true") == 0)
		{
			json_object_object_add(extFields, IS_DYED_MSG, json_object_new_string("true"));
		}
		json_object_object_add(jsonSystem, MSG_BODY_SYSTEM_EXTFIELDS_STR, extFields);
	    const char* systemStr = json_object_get_string(jsonSystem);
	    WEMQJSON *jsonSystemHeader = json_tokener_parse(systemStr);
	    json_object_put(extFields);
	    json_object_put(jsonSystem);
		return jsonSystemHeader;
	}
}


/**
 * set destination proto, like:
 * 		"destination" : {
 *   		"name" : "A00/s/10000000/01/0",
 *   		"type" : "se",
 *   		"serviceOrEventId" : "10000000",
 *   		"scenario" : "01",
 *   		"dcnNo" : "A00",			-- not support 000
 *   		"organizationId" : "99996",
 *   		"organizationIdInputFlag" : 0,
 * 	  	 }
 * 其中，type固定为"se",wemq java历史遗留问题
 */
static WEMQJSON* rmb_pub_encode_body_dest_for_wemq(StRmbMsg *ptSendMsg)
{
	if (ptSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptSendMsg is null");
		return NULL;
	}

	WEMQJSON *jsonDest = json_object_new_object();
	if (jsonDest == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object failed");
		return NULL;
	}

	json_object_object_add(jsonDest, MSG_BODY_DEST_NAME_STR, json_object_new_string(ptSendMsg->dest.cDestName));
	json_object_object_add(jsonDest, MSG_BODY_DEST_TYPE_STR, json_object_new_string("se"));
	json_object_object_add(jsonDest, MSG_BODY_DEST_SORE_STR, json_object_new_string(ptSendMsg->strServiceId));
	json_object_object_add(jsonDest, MSG_BODY_DEST_SCENARIO_STR, json_object_new_string(ptSendMsg->strScenarioId));
	json_object_object_add(jsonDest, MSG_BODY_DEST_DCN_STR, json_object_new_string(ptSendMsg->strTargetDcn));
	json_object_object_add(jsonDest, MSG_BODY_DEST_ANY_DCN_STR, json_object_new_boolean(FALSE));
    
	json_object_object_add(jsonDest, MSG_BODY_DEST_ORGID_STR, json_object_new_string(ptSendMsg->strTargetOrgId));
	json_object_object_add(jsonDest, MSG_BODY_DEST_ORGFLAG_INT, json_object_new_int(ptSendMsg->iFLagForOrgId));

	return jsonDest;
}

WEMQJSON* rmb_pub_encode_property_for_wemq(unsigned int uiCmd, StRmbMsg *ptSendMsg)
{
	if (ptSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptSendMsg is null");
		return NULL;
	}
   
	WEMQJSON *jsonProperty = NULL;

	if (uiCmd == THREAD_MSG_CMD_SEND_REPLY || uiCmd == THREAD_MSG_CMD_RECV_MSG_ACK || uiCmd == THREAD_MSG_CMD_SEND_MSG_ACK) {
		jsonProperty = json_tokener_parse(ptSendMsg->sysHeader.cProperty);
		if(jsonProperty == NULL){
			jsonProperty = json_object_new_object();
		}
	} else {
		jsonProperty = json_object_new_object();
		json_object_object_add(jsonProperty, MSG_BODY_PROPERTY_REPLYTO_STR, json_object_new_string(ptSendMsg->replyTo.cDestName));
		json_object_object_add(jsonProperty, MSG_BODY_PROPERTY_RR_REQUEST_UNIQ_ID_STR, json_object_new_string(ptSendMsg->sysHeader.cUniqueId));
        json_object_object_add(jsonProperty, MSG_BODY_PROPERTY_KEYS_STR, json_object_new_string(ptSendMsg->sysHeader.cConsumerSeqNo));
        json_object_object_add(jsonProperty, MSG_BODY_PROPERTY_MSG_TYPE_STR, json_object_new_string("persistent"));
	    json_object_object_add(jsonProperty, MSG_BODY_PROPERTY_TTL_INT, json_object_new_int64(ptSendMsg->ulMsgLiveTime));
		json_object_object_add(jsonProperty, MSG_BODY_PROPERTY_SEQ_STR, json_object_new_string(ptSendMsg->sysHeader.cBizSeqNo));
	}
	return jsonProperty;
}


WEMQJSON* rmb_pub_encode_byte_body_for_wemq(unsigned int uiCmd, StRmbMsg *ptSendMsg)
{
	if (ptSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptSendMsg is null");
		return NULL;
	}

	WEMQJSON *jsonByteBody = json_object_new_object();
	json_object_object_add(jsonByteBody, MSG_BODY_BYTE_BODY_APPHEADER_CONTENT_JSON, json_object_new_string(ptSendMsg->cAppHeader));
    json_object_object_add(jsonByteBody, MSG_BODY_BYTE_BODY_APPHEADER_NAME_STR, json_object_new_string(ptSendMsg->sysHeader.cAppHeaderClass));
	json_object_object_add(jsonByteBody, MSG_BODY_BYTE_BODY_CONTENT_STR, json_object_new_string_len(ptSendMsg->cContent, ptSendMsg->iContentLen));
	json_object_object_add(jsonByteBody, MSG_BODY_BYTE_BODY_CREATETIME_LINT, json_object_new_int64(pRmbStConfig->ulNowTtime));
    json_object_object_add(jsonByteBody, MSG_BODY_COID_STR, json_object_new_string("#c"));
	json_object_object_add(jsonByteBody, MSG_BODY_DELIVERYTIME_INT, json_object_new_int(1));
	json_object_object_add(jsonByteBody, MSG_BODY_TTL_LINT, json_object_new_int64(ptSendMsg->ulMsgLiveTime));
	if (uiCmd == THREAD_MSG_CMD_SEND_REQUEST || uiCmd == THREAD_MSG_CMD_SEND_REQUEST_ASYNC || uiCmd == THREAD_MSG_CMD_SEND_REPLY) {
		json_object_object_add(jsonByteBody, MSG_BODY_SYN_BOOL, json_object_new_boolean(TRUE));
	}
	else{
		json_object_object_add(jsonByteBody, MSG_BODY_SYN_BOOL, json_object_new_boolean(FALSE));
	}	

	WEMQJSON *jsonSystemHeaderContent = rmb_pub_encode_system_header_for_wemq(uiCmd, ptSendMsg);
	if (jsonSystemHeaderContent == NULL) {
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_system_header return null");
		return NULL;
	}
	const char* systemHeaderContentStr = json_object_get_string(jsonSystemHeaderContent);
	json_object_object_add(jsonByteBody, MSG_BODY_BYTE_BODY_SYSTEM_HEADER_CONTENT_JSON, json_object_new_string(systemHeaderContentStr));
    
    WEMQJSON *jsonBodyDest = rmb_pub_encode_body_dest_for_wemq(ptSendMsg);
	if (jsonBodyDest == NULL) {
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_body_dest return null");
		return NULL;
	}
	const char* jsonBodyDestStr = json_object_get_string(jsonBodyDest);
	json_object_object_add(jsonByteBody, MSG_BODY_DEST_JSON, json_object_new_string(jsonBodyDestStr));

	const char* byteBodyStr = json_object_get_string(jsonByteBody);
	if(byteBodyStr == NULL)
	{
		json_object_put(jsonByteBody);
		return NULL;
	}


	int sysLen = strlen(byteBodyStr);
	//LOGRMB(RMB_LOG_DEBUG, "Gen thread msg json byte body succ, len %d, %s\n", sysLen, byteBodyStr);
	json_object_put(jsonSystemHeaderContent);
	json_object_put(jsonBodyDest);
	return jsonByteBody;
}



//根据最新协议解析
int rmb_pub_encode_body_for_wemq(unsigned int uiCmd, StWemqThreadMsg *ptThreadMsg, StRmbMsg *ptSendMsg, unsigned long ulTimeToAlive)
{
	if (ptThreadMsg == NULL || ptSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptThreadMsg or ptSendMsg is null");
		return -1;
	}

	WEMQJSON *jsonBody = json_object_new_object();
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object return null");
		return -1;
	}
   char cTopic[128];
   char serviceOrEvent = (*(ptSendMsg->strServiceId + 3) == '0') ? 's' : 'e';
	snprintf(cTopic, sizeof(cTopic), "%s-%c-%s-%s-%c", ptSendMsg->strTargetDcn, serviceOrEvent, ptSendMsg->strServiceId, ptSendMsg->strScenarioId, *(ptSendMsg->strServiceId + 3));
    json_object_object_add(jsonBody, MSG_BODY_TOPIC_STR, json_object_new_string(cTopic));

	if(THREAD_MSG_CMD_SEND_REQUEST_ASYNC == uiCmd){
		snprintf(ptSendMsg->sysHeader.cExtFields, sizeof("{\"rrType\": 1 }"), "%s", "{\"rrType\": 1 }");
	}
	WEMQJSON *jsonBodyProperty = rmb_pub_encode_property_for_wemq(uiCmd, ptSendMsg);
    if (jsonBodyProperty == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_property_for_wemq return null");
		json_object_put(jsonBody);
		return -1;
	}

	json_object_object_add(jsonBody, MSG_BODY_PROPERTY_JSON, jsonBodyProperty);

	WEMQJSON *jsonByteBody = rmb_pub_encode_byte_body_for_wemq(uiCmd, ptSendMsg);
    if (jsonByteBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_byte_body_for_wemq return null");
		json_object_put(jsonBody);
		json_object_put(jsonBodyProperty);
		return -1;
	}
	const char *byteBodyStr = json_object_get_string(jsonByteBody);
	
	json_object_object_add(jsonBody, MSG_BODY_BYTE_BODY_JSON, json_object_new_string(byteBodyStr));
    //json_object_object_add(jsonBody, MSG_BODY_BYTE_BODY_JSON, jsonByteBody);

	const char *bodyStr = json_object_get_string(jsonBody);
	if (bodyStr == NULL) {
		LOGRMB(RMB_LOG_ERROR, "Get thread msg body failed\n");
		json_object_put(jsonBody);
        json_object_put(jsonBodyProperty);
		json_object_put(jsonByteBody);
		return -1;
	}
	ptThreadMsg->m_iBodyLen = strlen(bodyStr);

	//LOGRMB(RMB_LOG_DEBUG, "Get thread msg body succ, len=%d,%s", ptThreadMsg->m_iBodyLen, bodyStr);
	ptThreadMsg->m_pBody = (char *)malloc((ptThreadMsg->m_iBodyLen + 1) * sizeof(char));
	if (ptThreadMsg->m_pBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for ptThreadMsg->m_pBody failed");
		json_object_put(jsonBody);
        json_object_put(jsonBodyProperty);
		json_object_put(jsonByteBody);
		return -1;
	}
//	strncpy(ptThreadMsg->m_pBody, bodyStr, ptThreadMsg->m_iBodyLen);
	memcpy(ptThreadMsg->m_pBody, bodyStr, ptThreadMsg->m_iBodyLen);
	ptThreadMsg->m_pBody[ptThreadMsg->m_iBodyLen] = '\0';
	json_object_put(jsonBody);
    json_object_put(jsonBodyProperty);
	json_object_put(jsonByteBody);

	return 0;
}


int rmb_pub_encode_thread_msg(unsigned int uiCmd, StWemqThreadMsg *ptThreadMsg, StRmbMsg *ptSendMsg, unsigned long ulTimeToAlive)
{
	int iRet = -1;

	if (ptSendMsg == NULL || ptThreadMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "ptThreadMsg or ptSendMsg is null");
		return -1;
	}

	iRet = rmb_pub_encode_header_for_wemq(uiCmd, ptThreadMsg, ptSendMsg);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_header_for_wemq failed");
		return iRet;
	}
	
	iRet = rmb_pub_encode_body_for_wemq(uiCmd, ptThreadMsg, ptSendMsg, ulTimeToAlive);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_body_for_wemq failed");
		return iRet;
	}
	LOGRMB(RMB_LOG_INFO,"Get thread msg header succ, headerLen=%d,%s Get thread msg body succ, bodyLen=%d,%s\n", ptThreadMsg->m_iHeaderLen,ptThreadMsg->m_pHeader,ptThreadMsg->m_iBodyLen,ptThreadMsg->m_pBody);

	return 0;

}

char* rmb_printf_service_status(StRmbPub *pStPub, StServiceStatus* pTmpService)
{
	//if ((int)pTmpService->cResult == 1)
	if ((pTmpService->cResult & 0x0F) == 0x01)
	{
		snprintf(pStPub->printGslBuf, sizeof(pStPub->printGslBuf) - 1, "service[%s,%s,%s,%d] null,gettime=%lu,InvalidTime=%lu pls check!",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId,
					pTmpService->ulGetTimes,
					pTmpService->ulInvalidTime
					);
	}
	//else if ((int)pTmpService->cResult == 2)
	else if ((pTmpService->cResult & 0x0F) == 0x02)
	{
		snprintf(pStPub->printGslBuf, sizeof(pStPub->printGslBuf) - 1, "service[%s,%s,%s,%d] route error,gettime=%lu,InvalidTime=%lu pls check!",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId,
					pTmpService->ulGetTimes,
					pTmpService->ulInvalidTime
					);
	}
	//else if ((int)pTmpService->cResult == 3)
	else if ((pTmpService->cResult & 0x0F) == 0x03)
	{
		snprintf(pStPub->printGslBuf, sizeof(pStPub->printGslBuf) - 1, "service[%s,%s,%s,%d] gsl_svr error,gettime=%lu,InvalidTime=%lu pls retry!",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId,
					pTmpService->ulGetTimes,
					pTmpService->ulInvalidTime
					);
	}
	else if ((int)pTmpService->cResult == 0)
	{
		if ((int)pTmpService->cRouteFlag == 2)
		{
			snprintf(pStPub->printGslBuf, sizeof(pStPub->printGslBuf) - 1, "service[%s,%s,%s,%d] routeFlag=%d,dcn=%s,time=%lu,InvalidTime=%lu",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				(int)pTmpService->cRouteFlag,
				pTmpService->strTargetDcn,
				pTmpService->ulGetTimes,
				pTmpService->ulInvalidTime
				);
		}
		else
		{
			snprintf(pStPub->printGslBuf, sizeof(pStPub->printGslBuf) - 1, "service[%s,%s,%s,%d] routeFlag=%d,time=%lu,InvalidTime=%lu",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				(int)pTmpService->cRouteFlag,
				pTmpService->ulGetTimes,
				pTmpService->ulInvalidTime
				);
		}
		
	}
	pStPub->printGslBuf[sizeof(pStPub->printGslBuf) - 1] = 0;
	return pStPub->printGslBuf;
}

/**
 * 将字符串中的'/'替换为'-'
 */
static void rmb_change_slash_to_hyphen(char *str, int iLen)
{
	int i = 0;
	for (i = 0; i < iLen && str[i] != '\0'; i++) {
		if (str[i] == '/') {
			str[i] = '-';
		}
	}
}

/**
 * 用于判断消息发往wemq
 */
static int rmb_pub_send_mode(StRmbPub *pPub, StRmbMsg *pMsg)
{
	if (pPub == NULL || pMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pPub or pMsg is null");
		return -1;
	}

	pMsg->iMsgMode = RMB_MSG_WEMQ;

	return 0;
}

/**
 * private func, request gsl for route and dcn
 * 参见<GSL错误码细化.doc文档>
 * api cache需求:
 * 1. api使用同步的方式更新本地gsl cache,不做全量缓存
 * 2. api请求gsl服务的超时时间默认为1000ms
 * 3. cache数据初始的超时时间为600s
 * 4. cache数据更新逻辑如下：
 * 	  1) cache中没有本次查询的数据, 则:
 * 	     result字段为0,则查询结果存入缓存,失效时间为600s
 * 	     result字段低4位为1,则查询结果存入缓存,失效时间为600s,向调用方返错
 * 	     result字段低4位为2,则查询结果不存入缓存,向调用方返错
 * 	     result字段低4位为3或者查询超时,查询结果不存入缓存,向调用方返错
 * 	  2) api发送消息时发现cache中数据已失效,则去gsl拉取数据:
 * 	     result字段为0,更新本地cache,同时将失效时间延后600s,使用最新查询到的数据发送rmb消息
 * 	     result字段低4位为1,则查询结果存入缓存,失效时间为600s,向调用方返错
 * 	     result字段低4位为2,则删除本地缓存数据,向调用方返错
 * 	     result字段低4位为3或者查询超时,如果cache原数据的result字段为0,更新本地数据的失效时间,延后30s
 */
static int rmb_pub_send_gsl(StRmbPub *pStPub, StServiceStatus* pTmpService, const char *cBizSeqNo, const char *cConsumerSeqNo)
{
	//get dcn
	//char pkgBuf[MAX_GSL_REQ_BUF_SIZE];
	char *p = pStPub->pkgBuf;
	int iPkgLen = 0;
	//*p = GSL_SUBCMD_QUERY_SERVICE;
	*p = GSL_SUBCMD_NEW_QUERY_SERVICE;
	p += 1;
	iPkgLen += 1;

	int tmp = strlen(pTmpService->strTargetOrgId);
	*p = tmp;
	p += 1;
	iPkgLen += 1;
	memcpy(p, pTmpService->strTargetOrgId, tmp);
	p += tmp;
	iPkgLen += tmp;

	tmp = strlen(pRmbStConfig->cConsumerDcn);
	*p = tmp;
	p += 1;
	iPkgLen += 1;
	memcpy(p, pRmbStConfig->cConsumerDcn, tmp);
	p += tmp;
	iPkgLen += tmp;

	tmp = strlen(pTmpService->strServiceId);
	*p = tmp;
	p += 1;
	iPkgLen += 1;
	memcpy(p, pTmpService->strServiceId, tmp);
	p += tmp;
	iPkgLen += tmp;

	tmp = strlen(pTmpService->strScenarioId);
	*p = tmp;
	p += 1;
	iPkgLen += 1;
	memcpy(p, pTmpService->strScenarioId, tmp);
	p += tmp;
	iPkgLen += tmp;

	*p = pTmpService->cFlagForOrgId;
	iPkgLen += 1;

	rmb_msg_set_bizSeqNo(pStPub->pSendMsg, cBizSeqNo);
	rmb_msg_set_consumerSeqNo(pStPub->pSendMsg, cConsumerSeqNo);
	rmb_msg_set_orgSysId(pStPub->pSendMsg, pRmbStConfig->cConsumerSysId);
	rmb_msg_set_dest_v2_1(pStPub->pSendMsg, GSL_DEFAULT_DCN, GSL_DEFAULT_SERVICE_ID, GSL_DEFAULT_SCENE_ID, GSL_DEFAULT_COMMON_ORGID);

	rmb_msg_set_content(pStPub->pSendMsg, pStPub->pkgBuf, iPkgLen);
	char appHeader[5] = "{}";
	rmb_msg_set_app_header(pStPub->pSendMsg, appHeader, strlen(appHeader));

	int iRet = rmb_pub_send_and_receive(pStPub, pStPub->pSendMsg, pStPub->pRcvMsg, pRmbStConfig->iQueryTimeout);
	if (iRet == 0)
	{
		char receiveBuf[MAX_GSL_RSP_BUF_SIZE];
		unsigned int receiveLen = sizeof(receiveBuf);
		rmb_msg_get_content(pStPub->pRcvMsg, receiveBuf, &receiveLen);
		if (receiveLen == 0)
		{
			LOGRMB(RMB_LOG_ERROR, "GSL reply len=0, req=%s\n", rmb_msg_print(pStPub->pSendMsg));
			rmb_errno = RMB_ERROR_GSL_SVR_ERROR;
			return 3;
		}
		// result(char) + routeFlag(char) + targetDcn(cStr)
		char result = *(receiveBuf + 1);
		pTmpService->cResult = result;

		if (result == 0)
		{
			pTmpService->cRouteFlag = *(receiveBuf + 2);
			if (pTmpService->cRouteFlag == 0 || pTmpService->cRouteFlag == 1)
			{
				LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] routeFlag=%d\n",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId,
					(int)pTmpService->cRouteFlag
					);
				return 0;
			}
			unsigned int uiDcnLen = *(receiveBuf + 3);
			memcpy(pTmpService->strTargetDcn, receiveBuf + 4, uiDcnLen);
			pTmpService->strTargetDcn[uiDcnLen] = 0;

			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d],get succ!routeFlag=2,targetDcn=%s \n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pTmpService->strTargetDcn
				);
			return 0;
		}
		//兼容老的gsl的错误返回码
		else if (result == 1)
		{
			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] service=NULL,result=1， uniqueID=%s\n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pStPub->pRcvMsg->sysHeader.cUniqueId
				);
			//pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
			rmb_errno = RMB_ERROR_GSL_SERVICE_ID_NULL;
			return 1;
		}
		else if (result == 2)
		{

			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] service error,result=2, uniqueID=%s\n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pStPub->pRcvMsg->sysHeader.cUniqueId
				);
			//pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
			rmb_errno = RMB_ERROR_GSL_SERVICE_ID_ERROR;
			return 2;
		}
		else if (result == 3)
		{
			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] gsl svr error,result=3, uniqueID=%s\n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pStPub->pRcvMsg->sysHeader.cUniqueId
				);
			//pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
			rmb_errno = RMB_ERROR_GSL_SVR_ERROR;
			//return 3;
			return 4;
		}
		//////////////////////////////
		else if ((result & 0x0F) == 0x01) {
			int index = (result & 0xF0) >> 4;
			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] uId=%s, result=0x%02x, %s\n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pStPub->pRcvMsg->sysHeader.cUniqueId,
				gsl_error[index].result,
				gsl_error[index].err_msg
				);
			rmb_errno = RMB_ERROR_GSL_SERVICE_ID_NULL;
			return 1;
		}
		else if ((result & 0x0F) == 0x02) {
			int index = ((result & 0xF0) >> 4) + 16;
			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] uId=%s, result=0x%02x, %s\n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pStPub->pRcvMsg->sysHeader.cUniqueId,
				gsl_error[index].result,
				gsl_error[index].err_msg
				);
			rmb_errno = RMB_ERROR_GSL_SERVICE_ID_ERROR;
			return 2;
		}
		else if ((result & 0x0F) == 0x03) {
			int index = ((result & 0xF0) >> 4) + 32;
			LOGRMB(RMB_LOG_INFO, "GSL:[%s-%s-%s-%d] uId=%s, result=0x%02x, %s\n",
				pTmpService->strServiceId,
				pTmpService->strScenarioId,
				pTmpService->strTargetOrgId,
				(int)pTmpService->cFlagForOrgId,
				pStPub->pRcvMsg->sysHeader.cUniqueId,
				gsl_error[index].result,
				gsl_error[index].err_msg
				);
			rmb_errno = RMB_ERROR_GSL_SVR_ERROR;
			return 4;
		}
	}
	else
	{
		if (rmb_errno == RMB_ERROR_SEND_RR_MSG_TIMEOUT) {
			LOGRMB(RMB_LOG_ERROR, "GSL:[%s-%s-%s-%d],send and receive from GSL timeout.\n",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId);
			pTmpService->cResult = 3;
			return 4;
		}

		LOGRMB(RMB_LOG_ERROR, "GSL:[%s-%s-%s-%d],send and recevie from GSL error,iRet=%d!\n",
			pTmpService->strServiceId,
			pTmpService->strScenarioId,
			pTmpService->strTargetOrgId,
			(int)pTmpService->cFlagForOrgId,
			iRet
			);
		//pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
		pTmpService->cResult = 3;
		rmb_errno = RMB_ERROR_REQ_GSL_ERROR;
		return 3;
	}
	return 0;
}


/**
 * 给gsl发染色消息
 */

int rmb_pub_send_dyed_msg_to_gsl(StRmbPub *pStPub)
{
	char *cBizSeqNo = (char *)malloc(sizeof(char) *50);
	const char *cConsumerSeqNo = (char *)malloc(sizeof(char) *50);
	rmb_msg_random_uuid(cBizSeqNo,32);
	rmb_msg_random_uuid(cConsumerSeqNo,32);

	rmb_msg_set_bizSeqNo(pStPub->pSendMsg, cBizSeqNo);
	rmb_msg_set_consumerSeqNo(pStPub->pSendMsg, cConsumerSeqNo);
	rmb_msg_set_orgSysId(pStPub->pSendMsg, pRmbStConfig->cConsumerSysId);
	rmb_msg_set_dest_v2_1(pStPub->pSendMsg, GSL_DEFAULT_DCN, GSL_DEFAULT_SERVICE_ID, GSL_DEFAULT_SCENE_ID, GSL_DEFAULT_COMMON_ORGID);

	rmb_msg_set_content(pStPub->pSendMsg, "", 0);
	char appHeader[5] = "{}";
	rmb_msg_set_app_header(pStPub->pSendMsg, appHeader, strlen(appHeader));
	rmb_msg_set_dyedMsg(pStPub->pSendMsg,"true");

	int iRet = rmb_pub_send_and_receive(pStPub, pStPub->pSendMsg, pStPub->pRcvMsg, pRmbStConfig->iQueryTimeout);
	if(iRet==1){
	LOGRMB(RMB_LOG_DEBUG, "send dyed msg to GSL success,ret code is %d",iRet);
	}
	rmb_msg_clear(pStPub->pSendMsg);
  free(cBizSeqNo);
  free(cConsumerSeqNo);
	return iRet;
}

//0:succ
//1:GSL服务id为空
//2:GSL服务id路由错误
//3:GSL服务器错误
//-1:服务错误

int rmb_pub_send_gsl_and_insert_cache(StRmbPub *pStPub, StRmbMsg *pStMsg, StServiceStatus* pTmpService, StServiceStatus* hasCachedService)
{
	//pthread_mutex_lock(&pStPub->pubMutex);
	if (hasCachedService != NULL)
	{
		//if (hasCachedService->ulGetTimes + pRmbStConfig->iCacheTimeoutTime * 1000 >= pRmbStConfig->ulNowTtime)
		if (hasCachedService->ulInvalidTime >= pRmbStConfig->ulNowTtime)
		{
			//if (hasCachedService->cResult == 1)
			if ((hasCachedService->cResult & 0x0F)  == 0x01)
			{
				LOGRMB(RMB_LOG_ERROR, "Cache:[%s-%s-%s-%d] service=NULL",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId
					);
				rmb_errno = RMB_ERROR_GSL_SERVICE_ID_NULL;
				return 1;
			}
//			else if (hasCachedService->cResult == 2)
//			{
//				LOGRMB(RMB_LOG_ERROR, "Cache:[%s-%s-%s-%d] service error",
//					pTmpService->strServiceId,
//					pTmpService->strScenarioId,
//					pTmpService->strTargetOrgId,
//					(int)pTmpService->cFlagForOrgId
//					);
//				rmb_errno = RMB_ERROR_GSL_SERVICE_ID_ERROR;
//				return 2;
//			}
			else if (hasCachedService->cResult == 0)
			{
				if (hasCachedService->cRouteFlag == 0 || hasCachedService->cRouteFlag == 1)
				{
					return 0;
				}
				else
				{
					//LOGRMB(RMB_LOG_DEBUG, "nowTime=%lu,Cache:[%s]", pRmbStConfig->ulNowTtime, rmb_printf_service_status(pStPub, tmpService));
					if (strcmp(pStMsg->strTargetDcn, hasCachedService->strTargetDcn) != 0)
					{
						LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", hasCachedService->strTargetDcn, pStMsg->strTargetDcn);
						strncpy(pStMsg->strTargetDcn, hasCachedService->strTargetDcn, sizeof(pStMsg->strTargetDcn) - 1);
						//LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", hasCachedService->strTargetDcn, pStMsg->strTargetDcn);
					}
					return 0;
				}
			}
		}
	}
	else
	{
		hasCachedService = bsearch(pTmpService, pRmbStConfig->serviceStatusList, pRmbStConfig->iCacheServiceNums, sizeof(StServiceStatus), cmpServiceStatusStr);
		if (hasCachedService != NULL)
		{
			//if (hasCachedService->ulGetTimes + pRmbStConfig->iCacheTimeoutTime * 1000 >= pRmbStConfig->ulNowTtime)
			if (hasCachedService->ulInvalidTime >= pRmbStConfig->ulNowTtime)
			{
				//if (hasCachedService->cResult == 1)
				if ((hasCachedService->cResult & 0x0F) == 0x01)
				{
					LOGRMB(RMB_LOG_ERROR, "Cache:[%s-%s-%s-%d] service=NULL",
						pTmpService->strServiceId,
						pTmpService->strScenarioId,
						pTmpService->strTargetOrgId,
						(int)pTmpService->cFlagForOrgId
						);
					rmb_errno = RMB_ERROR_GSL_SERVICE_ID_NULL;
					return 1;
				}
//				else if (hasCachedService->cResult == 2)
//				{
//					LOGRMB(RMB_LOG_ERROR, "Cache:[%s-%s-%s-%d] service error",
//						pTmpService->strServiceId,
//						pTmpService->strScenarioId,
//						pTmpService->strTargetOrgId,
//						(int)pTmpService->cFlagForOrgId
//						);
//					rmb_errno = RMB_ERROR_GSL_SERVICE_ID_ERROR;
//					return 2;
//				}
				else if (hasCachedService->cResult == 0)
				{
					if (hasCachedService->cRouteFlag == 0 || hasCachedService->cRouteFlag == 1)
					{
						return 0;
					}
					else
					{
						//LOGRMB(RMB_LOG_DEBUG, "nowTime=%lu,Cache:[%s]", pRmbStConfig->ulNowTtime, rmb_printf_service_status(pStPub, tmpService));
						if (strcmp(pStMsg->strTargetDcn, hasCachedService->strTargetDcn) != 0)
						{
							LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", hasCachedService->strTargetDcn, pStMsg->strTargetDcn);
							strncpy(pStMsg->strTargetDcn, hasCachedService->strTargetDcn, sizeof(pStMsg->strTargetDcn) - 1);
						}
						return 0;
					}
				}
			}
		}
	}
	int iRet = rmb_pub_send_gsl(pStPub, pTmpService, pStMsg->sysHeader.cBizSeqNo, pStMsg->sysHeader.cConsumerSeqNo);
	if (iRet == 2 || iRet == 3) {
	    pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
		if (hasCachedService != NULL) {
			hasCachedService->cResult = pTmpService->cResult;
			hasCachedService->cRouteFlag = pTmpService->cRouteFlag;
		}
		return iRet;
	}
//	if (iRet == 3)
//	{
//		pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
//		pTmpService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheFailedTimeoutTime * 1000;
//		//pthread_mutex_unlock(&pStPub->pubMutex);
//		return 3;
//	}
	
	if (iRet == 4) {
		pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
		pTmpService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheFailedTimeoutTime * 1000;
		if (hasCachedService != NULL) {
			if ((hasCachedService->cResult == 0) || ((hasCachedService->cResult & 0x0F) == 0x01)) {
				hasCachedService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheFailedTimeoutTime * 1000;
			}
		}
		return 4;
	}

	//cache过期,且在本地存在
	if (hasCachedService != NULL)
	{
		pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;

		hasCachedService->cResult = pTmpService->cResult;
		hasCachedService->cRouteFlag = pTmpService->cRouteFlag;
		hasCachedService->ulGetTimes = pRmbStConfig->ulNowTtime;
		if (hasCachedService->cResult == 0)
		{
			memcpy(hasCachedService->strTargetDcn, pTmpService->strTargetDcn, sizeof(hasCachedService->strTargetDcn));
		}
		if (iRet == 0 || iRet == 1) {
			hasCachedService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheSuccTimeoutTime * 1000;
		} else {
			hasCachedService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheFailedTimeoutTime * 1000;
		}
	}
	else
	{		//本地无cach
		//本地无cache,且返回结果为2或3，则不缓存
		if (iRet == 2 || iRet == 3 || iRet == 4)
			return iRet;

		pTmpService->ulGetTimes = pRmbStConfig->ulNowTtime;
		if (iRet == 0 || iRet == 1) {
			pTmpService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheSuccTimeoutTime * 1000;
		} else {
			pTmpService->ulInvalidTime = pRmbStConfig->ulNowTtime + pRmbStConfig->iCacheFailedTimeoutTime * 1000;
		}
		//select
		StServiceStatus* pTmp = NULL;
		if (pRmbStConfig->iCacheServiceNums >= MAX_SERVICE_STATUS_CACHE_NUMS)
		{
			//find the oldest cache
			int i = 0;
			//unsigned long ulMinTimestamp = 1 << (sizeof(unsigned long)-1);
			unsigned long ulMinTimestamp = pRmbStConfig->serviceStatusList[0].ulGetTimes;
			for (; i < MAX_SERVICE_STATUS_CACHE_NUMS; i++)
			{
				if (ulMinTimestamp > pRmbStConfig->serviceStatusList[i].ulGetTimes)
				{
					ulMinTimestamp = pRmbStConfig->serviceStatusList[i].ulGetTimes;
					pTmp = &pRmbStConfig->serviceStatusList[i];
				}
			}
			pRmbStConfig->iCacheServiceNums -= 1;
		}
		else
		{
			pTmp = &pRmbStConfig->serviceStatusList[pRmbStConfig->iCacheServiceNums];
		}
		memcpy(pTmp, pTmpService, sizeof(StServiceStatus));
		pRmbStConfig->iCacheServiceNums += 1;
		//sort
		qsort(pRmbStConfig->serviceStatusList, pRmbStConfig->iCacheServiceNums, sizeof(StServiceStatus), cmpServiceStatusStr);
	}
	int i = 0;
	for (; i < pRmbStConfig->iCacheServiceNums; i++)
	{
		LOGRMB(RMB_LOG_INFO, "cache%dth:[%s]", i, rmb_printf_service_status(pStPub, &pRmbStConfig->serviceStatusList[i]));
	}
	//pthread_mutex_unlock(&pStPub->pubMutex);
	//return pTmpService->cResult;
	return iRet;
}


/**
 * before 0.9.15 version
 * req: subcmd(char) + targetOrgId(cStr) + selfDcn(cStr) + serverId(cStr) + scenseId(cStr) + flag(char)(0: user 1: api)
 * rsp: subcmd(char) + result(char) + routeFlag(char) + targetDcn(cStr)
 *
 * 0.9.15:
 * req: subcmd -- 0x11
 * rsp: result -- 0x11 0x21 ...
 * result 0x1x  -- cache 600s
 * result 0x2x  -- clean cache
 * return:
 * 		0:success
 * 		1:null
 * 		2:serverId error
 * 		3:GSL server error
 */
int rmb_pub_get_target_dcn(StRmbPub *pStPub, StRmbMsg *pStMsg)
{
	//search cache
	//int iFLagForReqGsl = 1;
	StServiceStatus tmpStatus;
	tmpStatus.ulInvalidTime = 0;
	StServiceStatus* pTmpService = &tmpStatus;

	pTmpService->cFlagForOrgId = pStMsg->iFLagForOrgId;
	if (pStMsg->iFLagForOrgId == RMB_COMMIT_BY_OWN)
	{
		strncpy(pTmpService->strTargetOrgId, pStMsg->strTargetOrgId, sizeof(pTmpService->strTargetOrgId) - 1);
	}
	else
	{
		strncpy(pTmpService->strTargetOrgId, pRmbStConfig->strOrgId, sizeof(pTmpService->strTargetOrgId) - 1);
	}
	strncpy(pTmpService->strServiceId, pStMsg->strServiceId, sizeof(pTmpService->strServiceId) - 1);
	strncpy(pTmpService->strScenarioId, pStMsg->strScenarioId, sizeof(pTmpService->strScenarioId) - 1);
	
	StServiceStatus* tmpService = bsearch(pTmpService, pRmbStConfig->serviceStatusList, pRmbStConfig->iCacheServiceNums, sizeof(StServiceStatus), cmpServiceStatusStr);
	if (tmpService != NULL)
	{
		//if (tmpService->ulGetTimes + pRmbStConfig->iCacheTimeoutTime * 1000 >= pRmbStConfig->ulNowTtime)
		if (tmpService->ulInvalidTime >= pRmbStConfig->ulNowTtime)
		{
			//if (tmpService->cResult == 1)
//			if (tmpService->cResult == 0x11 || tmpService->cResult == 0x21 || tmpService->cResult == 0x31 ||
//					tmpService->cResult == 0x41 || tmpService->cResult == 0x51 || tmpService->cResult == 0x61)
			if ((tmpService->cResult & 0x0F) == 0x01)
			{
				LOGRMB(RMB_LOG_ERROR, "Cache:[%s-%s-%s-%d] service=NULL",
					pTmpService->strServiceId,
					pTmpService->strScenarioId,
					pTmpService->strTargetOrgId,
					(int)pTmpService->cFlagForOrgId
					);
				rmb_errno = RMB_ERROR_GSL_SERVICE_ID_NULL;
				return 1;
			}
//			else if (tmpService->cResult == 2)
//			{
//				LOGRMB(RMB_LOG_ERROR, "Cache:[%s-%s-%s-%d] service error",
//					pTmpService->strServiceId,
//					pTmpService->strScenarioId,
//					pTmpService->strTargetOrgId,
//					(int)pTmpService->cFlagForOrgId
//					);
//				rmb_errno = RMB_ERROR_GSL_SERVICE_ID_ERROR;
//				return 2;
//			}
			else if (tmpService->cResult == 0)
			{
				if (tmpService->cRouteFlag == 0 || tmpService->cRouteFlag == 1)
				{
					return 0;
				}
				else
				{
					//LOGRMB(RMB_LOG_DEBUG, "nowTime=%lu,Cache:[%s]", pRmbStConfig->ulNowTtime, rmb_printf_service_status(pStPub, tmpService));
					if (strcmp(pStMsg->strTargetDcn, tmpService->strTargetDcn) != 0)
					{
						LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", tmpService->strTargetDcn, pStMsg->strTargetDcn);
						strncpy(pStMsg->strTargetDcn, tmpService->strTargetDcn, sizeof(pStMsg->strTargetDcn) - 1);
						//LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", tmpService->strTargetDcn, pStMsg->strTargetDcn);
					}
					return 0;
				}
			}
		}
		else
		{
			LOGRMB(RMB_LOG_INFO, "cache time out!nowTime=%lu, Cache:[%s] ", pRmbStConfig->ulNowTtime, rmb_printf_service_status(pStPub, tmpService));
		}
	}
	pthread_mutex_lock(&pStPub->pubMutex);
	int iRet = rmb_pub_send_gsl_and_insert_cache(pStPub, pStMsg, pTmpService, tmpService);
	pthread_mutex_unlock(&pStPub->pubMutex);
	if (iRet != 0 )
	{
//		if (iRet == 3 && tmpService != NULL)
		if (iRet == 4 && tmpService != NULL && tmpService->cResult == 0)
		{
			//copy dcn to msg
			if (tmpService->cRouteFlag == 2)
			{
				if (strcmp(pStMsg->strTargetDcn, tmpService->strTargetDcn) != 0)
				{
					LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", tmpService->strTargetDcn, pStMsg->strTargetDcn);
					strncpy(pStMsg->strTargetDcn, tmpService->strTargetDcn, sizeof(pStMsg->strTargetDcn) - 1);
					//LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", tmpService->strTargetDcn, pStMsg->strTargetDcn);
				}
			}
			LOGRMB(RMB_LOG_ERROR, "ReqGsl error,but use cache! gsl=[%s],cache=%s", rmb_printf_service_status(pStPub, pTmpService), rmb_printf_service_status(pStPub, tmpService));
			return 0;
		}
		LOGRMB(RMB_LOG_ERROR, "ReqGsl gsl=[%s]", rmb_printf_service_status(pStPub, pTmpService));
		return iRet;
	}
	else
	{
		//copy dcn to msg
		//
		if (pTmpService->cResult == 0 && pTmpService->cRouteFlag == 2 )
		{
			if (strcmp(pStMsg->strTargetDcn, pTmpService->strTargetDcn) != 0)
			{
				LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", pTmpService->strTargetDcn, pStMsg->strTargetDcn);
				strncpy(pStMsg->strTargetDcn, pTmpService->strTargetDcn, sizeof(pStMsg->strTargetDcn) - 1);
				//LOGRMB(RMB_LOG_INFO, "GSL DCN=%s is diffrent with input DCN=%s", pTmpService->strTargetDcn, pStMsg->strTargetDcn);
			}
		}
		LOGRMB(RMB_LOG_INFO, "ReqGsl succ! gsl=[%s]", rmb_printf_service_status(pStPub, pTmpService));
	}
	return 0;
}

int rmb_pub_set_destination_Interval(StRmbPub *pStPub, StRmbMsg *pStMsg)
{
	//req gsl control
	if (pRmbStConfig->iReqGsl == 1) {
		if (!strcmp(pStMsg->strServiceId, GSL_DEFAULT_SERVICE_ID))
		{
			strncpy(pStMsg->strTargetDcn, GSL_DEFAULT_DCN, sizeof(pStMsg->strTargetDcn)-1);
		}
		else
		{
			int iRet = rmb_pub_get_target_dcn(pStPub, pStMsg);
			if (iRet != 0)
			{
				LOGRMB(RMB_LOG_ERROR, "rmb_pub_get_target_dcn error!iRet=%d", iRet);
				return iRet;
			}
		}
	}
	
	if (pStMsg->iEventOrService == RMB_EVENT_CALL)
	{
		snprintf(pStMsg->dest.cDestName, sizeof(pStMsg->dest.cDestName), "%s/e/%s/%s/%c", pStMsg->strTargetDcn, pStMsg->strServiceId, pStMsg->strScenarioId, *(pStMsg->strServiceId + 3));
	}
	else
	{
		snprintf(pStMsg->dest.cDestName, sizeof(pStMsg->dest.cDestName), "%s/s/%s/%s/%c", pStMsg->strTargetDcn, pStMsg->strServiceId, pStMsg->strScenarioId, *(pStMsg->strServiceId + 3));
	}
	pStMsg->dest.iDestType = RMB_DEST_TOPIC;

	//set pub message to wemq 
	rmb_pub_send_mode(pStPub, pStMsg);


	return 0;
}

/**
 * Function: rmb_pub_init
 * Description: rmb pub initialize
 * Return:
 * 		0: success
 * 		-1: failed
 */
int rmb_pub_init(StRmbPub *pRmbPub)
{
	if (pRmbPub == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub arg is null\n");
		rmb_errno=RMB_ERROR_ARGV_NULL;
		return -1;
	}

	if (pRmbPub->uiContextNum == 1) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub has already init!");
		return 0;
	}
	pRmbPub->pContext = (StContext*)malloc(sizeof(StContext));
	if (pRmbPub->pContext == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub->pContext  malloc error!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	pRmbStConfig->uiPid = (unsigned int)getpid();
	memset(pRmbPub->pContext, 0, sizeof(StContext));
	pRmbPub->pContext->contextType = RMB_CONTEXT_TYPE_PUB;
   
	int iRet = rmb_context_init(pRmbPub->pContext);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_init failed!iRet=%d,error=%s", iRet, get_rmb_last_error());
		rmb_errno = RMB_ERROR_INIT_CONTEXT_FAIL;
		return -3;
	}
	pRmbPub->uiContextNum = 1;
	pRmbPub->pContext->pFather = (void*)pRmbPub;

	pRmbPub->ulLastTime = 0;
	//rmb_pub_rand(pRmbPub);
	
	pRmbPub->pSendMsg = rmb_msg_malloc();
	if (pRmbPub->pSendMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_init malloc failed!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}

	//pRmbPub->pRcvMsg = (StRmbMsg*)malloc(sizeof(StRmbMsg));
	pRmbPub->pRcvMsg = rmb_msg_malloc();
	if (pRmbPub->pRcvMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_init malloc failed!");
		rmb_errno = RMB_ERROR_MALLOC_FAIL;
		return -2;
	}
	pthread_mutex_init(&pRmbPub->pubMutex, NULL);
	rmb_pub_send_dyed_msg_to_gsl(pRmbPub);

	return 0;
}

/**
 * Function: rmb_pub_init_python
 * Description: rmb pub initialize
 * Return:
 * 		0: success
 * 		-1: failed
 */
int rmb_pub_init_python()
{
   pRmbGlobalPub = (StRmbPub* )calloc(1, sizeof(StRmbPub));
	if (pRmbGlobalPub == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbGlobalPub arg is null\n");
		rmb_errno=RMB_ERROR_ARGV_NULL;
		return -1;
	}

	rmb_pub_init(pRmbGlobalPub);


	return 0;
}

int rmb_pub_send_msg_to_wemq(StRmbPub *pRmbPub, StRmbMsg *pMsg)
{
	if (pRmbPub == NULL || pMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub or pMsg is null");
		return -1;
	}

	//check dest is set
	if (rmb_check_msg_valid(pMsg) != 0) {
		rmb_errno = RMB_ERROR_MSG_MISSING_PART;
		return -2;
	}

	if (pMsg->iEventOrService == (int)RMB_SERVICE_CALL) {
		LOGRMB(RMB_LOG_ERROR, "rmb pub event interface can't send rr msg,serviceId=%s!\n", pMsg->strServiceId);
		rmb_errno = RMB_ERROR_EVENT_INTERFACE_CAN_NOT_SEND_RR_MSG;
		return -3;
	}

	int iRet = 0;


	iRet = rmb_msg_init(pMsg, pRmbStConfig, C_TYPE_WEMQ);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_msg_init failed!iRet=%d\n", iRet);
		return -5;
	}

	if (pMsg->ulMsgLiveTime == 0 || pMsg->ulMsgLiveTime > DEFAULT_MSG_MAX_LIVE_TIME) {
		pMsg->ulMsgLiveTime = DEFAULT_MSG_MAX_LIVE_TIME;
	}

	pMsg->cLogicType = EVENT_PKG_IN_WEMQ;
	GetRmbNowLongTime();
	pMsg->sysHeader.ulSendTime = pRmbStConfig->ulNowTtime;

	StContext *pStContext = pRmbPub->pContext;
	pStContext->uiPkgLen = MAX_LENTH_IN_A_MSG;

	if (pMsg->ulMsgLiveTime == 0 || pMsg->ulMsgLiveTime > DEFAULT_MSG_MAX_LIVE_TIME) {
		pMsg->ulMsgLiveTime = DEFAULT_MSG_MAX_LIVE_TIME;
	}

	stContextProxy *pContextProxy = pStContext->pContextProxy;
	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_MSG;
	iRet = rmb_pub_encode_thread_msg(stThreadMsg.m_iCmd, &stThreadMsg, pMsg, pMsg->ulMsgLiveTime);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_thread_msg error!");
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_ENCODE_FAIL, "wemq_pub_encode_thread_msg error", pMsg);
		return iRet;
	}

    pthread_mutex_lock(&pContextProxy->eventMutex);
	iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!,iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_WORKER_PUT_FIFO_ERROR, "wemq_kfifo_put error", pMsg);
		return -5;
	}

	struct timeval tv;
	gettimeofday(&tv, NULL);
	struct timespec ts_timeout;
	ts_timeout.tv_sec = tv.tv_sec + (tv.tv_usec / 1000 + pRmbStConfig->accessAckTimeOut) / 1000;
	ts_timeout.tv_nsec = ((tv.tv_usec / 1000 + pRmbStConfig->accessAckTimeOut) % 1000) * 1000 * 1000;


	pContextProxy->iFlagForEvent = -1;
	if (pContextProxy->iFlagForEvent == -1) {
		//reset seq
		pContextProxy->iSeqForEvent = g_iSendReqForEvent;
		LOGRMB(RMB_LOG_DEBUG, "reset seq:%ld, pContextProxy->iSeqForEvent:%ld", g_iSendReqForEvent, pContextProxy->iSeqForEvent);

		pthread_cond_timedwait(&pContextProxy->eventCond, &pContextProxy->eventMutex, &ts_timeout);

	}
	pthread_mutex_unlock(&pContextProxy->eventMutex);

	switch(pContextProxy->iFlagForEvent)
	{
		case RMB_CODE_TIME_OUT:
			LOGRMB(RMB_LOG_ERROR, "time out!req=%s", rmb_msg_print(pMsg));
			rmb_errno = RMB_ERROR_SEND_EVENT_MSG_FAIL;
			rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_SEND_EVENT_MSG_FAIL, "wemq send event msg ack timeout", pMsg);
			return -6;
		case RMB_CODE_SUSS:
			LOGRMB(RMB_LOG_DEBUG, "send msg succ!req=%s\n", rmb_msg_print(pMsg));
			return 0;
		case RMB_CODE_OTHER_FAIL:
			LOGRMB(RMB_LOG_ERROR, "send msg failed!req=%s", rmb_msg_print(pMsg));
			return -6;
		case RMB_CODE_AUT_FAIL:
			LOGRMB(RMB_LOG_ERROR, "Authentication failed!req=%s", rmb_msg_print(pMsg));
			return -5;
	}

}

static int rmb_pub_send_and_receive_to_wemq(StRmbPub *pRmbPub, StRmbMsg *pSendMsg, StRmbMsg *pRevMsg, unsigned int uiTimeOut)
{
	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");
	RMB_CHECK_POINT_NULL(pSendMsg, "pSendMsg");
	RMB_CHECK_POINT_NULL(pRevMsg, "pRevMsg");

	if (pSendMsg->iEventOrService == (int)RMB_EVENT_CALL) {
		LOGRMB(RMB_LOG_ERROR, "rr interface can't send event msg!");
		rmb_errno = RMB_ERROR_RR_INTERFACE_CAN_NOT_SEND_EVENT_MSG;
		return -1;
	}

	int iRet = 0;

	iRet = rmb_msg_init(pSendMsg, pRmbStConfig, C_TYPE_WEMQ);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_msg_init failed!iRet=%d", iRet);
		return -3;
	}

	if (pSendMsg->ulMsgLiveTime == 0 || pSendMsg->ulMsgLiveTime > DEFAULT_MSG_MAX_LIVE_TIME) {
		pSendMsg->ulMsgLiveTime = uiTimeOut;
	}

	pSendMsg->cLogicType = REQ_PKG_IN_WEMQ;
	GetRmbNowLongTime();
	pSendMsg->sysHeader.ulSendTime = pRmbStConfig->ulNowTtime;

	StContext *pStContext = pRmbPub->pContext;
	pStContext->uiPkgLen = MAX_LENTH_IN_A_MSG;
	stContextProxy *pContextProxy = pStContext->pContextProxy;
	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_REQUEST;
	iRet = rmb_pub_encode_thread_msg(stThreadMsg.m_iCmd, &stThreadMsg, pSendMsg, uiTimeOut);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_thread_msg error!");
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_ENCODE_FAIL, "wemq_pub_encode_thread_msg error", pSendMsg);
		return -4;
	}

    pthread_mutex_lock(&pContextProxy->rrMutex);
	iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_WORKER_PUT_FIFO_ERROR, "wemq_kfifo_put error", pSendMsg);
		return -5;
	}

	struct timeval tv;
	gettimeofday(&tv, NULL);
	struct timespec ts_timeout;
	ts_timeout.tv_sec = tv.tv_sec + (tv.tv_usec / 1000 + uiTimeOut) / 1000;
	ts_timeout.tv_nsec = ((tv.tv_usec / 1000 + uiTimeOut) % 1000) * 1000 * 1000;

	int i = 0;
	unsigned int uiUniqueLen = strlen(pSendMsg->sysHeader.cUniqueId);
	pContextProxy->iFlagForRR = -1;
	
	if (pContextProxy->iFlagForRR == -1) {
		//add uniqueId
		strncpy(pContextProxy->stUnique.unique_id, pSendMsg->sysHeader.cUniqueId, uiUniqueLen);
		pContextProxy->stUnique.unique_id[uiUniqueLen] = '\0';
		pContextProxy->stUnique.flag = 1;
	
	}

		pthread_cond_timedwait(&pContextProxy->rrCond, &pContextProxy->rrMutex, &ts_timeout);
		
		if (pContextProxy->iFlagForRR == RMB_CODE_TIME_OUT) {
			pContextProxy->stUnique.flag = 0;

		} 
	pthread_mutex_unlock(&pContextProxy->rrMutex);

	switch(pContextProxy->iFlagForRR)
	{
		case RMB_CODE_TIME_OUT:
			LOGRMB(RMB_LOG_ERROR, "time out!req=%s", rmb_msg_print(pSendMsg));
			rmb_errno = RMB_ERROR_SEND_RR_MSG_TIMEOUT;
			rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_SEND_RR_MSG_TIMEOUT, "wemq send rr msg timeout", pSendMsg);
			return -6;
		case RMB_CODE_SUSS:
			trans_json_2_rmb_msg(pRevMsg, pContextProxy->mPubRRBuf, RESPONSE_TO_CLIENT);
			LOGRMB(RMB_LOG_DEBUG, "receive reply succ,buf:%s", pContextProxy->mPubRRBuf);
			pRevMsg->cPkgType = RR_TOPIC_PKG;
			return 0;
		case RMB_CODE_OTHER_FAIL:
			LOGRMB(RMB_LOG_ERROR, "receive reply failed!req=%s", rmb_msg_print(pSendMsg));
			rmb_errno = RMB_ERROR_SEND_RR_MSG_TIMEOUT;
			return -4;
		case RMB_CODE_AUT_FAIL:
			LOGRMB(RMB_LOG_ERROR, "receive reply Authentication failed!req=%s", rmb_msg_print(pSendMsg));
			rmb_errno = RMB_ERROR_SEND_RR_MSG_TIMEOUT;
			return -5;
		case RMB_CODE_DYED_MSG:
			LOGRMB(RMB_LOG_INFO, "receive dyed msg:%s", pContextProxy->mPubRRBuf);
			return 1;
	}

}

int rmb_pub_send_rr_msg_async_to_wemq(StRmbPub *pRmbPub, StRmbMsg *pSendMsg, unsigned int uiTimeOut)
{
	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");
	RMB_CHECK_POINT_NULL(pSendMsg, "pSendMsg");

	if (pSendMsg->iEventOrService == (int)RMB_EVENT_CALL) {
		LOGRMB(RMB_LOG_ERROR, "async rr interface can't send event msg!");
		rmb_errno = RMB_ERROR_RR_INTERFACE_CAN_NOT_SEND_EVENT_MSG;
		return -1;
	}

	//pub connect status error
	//if (pRmbPub->pContext->pContextProxy->iFlagForPublish == 0) {
	//	LOGRMB(RMB_LOG_ERROR, "rmb pub not connect to access!!!");
	//	return -4;
	//}

	int iRet = 0;
//	iRet = rmb_pub_set_destination_Interval(pRmbPub, pSendMsg);
//	if (iRet != 0) {
//		LOGRMB(RMB_LOG_ERROR, "rmb set destination error!serviceId=%s,sceneId=%s,iRet=%d", pSendMsg->strServiceId, pSendMsg->strScenarioId, iRet);
//		return -2;
//	}
	LOGRMB(RMB_LOG_DEBUG, "pubMsg dest=%d,%s,replyTo=%s", pSendMsg->dest.iDestType, pSendMsg->dest.cDestName, pSendMsg->replyTo.cDestName);

	iRet = rmb_msg_init(pSendMsg, pRmbStConfig, C_TYPE_WEMQ);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_msg_init failed!iRet=%d", iRet);
		return -3;
	}

	if(uiTimeOut > RR_ASYNC_MSG_MAX_LIVE_TIME){
		LOGRMB(RMB_LOG_ERROR, "RR sync ttl too large, max value is:%ld", RR_ASYNC_MSG_MAX_LIVE_TIME);
		return -4;
	}

	if (pSendMsg->ulMsgLiveTime == 0 || pSendMsg->ulMsgLiveTime > DEFAULT_MSG_MAX_LIVE_TIME) {
		pSendMsg->ulMsgLiveTime = uiTimeOut;
	}

	GetRmbNowLongTime();
	pSendMsg->sysHeader.ulSendTime = pRmbStConfig->ulNowTtime;
	pSendMsg->replyTo.iDestType = RMB_DEST_TOPIC;

	StContext *pStContext = pRmbPub->pContext;
	pStContext->uiPkgLen = MAX_LENTH_IN_A_MSG;
	stContextProxy *pContextProxy = pStContext->pContextProxy;
	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_REQUEST_ASYNC;
	iRet = rmb_pub_encode_thread_msg(stThreadMsg.m_iCmd, &stThreadMsg, pSendMsg, 0);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_pub_encode_thread_msg error!");
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_ENCODE_FAIL, "wemq_pub_encode_thread_msg error", pSendMsg);
		return iRet;
	}
	unsigned int uiUniqueLen = strlen(pSendMsg->sysHeader.cUniqueId);
	int i = 0;
	struct timeval tv_now;
	gettimeofday(&tv_now, NULL);
	unsigned long ulNowTime = tv_now.tv_sec * 1000 + tv_now.tv_usec / 1000;
	int iFlagForList = 0;
	for (i = 0; i < pContextProxy->pUniqueListForRRAsyncNew.get_array_size(&pContextProxy->pUniqueListForRRAsyncNew); i++) {

		if (pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag == 0) {
			pthread_mutex_lock(&pContextProxy->rrMutex);
			snprintf(pContextProxy->pUniqueListForRRAsyncNew.Data[i].unique_id, sizeof(pContextProxy->pUniqueListForRRAsyncNew.Data[i].unique_id), "%s", pSendMsg->sysHeader.cUniqueId);
			snprintf(pContextProxy->pUniqueListForRRAsyncNew.Data[i].biz_seq, sizeof(pContextProxy->pUniqueListForRRAsyncNew.Data[i].biz_seq), "%s", pSendMsg->sysHeader.cBizSeqNo);
			pContextProxy->pUniqueListForRRAsyncNew.Data[i].flag = 1;
			pContextProxy->pUniqueListForRRAsyncNew.Data[i].timeStamp = ulNowTime;
			pContextProxy->pUniqueListForRRAsyncNew.Data[i].timeout = uiTimeOut;
			iFlagForList = 1;
			pthread_mutex_unlock(&pContextProxy->rrMutex);
			break;
		}
	}
	//已有空间已装满
    if(iFlagForList == 0){
		LOGRMB(RMB_LOG_INFO, "local list for rr async push back");
		StUniqueIdList uniqueIdList;
		strncpy(uniqueIdList.unique_id, pSendMsg->sysHeader.cUniqueId, uiUniqueLen);
		uniqueIdList.unique_id[uiUniqueLen] = '\0';
		uniqueIdList.flag = 1;
		uniqueIdList.timeStamp = ulNowTime;
		uniqueIdList.timeout = uiTimeOut;
		pthread_mutex_lock(&pContextProxy->rrMutex);
		pContextProxy->pUniqueListForRRAsyncNew.Input(uniqueIdList, &pContextProxy->pUniqueListForRRAsyncNew);
		pthread_mutex_unlock(&pContextProxy->rrMutex);
	}	
	iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_WORKER_PUT_FIFO_ERROR, "wemq_kfifo_put error", pSendMsg);
    	return -4;
	}

    pContextProxy->iFlagForRRAsync = 1;
	return 0;
}

/**
Function: wemq_pub_reply_msg
Description:send report packet
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_reply_msg_for_wemq(StRmbPub *pRmbPub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg)
{
	if (pRmbPub == NULL || pStReceiveMsg == NULL || pStReplyMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub or pStReceiveMsg or pStReplyMsg is null");
		return -1;
	}

	if (strlen(pStReceiveMsg->replyTo.cDestName) == 0) {
		LOGRMB(RMB_LOG_ERROR, "receiveMsg has no replyTo,can't reply!\n");
		return -2;
	}

	//pub connect status error
	//if (pRmbPub->pContext->pContextProxy->iFlagForPublish == 0) {
	//	LOGRMB(RMB_LOG_ERROR, "rmb pub not connect to access!!!");
	//	return -4;
	//}

	memcpy(&pStReplyMsg->sysHeader, &pStReceiveMsg->sysHeader, sizeof(pStReceiveMsg->sysHeader));
	memcpy(&pStReplyMsg->dest, &pStReceiveMsg->replyTo, sizeof(pStReceiveMsg->replyTo));

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
/*
	while (CURRENT_WINDOW_SIZE >= DEFAULT_WINDOW_SIZE) {
		LOGRMB(RMB_LOG_ERROR, "Send Window Full, recvSeq=%u,sendSeq=%u", g_uiRecvMsgSeq, g_uiSendMsgSeq);
		usleep(1000);
	}
*/
	StContext *pStContext = pRmbPub->pContext;
	pStContext->uiPkgLen = MAX_LENTH_IN_A_MSG;
	stContextProxy *pContextProxy = pStContext->pContextProxy;

	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_REPLY;
	int iRet = 0;
	iRet = rmb_pub_encode_thread_msg(stThreadMsg.m_iCmd, &stThreadMsg, pStReplyMsg, 0);
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, "rmb_pub_encode_thread_msg error!");
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_ENCODE_FAIL, "wemq_reply_encode_thread_msg error", pStReplyMsg);
		return iRet;
	}

	iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
	if (iRet <= 0) {
		LOGRMB(RMB_LOG_ERROR, "wemq_kfifo_put error!iRet=%d", iRet);
		rmb_errno = RMB_ERROR_WORKER_PUT_FIFO_ERROR;
		rmb_send_log_for_error(pStContext->pContextProxy, RMB_ERROR_WORKER_PUT_FIFO_ERROR, "reply message wemq_kfifo_put error", pStReplyMsg);
		return rmb_errno;
	}

	return 0;
}

/**
 * Function: rmb_pub_send_msg
 * Description: send event msg
 * Return:
 * 		0: success
 * 		-1: failed
 * 		-2: queue full
 */
int rmb_pub_send_msg(StRmbPub *pRmbPub, StRmbMsg *pStMsg)
{

	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");
	RMB_CHECK_POINT_NULL(pStMsg, "pStMsg");

	if (rmb_check_msg_valid(pStMsg) != 0) {
		rmb_errno = RMB_ERROR_MSG_MISSING_PART;
		return rmb_errno;
	}

	if (pStMsg->iEventOrService == (int)RMB_SERVICE_CALL) {
		LOGRMB(RMB_LOG_ERROR, "event interface can't send rr msg,serviceId=%s!", pStMsg->strServiceId);
		rmb_errno = RMB_ERROR_EVENT_INTERFACE_CAN_NOT_SEND_RR_MSG;
		return rmb_errno;
	}

	int iRet = rmb_pub_set_destination_Interval(pRmbPub, pStMsg);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb set destination error!serviceId=%s,sceneId=%s,iRet=%d,error=%s", pStMsg->strServiceId, pStMsg->strScenarioId, iRet, get_rmb_last_error());
		return rmb_errno;
	}

	return rmb_pub_send_msg_to_wemq(pRmbPub, pStMsg);

}

/**
 * Function: rmb_pub_send_msg_python
 * Description: send event msg_python
 * Return:
 * 		0: success
 * 		-1: failed
 * 		-2: queue full
 */
int rmb_pub_send_msg_python(StRmbMsg *pStMsg)
{
	StRmbPub *pRmbPub = pRmbGlobalPub;

	return rmb_pub_send_msg(pRmbPub,pStMsg);

}

/**
Function: rmb_pub_send_rr_msg
Description:send RR asynchronous message
Retrun:
	0    --success
	-1	 --failed
	-2   --queue full
*/
int rmb_pub_send_rr_msg_async(StRmbPub *pRmbPub, StRmbMsg *pStMsg,unsigned int uiTimeOut)
{
	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");
	RMB_CHECK_POINT_NULL(pStMsg, "pStMsg");

	if (pStMsg->iEventOrService == (int)RMB_EVENT_CALL)
	{
		LOGRMB(RMB_LOG_ERROR, "aync RR interface can't send event msg!");
		rmb_errno = RMB_ERROR_RR_INTERFACE_CAN_NOT_SEND_EVENT_MSG;
		return rmb_errno;
	}


	//set destination
	int iRet = rmb_pub_set_destination_Interval(pRmbPub, pStMsg);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb set destination error!serviceId=%s,sceneId=%s,iRet=%d", pStMsg->strServiceId, pStMsg->strScenarioId, iRet);
		return rmb_errno;
	}

	return rmb_pub_send_rr_msg_async_to_wemq(pRmbPub, pStMsg, uiTimeOut);
	
}


/**
Function: rmb_pub_send_rr_msg_async_python
Description:send RR asynchronous message
Retrun:
	0    --success
	-1	 --failed
	-2   --queue full
*/
int rmb_pub_send_rr_msg_async_python(StRmbMsg *pStMsg,unsigned int uiTimeOut)
{
	StRmbPub *pRmbPub = pRmbGlobalPub;

	return rmb_pub_send_rr_msg_async(pRmbPub,pStMsg,uiTimeOut);
}


/**
Function: rmb_pub_send_and_receive
Description:send message and wait for report
Retrun:
	0    --success
	-1	 --timeout
	-2   --error
*/
int rmb_pub_send_and_receive(StRmbPub *pRmbPub, StRmbMsg *pSendMsg, StRmbMsg *pRevMsg, unsigned int uiTimeOut)
{
	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");
	RMB_CHECK_POINT_NULL(pSendMsg, "pSendMsg");
	RMB_CHECK_POINT_NULL(pRevMsg, "pRevMsg");
	
	if (pSendMsg->iEventOrService == (int)RMB_EVENT_CALL) {
		LOGRMB(RMB_LOG_ERROR, "RR interface can't send event msg!");
		rmb_errno = RMB_ERROR_RR_INTERFACE_CAN_NOT_SEND_EVENT_MSG;
		return rmb_errno;
	}

	//set destination
	int iRet = rmb_pub_set_destination_Interval(pRmbPub, pSendMsg);
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "rmb set destination error!serviceId=%s,sceneId=%s,iRet=%d", pSendMsg->strServiceId, pSendMsg->strScenarioId, iRet);
		return rmb_errno;
	}

	return rmb_pub_send_and_receive_to_wemq(pRmbPub, pSendMsg, pRevMsg, uiTimeOut);
	
}

int rmb_pub_send_and_receive_python(StRmbMsg *pSendMsg, StRmbMsg *pRevMsg, unsigned int uiTimeOut)
{
	StRmbPub *pRmbPub = pRmbGlobalPub;
	
	return rmb_pub_send_and_receive(pRmbPub,pSendMsg,pRevMsg,uiTimeOut);
}
/**
Function: rmb_pub_reply_msg
Description:send report packet
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_reply_msg(StRmbPub *pRmbPub, StRmbMsg *pStReceiveMsg, StRmbMsg *pStReplyMsg)
{
	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");
	RMB_CHECK_POINT_NULL(pStReceiveMsg, "pStReceiveMsg");
	RMB_CHECK_POINT_NULL(pStReplyMsg, "pStReplyMsg");
    if (strlen(pStReceiveMsg->replyTo.cDestName) == 0){
    	//LOGRMB(RMB_LOG_INFO, "rmb receivemsg reply destname empty");
    	LOGRMB(RMB_LOG_WARN, "pStReceiveMsg->replyTo.cDestName=%s", pStReceiveMsg->replyTo.cDestName);
    	return 0;
	}
	return rmb_pub_reply_msg_for_wemq(pRmbPub, pStReceiveMsg, pStReplyMsg);
	
}

/**
Function: rmb_pub_close
Description:close pub
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_close(StRmbPub *pRmbPub)
{
	if (pRmbPub == NULL || pRmbPub->pContext == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub or pRmbPub->pContext is null");
		return 0;
	}

	//wemq
	if (pRmbStConfig->iConnWemq == 1 || pRmbStConfig->iApiLogserverSwitch == 1) {
		if (pRmbPub->pContext->pContextProxy != NULL) {
			stContextProxy *pContextProxy = pRmbPub->pContext->pContextProxy;

			//pContextProxy->iFlagForRun = 0;
			if(rmb_pub_send_client_goodbye_to_wemq(pRmbPub) != 0){
				LOGRMB(RMB_LOG_DEBUG, "rmb_pub_send_client_goodbye_to_wemq failed");
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
		}
	}
	return 0;
}

int rmb_pub_close_python()
{
	StRmbPub *pRmbPub = pRmbGlobalPub;
	
	int ret = rmb_pub_close_v2(pRmbPub);
	free(pRmbPub);
	return ret;
}


/**
Function: rmb_pub_close_v2
Description:close pub
Retrun:
	0    --success
	-1	 --failed
*/
int rmb_pub_close_v2(StRmbPub *pRmbPub)
{
	if (pRmbPub == NULL || pRmbPub->pContext == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbPub or pRmbPub->pContext is null");
		return 0;
	}

	//wemq
	if (pRmbStConfig->iConnWemq == 1 || pRmbStConfig->iApiLogserverSwitch == 1) {
		if (pRmbPub->pContext->pContextProxy != NULL) {
			stContextProxy *pContextProxy = pRmbPub->pContext->pContextProxy;

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
		}
	}

	return 0;
}

int rmb_pub_send_client_goodbye_to_wemq(StRmbPub *pRmbPub)
{
	RMB_CHECK_POINT_NULL(pRmbPub, "pRmbPub");

	stContextProxy *pContextProxy = pRmbPub->pContext->pContextProxy;

	StWemqThreadMsg stThreadMsg;
	memset(&stThreadMsg, 0x00, sizeof(StWemqThreadMsg));
	stThreadMsg.m_iCmd = THREAD_MSG_CMD_SEND_CLIENT_GOODBYE;

	WEMQJSON *jsonHeader = json_object_new_object();
	if (jsonHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object failed");
		return -2;
	}
	//add type
	json_object_object_add(jsonHeader, MSG_HEAD_COMMAND_STR, json_object_new_string(CLIENT_GOODBYE_REQUEST));
	//add seq
	json_object_object_add(jsonHeader, MSG_HEAD_SEQ_INT, json_object_new_int(0));
	//add status
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

	int iRet = wemq_kfifo_put(&pContextProxy->pubFifo, stThreadMsg);
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
	pthread_mutex_lock(&pContextProxy->goodByeMutex);
	if (pContextProxy->iFlagForGoodBye == 0)
	{
		pthread_cond_timedwait(&pContextProxy->goodByeCond, &pContextProxy->goodByeMutex, &timeout);
	}
	pthread_mutex_unlock(&pContextProxy->goodByeMutex);

	if (pContextProxy->iFlagForGoodBye != 1)
	{
		LOGRMB(RMB_LOG_ERROR, "send pub client goodBye timeout!");
		rmb_errno = RMB_ERROR_CLIENT_GOODBYE_TIMEOUT;
		return rmb_errno;
	}

	LOGRMB(RMB_LOG_DEBUG, "send and recv sub client goodbye succ");  

	return 0;
}


