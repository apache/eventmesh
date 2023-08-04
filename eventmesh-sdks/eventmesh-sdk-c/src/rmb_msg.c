#include "rmb_msg.h"
#include "string.h"
#include "time.h"
#include "solClient.h"
#include "rmb_errno.h"

static const int i1KB = 1 << 10;
static const int i2KB = 1 << 11;
static const int i4KB = 1 << 12;
static const int i8KB = 1 << 13;
static const int i16KB = 1 << 14;
static const int i32KB = 1 << 15;
static const int i64KB = 1 << 16;
static const int i128KB = 1 << 17;
static const int i256KB = 1 << 18;
static const int i512KB = 1 << 19;
static const int i1024KB = 1 << 20;
static const int i2048KB = 1 << 21;

//private func
const static char* strLogicType[] = { "null", "req_in", "rsp_in", "event_in", "req_out", "rsp_out", "event_out", "null" };

int rmb_msg_clear(StRmbMsg *pStMsg)
{
	RMB_CHECK_POINT_NULL(pStMsg, "pStMsg");

	//pStMsg->cPkgType = 0;
	//pStMsg->cLogicType = 0;
	memset(&(pStMsg->sysHeader), 0, sizeof(StSystemHeader));
	pStMsg->replyTo.cDestName[0] = '\0';
	pStMsg->dest.cDestName[0]= '\0';
	pStMsg->iCorrLen = 0;
	pStMsg->iContentLen = 0;
	pStMsg->iAppHeaderLen = 0;
	pStMsg->ulMsgId = 0;
	pStMsg->ulMsgLiveTime = 0;
	//add in 2016-09-26
	pStMsg->flag = 0;
	pStMsg->isDyedMsg[0] = "\0";
	return 0;
}


int rmb_msg_init(StRmbMsg* pRmbMsg, StRmbConfig *pConfig, enum RMB_API_YPE type)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");

	pRmbMsg->replyTo.cDestName[0] = '\0';
	char cUniqueId[100] = {0};
	char cUniqueIdEx[50] = {0};
    rmb_msg_random_uuid(cUniqueIdEx, sizeof(cUniqueIdEx));
	snprintf(cUniqueId, sizeof(cUniqueId), "c/%s", cUniqueIdEx);
//	pRmbMsg->cApiType = C_TYPE;
	pRmbMsg->cApiType = type;
	pRmbMsg->cLogicType = 0;
	pRmbMsg->sysHeader.iReceiveMode = 1;
	RMB_MEMCPY(pRmbMsg->sysHeader.cConsumerSysId, pConfig->cConsumerSysId);
	if (pRmbMsg->sysHeader.iSetSysVersionFlag != 1 ){   //没有被用户设置过
	    RMB_MEMCPY(pRmbMsg->sysHeader.cConsumerSysVersion, pConfig->cConsumerSysVersion);
	}
	RMB_MEMCPY(pRmbMsg->sysHeader.cConsumerSvrId, pConfig->cHostIp);
	RMB_MEMCPY(pRmbMsg->sysHeader.cOrgSvrId, pConfig->cHostIp);	
	RMB_MEMCPY(pRmbMsg->sysHeader.cUniqueId, cUniqueId);
	RMB_MEMCPY(pRmbMsg->sysHeader.cConsumerDcn, pConfig->cConsumerDcn);
	RMB_MEMCPY(pRmbMsg->sysHeader.cOrgId, pConfig->strOrgId);
	RMB_MEMCPY(pRmbMsg->sysHeader.cRmbVersion, RMBVERSION);

	char cAppHeaderClassName[50] = "cn.webank.rmb.message.AppHeader";
	RMB_MEMCPY(pRmbMsg->sysHeader.cAppHeaderClass, cAppHeaderClassName);

	//RMB_MEMSET(pRmbMsg->strScenarioId);
	//RMB_MEMSET(pRmbMsg->strServiceId);
	//RMB_MEMSET(pRmbMsg->strTargetDcn);
	//pRmbMsg->iEventOrService = 0;

	GetRmbNowLongTime();
	pRmbMsg->sysHeader.ulTranTimeStamp = pRmbStConfig->ulNowTtime;
	pRmbMsg->sysHeader.ulMessageDate = pRmbStConfig->ulNowTtime;

	//for wemq

	return 0;
}

//you must fill in under fields
int rmb_msg_set_bizSeqNo(StRmbMsg* pRmbMsg, const char* cBizSeqNo)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(cBizSeqNo, "cBizSeqNo");
	RMB_LEN_CHECK(cBizSeqNo,32);

	RMB_MEMCPY(pRmbMsg->sysHeader.cBizSeqNo, cBizSeqNo);
	return 0;
}

int rmb_msg_set_consumerSysVersion(StRmbMsg* pRmbMsg, const char* cConsumerSysVersion)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(cConsumerSysVersion, "cConsumerSysVersion");
	//RMB_LEN_CHECK(cBizSeqNo,32);
	
	RMB_MEMCPY(pRmbMsg->sysHeader.cConsumerSysVersion, cConsumerSysVersion);
	pRmbMsg->sysHeader.iSetSysVersionFlag = 1;
	return 0;
}

int rmb_msg_set_dyedMsg(StRmbMsg* pRmbMsg,const char* sign)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_MEMCPY(pRmbMsg->isDyedMsg, sign);
	return 0;	
}

int rmb_msg_set_consumerSeqNo(StRmbMsg* pRmbMsg, const char* cConsumerSeqNo)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(cConsumerSeqNo, "cConsumerSeqNo");
	RMB_LEN_CHECK(cConsumerSeqNo,32);

	RMB_MEMCPY(pRmbMsg->sysHeader.cConsumerSeqNo, cConsumerSeqNo);
	return 0;
}

int rmb_msg_set_orgSysId(StRmbMsg* pRmbMsg, const char* cOrgSysId)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(cOrgSysId, "cOrgSysId");
	RMB_LEN_CHECK(cOrgSysId,4)

	RMB_MEMCPY(pRmbMsg->sysHeader.cOrgSysId, cOrgSysId);
	return 0;
}

//***********************

int rmb_msg_set_dest(StRmbMsg* pRmbMsg, int desType, const char *cTargetDcn, int iServiceOrEven, const char *cServiceId, const char *cScenario)
{
	return rmb_msg_set_dest_v2(pRmbMsg, cTargetDcn, cServiceId, cScenario);

}


int rmb_msg_set_dest_v2(StRmbMsg* pRmbMsg, const char *cTargetDcn, const char *cServiceId, const char *cScenario)
{
	return rmb_msg_set_dest_v2_1(pRmbMsg, cTargetDcn, cServiceId, cScenario, (char*)NULL);
}

int rmb_msg_set_dest_v2_1(StRmbMsg* pRmbMsg, const char *cTargetDcn, const char *cServiceId, const char *cScenario, const char *cTargetOrgId)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	//RMB_CHECK_POINT_NULL(cTargetDcn, "cTargetDcn");
	RMB_CHECK_POINT_NULL(cServiceId, "cServiceId");
	RMB_CHECK_POINT_NULL(cScenario, "cScenario");

	if (cTargetDcn == NULL || strlen(cTargetDcn) == 0)
	{
		pRmbMsg->strTargetDcn[0] = 0;
	}
	else
	{
		if (strlen(cTargetDcn) != DEFAULT_DCN_LENGTH)
		{
			LOGRMB(RMB_LOG_ERROR, "destination dcn must be 3 length!input=%lu", strlen(cTargetDcn));
			rmb_errno = RMB_ERROR_ARGV_LEN_ERROR;
			return -2;
		}
		strncpy(pRmbMsg->strTargetDcn, cTargetDcn, sizeof(pRmbMsg->strTargetDcn) - 1);
	}
	
	if (strlen(cServiceId) != DEFAULT_SERVICE_ID_LENGTH)
	{
		LOGRMB(RMB_LOG_ERROR, "destination service_id must be 8 length!input=%lu", strlen(cServiceId));
		rmb_errno = RMB_ERROR_ARGV_LEN_ERROR;
		return -2;
	}
	if (strlen(cScenario) != DEFAULT_SCENE_ID_LENGTH)
	{
		LOGRMB(RMB_LOG_ERROR, "destination scene_id must be 2 length!input=%lu", strlen(cScenario));
		rmb_errno = RMB_ERROR_ARGV_LEN_ERROR;
		return -2;
	}

	pRmbMsg->iEventOrService = (*(cServiceId + 3) == '0') ? RMB_SERVICE_CALL : RMB_EVENT_CALL;
	
	strncpy(pRmbMsg->strServiceId, cServiceId, sizeof(pRmbMsg->strServiceId) - 1);
	strncpy(pRmbMsg->strScenarioId, cScenario, sizeof(pRmbMsg->strScenarioId) - 1);
	if (cTargetOrgId == NULL)
	{
		pRmbMsg->iFLagForOrgId = RMB_COMMIT_BY_API;
	}
	else
	{
		pRmbMsg->iFLagForOrgId = RMB_COMMIT_BY_OWN;
		strncpy(pRmbMsg->strTargetOrgId, cTargetOrgId, sizeof(pRmbMsg->strTargetOrgId) - 1);
	}
	//modify 2016-09-26
	//pRmbMsg->flag = pRmbMsg->flag & (1 << RMBMSG_DEST_HAS_SET);
	pRmbMsg->flag = pRmbMsg->flag | (1 << RMBMSG_DEST_HAS_SET);

	return 0;
}

//////////////////1.7.3 add //////////////////////////////////////////////////////

//get dest
int rmb_msg_get_dest(StRmbMsg *pRmbMsg, char *dest, unsigned int *uiLen)
{
	if (pRmbMsg == NULL || dest == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg or dest is null");
		return -1;
	}

	if (*uiLen < strlen(pRmbMsg->dest.cDestName)) {
		LOGRMB(RMB_LOG_ERROR, "dest is too small, len=%u, but msg_dest_len=%u\n", *uiLen, strlen(pRmbMsg->dest.cDestName));
		return -2;
	}

	memcpy(dest, pRmbMsg->dest.cDestName, strlen(pRmbMsg->dest.cDestName));
	*uiLen = strlen(pRmbMsg->dest.cDestName);

	return 0;
}

const char* rmb_msg_get_dest_ptr(StRmbMsg* pRmbMsg)
{
	if (pRmbMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg is null");
		return NULL;
	}

	return pRmbMsg->dest.cDestName;
}

//get cConsumerSysId
int rmb_msg_get_consumerSysId(StRmbMsg *pRmbMsg, char *cConsumerSysId, unsigned int *uiLen)
{
	if (pRmbMsg == NULL || cConsumerSysId == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg or cConsumerSysId is null");
		return -1;
	}

	unsigned int uiConsumerLen = (unsigned int)strlen(pRmbMsg->sysHeader.cConsumerSysId);

	if (*uiLen < uiConsumerLen) {
		LOGRMB(RMB_LOG_ERROR, "rmb msg consumerSysId len=%u, but arg len=%u(too small)", uiConsumerLen, *uiLen);
		return -2;
	}

	memcpy(cConsumerSysId, pRmbMsg->sysHeader.cConsumerSysId, uiConsumerLen);
	*uiLen = uiConsumerLen;

	return 0;
}

const char* rmb_msg_get_consumerSysId_ptr(StRmbMsg* pRmbMsg)
{
	if (pRmbMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg is null");
		return NULL;
	}

	return pRmbMsg->sysHeader.cConsumerSysId;
}

//get cConsumerSvrId
int rmb_msg_get_consumerSvrId(StRmbMsg *pRmbMsg, char *cConsumerSvrId, unsigned int *uiLen)
{
	if (pRmbMsg == NULL || cConsumerSvrId == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg or cConsumerSvrId is null");
		return -1;
	}

	unsigned int uiConsumerLen = (unsigned int)strlen(pRmbMsg->sysHeader.cConsumerSvrId);

	if (*uiLen < uiConsumerLen) {
		LOGRMB(RMB_LOG_ERROR, "rmb msg consumerSvrId len=%u, but arg len=%u(too small)", uiConsumerLen, *uiLen);
		return -2;
	}

	memcpy(cConsumerSvrId, pRmbMsg->sysHeader.cConsumerSvrId, uiConsumerLen);
	*uiLen = uiConsumerLen;

	return 0;
}

const char* rmb_msg_get_consumerSvrId_ptr(StRmbMsg* pRmbMsg)
{
	if (pRmbMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg is null");
		return NULL;
	}

	return pRmbMsg->sysHeader.cConsumerSvrId;
}
//////////////////////////////////////////////////////////////////////////////

int rmb_check_msg_valid(StRmbMsg *pStMsg)
{
	//modify in 2016-09-26
	//if (pStMsg->flag | (1 << RMBMSG_DEST_HAS_SET == 0))
	if ((pStMsg->flag & (1 << RMBMSG_DEST_HAS_SET)) == 0)
	{
		LOGRMB(RMB_LOG_ERROR, "pStMsg=%s must be init first!", rmb_msg_print(pStMsg));
		rmb_errno = RMB_ERROR_MSG_MISSING_PART;
		return -1;
	}
	return 0;
}

int rmb_msg_set_live_time(StRmbMsg* pRmbMsg, unsigned long ulLiveTime)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	if (ulLiveTime == 0)
	{
		LOGRMB(RMB_LOG_ERROR, "ttl time must gt 0");
		rmb_errno = RMB_ERROR_MSG_TTL_0;
		return -2;
	}

	pRmbMsg->ulMsgLiveTime = ulLiveTime;
	return 0;
}

int rmb_get_fit_size(const unsigned int uiLen, const unsigned int uiMaxLen)
{
//	if (uiLen >= uiMaxLen)
	if (uiLen > uiMaxLen)
	{
		return -1;
	}
	unsigned int uiCount = 0;
	unsigned int uiTmpLen = uiLen;
	while ((uiTmpLen = uiTmpLen >> 1) != 0)
	{
		uiCount++;
	}
	uiCount++;
	uiCount = (uiCount >= 10) ? uiCount:10;
	return (1 << uiCount) + 1;
}

int rmb_msg_set_app_header(StRmbMsg* pRmbMsg, const char* appHeader, unsigned int uiLen)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(appHeader, "appHeader");

//	if (uiLen >= MAX_APPHEADER_LENGTH)
//	{
//		LOGRMB(RMB_LOG_ERROR, "uiLen=%u too large!max_limit=%u", uiLen, MAX_APPHEADER_LENGTH);
//		rmb_errno = RMB_ERROR_MSG_SET_APPHEADER_TOO_LARGE;
//		return -2;
//	}
	if (pRmbMsg->cAppHeader == NULL || pRmbMsg->iMallocAppHeaderLength == 0)
	{
		pRmbMsg->cAppHeader = (char*)malloc(i1KB);
		pRmbMsg->iMallocAppHeaderLength = i1KB;
	}
	if (uiLen >= pRmbMsg->iMallocAppHeaderLength)
	{
		int iFitSize = rmb_get_fit_size(uiLen, MAX_APPHEADER_LENGTH);
		if (iFitSize < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "uiLen=%u too large!max_limit=%u\n", uiLen, MAX_APPHEADER_LENGTH);
			rmb_errno = RMB_ERROR_BUF_NOT_ENOUGH;
			return -2;
		}
		free(pRmbMsg->cAppHeader);
		pRmbMsg->cAppHeader = NULL;
		pRmbMsg->cAppHeader = (char*)malloc(iFitSize);
		pRmbMsg->iMallocAppHeaderLength = iFitSize;
	}

	memcpy(pRmbMsg->cAppHeader, appHeader, uiLen);
	pRmbMsg->cAppHeader[uiLen] = '\0';
	pRmbMsg->iAppHeaderLen = (int)uiLen;
	return 0;
}

int rmb_msg_set_content(StRmbMsg* pRmbMsg, const char* content, unsigned int uiLen)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(content, "content");

//	if (uiLen >= sizeof(pRmbMsg->cContent))
//	{
//		LOGRMB(RMB_LOG_ERROR, "uiLen=%u too large!max_limit=%lu",uiLen, sizeof(pRmbMsg->cContent));
//		rmb_errno = RMB_ERROR_MSG_SET_CONTENT_TOO_LARGE;
//		return -2;
//	}

	if (pRmbMsg->cContent == NULL || pRmbMsg->iMallocContentLength == 0)
	{
		pRmbMsg->cContent = (char*)malloc(i1KB);
		pRmbMsg->iMallocContentLength = i1KB;
	}
	if (uiLen >= pRmbMsg->iMallocContentLength)
	{
		int iFitSize = rmb_get_fit_size(uiLen, MAX_MSG_CONTENT_SIZE);
		if (iFitSize < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "uiLen=%u too large!max_limit=%u\n", uiLen, MAX_MSG_CONTENT_SIZE);
			rmb_errno = RMB_ERROR_BUF_NOT_ENOUGH;
			return rmb_errno;
		}
		free(pRmbMsg->cContent);
		pRmbMsg->cContent = NULL;
		pRmbMsg->cContent = (char*)malloc(iFitSize);
		pRmbMsg->iMallocContentLength = iFitSize;
	}

	memcpy(pRmbMsg->cContent, content, uiLen);
	pRmbMsg->cContent[uiLen] = '\0';
	pRmbMsg->iContentLen = (int)uiLen;
	pRmbMsg->sysHeader.iContentLength = pRmbMsg->iContentLen;
	return 0;
}

/**
 * get msg type
 * 0: undefined type
 * 1: request in queue
 * 2: reply package in RR
 * 3: broadcast
 * see: C_RMB_PKG_TYPE
 */
int rmb_msg_get_msg_type(StRmbMsg* pRmbMsg)
{
	return (int)pRmbMsg->cPkgType;
}

const char* rmb_msg_get_uniqueId_ptr(StRmbMsg* pRmbMsg)
{
	return pRmbMsg->sysHeader.cUniqueId;
}

const char* rmb_msg_get_biz_seq_no_ptr(StRmbMsg* pRmbMsg)
{
	return pRmbMsg->sysHeader.cBizSeqNo;
}

const char* rmb_msg_get_consumer_seq_no_ptr(StRmbMsg* pRmbMsg)
{
	return pRmbMsg->sysHeader.cConsumerSeqNo;
}

const char* rmb_msg_get_consumer_dcn_ptr(StRmbMsg* pRmbMsg)
{
	return pRmbMsg->sysHeader.cConsumerDcn;
}

const char* rmb_msg_get_org_sys_id_ptr(StRmbMsg* pRmbMsg)
{
	return pRmbMsg->sysHeader.cOrgSysId;
}

const char* rmb_msg_get_org_id_ptr(StRmbMsg* pRmbMsg)
{
	return pRmbMsg->sysHeader.cOrgId;
}

const char* rmb_msg_get_app_header_ptr(StRmbMsg* pRmbMsg, unsigned int *pLen)
{
	if (pRmbMsg == NULL || pLen == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg or pLen == NULL");
	}

	*pLen = pRmbMsg->iAppHeaderLen;
	return  pRmbMsg->cAppHeader;
}

int rmb_msg_get_app_header(StRmbMsg* pRmbMsg, char* userHeader, unsigned int *pLen)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(userHeader, "userHeader");

	if (pRmbMsg->iAppHeaderLen > *pLen)
	{
		LOGRMB(RMB_LOG_ERROR, " buffer len too small.uiLen=%u,buffSize=%u", *pLen, pRmbMsg->iAppHeaderLen);
		rmb_errno = RMB_ERROR_BUF_NOT_ENOUGH;
		return rmb_errno;
	}
	memcpy(userHeader, pRmbMsg->cAppHeader, pRmbMsg->iAppHeaderLen);
	*pLen = pRmbMsg->iAppHeaderLen;
	return 0;
}

const char* rmb_msg_get_content_ptr(StRmbMsg* pRmbMsg, unsigned int *pLen)
{
	if (pRmbMsg == NULL || pLen == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, "pRmbMsg or pLen == NULL");
	}

	*pLen = pRmbMsg->iContentLen;
	return pRmbMsg->cContent;
}

int rmb_msg_get_content(StRmbMsg* pRmbMsg, char* content, unsigned int *pLen)
{
	RMB_CHECK_POINT_NULL(pRmbMsg, "pRmbMsg");
	RMB_CHECK_POINT_NULL(content, "content");

	if (pRmbMsg->iContentLen > *pLen)
	{
		LOGRMB(RMB_LOG_ERROR, " content buffer len too small.uiLen=%u,buffSize=%u", *pLen, pRmbMsg->iContentLen);
		rmb_errno = RMB_ERROR_BUF_NOT_ENOUGH;
		return rmb_errno;
	}
	memcpy(content, pRmbMsg->cContent, pRmbMsg->iContentLen);
	*pLen = pRmbMsg->iContentLen;
	return 0;
}

int shift_buf_2_msg(StRmbMsg *pStMsg, const char* cBuf, unsigned int uiLen)
{
	//StRmbMsg msg;
	const char *p = cBuf;

	unsigned int uiBufLen = 3*sizeof(char) + sizeof(pStMsg->sysHeader) + 2 * sizeof(pStMsg->dest) + 2*sizeof(unsigned long)
			+ 3 * sizeof(int) + sizeof(StFlow *) + sizeof(pStMsg->strTargetDcn) + sizeof(pStMsg->strServiceId) + sizeof(pStMsg->strScenarioId) + sizeof(int)
			+ sizeof(int);
	if (uiBufLen > uiLen) 
	{
		rmb_errno = RMB_ERROR_BUF_2_MSG_FAIL;
		return -1;
	}

	//copy msg src
	memcpy(&pStMsg->iMsgMode, p, sizeof(int));
	p += sizeof(int);

	//copy cPkgType
	pStMsg->cPkgType = *p;
	p += sizeof(char);

	//copy cLogicType
	pStMsg->cLogicType = *p;
	p += sizeof(char);

	//copy cApiType
	pStMsg->cApiType = *p;
	p += sizeof(char);

	//copy sysHeader
	memcpy(&pStMsg->sysHeader, p, sizeof(pStMsg->sysHeader));
	p += sizeof(pStMsg->sysHeader);

	//copy dest
	memcpy(&pStMsg->dest, p, sizeof(pStMsg->dest));
	p += sizeof(pStMsg->dest);

	//copy replyTo
	memcpy(&pStMsg->replyTo, p, sizeof(pStMsg->replyTo));
	p += sizeof(pStMsg->replyTo);

	//copy msgid
	memcpy(&pStMsg->ulMsgId, p, sizeof(unsigned long));
	p += sizeof(unsigned long);

	//copy msgLiveTime
	memcpy(&pStMsg->ulMsgLiveTime, p, sizeof(unsigned long));
	p += sizeof(unsigned long);

	//copy AppHeader  -- len
	memcpy(&pStMsg->iAppHeaderLen, p, sizeof(int));
	p += sizeof(int);

	uiBufLen += pStMsg->iAppHeaderLen;
	if (pStMsg->iAppHeaderLen >= pStMsg->iMallocAppHeaderLength)
	{
		int iFitSize = rmb_get_fit_size(pStMsg->iAppHeaderLen, MAX_APPHEADER_LENGTH);
		if (iFitSize < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "appheader len=%u too large!max_limit=%u\n", pStMsg->iAppHeaderLen, MAX_APPHEADER_LENGTH);
			rmb_errno = RMB_ERROR_BUF_2_MSG_FAIL;
			return rmb_errno;
		}
		free(pStMsg->cAppHeader);
		pStMsg->cAppHeader = NULL;
		pStMsg->cAppHeader = (char*)malloc(iFitSize);
		pStMsg->iMallocAppHeaderLength = iFitSize;
	}
	if (uiBufLen > uiLen) 
	{
		rmb_errno = RMB_ERROR_BUF_2_MSG_FAIL;
		return -2;
	}

	//copy AppHeader  -- data
	memcpy(pStMsg->cAppHeader, p, pStMsg->iAppHeaderLen);
	pStMsg->cAppHeader[pStMsg->iAppHeaderLen] = 0;
	p += pStMsg->iAppHeaderLen;

	//copy content
	memcpy(&pStMsg->iContentLen, p, sizeof(int));
	p += sizeof(int);

	uiBufLen += pStMsg->iContentLen;
	if (pStMsg->iContentLen >= pStMsg->iMallocContentLength)
	{
		int iFitSize = rmb_get_fit_size(pStMsg->iContentLen, MAX_MSG_CONTENT_SIZE);
		if (iFitSize < 0)
		{
			LOGRMB(RMB_LOG_ERROR, "content len=%u too large!max_limit=%u\n", uiLen, MAX_MSG_CONTENT_SIZE);
			rmb_errno = RMB_ERROR_BUF_2_MSG_FAIL;
			return -3;
		}
		free(pStMsg->cContent);
		pStMsg->cContent = NULL;
		pStMsg->cContent = (char*)malloc(iFitSize);
		pStMsg->iMallocContentLength = iFitSize;
	}

	if (uiBufLen > uiLen)
	{
		rmb_errno = RMB_ERROR_BUF_2_MSG_FAIL;
		return -3;
	}

	//copy content
	memcpy(pStMsg->cContent, p, pStMsg->iContentLen);
	pStMsg->cContent[pStMsg->iContentLen] = 0;
	p += pStMsg->iContentLen;

	//copy corrid
	memcpy(&pStMsg->iCorrLen, p, sizeof(int));
	p += sizeof(int);

	uiBufLen += pStMsg->iCorrLen;
	if (uiBufLen > uiLen) 
	{
		rmb_errno = RMB_ERROR_BUF_2_MSG_FAIL;
		return -4;
	}

	memcpy(pStMsg->cCorrId, p, pStMsg->iCorrLen);
	p += pStMsg->iCorrLen;

	memcpy(&pStMsg->iEventOrService, p, sizeof(int));
	p += sizeof(int);

	memcpy(pStMsg->strTargetDcn, p, sizeof(pStMsg->strTargetDcn));
	p += sizeof(pStMsg->strTargetDcn);

	memcpy(pStMsg->strServiceId, p, sizeof(pStMsg->strServiceId));
	p += sizeof(pStMsg->strServiceId);

	memcpy(pStMsg->strScenarioId, p, sizeof(pStMsg->strScenarioId));
	p += sizeof(pStMsg->strScenarioId);

	//memcpy(pStMsg, &msg, sizeof(StRmbMsg));

	return 0;
}

int shift_msg_2_buf(char* cBuf, unsigned int *pLen, const StRmbMsg *pStMsg)
{
	unsigned int uiMsgLen = 0;
	uiMsgLen = 3*sizeof(char) + sizeof(StSystemHeader) + 2*sizeof(StDestination) + 2*sizeof(unsigned long);
	uiMsgLen +=  3 * sizeof(int) + sizeof(StFlow *) + sizeof(pStMsg->strTargetDcn) + sizeof(pStMsg->strServiceId) + sizeof(pStMsg->strScenarioId) + sizeof(int);
	uiMsgLen += pStMsg->iAppHeaderLen + pStMsg->iContentLen + pStMsg->iCorrLen;
	uiMsgLen += sizeof(int);

	char *p = cBuf;

	if (*pLen < uiMsgLen) 
	{
		rmb_errno = RMB_ERROR_MSG_2_BUF_FAIL;
		return rmb_errno;
	}

	*pLen = uiMsgLen;

	//set msg src
	memcpy(p, &pStMsg->iMsgMode, sizeof(int));
	p += sizeof(int);

	//copy cPkgType
	*p = pStMsg->cPkgType;
	p += sizeof(char);

	//copy cLogicType
	*p = pStMsg->cLogicType;
	p += sizeof(char);

	//copy cApiType
	*p = pStMsg->cApiType;
	p += sizeof(char);

	//copy sysheader
	memcpy(p, &pStMsg->sysHeader, sizeof(pStMsg->sysHeader));
	p += sizeof(pStMsg->sysHeader);

	//copy dest
	memcpy(p, &pStMsg->dest, sizeof(pStMsg->dest));
	p += sizeof(pStMsg->dest);

	//copy replyTo
	memcpy(p, &pStMsg->replyTo, sizeof(pStMsg->replyTo));
	p += sizeof(pStMsg->replyTo);

	//copy msgid
	memcpy(p, &pStMsg->ulMsgId, sizeof(unsigned long));
	p += sizeof(unsigned long);
	
	//copy msgLiveTime
	memcpy(p, &pStMsg->ulMsgLiveTime, sizeof(unsigned long));
	p += sizeof(unsigned long);

	//copy Appheader
	memcpy(p, &pStMsg->iAppHeaderLen, sizeof(int));
	p += sizeof(int);

	memcpy(p, pStMsg->cAppHeader, pStMsg->iAppHeaderLen);
	p += pStMsg->iAppHeaderLen;

	//copy content
	memcpy(p, &pStMsg->iContentLen, sizeof(int));
	p += sizeof(int);

	memcpy(p, pStMsg->cContent, pStMsg->iContentLen);
	p += pStMsg->iContentLen;

	//copy corrid
	memcpy(p, &pStMsg->iCorrLen, sizeof(int));
	p += sizeof(int);

	memcpy(p, pStMsg->cCorrId, pStMsg->iCorrLen);
	p += pStMsg->iCorrLen;

	memcpy(p, &pStMsg->iEventOrService, sizeof(int));
	p += sizeof(int);
	
	memcpy(p, pStMsg->strTargetDcn, sizeof(pStMsg->strTargetDcn));
	p += sizeof(pStMsg->strTargetDcn);

	memcpy(p, pStMsg->strServiceId, sizeof(pStMsg->strServiceId));
	p += sizeof(pStMsg->strServiceId);

	memcpy(p, pStMsg->strScenarioId, sizeof(pStMsg->strScenarioId));
	p += sizeof(pStMsg->strScenarioId);

	return 0;
}

int set_extfields_2_rmb_msg(StRmbMsg *pStMsg,const char* command,int iSeq)
{
	char born_time[32];
	char store_time[32];
	char leave_time[32];
	char arrive_time[32];
 
	memset(born_time,0x00,sizeof(born_time));
	memset(store_time,0x00,sizeof(store_time));
	memset(leave_time,0x00,sizeof(leave_time));
	memset(arrive_time,0x00,sizeof(arrive_time));

	WEMQJSON *jsonDecoder = NULL;
	WEMQJSON *jsonExtField = NULL;	
	WEMQJSON *property  = json_tokener_parse(pStMsg->sysHeader.cProperty);
	if(NULL != property)
	{
		if (json_object_object_get_ex(property, MSG_BODY_PROPERTY_BORN_TIME_STR, &jsonDecoder)) {
			const char *tmpTime = json_object_get_string(jsonDecoder);
		snprintf(born_time, sizeof(born_time), "%s", tmpTime);
		}else{
			LOGRMB(RMB_LOG_ERROR, "In property, %s is null!", MSG_BODY_PROPERTY_BORN_TIME_STR);
		}

		if (json_object_object_get_ex(property, MSG_BODY_PROPERTY_STORE_TIME_STR, &jsonDecoder)) {
			const char *tmpTime = json_object_get_string(jsonDecoder);
		snprintf(store_time, sizeof(store_time), "%s", tmpTime);
		}else{
			LOGRMB(RMB_LOG_ERROR, "In property, %s is null!", MSG_BODY_PROPERTY_STORE_TIME_STR);
		}

		if (json_object_object_get_ex(property, MSG_BODY_PROPERTY_LEAVE_TIME_STR, &jsonDecoder)) {
			const char *tmpTime = json_object_get_string(jsonDecoder);
		snprintf(leave_time, sizeof(leave_time), "%s", tmpTime);
		}else{
			LOGRMB(RMB_LOG_ERROR, "In property, %s is null!", MSG_BODY_PROPERTY_LEAVE_TIME_STR);
		}

		if (json_object_object_get_ex(property, MSG_BODY_PROPERTY_ARRIVE_TIME_STR, &jsonDecoder)) {
			const char *tmpTime = json_object_get_string(jsonDecoder);
		snprintf(arrive_time, sizeof(arrive_time), "%s", tmpTime);
		}else{
			LOGRMB(RMB_LOG_ERROR, "In property, %s is null!", MSG_BODY_PROPERTY_ARRIVE_TIME_STR);
		}		
	}
	jsonExtField = json_tokener_parse(pStMsg->sysHeader.cExtFields);
	if(jsonExtField == NULL){
		jsonExtField = json_object_new_object();
	}

	if(strcmp(command, REQUEST_TO_CLIENT) == 0 || strcmp(command, ASYNC_MESSAGE_TO_CLIENT) == 0 || strcmp(command, BROADCAST_MESSAGE_TO_CLIENT) == 0){
		json_object_object_add(jsonExtField, REQ_BORN_TIMESTAMP,  json_object_new_string(born_time));
		json_object_object_add(jsonExtField, REQ_STORE_TIMESTAMP, json_object_new_string(store_time));
		json_object_object_add(jsonExtField, REQ_LEAVE_TIMESTAMP, json_object_new_string(leave_time));
		json_object_object_add(jsonExtField, REQ_ARRIVE_TIMESTAMP, json_object_new_string(arrive_time));
		json_object_object_add(jsonExtField, MSG_BODY_SYSTEM_ACK_SEQ, json_object_new_int(iSeq));
	}
	else if(strcmp(command, RESPONSE_TO_CLIENT) == 0){
		json_object_object_add(jsonExtField, RSP_BORN_TIMESTAMP,  json_object_new_string(born_time));
		json_object_object_add(jsonExtField, RSP_STORE_TIMESTAMP, json_object_new_string(store_time));
		json_object_object_add(jsonExtField, RSP_LEAVE_TIMESTAMP, json_object_new_string(leave_time));
		json_object_object_add(jsonExtField, RSP_ARRIVE_TIMESTAMP, json_object_new_string(arrive_time));
	}

	const char* extFields = json_object_get_string(jsonExtField);	
	if (extFields != NULL) {
		int len = (int)strlen(extFields);
		if (len >= RMB_SYSTEMHEADER_EXTFIELDS_MAX_LEN) {
			LOGRMB(RMB_LOG_ERROR, "systemHeader len=%d too large!max_limit=%d: %s", len, RMB_SYSTEMHEADER_EXTFIELDS_MAX_LEN, extFields);
		}else{
    		snprintf(pStMsg->sysHeader.cExtFields, sizeof(pStMsg->sysHeader.cExtFields), "%s", extFields);
		}
	}
	json_object_put(property);	
	json_object_put(jsonDecoder);	
	json_object_put(jsonExtField);
	return 0;
}


static WEMQJSON* generate_destination_from_topic(StRmbMsg *pStMsg,char* pTopic)
{
			
	char cDestName[200];
	char strTargetDcn[10];
	char strServiceId[10];
	char strScenarioId[5];

	//判断分隔符数量是否正确
	int i = 0;
	char* ptopic = pTopic;
	char* pTemp = NULL;
	do {
		pTemp = strchr(ptopic, '-');
		if (pTemp == NULL) 
		{
			break;
		}
		i++;
		ptopic = pTemp+1;
	} while (ptopic != NULL);

	if (i != 4) 
	{
		if(strstr(pTopic, "reply-topic") == NULL){
		    LOGRMB(RMB_LOG_WARN, "topic:%s  formatting is not conformed!",pTopic);
		    return NULL;
		}
	}

	WEMQJSON *jsonDest = json_object_new_object();
	if (jsonDest == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_object_new_object failed");
		return NULL;
	}	

	memset(cDestName,0x00,sizeof(cDestName));
	snprintf(cDestName, sizeof(pStMsg->dest.cDestName), "%s",pTopic);


    for (i = 0; i < strlen(cDestName) && cDestName[i] != '\0'; i++) {
         if (cDestName[i] == '-') {
                 cDestName[i] = '/';
         }
    }
	json_object_object_add(jsonDest, MSG_BODY_DEST_NAME_STR, json_object_new_string(cDestName));
	
	pTemp = strrchr(cDestName, '/');
	*pTemp='\0';
	//copy ScenarioId
	pTemp = strrchr(cDestName, '/');
	if (pTemp == NULL)
	{
	    return NULL;
	}
	snprintf(strScenarioId, sizeof(strScenarioId),"%s",pTemp+1);
	*pTemp = '\0';
		
	//copy ServiceId
	pTemp = strrchr(cDestName, '/');
	if (pTemp == NULL)
	{
	   return NULL;
	}
	snprintf(strServiceId, sizeof(strServiceId),"%s",pTemp+1);
	*pTemp = '\0';
		
	pTemp = strrchr(cDestName, '/');
	if (pTemp == NULL)
	{
	    return NULL;
	}
	*pTemp = '\0';
	//copy TargetDcn
	snprintf(strTargetDcn, sizeof(strTargetDcn),"%s",cDestName);
	

	json_object_object_add(jsonDest, MSG_BODY_DEST_TYPE_STR, json_object_new_string("se"));
	json_object_object_add(jsonDest, MSG_BODY_DEST_SORE_STR, json_object_new_string(strServiceId));
	json_object_object_add(jsonDest, MSG_BODY_DEST_SCENARIO_STR, json_object_new_string(strScenarioId));
	json_object_object_add(jsonDest, MSG_BODY_DEST_DCN_STR, json_object_new_string(strTargetDcn));
	json_object_object_add(jsonDest, MSG_BODY_DEST_ORGID_STR, json_object_new_string(pStMsg->sysHeader.cOrgId));

	return jsonDest;
}


int trans_json_2_rmb_msg(StRmbMsg *pStMsg, const char *bodyJson, const char *command)
{
	if (pStMsg == NULL || bodyJson == NULL) {
		LOGRMB(RMB_LOG_ERROR, "pStMsg or bodyJson is null");
		return -1;
	}

	rmb_msg_clear(pStMsg);

	pStMsg->cApiType = C_TYPE_WEMQ;

	WEMQJSON *systemHeader = NULL;
	WEMQJSON *dest = NULL;
	WEMQJSON *jsonDecoder = NULL;
	WEMQJSON *property = NULL;
	WEMQJSON *jsonByteBody = NULL;

	WEMQJSON *jsonBody = json_tokener_parse(bodyJson);
	if (jsonBody == NULL) {
		LOGRMB(RMB_LOG_ERROR, "json_tokener_parse failed!,buf is:%s", bodyJson);
		return -1;
	}
	if (!json_object_object_get_ex(jsonBody, MSG_BODY_BYTE_BODY_JSON, &jsonByteBody)) {
		LOGRMB(RMB_LOG_ERROR, "body json no byte body!");
		return -2;
	}//byte body json

	if (!json_object_object_get_ex(jsonBody, MSG_BODY_PROPERTY_JSON, &property)) {
		LOGRMB(RMB_LOG_ERROR, "body json no properties!");
		return -2;
	}//property json
    jsonByteBody = json_tokener_parse(json_object_get_string(jsonByteBody));
	if (!json_object_object_get_ex(jsonByteBody, MSG_BODY_BYTE_BODY_SYSTEM_HEADER_CONTENT_JSON, &systemHeader)) {
		LOGRMB(RMB_LOG_ERROR, "byte body json no system header content!");
		return -2;
	}//system header json
    systemHeader = json_tokener_parse(json_object_get_string(systemHeader));
	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_BIZ_STR, &jsonDecoder)) {
		const char *bizNo = json_object_get_string(jsonDecoder);
		if (bizNo != NULL) {
			snprintf(pStMsg->sysHeader.cBizSeqNo, sizeof(pStMsg->sysHeader.cBizSeqNo), "%s", bizNo);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_BIZ_STR);
		}
	}//system header bizno

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_SEQNO_STR, &jsonDecoder)) {
		const char *seqNo = json_object_get_string(jsonDecoder);
		if (seqNo != NULL) {
			snprintf(pStMsg->sysHeader.cConsumerSeqNo, sizeof(pStMsg->sysHeader.cConsumerSeqNo), "%s", seqNo);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_SEQNO_STR);
		}
	}//system header seqNo

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_SVRID_STR, &jsonDecoder)) {
		const char *svrId = json_object_get_string(jsonDecoder);
		if (svrId != NULL) {
			snprintf(pStMsg->sysHeader.cConsumerSvrId, sizeof(pStMsg->sysHeader.cConsumerSvrId), "%s", svrId);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_SVRID_STR);
		}
	}//system header svrId
    

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_ORGSYS_STR, &jsonDecoder)) {
		const char *orgId = json_object_get_string(jsonDecoder);
		if (orgId != NULL) {
			snprintf(pStMsg->sysHeader.cOrgSysId, sizeof(pStMsg->sysHeader.cOrgSysId), "%s", orgId);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_ORGSYS_STR);
		}
	}//system header orgId

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_CSMID_STR, &jsonDecoder)) {
		const char *csmId = json_object_get_string(jsonDecoder);
		if (csmId != NULL) {
			snprintf(pStMsg->sysHeader.cConsumerSysId, sizeof(pStMsg->sysHeader.cConsumerSysId), "%s", csmId);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_CSMID_STR);
		}
	}//system header csmId

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_TIME_LINT, &jsonDecoder)) {
		pStMsg->sysHeader.ulTranTimeStamp = json_object_get_int64(jsonDecoder);
	}//system header transTime

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_CSMDCN_STR, &jsonDecoder)) {
		const char *csmDcn = json_object_get_string(jsonDecoder);
		if (csmDcn != NULL) {
			snprintf(pStMsg->sysHeader.cConsumerDcn, sizeof(pStMsg->sysHeader.cConsumerDcn), "%s", csmDcn);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_CSMDCN_STR);
		}
	}//system header csmDcn

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_ORGSVR_STR, &jsonDecoder)) {
		const char *orgSysId = json_object_get_string(jsonDecoder);
		if (orgSysId != NULL) {
			snprintf(pStMsg->sysHeader.cOrgSvrId, sizeof(pStMsg->sysHeader.cOrgSvrId), "%s", orgSysId);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_ORGSVR_STR);
		}
	}//system header orgSysId

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_ORGID_STR, &jsonDecoder)) {
		const char *cOrgId = json_object_get_string(jsonDecoder);
		if (cOrgId != NULL) {
			snprintf(pStMsg->sysHeader.cOrgId, sizeof(pStMsg->sysHeader.cOrgId), "%s", cOrgId);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_ORGID_STR);
		}
	}//system header cOrgId

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_UNIID_STR, &jsonDecoder)) {
		const char *cUniqueId = json_object_get_string(jsonDecoder);
		if (cUniqueId != NULL) {
			snprintf(pStMsg->sysHeader.cUniqueId, sizeof(pStMsg->sysHeader.cUniqueId), "%s", cUniqueId);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_UNIID_STR);
		}
	}//system header cUniqueId

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_CONLEN_INT, &jsonDecoder)) {
		pStMsg->sysHeader.iContentLength = json_object_get_int(jsonDecoder);
	}//system header iContentLength

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_SENDTIME_LINT, &jsonDecoder)) {
		pStMsg->sysHeader.ulSendTime = json_object_get_int64(jsonDecoder);
	}//system header ulSendTime

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_RECVTIME_LINT, &jsonDecoder)) {
		pStMsg->sysHeader.ulReceiveTime = json_object_get_int64(jsonDecoder);
	}
	if (pStMsg->sysHeader.ulReceiveTime == 0) {
		GetRmbNowLongTime();
		pStMsg->sysHeader.ulReceiveTime = pRmbStConfig->ulNowTtime;
	}
	//system header ulReceiveTime

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_REPLYTIME_LINT, &jsonDecoder)) {
		pStMsg->sysHeader.ulReplyTime = json_object_get_int64(jsonDecoder);
	}//system header ulReplyTime

	if (json_object_object_get_ex(jsonByteBody, MSG_BODY_BYTE_BODY_APPHEADER_NAME_STR, &jsonDecoder)) {
		const char *pAppheaderClass = json_object_get_string(jsonDecoder);
		if (pAppheaderClass != NULL) {
			snprintf(pStMsg->sysHeader.cAppHeaderClass, sizeof(pStMsg->sysHeader.cAppHeaderClass), "%s", pAppheaderClass);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_BYTE_BODY_APPHEADER_NAME_STR);
		}
	}// system header cAppHeaderClass

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_VER_STR, &jsonDecoder)) {
		const char *pConsumerSysVersion = json_object_get_string(jsonDecoder);
		if (pConsumerSysVersion != NULL) {
			snprintf(pStMsg->sysHeader.cConsumerSysVersion, sizeof(pStMsg->sysHeader.cConsumerSysVersion), "%s", pConsumerSysVersion);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_VER_STR);
		}
	}
	//system consumerSysVersion
	
	const char *cProperty = json_object_get_string(property);
	if(cProperty != NULL){
		int len = (int)strlen(property);
		if (len >= RMB_SYSTEMHEADER_PROPERTY_MAX_LEN) {
				LOGRMB(RMB_LOG_ERROR, "property len=%d too large!max_limit=%d: %s", len, RMB_SYSTEMHEADER_PROPERTY_MAX_LEN, cProperty);
			}
		else{
		    snprintf(pStMsg->sysHeader.cProperty, sizeof(pStMsg->sysHeader.cProperty), "%s", cProperty);
		}
	}
	else{
		LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_PROPERTY_JSON);
	}

	if (json_object_object_get_ex(systemHeader, MSG_BODY_SYSTEM_EXTFIELDS_STR, &jsonDecoder)) {
	    	const char *extFields = json_object_get_string(jsonDecoder);
		if (extFields != NULL) {
			int len = (int)strlen(extFields);
		    	if (len >= RMB_SYSTEMHEADER_EXTFIELDS_MAX_LEN) {
		    		LOGRMB(RMB_LOG_ERROR, "systemHeader len=%d too large!max_limit=%d: %s", len, RMB_SYSTEMHEADER_EXTFIELDS_MAX_LEN, extFields);
			}else{
		    		snprintf(pStMsg->sysHeader.cExtFields, sizeof(pStMsg->sysHeader.cExtFields), "%s", extFields);
			}
			if(strstr(extFields,"\"IS_DYED_MSG\": \"true\"")!=NULL)
			{
				LOGRMB(RMB_LOG_DEBUG, "trans msg, extfield is %s", extFields);
				strcpy(pStMsg->isDyedMsg,"true");
			}
			else
			{
				strcpy(pStMsg->isDyedMsg,"false");
			}
	   	}else{
	 	    LOGRMB(RMB_LOG_ERROR, "In systemHeader, %s is null!", MSG_BODY_SYSTEM_EXTFIELDS_STR);
	   	}
	    
	}//system header extFields

	if (json_object_object_get_ex(jsonByteBody, MSG_BODY_BYTE_BODY_APPHEADER_CONTENT_JSON, &jsonDecoder)) {
		const char *appHeader = json_object_get_string(jsonDecoder);
		if (appHeader != NULL) {
			//get appHeader len
			unsigned int uiAppHeaderLen = json_object_get_string_len(jsonDecoder);
			if (uiAppHeaderLen >= pStMsg->iMallocAppHeaderLength) {
				int iFitSize = rmb_get_fit_size(uiAppHeaderLen, MAX_APPHEADER_LENGTH);
				if (iFitSize < 0) {
					LOGRMB(RMB_LOG_ERROR, "appHeader len=%d too large!max_limit=%u", uiAppHeaderLen, MAX_APPHEADER_LENGTH);
					return -1;
				}
				free(pStMsg->cAppHeader);
				pStMsg->cAppHeader = NULL;
				pStMsg->cAppHeader = (char *)malloc(iFitSize);
				pStMsg->iMallocAppHeaderLength = iFitSize;
			}
			pStMsg->iAppHeaderLen = uiAppHeaderLen;
			memcpy(pStMsg->cAppHeader, json_object_get_string(jsonDecoder), uiAppHeaderLen);
			pStMsg->cAppHeader[pStMsg->iAppHeaderLen] = '\0';
		} else {
			LOGRMB(RMB_LOG_ERROR, "In body, %s is null!", MSG_BODY_APP_JSON);
		}
	}//appHeader

    
	if (json_object_object_get_ex(property, MSG_BODY_PROPERTY_TTL_INT, &jsonDecoder)) {
		pStMsg->ulMsgLiveTime = json_object_get_int64(jsonDecoder);
	}//header ulMsgLiveTime

	//if (json_object_object_get_ex(jsonBody, MSG_BODY_TOPIC_STR, &jsonDecoder)) 
	//{
	//	const char *topic = json_object_get_string(jsonDecoder);            
       		 
	//	dest = generate_destination_from_topic(pStMsg,topic);
		if (json_object_object_get_ex(jsonByteBody, MSG_BODY_DEST_JSON, &dest)) {
			dest = json_tokener_parse(json_object_get_string(dest));
		}
		else{
			if (json_object_object_get_ex(jsonBody, MSG_BODY_TOPIC_STR, &jsonDecoder)) {
				const char *topic = json_object_get_string(jsonDecoder);    
				dest = generate_destination_from_topic(pStMsg,topic);
			}
	
		}
		if(NULL != dest)
		{
			if (json_object_object_get_ex(dest, MSG_BODY_DEST_NAME_STR, &jsonDecoder)) 
			{
				//get name
				const char *destTmp = json_object_get_string(jsonDecoder);
				if (destTmp != NULL) {
					snprintf(pStMsg->dest.cDestName, sizeof(pStMsg->dest.cDestName), "%s", destTmp);
				} else {
					LOGRMB(RMB_LOG_ERROR, "In destination, %s is null", MSG_BODY_DEST_NAME_STR);
				}
			}//get name

			if (json_object_object_get_ex(dest, MSG_BODY_DEST_SORE_STR, &jsonDecoder)) {
				const char *pServiceId = json_object_get_string(jsonDecoder);
				if (pServiceId != NULL) {
					snprintf(pStMsg->strServiceId, sizeof(pStMsg->strServiceId), "%s", pServiceId);
					pStMsg->iEventOrService = (*(pServiceId + 3) == '0') ? RMB_SERVICE_CALL : RMB_EVENT_CALL;
				} else {
					LOGRMB(RMB_LOG_ERROR, "In destination, %s is null", MSG_BODY_DEST_SORE_STR);
				}
			}//get serviceOrEventId

			if (json_object_object_get_ex(dest, MSG_BODY_DEST_SCENARIO_STR, &jsonDecoder)) {
				const char *pScenarioId = json_object_get_string(jsonDecoder);
				if (pScenarioId != NULL) {
					snprintf(pStMsg->strScenarioId, sizeof(pStMsg->strScenarioId), "%s", pScenarioId);
				} else {
					LOGRMB(RMB_LOG_ERROR, "In destination, %s is null!", MSG_BODY_DEST_SCENARIO_STR);
				}
			}//get scenario

			if (json_object_object_get_ex(dest, MSG_BODY_DEST_DCN_STR, &jsonDecoder)) {
				const char *pDcn = json_object_get_string(jsonDecoder);
				if (pDcn != NULL) {
					snprintf(pStMsg->strTargetDcn, sizeof(pStMsg->strTargetDcn), "%s", pDcn);
				} else {
					LOGRMB(RMB_LOG_ERROR, "In destination, %s is null!", MSG_BODY_DEST_DCN_STR);
				}
			}//get dcn

			if (json_object_object_get_ex(dest, MSG_BODY_DEST_ORGID_STR, &jsonDecoder)) {
				const char *pOrganization = json_object_get_string(jsonDecoder);
				if (pOrganization != NULL) {
					snprintf(pStMsg->strTargetOrgId, sizeof(pStMsg->strTargetOrgId), "%s", pOrganization);
				} else {
					LOGRMB(RMB_LOG_ERROR, "In destination, %s is null", MSG_BODY_DEST_ORGID_STR);
				}
			}//get organization
			//header dest
		}
	//topic
	
	if (json_object_object_get_ex(property, MSG_BODY_PROPERTY_REPLYTO_STR, &jsonDecoder)) {
		const char *replyTo = json_object_get_string(jsonDecoder);
		if (replyTo != NULL) {
			snprintf(pStMsg->replyTo.cDestName, sizeof(pStMsg->replyTo.cDestName), "%s", replyTo);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In bodyjson, %s is null", MSG_BODY_REPLYTO_STR);
		}
	}//header replyTo

	if (json_object_object_get_ex(jsonByteBody, MSG_BODY_BYTE_BODY_CONTENT_STR, &jsonDecoder)) {
		const char *cContent = json_object_get_string(jsonDecoder);
		if (cContent != NULL) {
//			pStMsg->iContentLen = strlen(cContent);
			pStMsg->iContentLen = json_object_get_string_len(jsonDecoder);
			LOGRMB(RMB_LOG_DEBUG, "get content len:%d", pStMsg->iContentLen);
			if (pStMsg->iContentLen >= pStMsg->iMallocContentLength) {
				int iFitSize = rmb_get_fit_size(pStMsg->iContentLen, MAX_MSG_CONTENT_SIZE);
				if (iFitSize < 0) {
					LOGRMB(RMB_LOG_ERROR, "content len=%d too large!max_limit=%u", pStMsg->iContentLen, MAX_MSG_CONTENT_SIZE);
					return -1;
				}
				free(pStMsg->cContent);
				pStMsg->cContent = NULL;
				pStMsg->cContent = (char *)malloc(iFitSize);
				pStMsg->iMallocContentLength = iFitSize;
			}
			//strncpy(pStMsg->cContent, cContent, pStMsg->iContentLen);
			memcpy(pStMsg->cContent, cContent, pStMsg->iContentLen);
			pStMsg->cContent[pStMsg->iContentLen] = '\0';
			LOGRMB(RMB_LOG_DEBUG, "get content:%d - %s", pStMsg->iContentLen, pStMsg->cContent);
		} else {
			LOGRMB(RMB_LOG_ERROR, "In bodyjson, %s is null", MSG_BODY_CONTENT_STR);
		}
	}//header cContent

	json_object_put(jsonDecoder);
	json_object_put(dest);
	json_object_put(systemHeader);
	json_object_put(jsonBody);
	json_object_put(jsonByteBody);
	json_object_put(property);

	return 0;
}

int rmb_msg_print_v(StRmbMsg* pRmbMsg)
{
	LOGRMB(RMB_LOG_DEBUG,"[type=%s,send=%lu,rev=%lu,dest=%s,bizSeqNo=%s,cSeqNo=%s,replyTo=%s,len=%d,content=%s]",
			(int)pRmbMsg->cLogicType<=6 ? strLogicType[(int)(pRmbMsg->cLogicType)]:"null",
                        pRmbMsg->sysHeader.ulSendTime,
                        pRmbMsg->sysHeader.ulReceiveTime,
                        pRmbMsg->dest.cDestName,
                        pRmbMsg->sysHeader.cBizSeqNo,
                        pRmbMsg->sysHeader.cConsumerSeqNo,
                        pRmbMsg->replyTo.cDestName,
                        pRmbMsg->iContentLen,
                        pRmbMsg->cContent);
	return 0;
}


const char* rmb_msg_print(StRmbMsg* pRmbMsg)
{
	if (pRmbMsg->cLogicType == REQ_PKG_IN || pRmbMsg->cLogicType == EVENT_PKG_IN )
	{
		snprintf(pRmbMsg->strLogBuf, sizeof(pRmbMsg->strLogBuf)-1, "[type=%s,send=%lu,rev=%lu,dest=%s,bizSeqNo=%s,cSeqNo=%s,replyTo=%s,len=%d,content=%s]",
			(int)pRmbMsg->cLogicType<=6 ? strLogicType[(int)(pRmbMsg->cLogicType)]:"null",
			pRmbMsg->sysHeader.ulSendTime,
			pRmbMsg->sysHeader.ulReceiveTime,
			pRmbMsg->dest.cDestName,
			pRmbMsg->sysHeader.cBizSeqNo,
			pRmbMsg->sysHeader.cConsumerSeqNo,
			pRmbMsg->replyTo.cDestName,
			pRmbMsg->iContentLen,
			pRmbMsg->cContent
			);
	}
	else if (pRmbMsg->cLogicType == RSP_PKG_IN || pRmbMsg->cLogicType == RSP_PKG_OUT)
	{
		snprintf(pRmbMsg->strLogBuf, sizeof(pRmbMsg->strLogBuf)-1, "[type=%s,send=%lu,rev=%lu,reply=%lu,dest=%s,bizSeqNo=%s,cSeqNo=%s,replyTo=%s,len=%d,content=%s]",
			(int)pRmbMsg->cLogicType <= 6 ? strLogicType[(int)(pRmbMsg->cLogicType)] : "null",
			pRmbMsg->sysHeader.ulSendTime,
			pRmbMsg->sysHeader.ulReceiveTime,
			pRmbMsg->sysHeader.ulReplyTime,
			pRmbMsg->dest.cDestName,
			pRmbMsg->sysHeader.cBizSeqNo,
			pRmbMsg->sysHeader.cConsumerSeqNo,
			pRmbMsg->replyTo.cDestName,
			pRmbMsg->iContentLen,
			pRmbMsg->cContent
			);
	}
	else
	{
		snprintf(pRmbMsg->strLogBuf, sizeof(pRmbMsg->strLogBuf)-1, "[type=%s,send=%lu,dest=%s,bizSeqNo=%s,cSeqNo=%s,replyTo=%s,len=%d,content=%s]",
			(int)pRmbMsg->cLogicType <= 6 ? strLogicType[(int)(pRmbMsg->cLogicType)] : "null",
			pRmbMsg->sysHeader.ulSendTime,
			pRmbMsg->dest.cDestName,
			pRmbMsg->sysHeader.cBizSeqNo,
			pRmbMsg->sysHeader.cConsumerSeqNo,
			pRmbMsg->replyTo.cDestName,
			pRmbMsg->iContentLen,
			pRmbMsg->cContent
			);
	}

	pRmbMsg->strLogBuf[sizeof(pRmbMsg->strLogBuf)-1] = 0;
	return pRmbMsg->strLogBuf;
}

//消息初始化
StRmbMsg* rmb_msg_malloc()
{
	StRmbMsg* rmbMsg = (StRmbMsg *)malloc(sizeof(StRmbMsg));
	if (rmbMsg == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for rmbMsg error!\n");
		return NULL;
	}
	memset(rmbMsg, 0, sizeof(StRmbMsg));

	rmbMsg->cContent = (char*)malloc(i1KB);
	if (rmbMsg->cContent == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for rmbMsg->cContent error!\n");
		free(rmbMsg);
		rmbMsg = NULL;
		return NULL;
	}
	memset(rmbMsg->cContent, 0x00, sizeof(char)*i1KB);
	rmbMsg->iMallocContentLength = i1KB;

	rmbMsg->cAppHeader = (char*)malloc(i1KB);
	if (rmbMsg->cAppHeader == NULL) {
		LOGRMB(RMB_LOG_ERROR, "malloc for rmbMsg->cAppHeader error!\n");
		free(rmbMsg->cContent);
		rmbMsg->cContent = NULL;
		free(rmbMsg);
		rmbMsg = NULL;
		return NULL;
	}
	memset(rmbMsg->cAppHeader, 0x00, sizeof(char)*i1KB);
	rmbMsg->iMallocAppHeaderLength = i1KB;

	return rmbMsg;
}

//消息释放
int rmb_msg_free(StRmbMsg* pRmbMsg)
{
	if (pRmbMsg == NULL)
	{
		return 0;
	}
	if (pRmbMsg->cContent != NULL && pRmbMsg->iMallocContentLength != 0)
	{
		free(pRmbMsg->cContent);
		pRmbMsg->cContent = NULL;
	}
	if (pRmbMsg->cAppHeader != NULL && pRmbMsg->iMallocAppHeaderLength != 0)
	{
		free(pRmbMsg->cAppHeader);
		pRmbMsg->cAppHeader = NULL;
	}

	free(pRmbMsg);
	return 0;
}




int rmb_msg_random_uuid(char* puuid, size_t size)
{
	char cUniqueIdEx[50] = {0};
	int iRet = solClient_generateUUIDString(cUniqueIdEx, sizeof(cUniqueIdEx));
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, " solClient_generateUUIDString failed!iRet=%d", iRet);
		return -1;
	}
	snprintf(puuid, size, "%s", cUniqueIdEx);
	return 0;
}
