#ifndef __MESSAGE_LOG_API_H_
#define __MESSAGE_LOG_API_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "rmb_define.h"
#include "rmb_pub.h"

#define LOG_MSG_COM_CONSUMERID "consumerId"
#define LOG_MSG_COM_LOGNAME "logName"
#define LOG_MSG_COM_TIMESTAMP "logTimestamp"
#define LOG_MSG_COM_CONTENT "content"
#define LOG_MSG_COM_LOGTYPE "logType"
#define LOG_MSG_COM_LANG "lang"
#define LOG_MSG_COM_ID "id"
#define LOG_MSG_COM_PROCESSID "processId"
#define LOG_MSG_COM_THREADID "threadId"
#define LOG_MSG_COM_CONSUMERSVRID "consumerSvrId"
#define LOG_MSG_COM_LEVEL "level"
#define LOG_MSG_COM_EXTFIELDS "extFields"

#define LOG_INFO_LEVEL "info"
#define LOG_DEBUG_LEVEL "debug"
#define LOG_WARN_LEVEL "warn"
#define LOG_ERROR_LEVEL "error"
#define LOG_FATAL_LEVEL "fatal"

//应用可以调用此接口上传log日志
int rmb_log_for_common(StContext *pStContext,const char* iLogLevel, const char* cLogName, const char *content,const char *extFields);

#ifdef __cplusplus
}
#endif

#endif
