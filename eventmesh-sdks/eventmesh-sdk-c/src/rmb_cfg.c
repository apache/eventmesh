#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "rmb_log.h"
#include "rmb_udp.h"
#include "rmb_cfg.h"
#include <pthread.h>  
#include <unistd.h>
#include <signal.h>
#include <fcntl.h>  

#include "rmb_access_config.h"

StRmbConfig *pRmbStConfig;

int rmb_reload_config();

static char *rmb_get_val(char *desc, char *src)
{
    char *descp = desc, *srcp = src;
    int mtime = 0, space = 0;

    while(mtime != 2 && *srcp != '\0')
    {
        switch (*srcp)
        {
            case ' ':
            case '\t':
            case '\0':
            case '\n':
            case US:
                space = 1;
                srcp++;
                break;
            default:
                if(space || srcp == src)
                    mtime++;
                space = 0;
                if(mtime == 2)
                    break;
                *descp = *srcp;
                descp++;
                srcp++;
        }
    }
    *descp = '\0';
    strcpy(src, srcp);
    return desc;
}

static void rmb_InitDefault(va_list ap)
{
    char *sParam, *sVal, *sDefault;
    double *pdVal, dDefault;
    long *plVal, lDefault;
    int iType, *piVal, iDefault;
    long lSize;
	short *pwVal= NULL ;
	short wDefault ;

    sParam = va_arg(ap, char *);

    while(sParam != NULL)
    {
        iType = va_arg(ap, int);

        switch (iType)
        {
            case CFG_LINE:
                sVal = va_arg(ap, char *);
                sDefault = va_arg(ap, char *);
                lSize = va_arg(ap, long);

                strncpy(sVal, sDefault, (int) lSize - 1);
                sVal[lSize - 1] = 0;
                //snprintf(sVal,lSize,"%s",sDefault);
                break;
            case CFG_STRING:
                sVal = va_arg(ap, char *);
                sDefault = va_arg(ap, char *);
                lSize = va_arg(ap, long);

                strncpy(sVal, sDefault, (int) lSize - 1);
                sVal[lSize - 1] = 0;
                //snprintf(sVal,lSize,"%s",sDefault);
                break;
            case CFG_LONG:
                plVal = va_arg(ap, long *);
                lDefault = va_arg(ap, long);

                *plVal = lDefault;
                break;
            case CFG_INT:
                piVal = va_arg(ap, int *);
                iDefault = va_arg(ap, int);

                *piVal = iDefault;
                break;
            case CFG_SHORT:
                pwVal = va_arg(ap, short *);
                wDefault = va_arg(ap, int);
                *pwVal = wDefault;
                break;
            case CFG_DOUBLE:
                pdVal = va_arg(ap, double *);
                dDefault = va_arg(ap, double);

                *pdVal = dDefault;
                break;
        }
        sParam = va_arg(ap, char *);
    }
}

static void rmb_SetVal(va_list ap, const char *sP, char *sV)
{
    char *sParam = NULL, *sVal = NULL, *sDefault = NULL;
    double *pdVal = NULL, dDefault;
    long *plVal = NULL, lDefault;
    int iType, *piVal = NULL, iDefault;
    long lSize = 0;
    char sLine[MAX_CONFIG_LINE_LEN + 1], sLine1[MAX_CONFIG_LINE_LEN + 1];
	short *pwVal= NULL ;
	short wDefault ;


    strcpy(sLine, sV);
    strcpy(sLine1, sV);
    rmb_get_val(sV, sLine1);
    sParam = va_arg(ap, char *);

    while(sParam != NULL)
    {
        iType = va_arg(ap, int);

        switch (iType)
        {
            case CFG_LINE:
                sVal = va_arg(ap, char *);
                sDefault = va_arg(ap, char *);
                lSize = va_arg(ap, long);

                if(strcmp(sP, sParam) == 0)
                {
                    strncpy(sVal, sLine, (int) lSize - 1);
                    sVal[lSize - 1] = 0;
                    //snprintf(sVal,lSize,"%s",sLine);
                }
                break;
            case CFG_STRING:
                sVal = va_arg(ap, char *);
                sDefault = va_arg(ap, char *);
                lSize = va_arg(ap, long);

                break;
            case CFG_LONG:
                plVal = va_arg(ap, long *);
                lDefault = va_arg(ap, long);

                /*
                   if (strcmp(sP, sParam) == 0)
                   {
                   *plVal = atol(sV);
                   }
                 */
                break;
            case CFG_INT:
                piVal = va_arg(ap, int *);
                iDefault = va_arg(ap, int);


                /*
                   if (strcmp(sP, sParam) == 0)
                   {
                   *piVal = iDefault;
                   }
                 */
                break;
            case CFG_SHORT:
                pwVal = va_arg(ap, short *);
                wDefault = va_arg(ap, int);
				break ;
            case CFG_DOUBLE:
                pdVal = va_arg(ap, double *);
                dDefault = va_arg(ap, double);

                *pdVal = dDefault;
                break;
        }

        if(strcmp(sP, sParam) == 0)
        {
            switch (iType)
            {
                case CFG_STRING:
                    strncpy(sVal, sV, (int) lSize - 1);
                    sVal[lSize - 1] = 0;
                    break;
                case CFG_LONG:
                    //*plVal = atol(sV);
                    *plVal = strtoul(sV,NULL,0);
                    break;
                case CFG_INT:
                    //*piVal = atoi(sV);
                    *piVal = strtoul(sV,NULL,0);
                    break;
                case CFG_SHORT:
                    *pwVal = strtoul(sV,NULL,0);
                    break;
                case CFG_DOUBLE:
                    *pdVal = atof(sV);
                    break;
            }

            return;
        }

        sParam = va_arg(ap, char *);
    }
}

static int rmb_GetParamVal(char *sLine, char *sParam, char *sVal)
{

    rmb_get_val(sParam, sLine);
    strcpy(sVal, sLine);

    if(sParam[0] == '#')
        return 1;

    return 0;
}

void RMB_TLib_Cfg_GetConfig(char *sConfigFilePath, ...)
{
    FILE *pstFile;
    char sLine[MAX_CONFIG_LINE_LEN + 1], sParam[MAX_CONFIG_LINE_LEN + 1], sVal[MAX_CONFIG_LINE_LEN + 1];
    va_list ap;
	char *pCur = NULL ;

    va_start(ap, sConfigFilePath);
    rmb_InitDefault(ap);
    va_end(ap);

    if((pstFile = fopen(sConfigFilePath, "r")) == NULL)
    {
        // printf("Can't open Config file '%s', ignore.", sConfigFilePath);
        return;
    }

    while(1)
    {
        pCur = fgets(sLine, sizeof(sLine), pstFile);
		if(pCur == NULL )
		    break;

        if(feof(pstFile))
        {
            break;
        }

        if(rmb_GetParamVal(sLine, sParam, sVal) == 0)
        {
            va_start(ap, sConfigFilePath);
            rmb_SetVal(ap, sParam, sVal);
            va_end(ap);
        }
    }

    fclose(pstFile);
}


void rmb_cfg_process_signal(int sigalNo)
{
	switch (sigalNo)
	{
		case  SIGUSR1:
			{
				LOGRMB(RMB_LOG_INFO, "pid=%u receive user signal 1,reload cfg!", pRmbStConfig->uiPid);
				rmb_reload_config();
				LOGRMB(RMB_LOG_INFO, "pid=%u reload cfg succ!", pRmbStConfig->uiPid);
				break;
			}
		case SIGUSR2:
			{
				LOGRMB(RMB_LOG_INFO, "pid=%u receive user signal 2,reload cfg!", pRmbStConfig->uiPid);
				rmb_reload_config();
				LOGRMB(RMB_LOG_INFO, "pid=%u reload cfg succ!", pRmbStConfig->uiPid);
				break;
			}
		default:
			{
				printf("receive signal=%u", sigalNo);
			}
	}
	return;
}


int rmb_reload_config()
{
	Rmb_TLib_Cfg_GetConfig(pRmbStConfig->strConfigFile,
		"consumerSysId", CFG_STRING, pRmbStConfig->cConsumerSysId, "", sizeof(pRmbStConfig->cConsumerSysId),
		"consumerSysVersion", CFG_STRING, pRmbStConfig->cConsumerSysVersion, "", sizeof(pRmbStConfig->cConsumerSysVersion),
		"consumerDcn", CFG_STRING, pRmbStConfig->cConsumerDcn, "", sizeof(pRmbStConfig->cConsumerDcn),

		"logFile", CFG_STRING, pRmbStConfig->logFileName, "../rmb/log", sizeof(pRmbStConfig->logFileName),
		"logLevel", CFG_INT, &(pRmbStConfig->iLogLevel), 5,
		"logFileNums", CFG_INT, &(pRmbStConfig->iLogFileNums), 100,
		"logFileSize", CFG_INT, &(pRmbStConfig->iLogFileSize), 500000000,
		"logSwiftType", CFG_INT, &(pRmbStConfig->iLogShiftType), 1,

		"debugSwitch", CFG_INT, &(pRmbStConfig->iDebugSwitch), 0,

		"everyProcessNumFromMq", CFG_INT, &(pRmbStConfig->iEveryTimeProcessNum), 10,
		"notifyCheckSpan", CFG_INT, &(pRmbStConfig->iNotifyCheckSpan), 1,
		"notifyMergeSwitch", CFG_INT, &(pRmbStConfig->iFLagForMergeNotify), 1,

		"orgId", CFG_STRING, pRmbStConfig->strOrgId, "99996", sizeof(pRmbStConfig->strOrgId),
		"cacheTimeout", CFG_INT, &(pRmbStConfig->iCacheTimeoutTime), 10,
		"cacheSuccTimeout", CFG_INT, &(pRmbStConfig->iCacheSuccTimeoutTime), 600,
		"cacheFailedTimeout", CFG_INT, &(pRmbStConfig->iCacheFailedTimeoutTime), 30,
		"switchForReloadSignal1", CFG_INT, &(pRmbStConfig->iSwitchForSignal1), 1,
		"switchForReloadSignal2", CFG_INT, &(pRmbStConfig->iSwitchForSignal2), 0,

		"ackTimer", CFG_INT, &(pRmbStConfig->ackTimers), 100,
		"ackThreshold", CFG_INT, &(pRmbStConfig->ackThresHold), 30,

		"queryTimeout", CFG_INT, &(pRmbStConfig->iQueryTimeout), 1000,
		"CommonTimeOut", CFG_INT, &(pRmbStConfig->iCommonTimeOut), 500,
		"StatPeriod", CFG_INT, &(pRmbStConfig->iStatPeriod), 300,
		"GetGroupTopicTime", CFG_INT, &(pRmbStConfig->iGetGroupTopicTime), 5,
		"GetSendWhiteListTime", CFG_INT, &(pRmbStConfig->iGetSendWhiteListTime), 30,
		NULL);


	//ackTimer的范围为20到1500
	if (pRmbStConfig->ackTimers > 1500)
		pRmbStConfig->ackTimers = 1500;
	if (pRmbStConfig->ackTimers < 20)
		pRmbStConfig->ackTimers = 20;

	//ackThreshold的范围为1 到75
	if (pRmbStConfig->ackThresHold > 75)
		pRmbStConfig->ackThresHold = 75;
	if (pRmbStConfig->ackThresHold < 1)
		pRmbStConfig->ackThresHold = 1;

	if (pRmbStConfig->iQueryTimeout > 5000)
		pRmbStConfig->iQueryTimeout = 5000;
	if (pRmbStConfig->iQueryTimeout < 500)
		pRmbStConfig->iQueryTimeout = 500;

	if (pRmbStConfig->iSwitchForSignal1)
	{
		signal(SIGUSR1, rmb_cfg_process_signal);
	}

	if (pRmbStConfig->iSwitchForSignal2)
	{
		signal(SIGUSR2, rmb_cfg_process_signal);
	}

	return 0;
}


int rmb_load_config(const char *configPath)
{
	pRmbStConfig = (StRmbConfig*)calloc(1, sizeof(StRmbConfig));
	if (pRmbStConfig == NULL)
	{
		printf("rmb_load_config pRmbStConfig calloc error!");
		return -1;
	}

	init_error();
	snprintf(pRmbStConfig->strConfigFile, sizeof(pRmbStConfig->strConfigFile) - 1, "%s", configPath);
	Rmb_TLib_Cfg_GetConfig(pRmbStConfig->strConfigFile,
		
			"consumerSysId",CFG_STRING, pRmbStConfig->cConsumerSysId,"",sizeof(pRmbStConfig->cConsumerSysId),
			"consumerSysVersion",CFG_STRING, pRmbStConfig->cConsumerSysVersion,"1.0.0",sizeof(pRmbStConfig->cConsumerSysVersion),
			"consumerDcn",CFG_STRING, pRmbStConfig->cConsumerDcn,"",sizeof(pRmbStConfig->cConsumerDcn),

			"logFile", CFG_STRING, pRmbStConfig->logFileName, "../log/rmb.log", sizeof(pRmbStConfig->logFileName),
			"logLevel"  , CFG_INT, &(pRmbStConfig->iLogLevel), 5,
			"logFileNums", CFG_INT, &(pRmbStConfig->iLogFileNums), 100,
			"logFileSize", CFG_INT, &(pRmbStConfig->iLogFileSize), 500000000,
			"logSwiftType", CFG_INT, &(pRmbStConfig->iLogShiftType), 1,

			"debugSwitch", CFG_INT, &(pRmbStConfig->iDebugSwitch), 0,
			
			"everyProcessNumFromMq", CFG_INT, &(pRmbStConfig->iEveryTimeProcessNum), 10,
			"notifyCheckSpan", CFG_INT, &(pRmbStConfig->iNotifyCheckSpan), 1,
			"notifyMergeSwitch", CFG_INT, &(pRmbStConfig->iFLagForMergeNotify), 1,

			"reqFifoPath", CFG_STRING, pRmbStConfig->strFifoPathForReq, "./tmp_req.fifo", sizeof(pRmbStConfig->strFifoPathForReq),
			"reqShmKey", CFG_INT, &(pRmbStConfig->uiShmKeyForReq), 0x20150203,
			"reqShmSize", CFG_INT, &(pRmbStConfig->uiShmSizeForReq), 200000000,
			
			"ayncRspFifoPath", CFG_STRING, pRmbStConfig->strFifoPathForRRrsp, "./tmp_aync_rsp.fifo", sizeof(pRmbStConfig->strFifoPathForReq),
			"ayncRspShmKey", CFG_INT, &(pRmbStConfig->uiShmKeyForRRrsp), 0x20150204,
			"ayncRspShmSize", CFG_INT, &(pRmbStConfig->uiShmSizeForRRrsp), 200000000,
			
			"broadcastFifoPath", CFG_STRING, pRmbStConfig->strFifoPathForBroadcast, "./tmp_broadcast.fifo", sizeof(pRmbStConfig->strFifoPathForReq),
			"broadcastShmKey", CFG_INT, &(pRmbStConfig->uiShmKeyForBroadcast), 0x20150205,
			"broadcastShmSize", CFG_INT, &(pRmbStConfig->uiShmSizeForBroadcast), 200000000,

			"orgId", CFG_STRING, pRmbStConfig->strOrgId, "99996", sizeof(pRmbStConfig->strOrgId),

			"solaceLogFile", CFG_STRING, pRmbStConfig->strSolaceLog, "solace", sizeof(pRmbStConfig->strSolaceLog),
			"solaceLogLevel", CFG_INT, &(pRmbStConfig->iSolaceLogLevel), SOLCLIENT_LOG_DEBUG,
			"solaceLogSwitch", CFG_INT, &(pRmbStConfig->iSwitchForSolaceLog), 0,

			"cacheTimeout", CFG_INT, &(pRmbStConfig->iCacheTimeoutTime), 10,
			"cacheSuccTimeout", CFG_INT, &(pRmbStConfig->iCacheSuccTimeoutTime), 600,
			"cacheFailedTimeout", CFG_INT, &(pRmbStConfig->iCacheFailedTimeoutTime), 30,
			"createConnectionTimeOut", CFG_INT, &(pRmbStConfig->createConnectionTimeOut), 120,

			"switchForReloadSignal1", CFG_INT, &(pRmbStConfig->iSwitchForSignal1), 1,
			"switchForReloadSignal2", CFG_INT, &(pRmbStConfig->iSwitchForSignal2), 0,

			"ackTimer", CFG_INT, &(pRmbStConfig->ackTimers), 100,
			"ackThreshold", CFG_INT, &(pRmbStConfig->ackThresHold), 30,

			"queryTimeout", CFG_INT, &(pRmbStConfig->iQueryTimeout), 1000,
			"CommonTimeOut", CFG_INT, &(pRmbStConfig->iCommonTimeOut), 500,
			"StatPeriod", CFG_INT, &(pRmbStConfig->iStatPeriod), 300,
			"GetGroupTopicTime", CFG_INT, &(pRmbStConfig->iGetGroupTopicTime), 5,

			"logserverSwitch", CFG_INT, &(pRmbStConfig->iLogserverSwitch), 1,
			"logserverForApiSwitch", CFG_INT, &(pRmbStConfig->iApiLogserverSwitch), 1,
			"ReqGslSwitch", CFG_INT, &(pRmbStConfig->iReqGsl), 1,
			"wemqUseHttpCfg", CFG_INT, &(pRmbStConfig->iWemqUseHttpCfg), 1,
			"configCenterIp",CFG_STRING, pRmbStConfig->cConfigIp, "", sizeof(pRmbStConfig->cConfigIp),
			"configCenterPort", CFG_INT, &(pRmbStConfig->iConfigPort), 8091,
			"configCenterAddrMulti", CFG_STRING, pRmbStConfig->ConfigAddr,"",sizeof(pRmbStConfig->ConfigAddr),
			"configCenterTimeout", CFG_INT, &(pRmbStConfig->iConfigTimeout), 10000,
			"mergeQueueSwitch", CFG_INT, &(pRmbStConfig->iMergeQueue), 0,

			"wemq_user", CFG_STRING, pRmbStConfig->cWemqUser, "", sizeof(pRmbStConfig->cWemqUser),
			"wemq_passwd", CFG_STRING, pRmbStConfig->cWemqPasswd, "", sizeof(pRmbStConfig->cWemqPasswd),
			"localIdc",CFG_STRING, pRmbStConfig->cRegion, "", sizeof(pRmbStConfig->cRegion),
			"heartBeatPeriod", CFG_INT, &(pRmbStConfig->heartBeatPeriod), 30,
			"heartBeatTimeout", CFG_INT, &(pRmbStConfig->heartBeatTimeout), 45,
			"getAccessIpPeriod", CFG_INT, &(pRmbStConfig->getAccessIpPeriod), 30,
			"wemqTcpConnectRetryNum", CFG_INT, &(pRmbStConfig->iWemqTcpConnectRetryNum), 1,
			"wemqTcpConnectDelayTime", CFG_INT, &(pRmbStConfig->iWemqTcpConnectDelayTime), 5,
			"wemqTcpConnectTimeout", CFG_INT, &(pRmbStConfig->iWemqTcpConnectTimeout), 3000,
			"wemqTcpSocketTimeout", CFG_INT, &(pRmbStConfig->iWemqTcpSocketTimeout), 5000,
			"wemqProxyIp", CFG_STRING, pRmbStConfig->cWemqProxyIp, "", sizeof(pRmbStConfig->cWemqProxyIp),
			"wemqProxyPort", CFG_INT, &(pRmbStConfig->cWemqProxyPort), 50001,
			"normalTimeout", CFG_INT, &(pRmbStConfig->iNormalTimeout), 120000,
			"exitTimeOut", CFG_INT, &(pRmbStConfig->ulExitTimeOut), 30000,
			"getSendWhiteListTime", CFG_INT, &(pRmbStConfig->iGetSendWhiteListTime), 300,
			"accessAckTimeOut", CFG_INT, &(pRmbStConfig->accessAckTimeOut), 2000,
			"rrAsyncTimeOut", CFG_INT, &(pRmbStConfig->rrAsyncTimeOut), 3,   //默认rr异步3秒超时
			"goodByeTimeOut", CFG_INT, &(pRmbStConfig->goodByeTimeOut), 2000,   //goodbye time 2s
			//"rrAsyncListNum", CFG_INT, &(pRmbStConfig->accessAckTimeOut),10240,
			"mqIsEmpty", CFG_INT, &(pRmbStConfig->mqIsEmpty),MQ_INIT ,
			"departMent",CFG_STRING, pRmbStConfig->strDepartMent, "wemqAccessServer", sizeof(pRmbStConfig->strDepartMent),

			NULL);

	pRmbStConfig->uiPid = getpid();
	snprintf(pRmbStConfig->cRmbMode, sizeof(pRmbStConfig->cRmbMode), "wemq");
	//initialize for log
	InitRmbLogFile(pRmbStConfig->iLogLevel, pRmbStConfig->iLogShiftType, pRmbStConfig->logFileName, pRmbStConfig->iLogFileSize, pRmbStConfig->iLogFileNums);
	//API CONNECT MODE
	//connect to wemq
	pRmbStConfig->iConnWemq = 1;

	//solace api 初始化，用于生成uuid
	pRmbStConfig->uiIsInitSolaceApi = 0;

	int iRet = 0;
	//从配置中心拉取access的ip list,如果配置为指定access的ip,则无须从配置中心拉取
	if ((pRmbStConfig->iWemqUseHttpCfg == 1) && (pRmbStConfig->iConnWemq == 1 || pRmbStConfig->iApiLogserverSwitch == 1 )) {
		char url[512];
		char addrArr[15][50] = {0};
		int lenAddrs = 0;
		pRmbStConfig->configIpPosInArr=0;
		char tmpIps[512] = {0};
		strcpy(tmpIps,pRmbStConfig->ConfigAddr);
		LOGRMB(RMB_LOG_DEBUG, "config center addr str is:%s",tmpIps);
		split_str(tmpIps, addrArr, &lenAddrs);
		LOGRMB(RMB_LOG_DEBUG, "config center addr list len is %d,lists:",lenAddrs);
		int j=0;
		for(j=0;j<lenAddrs;++j) {
			LOGRMB(RMB_LOG_DEBUG, "%s",addrArr[j]);
		}

		if(lenAddrs==0)
		{
			LOGRMB(RMB_LOG_ERROR, "config center addr list len is 0,please check if configCenterAddrMulti is empty in conf file");
			return -1;
		}
		int i = 0;
		for (i = 0; i < lenAddrs; ++i)
		{
			memset(&url,0x00,sizeof(url));
			snprintf(url, sizeof(url), "%s/%s", addrArr[(i+pRmbStConfig->configIpPosInArr)%lenAddrs], WEMQ_ACCESS_SERVER);
			LOGRMB(RMB_LOG_DEBUG, "try to get access addr from %s",url);
			iRet = wemq_proxy_load_servers(url, pRmbStConfig->iConfigTimeout);
			if (iRet != 0) {
				LOGRMB(RMB_LOG_WARN, "get access ip from %s failed,try next config center ip", url);
				continue;
			} else {
				LOGRMB(RMB_LOG_INFO, "get all access ip list result=%d from url:%s", iRet, url);
				pRmbStConfig->configIpPosInArr=pRmbStConfig->configIpPosInArr+i;
				break;
			}
		}
		if(i == lenAddrs)
		{
			LOGRMB(RMB_LOG_ERROR, " no available cc");
			return -1;
		}
	}
	pRmbStConfig->iFlagForLoop = 0;

	//ackTimer的范围为20到1500
	if (pRmbStConfig->ackTimers > 1500)
		pRmbStConfig->ackTimers = 1500;
	if (pRmbStConfig->ackTimers < 20)
		pRmbStConfig->ackTimers = 20;

	//ackThreshold的范围为1 到75
	if (pRmbStConfig->ackThresHold > 75)
		pRmbStConfig->ackThresHold = 75;
	if (pRmbStConfig->ackThresHold < 1)
		pRmbStConfig->ackThresHold = 1;

	if (pRmbStConfig->iQueryTimeout > 5000)
		pRmbStConfig->iQueryTimeout = 5000;
	if (pRmbStConfig->iQueryTimeout < 500)
		pRmbStConfig->iQueryTimeout = 500;

	pRmbStConfig->pLogBuf = NULL;
	pRmbStConfig->pLogBuf = (char*)malloc((size_t)MAX_LOG_BUF_SIZE);
	if (pRmbStConfig->pLogBuf == NULL) {
		printf("malloc for pRmbStConfig->pLogBuf error!");
		return -1;
	}

	iRet = get_host_name(pRmbStConfig->cHostName, sizeof(pRmbStConfig->cHostName));
	if (iRet != 0) {
		LOGRMB(RMB_LOG_ERROR, " get_host_name failed!");
		//exit(1);
	}

//	iRet = get_local_ip(pRmbStConfig->cHostIp, sizeof(pRmbStConfig->cHostIp));
	iRet = get_local_ip_v2(pRmbStConfig->cHostIp, sizeof(pRmbStConfig->cHostIp));
	if (iRet != 0)
	{
		LOGRMB(RMB_LOG_ERROR, "get_local_ip failed!");
		//exit(1);
	}

	LOGRMB(RMB_LOG_INFO, "get_host_name hostname=%s,ip=%s", pRmbStConfig->cHostName, pRmbStConfig->cHostIp);

	//init queue config
	pthread_mutex_init(&pRmbStConfig->configMutex, NULL);  
	pthread_cond_init(&pRmbStConfig->configCond, NULL);
	pRmbStConfig->iFlag = 0;
	GetRmbNowLongTime();
	pRmbStConfig->ulStartTime = pRmbStConfig->ulNowTtime;
	pRmbStConfig->iCacheServiceNums = 0;

	//init mergeq for gsl
	pthread_mutex_init(&pRmbStConfig->mergeqForGslMutex, NULL);
	pthread_cond_init(&pRmbStConfig->mergeqForGslCond, NULL);
	pRmbStConfig->iFlagForMerge = 0;

	//init merge queue mutex
	pthread_mutex_init(&pRmbStConfig->mergeQueueMutex, NULL);

	//init for log
	pthread_mutex_init(&pRmbStConfig->configLog, NULL);

	//pthread_mutex_init(&pRmbStConfig->sendWhiteListMutex, NULL);

	if (pRmbStConfig->iSwitchForSignal1)
	{
		signal(SIGUSR1, rmb_cfg_process_signal);
	}

	if (pRmbStConfig->iSwitchForSignal2)
	{
		signal(SIGUSR2, rmb_cfg_process_signal);
	}

	pRmbStConfig->flag_merge = 0;
	pRmbStConfig->pid_merge = 0;


	return 0;
}


void rmb_get_config_python(RmbPythonConfig* config)
{
	//pythonConfig init
	snprintf(config->strFifoPathForReq, sizeof(config->strFifoPathForReq),pRmbStConfig->strFifoPathForReq);
	config->uiShmKeyForReq = pRmbStConfig->uiShmKeyForReq;
	config->uiShmSizeForReq = pRmbStConfig->uiShmSizeForReq;
	snprintf(config->strFifoPathForRRrsp, sizeof(config->strFifoPathForRRrsp),pRmbStConfig->strFifoPathForRRrsp);
	config->uiShmKeyForRRrsp = pRmbStConfig->uiShmKeyForRRrsp;
	config->uiShmSizeForRRrsp = pRmbStConfig->uiShmSizeForRRrsp;

	snprintf(config->strFifoPathForBroadcast, sizeof(config->strFifoPathForBroadcast),pRmbStConfig->strFifoPathForBroadcast);
	config->uiShmKeyForBroadcast = pRmbStConfig->uiShmKeyForBroadcast;
	config->uiShmSizeForBroadcast = pRmbStConfig->uiShmSizeForBroadcast;
	return;
}


const char* rmb_get_host_ip()
{
	return pRmbStConfig->cHostIp;
}


