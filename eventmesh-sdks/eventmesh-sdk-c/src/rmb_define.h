#ifndef RMB_DEFINE_H_
#define RMB_DEFINE_H_
#ifdef __cplusplus
extern "C" {
#endif

/*
rmb_define.h
RMB基本定义 -- jasonrdzhou
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <unistd.h>
#include <sys/epoll.h>
#include <time.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <pthread.h>  
#include <unistd.h>
//#include "rmb_mq.h"
#include "rmb_log.h"
#include "rmb_errno.h"
#include "solClient.h"
#include "json.h"
#include "wemq_fifo.h"

#include "wemq_topic_list.h"
#include "wemq_proto.h"

#define RMBVERSION "2.1.5"
#define RMBVERSIONFORBIZ "00002140"
static const char rmbVersion[20] = "2.1.5";

#define DEFAULT_MSG_MAX_LIVE_TIME 14400000
#define RR_ASYNC_MSG_MAX_LIVE_TIME 60000
#define DEFAULT_DCN_LENGTH 3
#define DEFAULT_SERVICE_ID_LENGTH 8
#define DEFAULT_SCENE_ID_LENGTH 2


#define MAX_FLOWS_IN_A_SESSION 200				//the max flow numbers in one session
#define MAX_QUEUE_SESSIONS_IN_A_CONTEXT 1		//the max session numbers in one context
//#define MAX_LENTH_IN_A_MSG 2150000				//packages buf for send and receive
#define MAX_LENTH_IN_A_MSG 2625825				//packages buf for send and receive
#define MAX_PROPERTY_NUMS 50					//the max numbers for property
#define MAX_PROPERTY_LENTH 255					//the max length for one property
#define MAX_RMB_CONTEXT  2						//the max numbers of context -- solace threads

#define MAX_SYSHEADER_LENGTH 20
//流控比中每个topic最大的计数
#define MAX_TOPIC_COUNT 1000000  
//#define MAX_APPHEADER_LENGTH 50000
//0.9.12 保持和java一致
//0.9.16，由于c会加\0，所以字节数需要加1
//#define MAX_APPHEADER_LENGTH 525000
//#define MAX_APPHEADER_LENGTH 525001
#define MAX_APPHEADER_LENGTH (1 << 19)
#define MAX_PROPERTY_SIZE 50
#define MAX_RET_MSG_SIZE 512
//0.9.16，由于c会加\0，所以字节数需要加1
//#define MAX_MSG_CONTENT_SIZE 2100000
//#define MAX_MSG_CONTENT_SIZE 2100001
#define MAX_MSG_CONTENT_SIZE (1 << 21)
#define MAX_MSG_ATTACHMENT_SIZE 2048

#define MAX_COMMON_SIZE 20

#define MAX_LOG_BUF_SIZE 20000

#define MAX_SERVICE_STATUS_CACHE_NUMS 1000
#define MAX_GSL_REQ_BUF_SIZE 100
#define MAX_GSL_RSP_BUF_SIZE 100

#define MAX_GSL_RSP_TOPIC_GROUP_SIZE 256

#define GSL_DEFAULT_DCN "GSL"
#define GSL_DEFAULT_SERVICE_ID "12300009"
#define GSL_DEFAULT_SCENE_ID "01"
#define GSL_DEFAULT_COMMON_ORGID "10006"

#define MAX_RMB_WORKER_NUMS 400
#define MAX_RMB_PROXY_NUMS 10

//for config center
#define WEMQ_ACCESS_SERVER "dynamicKey/v1/wemqAccessServer.json"
#define DYNAMIC_KEY_NAME "dynamicKey/v1"


#define RMB_REQ_SINGLE "querySingle"
#define RMB_WHITE_LIST_RCV "whitelist-rcv"
#define RMB_WHITE_LIST_SND "whitelist-snd"
#define WEMQ_PROXY_SERVERS "wemq.proxy.servers"

//#define RMB_WHITE_LIST_RCV_MAX 200
//#define RMB_WHITE_LIST_SND_MAX 200
#define RMB_TOPIC_PER_SYS_MAX 10000
#define MAX_LISTEN_TOPIC_NUM 1000

#define RMB_MAX_NORMAL_SESSIONS_IN_A_CONTEXT_FOR_WEMQ 100	//the max normal session in context only for wemq proxy mode

#define TCP_BUF_SIZE (3<<20)




	enum RmbGslSubcmd
	{
		GSL_SUBCMD_QUERY_SERVICE = 0x1,
		GSL_SUBCMD_NEW_QUERY_SERVICE = 0x11,
		GSL_SUBCMD_QUERY_TOPICGROUP = 0x02,
	};

	enum RmbMsgSource {
		RMB_MSG_FROM_SOLACE = 1,
		RMB_MSG_FROM_WEMQ = 2,
	};

	enum RmbTargetOrgIdCommitType
	{
		RMB_COMMIT_BY_OWN = 0,
		RMB_COMMIT_BY_API = 1,
	};

	enum RmbFlagForMsgInit
	{
		RMBMSG_DEST_HAS_SET = 0,

	};

	enum EVENT_OR_SERVICE_CALL
	{
		RMB_EVENT_CALL = 0,
		RMB_SERVICE_CALL = 1,
	};

	enum RMB_SVR_MODE
	{
		RMB_OLD_MODE = 0,
		RMB_PROXY_WORKER = 1,
	};

	enum RMB_PROXY_MQ_TYPE
	{
		RMB_PROXY_MQ_RECEIVE = 1,
		RMB_PROXY_MQ_SEND = 2,
		RMB_PROXY_MQ_RR = 3,
	};

	enum RMB_LOG_POINT
	{
		RMB_LOG_START_CALL = 0,
		RMB_LOG_ENTRY =1,
		RMB_LOG_EXIT = 2,
		RMB_LOG_END_CALL = 3,
		RMB_LOG_ON_ERROR =4,
		RMB_LOG_OTHER = 5,
	};

	//proxy-worker模式下使用
	typedef struct StMqFifoArg
	{
		char* _fiFoPath;
		unsigned int _shmKey;
		unsigned int _shmSize;
	}StMqFifoArg;

enum RMB_MSG_MODE
{
	RMB_MSG_SOLACE = 0,
	RMB_MSG_WEMQ = 1,
};


enum RMB_RSP_CODE
{
	RMB_CODE_TIME_OUT = -1,
	RMB_CODE_SUSS,
	RMB_CODE_OTHER_FAIL,
	RMB_CODE_AUT_FAIL,
	RMB_CODE_DYED_MSG,
};

//**************************for wemq define********************************
#define MAX_WEMQ_KFIFO_LENGTH (1UL << 16)
#define GETMSGID(buf,msgId) ({\
	char *p = (buf);\
	p += 2 * sizeof(int);\
	p += 3 * sizeof(char);\
	p += sizeof(StSystemHeader);\
	p += 2 * sizeof(StDestination);\
	(msgId) = *((unsigned long*)(p));\
})

// context proxy新增结构
typedef struct json_object WEMQJSON;
typedef struct json_object PROPERTY;
#define IS_DYED_MSG "IS_DYED_MSG"
#define MSG_HEAD_COMMAND_STR "command"
#define MSG_HEAD_SEQ_INT "seq"
#define MSG_HEAD_CODE_INT "code"
#define MSG_HEAD_MSG_STR "msg"

#define MSG_HEAD_TYPE_INT "type"
#define MSG_HEAD_STATUS_INT "status"
#define MSG_HEAD_MSG_STR "msg"
#define MSG_HEAD_TIME_LINT "time"
#define MSG_HEAD_TIMESTAMP_LINT "timestamp"
#define MSG_HEAD_DEST_JSON "dest"
#define MSG_HEAD_DEST_SCENARIO_STR "scenario"
#define MSG_HEAD_DEST_SERVICE_STR "service"
#define MSG_HEAD_DEST_DCN_STR "dcn"
#define MSG_HEAD_DEST_ORGANIZATION_STR "organization"
#define MSG_HEAD_REDIRECT_OBJ "redirect"

#define MSG_HEAD_SUB_BYPASS_TOPIC "topic"

#define MSG_HEAD_IDC "idc"
#define MSG_HEAD_IP  "ip"

#define MSG_BODY_TOPIC_LIST_JSON "topicList"
#define MSG_BODY_TOPIC_STR "topic"
#define MSG_BODY_PROPERTY_JSON "properties"
#define MSG_BODY_PROPERTY_MSG_TYPE_STR "msgType"
#define MSG_BODY_PROPERTY_TTL_INT "TTL"
#define MSG_BODY_PROPERTY_SEQ_STR "SEQ"
#define MSG_BODY_PROPERTY_RR_REQUEST_UNIQ_ID_STR "RR_REQUEST_UNIQ_ID"
#define MSG_BODY_PROPERTY_KEYS_STR "keys"
#define MSG_BODY_PROPERTY_REPLYTO_STR "REPLY_TO"
#define MSG_BODY_PROPERTY_BORN_TIME_STR "BORN_TIME"
#define MSG_BODY_PROPERTY_STORE_TIME_STR "STORE_TIME"
#define MSG_BODY_PROPERTY_LEAVE_TIME_STR "LEAVE_TIME"
#define MSG_BODY_PROPERTY_ARRIVE_TIME_STR "ARRIVE_TIME"

#define MSG_BODY_BYTE_BODY_JSON "body"
#define MSG_BODY_BYTE_BODY_APPHEADER_CONTENT_JSON "appHeaderContent"
#define MSG_BODY_BYTE_BODY_APPHEADER_NAME_STR "appHeaderName"
#define MSG_BODY_BYTE_BODY_CONTENT_STR "body"
#define MSG_BODY_BYTE_BODY_CREATETIME_LINT "createTime"
#define MSG_BODY_BYTE_BODY_SYSTEM_HEADER_CONTENT_JSON "sysHeaderContent"

#define MSG_BODY_RMB_TRACE_LOG_JSON "rmbTraceLog"
#define MSG_BODY_RMB_TRACE_LOG_LOG_POINT_STR "logPoint"
#define MSG_BODY_RMB_TRACE_LOG_ERR_CODE_STR "errCode"
#define MSG_BODY_RMB_TRACE_LOG_ERR_MSG_STR "errMsg"
#define MSG_BODY_RMB_TRACE_LOG_MESSAGE_STR "message"
#define MSG_BODY_RMB_TRACE_LOG_EXTFIELDS_STR "extFields"

#define MSG_BODY_SYSTEM_JSON "sysHeader"
#define MSG_BODY_SYSTEM_BIZ_STR "bizSeqNo"
#define MSG_BODY_SYSTEM_SEQNO_STR "consumerSeqNo"
#define MSG_BODY_SYSTEM_SVRID_STR "consumerSvrId"
#define MSG_BODY_SYSTEM_ORGSYS_STR "orgSysId"
#define MSG_BODY_SYSTEM_CSMID_STR "consumerId"
#define MSG_BODY_SYSTEM_TIME_LINT "tranTimestamp"
#define MSG_BODY_SYSTEM_CSMDCN_STR "consumerDCN"
#define MSG_BODY_SYSTEM_ORGSVR_STR "orgSvrId"
#define MSG_BODY_SYSTEM_ORGID_STR "organizationId"
#define MSG_BODY_SYSTEM_VER_STR "version"
#define MSG_BODY_SYSTEM_UNIID_STR "uniqueId"
#define MSG_BODY_SYSTEM_CONLEN_INT "contentLength"
#define MSG_BODY_SYSTEM_MSGTYPE_INT "messageType"
#define MSG_BODY_SYSTEM_RRTYPE_INT "rrType"
#define MSG_BODY_SYSTEM_ACK_SEQ "ack_seq"
#define MSG_BODY_SYSTEM_RECVTYPE_INT "receiveMode"
#define MSG_BODY_SYSTEM_SENDTIME_LINT "sendTimestamp"
#define MSG_BODY_SYSTEM_RECVTIME_LINT "receiveTimestamp"
#define MSG_BODY_SYSTEM_REPLYTIME_LINT "replyTimestamp"
#define MSG_BODY_SYSTEM_REPLYRECEIVETIME_LINT "replyReceiveTimestamp"
#define MSG_BODY_SYSTEM_APITYPE_INT "apiType"
#define MSG_BODY_SYSTEM_LOGICTYPE_INT "logicType"
#define MSG_BODY_SYSTEM_SOCOID_STR "solCorrelationId"
#define MSG_BODY_SYSTEM_EXTFIELDS_STR "extFields"
#define MSG_BODY_SYSTEM_API_VERSION	"rmbVersion"
#define MSG_BODY_SYSTEM_REQ_IP	"req_ip"
#define MSG_BODY_SYSTEM_REQ_SYS	"req_sys"
#define MSG_BODY_SYSTEM_REQ_DCN	"req_dcn"
#define MSG_BODY_SYSTEM_REQ_IDC	"req_idc"
#define MSG_BODY_SYSTEM_RSP_IP	"rsp_ip"
#define MSG_BODY_SYSTEM_RSP_SYS	"rsp_sys"
#define MSG_BODY_SYSTEM_RSP_DCN	"rsp_dcn"
#define MSG_BODY_SYSTEM_RSP_IDC	"rsp_idc"


#define MSG_BODY_APP_JSON "appHeader"

#define MSG_BODY_DEST_JSON "destinationContent"
#define MSG_BODY_DEST_NAME_STR "name"
#define MSG_BODY_DEST_TYPE_STR "type"
#define MSG_BODY_DEST_SORE_STR "serviceOrEventId"
#define MSG_BODY_DEST_SCENARIO_STR "scenario"
#define MSG_BODY_DEST_ANY_DCN_STR "anyDCN"
#define MSG_BODY_DEST_DCN_STR "dcnNo"
#define MSG_BODY_DEST_ORGID_STR "organizationId"
#define MSG_BODY_DEST_ORGFLAG_INT "organizationIdInputFlag"

#define MSG_BODY_TTL_LINT "timeToLive"
#define MSG_BODY_CONTENT_STR "content"
#define MSG_BODY_REPLYTO_STR "replyTo"
#define MSG_BODY_CREATETIME_LINT "createTime"
#define MSG_BODY_DELIVERYTIME_INT "deliveryTimes"
#define MSG_BODY_RESENT_BOOL "resent"
#define MSG_BODY_COID_STR "correlationId"
#define MSG_BODY_DUP_BOOL "duplicated"
#define MSG_BODY_SYN_BOOL "syn"
#define LOG_ERROR_POINT "ON_ERROR"

typedef struct StRmbMsg StRmbMsg;

typedef struct StWemqThreadMsg
{
	unsigned int m_iCmd;

	unsigned int m_iHeaderLen;
	unsigned int m_iBodyLen;
	char* m_pHeader;
	char* m_pBody;
}StWemqThreadMsg;

#define RMB_MAX_UNIQUE_NUMS 2048

typedef struct StUniqueIdList {
	char unique_id[50];
	char biz_seq[50];
	unsigned int timeout;
	unsigned int flag;
	unsigned long timeStamp;	
}StUniqueIdList;


typedef struct StUniqueIdList DataType;

typedef struct array
{
    DataType *Data;
    int size,max_size;
    void (*Constructor)(struct array *);      //构造函数
    void (*Input)(DataType ,struct array *);           //输入数据
    int (*get_array_size)(struct array *);         //获取arr的大小
    int (*return_index_value)(struct array*, int);
    void (*Destructor)(struct array *);           //析构函数
}Array;


#define RMB_MAX_ERR_MSG_FROM_ACCESS 1024

//wemq msg 最多4m
#define WEMQ_MSG_MSX_LENGTH (1 << 22)   


typedef struct stContextProxy
{
	pthread_t mainThreadId;
	pthread_t coThreadId;

	char* mPubRRBuf;

	//for rr
	pthread_mutex_t rrMutex;
	pthread_cond_t rrCond;
	int iFlagForRR;

	//for event msg, wait for ack 
	pthread_mutex_t eventMutex;
	pthread_cond_t eventCond;
	int iFlagForEvent;
	long iSeqForEvent;

	//for add listen
	pthread_mutex_t regMutex;
	pthread_cond_t regCond;
	int iFlagForReg;
	//for add listen result check
	int iResultForReg;

	//for pub session connect
	pthread_mutex_t pubMutex;
	pthread_cond_t pubCond;
	int iFlagForPub;
	int iFlagForPublish;

	//for sub session connect
	pthread_mutex_t subMutex;
	pthread_cond_t subCond;
	int iFlagForSub;

//	myhash_t* rrHashTable;
	void* pubContext;
	void* subContext;

	StRmbMsg *pReplyMsg;

	//StUniqueIdList *pUniqueListForRRAsyncNew;
	//StUniqueIdList *pUniqueListForRRAsyncOld;

	Array pUniqueListForRRAsyncNew;
    Array pUniqueListForRRAsyncOld;
		
	StUniqueIdList stUnique;
    
	//StUniqueIdList stUniqueListForRRAsync[RMB_MAX_UNIQUE_NUMS];
	//StUniqueIdList stUniqueListForRRAsyncOld[RMB_MAX_UNIQUE_NUMS];  

	//Array stUniqueListForRRAsync;
   //Array stUniqueListForRRAsyncOld;

	int iFlagForRun;
	unsigned long ulGoodByeTime;
    unsigned long ulLastClearRRAysncMsgTime;
	unsigned long ulLastPrintOldListIsEmpty;
	int iFlagForRRAsync;

    //for goodbye
	pthread_mutex_t goodByeMutex;
	pthread_cond_t goodByeCond;
	int iFlagForGoodBye;

	StWemqTopicList stTopicList;
	STRUCT_WEMQ_KFIFO(StWemqThreadMsg, MAX_WEMQ_KFIFO_LENGTH) pubFifo;
	STRUCT_WEMQ_KFIFO(StWemqThreadMsg, MAX_WEMQ_KFIFO_LENGTH) subFifo;
} stContextProxy;

typedef struct StThreadArgs
{
	stContextProxy *pStContextProxy;
	int contextType;
}StThreadArgs;

#define WEMQ_FIFO_SIZE (2 << 20)

typedef struct WemqThreadCtx
{
	STRUCT_WEMQ_KFIFO(StWemqThreadMsg, WEMQ_FIFO_SIZE)* m_ptFifo;
	stContextProxy* m_ptProxyContext;

	//int m_iThreadId;
	char* m_pRecvBuff;
	char* m_pSendBuff;

	//cache msg which from user thread;
	StWemqThreadMsg m_stWemqThreadMsg;
	StWemqThreadMsg m_stHeartBeat;
    StWemqThreadMsg m_stHelloWord;
	StWemqThreadMsg m_stListen;

	int m_iWemqThreadMsgHandled;

//	StWemqHeader m_stWemqHeader;
	StWeMQMSG m_stWeMQMSG;
	StWemqTopicList* m_ptTopicList;

	//for epoll
	int m_iEpollFd;
	struct epoll_event m_stEv;
	struct epoll_event* m_ptEvents;

	int m_iFlagForSeverBreak;

	int m_iSockFd;
	int m_iSockFdNew;
	int m_iSockFdOld;
	int m_iLastState;
	int m_iState;
	int m_contextType;

	int m_iHeartBeatCount;
	unsigned int m_uiHeartBeatCurrent;
	struct timeval stTimeNow ;
	struct timeval stTimeLast;
	struct timeval stTimeLastRecv;

    // proxy server 地址
    char m_cProxyIP[100];
	char m_cProxyIPOld[100];
    unsigned int      m_uiProxyPort;
	unsigned int      m_uiProxyPortOld;	
	int  m_iLocalPort;      // socket local port

	pthread_t m_threadID;   // current thread's ID

//	bool m_lRedirect;
	int m_lRedirect;
	char m_cRedirectIP[100];
	int  m_iRedirectPort;

}WemqThreadCtx;
//*************************************************************************

//**************************rmb msg define*********************************

enum RmbDestinationType
{
	RMB_DEST_TOPIC = 0,
	RMB_DEST_QUEUE,
};
 
#define RMB_SYSTEMHEADER_EXTFIELDS_MAX_LEN    1024 * 2 // 2K
#define RMB_SYSTEMHEADER_PROPERTY_MAX_LEN     1024 * 2 // 2K

typedef struct StSystemHeader
{
	char cBizSeqNo[50];							//全局唯一业务流水号
	char cConsumerSeqNo[50];					//服务消费者系统调用流水号
	char cOrgSysId[10];							//交易原始发起方系统编号
	char cConsumerSysId[10];					//服务消费者系统编号
	char cConsumerSysVersion[10];				//服务消费者的系统版本号
	char cConsumerSvrId[50];					//服务消费者服务器标示(服务器名或IP地址)
	char cRmbVersion[10];						//rmb版本号
	
	char cOrgSvrId[50];
	char cUniqueId[50];							//unique id
	char cConsumerDcn[10];						//服务消费者所在DCN
	unsigned long ulTranTimeStamp;				//交易发起时间戳
	char cAppHeaderClass[50];					//类名
	char cOrgId[10];							//法人号
	int flag;

	unsigned long ulSendTime;					//发送时间
	unsigned long ulReceiveTime;				//接收时间
	unsigned long ulReplyTime;					//回包时间
	unsigned long ulReplyReceiveTime;		   //回包接收时间

	unsigned long ulMessageDate;
	
	int iReceiveMode;
	
	int iContentLength;
	char cExtFields[RMB_SYSTEMHEADER_EXTFIELDS_MAX_LEN];
	char cProperty[RMB_SYSTEMHEADER_PROPERTY_MAX_LEN];
	int iSetSysVersionFlag;
}StSystemHeader;

#define REQ_BORN_TIMESTAMP "req_born_timestamp"
#define REQ_STORE_TIMESTAMP "req_store_timestamp"
#define REQ_LEAVE_TIMESTAMP "req_leave_timestamp"
#define REQ_ARRIVE_TIMESTAMP "req_arrive_timestamp"

#define RSP_BORN_TIMESTAMP "rsp_born_timestamp"
#define RSP_STORE_TIMESTAMP "rsp_store_timestamp"
#define RSP_LEAVE_TIMESTAMP "rsp_leave_timestamp"
#define RSP_ARRIVE_TIMESTAMP "rsp_arrive_timestamp"

typedef struct StAppHeader
{
	char cTransCode[8];							//
	char cSourceChannelType[32];				//
	char cWordStationId[4];						//
}StAppHeader;

typedef struct StDestination
{
	int iDestType;								//target type: 0: topic 1: queue
	char cDestName[200];
}StDestination;

typedef struct StFlow StFlow;

//包类型分类
enum C_RMB_PKG_TYPE
{
	ALL_TYPE_RMB = 0,			//所有类型
	QUEUE_PKG = 1,				//queue上的消息，一般为请求
	RR_TOPIC_PKG = 2,			//RR的回包
	BROADCAST_TOPIC_PKG = 3,	//广播包
	MANAGE_TOPIC_PKG = 4,		//RMB内部管理topic包
	NEW_LOGIC_RECEIVE = 5,
	NEW_LOGIC_SEND = 6,
};

//包来源分类
enum C_RMB_LOGIC_TYPE
{
	REQ_PKG_IN = 1,
	RSP_PKG_IN = 2,
	EVENT_PKG_IN = 3,
	REQ_PKG_OUT = 4,
	RSP_PKG_OUT = 5,
	EVENT_PKG_OUT = 6,

	REQ_PKG_IN_WEMQ = 7,
	RSP_PKG_IN_WEMQ = 8,
	EVENT_PKG_IN_WEMQ = 9,
	REQ_PKG_OUT_WEMQ = 10,
	RSP_PKG_OUT_WEMQ = 11,
	EVENT_PKG_OUT_WEMQ = 12,
	MANAGE_PKG_IN_WEMQ = 13,
};

enum C_RMB_MESSAGE_TYPE
{
	RMB_REQ_MSG = 1,
	RMB_RSP_MSG = 2,
	RMB_EVENT_MSG = 3,
};


//rmb转发包的方式
enum UDP_OR_MQ
{
	MSG_IPC_UDP = 0,
	MSG_IPC_MQ = 1,
};

enum RMB_API_YPE
{
	JAVA_TYPE = 1,
	C_TYPE = 2,
	JAVA_TYPE_WEMQ = 3,
	C_TYPE_WEMQ = 4,
};

enum RMB_CONTEXT_TYPE {
    RMB_CONTEXT_TYPE_SUB = 0,
    RMB_CONTEXT_TYPE_PUB = 1
};

//typedef struct StRmbMsg
struct StRmbMsg
{
	//sessionIndex for wemq
	unsigned int uiSessIndex;
	unsigned int uiFlowIndex;

	//pkg type
	char cPkgType;								//收到包的类型
	char cLogicType;							//消息来源
	char cApiType;								//apiType, enum RMB_API_YPE
	StSystemHeader sysHeader;
	StDestination dest;
	StDestination replyTo;
	unsigned long ulMsgId;
	unsigned long ulMsgLiveTime;
	char isDyedMsg[10];

	//for receive msg
	//char cServiceId[8];
	//char cScenario[2];

	//char cAppHeader[MAX_APPHEADER_LENGTH];
	char *cAppHeader;
	int iMallocAppHeaderLength;
	int iAppHeaderLen;

	//char cContent[MAX_MSG_CONTENT_SIZE];
	char *cContent;
	int iMallocContentLength;
	int iContentLen;

	char cCorrId[MAX_COMMON_SIZE];
	int iCorrLen;
	
	
	//msg src
	int iEventOrService;    //0 event;1 service
	char strTargetDcn[10];
	char strServiceId[10];
	char strScenarioId[5];

	//for gsl
	char strTargetOrgId[10];
	int iFLagForOrgId;

	//for flag check
	int flag;

	int iMsgMode;			//pub:send msg to wemq or solace  sub:recv msg from wemq or solace

	char strLogBuf[1024];
//}StRmbMsg;
};

//************for mq
typedef void (*rmb_callback_func)(const char*, const int, void*);
typedef int(*rmb_callback_func_v2)(const char*, const int, void*);
#define MAX_MQ_NUMS 10
#define MAX_FIFO_PATH_NAME_LEN 200
#define MAX_MQ_PKG_SIZE 10000000
//static const unsigned int C_RMB_MQ_HEAD_SIZE = 2 * sizeof(unsigned int);
#define C_RMB_MQ_HEAD_SIZE  2 * sizeof(unsigned int)
#define C_RMB_MQ_PKG_HEAD_SIZE 2 * sizeof(unsigned int)
//static const unsigned int C_RMB_MQ_PKG_HEAD_SIZE = 2 * sizeof(unsigned int);

typedef struct StRmbMq
{
	unsigned int uiShmkey;
	unsigned long ulShmId;
	unsigned int uiShmSize;

	//head + tail +  real data
	char* pMqData;
	unsigned int *pHead;
	unsigned int *pTail;

	//real data
	char *pBlock;
	unsigned int uiBlockSize;

}StRmbMq;

typedef struct StRmbFifo
{
	int iFd;
	char strPath[MAX_FIFO_PATH_NAME_LEN];
}StRmbFifo;

typedef struct StRmbQueue
{
	unsigned int uiSize;

	//head + tail +  real data
	char* pData;
	unsigned int *pHead;
	unsigned int *pTail;

	//real data
	char *pBlock;
	unsigned int uiBlockSize;
}StRmbQueue;

typedef struct StRmbPipe
{
	int fd[2];
	int r_fd;
	int w_fd;
}StRmbPipe;

typedef struct StMqInfo
{
	StRmbMq* mq;
	StRmbFifo* fifo;

	//for wemq
	StRmbQueue *que;
	StRmbPipe *pipe;

	int iIndex;		//offset in vector
	int iMsgType;	//iPkgType,1 queue msg;2 rr topic msg;3 broadcast msg;4 manage msg

	rmb_callback_func func;
	rmb_callback_func_v2 funcForNew;
	void* args;

	//for epoll
	int active;

	//for notify num
	int iCount;
	unsigned int uiLastCheckTime;

	int iMergeNotifyFLag; //0：not marge 1:marge
	int iNotifyFactor;   //the factor of notify

	pthread_mutex_t queMutex;
}StMqInfo;

enum RmbMqIndex
{
	req_mq_index = 1,
	rr_rsp_mq_index = 2,
	broadcast_mq_index = 3,
	manage_mq_index = 4,

//	wemq_req_mq_index = 5,
//	wemq_rr_rsp_mq_index = 6,
//	wemq_broadcast_mq_index = 7,
//	wemq_manage_mq_index = 8,
};

//for mq notify
typedef struct StRmbMqFifoNotify
{
	StMqInfo vecMqInfo[MAX_MQ_NUMS];
	int iMqNum;

	StMqInfo* mqIndex[MAX_MQ_NUMS]; //see define of RmbMqIndex
	//for select
	fd_set readFd;
	fd_set tmpReadFd;
	struct timeval tv;
	int iMaxFd;

	//for epoll
	int iEpollFd;

	char* pBuf;
	unsigned int uiBufLen;

}StRmbMqFifoNotify;

///////////////////////////////

typedef struct StContext StContext;


//*************************context************************

//context的基本定义
struct StContext 
{
	// for solace
	unsigned int uiInitFlag;
	
	//for receive msgs
	StRmbMsg *pReceiveMsg;
	StRmbMsg *pReceiveMsgForRR;
	StRmbMsg *pReceiveMsgForBroadCast;

	StRmbMsg *pReceiveWemqMsg;
	StRmbMsg *pReceiveWemqMsgForRR;
	StRmbMsg *pReceiveWemqMsgForBroadCast;

	//for receive msgs
	char *pPkg;
	unsigned int uiPkgLen;

	//for wemq
	char *pWemqPkg;
	unsigned int uiWemqPkgLen;

	char *pWemqPkgForRRAsync;
	unsigned int uiWemqPkgForRRAsyncLen;

	//********UDP*************
	//for rev req or broadcast
	int iSocketForReq;
	struct sockaddr_in tmpReqAddr;

	//for rev reply
	int iSocketForRsp;
	struct sockaddr_in tmpReplyAddr;

	//for rev broadcast
	int iSocketForBroadcast;
	struct sockaddr_in tmpBroadcastAddr;

	//*******MQ***************
	StRmbMqFifoNotify fifoMq;

	unsigned int uiNowTime;

	//sub or pub
	void* pFather;

	//****for wemq
	unsigned int uiInitWemqFlag;

	//for wemq proxy
	stContextProxy* pContextProxy;
	//sub or pub;
	int contextType;
};

//********broadcast*********
typedef struct StBroadcastNode {
	char strServiceId[10];
	char strScenarioId[5];
	char cConsumerSysId[10];
	char cReserve[2];
}StBroadcastNode;

//********queue节点***********
typedef struct StQueueNode
{
	char cQueueName[30];
	int iQueueSize;
	int iQueueUnackSize;

}StQueueNode;

typedef struct StQueueName
{
	char cQueueName[30];
}StQueueName;


typedef struct StServiceStatus
{
	char strTargetOrgId[10];
	char strServiceId[10];
	char strScenarioId[5];
	char cFlagForOrgId;

	char cResult;
	char cRouteFlag;
	char strTargetDcn[10];
	unsigned long ulGetTimes;
	unsigned long ulInvalidTime;
}StServiceStatus;

typedef struct StRmbConfig
{
	char strConfigFile[500];


	char cConsumerSysId[10];
	char cConsumerSysVersion[10];
	char cConsumerSvrId[100];
	char cOrgSvrId[50];
	//char cUniqueId[100];
	char cConsumerDcn[10];
	
	//主机名
	char cHostName[100];
	char cHostIp[50];
	unsigned int uiPid;

	//log
	int iLogLevel;
	//RmbLogFile stLog;
	char logFileName[200];
	int iLogFileNums;
	int iLogFileSize;
	int iLogShiftType;

	StRmbLog gRmbLog;

	//for solace api init
	unsigned int uiIsInitSolaceApi;
	//solace
	int iSwitchForSolaceLog;
	char strSolaceLog[100];
	int iSolaceLogLevel;

	//for connect success timeout
	int createConnectionTimeOut;

	//debug switch
	int iDebugSwitch;
	
	//queue config
	StQueueNode queueNodeList[5000];
	int iQueueListSize;

	StBroadcastNode broadNodeList[5000];
	int iBroadListSize;

	//for browser
	pthread_mutex_t configMutex;  
	pthread_cond_t configCond;
	int iFlag;

	//for mergeq for gsl
	pthread_mutex_t mergeqForGslMutex;
	pthread_cond_t mergeqForGslCond;
	int iFlagForMerge;
    int iMergeQueue;
	//for merge queue
	pthread_mutex_t mergeQueueMutex;
    
	//for log
	pthread_mutex_t configLog;

	//for send white list
//	pthread_mutex_t sendWhiteListMutex;

	//for debug print
	char* pLogBuf;

	//for manage session
	int iManageFlag;     //0 表示还没有， 1表示有

	StQueueName fullQueueList[3000]; //queue
	int iFullNums;

	//for flag
	int iFlagForReq;		//0:UDP,1:MQ
	int iFlagForRRrsp;		//0:UDP,1:MQ
	int iFlagForBroadCast;	//0:UDP,1:MQ
	int iFlagForManage;		//0:UDP,1:MQ

	//每次处理的个数
	int iEveryTimeProcessNum;

	//提醒的try间隔
	int iNotifyCheckSpan;

	//for req mq
	char strFifoPathForReq[128];
	unsigned int uiShmKeyForReq;
	unsigned int uiShmSizeForReq;

	//for rsp
	char strFifoPathForRRrsp[128];
	unsigned int uiShmKeyForRRrsp;
	unsigned int uiShmSizeForRRrsp;

	//for broadcast
	char strFifoPathForBroadcast[128];
	unsigned int uiShmKeyForBroadcast;
	unsigned int uiShmSizeForBroadcast;

	//flag
	//是否合并通知的开关
	int iFLagForMergeNotify;
	
	//orgId
	char strOrgId[10];

	//last error
	char _lastError[300];

	//是否允许发送的开关
	int iFlagForPublish;
	unsigned long ulLastStopTime;
	unsigned long ulLastAllowTime;
	char cLastMsg[50];

	//rmb的启动时间
	unsigned long ulStartTime;   //rmb init time

	//RR info
	unsigned int uiSendRRMsgNums;
	unsigned int uiSendRRMsgError;
	
	//Event info
	unsigned int uiSendEventMsg;
	unsigned int uiSendEventMsgError;

	//AyncRR
	unsigned int uiSendAyncRRMsg;
	unsigned int uiSendAyncRRMsgError;

	//receiveMsg
	unsigned int uiReceiveServiceReqMsg;
	unsigned int uiReceiveAyncRRReply;
	unsigned int uiReceiveEventMsg;

	StServiceStatus serviceStatusList[MAX_SERVICE_STATUS_CACHE_NUMS];
	int iCacheServiceNums;

	int iCacheTimeoutTime;
	int iCacheSuccTimeoutTime;			//gsl query success, timeout default is 600s
	int iCacheFailedTimeoutTime;		//gsl query failed, timeout default is 30s

	//timtout
	unsigned long ulNowTtime;

	//exit timtout
	unsigned long ulExitTimeOut;

	//for rmb proxy worker
	int iRmbMode;

	int iWorkerSendBufSize;

	//for reload cfg
	//signal1
	int iSwitchForSignal1;
	//signal2
	int iSwitchForSignal2;

	//for ack
	int ackTimers;
	int ackThresHold;

	//for query timeout
	int iQueryTimeout;

	//for sub broadcast LVQ
	unsigned int uiIsBroadcastLVQ;

	//for msg trans by solace timeout :daxin
	int iCommonTimeOut;
	//for rmb period stat log
	int iStatPeriod;
	//get group topic status time
	int iGetGroupTopicTime;
	//get send white list time
	int iGetSendWhiteListTime;
	
	//for clean merge q thread
	pthread_t pid_merge;
	int flag_merge;

	// logServer log switch for user
	int iLogserverSwitch;
	//logServer log switch for api
	int iApiLogserverSwitch;
	//rmb mode: wemq 
	char cRmbMode[10];
	int iConnWemq;

	//gsl control
	int iReqGsl;

	//for config center
	char cConfigIp[256];
	int iConfigPort;
	int iConfigTimeout;
	int configIpPosInArr;
	char ConfigAddr[512];
//	char cWemqSavePath[1024];

	//local IDC config
	char cRegion[10];

	int iFlagForLoop;
    	

	//for proxyContext
	int iProxyContextNums;
	void* pProxyContext;

	//for mode worker heart beat;
	int heartBeatPeriod;
	int heartBeatTimeout;

	//for get access ip
	int getAccessIpPeriod;

	//for access ack
	int accessAckTimeOut;

	//for rr async
	int rrAsyncTimeOut;

    //for access goodbye
	int goodByeTimeOut;

	// wemq-access 配置中心服务器
	int  iWemqUseHttpCfg;         // 是否启用Http配置中心
	//for default tcp
	char cWemqProxyIp[100];
	unsigned int cWemqProxyPort;
	int  iWemqTcpConnectRetryNum;  // 连接wemq-access重试次数
	int  iWemqTcpConnectDelayTime; // 每次重连间隔时间(ms)

	int  iWemqTcpConnectTimeout;   // 每次TCP连接超时时间
	int  iWemqTcpSocketTimeout;    // TCP socket 超时时间

	//for topic
	int iNormalTimeout; 			// 监听topic超时时间(ms), default is:120000

	//for wemq user/passwd
	char cWemqUser[100];
	char cWemqPasswd[100];

	int mqIsEmpty;

	char strDepartMent[20];
}StRmbConfig;


typedef struct RmbPythonConfig{
		//for req mq
	char strFifoPathForReq[128];
	unsigned int uiShmKeyForReq;
	unsigned int uiShmSizeForReq;

	//for rsp
	char strFifoPathForRRrsp[128];
	unsigned int uiShmKeyForRRrsp;
	unsigned int uiShmSizeForRRrsp;

	//for broadcast
	char strFifoPathForBroadcast[128];
	unsigned int uiShmKeyForBroadcast;
	unsigned int uiShmSizeForBroadcast;
}RmbPythonConfig;

enum MQ_STATUS{
	MQ_INIT = 0,
	MQ_IS_NOT_EMPTY,
	MQ_IS_EMPTY
};


//灰度完成queue的场景ID
#define GRAY_COMPLETE_SCENE "FR"

enum TOPIC_STATUS {
	QUEUE_NOT_GRAY = 0,
	QUEUE_GRAY_INIT,
	QUEUE_GRAY_NOT_COMPLETE,
	QUEUE_GRAY_COMPLETE
};

typedef struct StRmbListenQueueInfo {
	char cDcn[5];
	char cServiceId[10];
	char cScenarioId[5];
}st_rmb_queue_info;

//rmb topic info
typedef struct StRmbTopicInfo {
	char cServiceId[10];
	char cScenarioId[5];
	char cTopicGroup[32];
	char cTopicStatus[3];
	int flag;
}StRmbTopicInfo;

extern StRmbConfig* pRmbStConfig;
extern char cManageTopic[30];
extern char cQueueFullTopic[30];
extern char cLogLevelTopic[30];
///////////////add by wan
extern char cPublishCheck[30];
/////////////////////////////
extern StContext* g_pStContextArry[MAX_RMB_CONTEXT];
#define LOGRMB(loglevel,fmt, args...) {LogRmb(loglevel, "[%s:%d(%s)]["fmt"]", __FILE__, __LINE__, __FUNCTION__, ## args);\
				if(loglevel==RMB_LOG_ERROR) snprintf(pRmbStConfig->_lastError, sizeof(pRmbStConfig->_lastError)-1, fmt, ## args);}

//****common tool*******
#define RMB_MGS_PRINT_P(p) p->cConsumerSysId,p->cConsumerSysId,p->cConsumerSysId,
#define RMB_MAX(a,b) (a>b)?a:b
#define RMB_MIN(a,b) (a<b)?a:b
//#define RMB_MEMCPY(a,b); memcpy(a, b, RMB_MIN( sizeof(a),strlen(b)+1 ) );
#define RMB_MEMCPY(a,b); strncpy(a, b, sizeof(a));

//#define RMB_LEN_CHECK(a,len); if(strlen(a) > len){return -1;}
#define RMB_LEN_CHECK(a,len); if(strlen(a) != len){rmb_errno=RMB_ERROR_ARGV_LEN_ERROR;return rmb_errno;}

//#define RMB_CHECK_POINT_NULL(a,b); if((void*)a == NULL) {LOGRMB(RMB_LOG_ERROR,"%s is NULL!",  b);return -1;}
#define RMB_CHECK_POINT_NULL(a,b); if((void*)a == NULL) {LOGRMB(RMB_LOG_ERROR,"%s is NULL!",  b);rmb_errno=RMB_ERROR_ARGV_NULL;return RMB_ERROR_ARGV_NULL;}
//end
#define RMB_MEMSET(a); memset(&a, 0, sizeof(a));

#define RMB_ADD_SESSION_PROPERTY(a,b,c);	sprintf(a->cSessionProps[a->uiPropIndex], "%s", b);\
											a->vecPSessProps[a->uiPropIndex] = a->cSessionProps[a->uiPropIndex];\
											a->uiPropIndex++;\
											sprintf(a->cSessionProps[a->uiPropIndex], "%s", c);\
											a->vecPSessProps[a->uiPropIndex] = a->cSessionProps[a->uiPropIndex];\
											a->uiPropIndex++;

#define RMB_ADD_FLOW_PROPERTY(a,b,c);	sprintf(a->cFlowProps[a->uiPropIndex], "%s", b);\
											a->vecPFlowProps[a->uiPropIndex] = a->cFlowProps[a->uiPropIndex];\
											a->uiPropIndex++;\
											sprintf(a->cFlowProps[a->uiPropIndex], "%s", c);\
											a->vecPFlowProps[a->uiPropIndex] = a->cFlowProps[a->uiPropIndex];\
											a->uiPropIndex++;

#define RMB_ADD_FLOW_PROPERTY_INT(a,b,c);	sprintf(a->cFlowProps[a->uiPropIndex], "%s", b);\
												a->vecPFlowProps[a->uiPropIndex] = a->cFlowProps[a->uiPropIndex]; \
												a->uiPropIndex++; \
												sprintf(a->cFlowProps[a->uiPropIndex], "%d", c);\
												a->vecPFlowProps[a->uiPropIndex] = a->cFlowProps[a->uiPropIndex];\
												a->uiPropIndex++;



#ifdef __cplusplus
}
#endif

#endif
