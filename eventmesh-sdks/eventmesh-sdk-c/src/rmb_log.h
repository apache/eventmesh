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

#ifndef _RMB_LOG_H_
#define _RMB_LOG_H_

#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>
#include <stdarg.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <stdarg.h>
#include <sys/stat.h>   
#include <unistd.h>
#include <stdarg.h>
#include <sys/stat.h> 
#include <fcntl.h>

enum RMB_LOG_TYPE
{
    RMB_LOG_TYPE_NORMAL = 0,		//不需要滚动
	RMB_LOG_TYPE_CYCLE,				//按大小滚动
	RMB_LOG_TYPE_DAILY,				//按日期滚动
	RMB_LOG_TYPE_DAILY_AND_CYCLE,	//按日期和大小一起滚动
	RMB_LOG_TYPE_CYCLE_BY = 6,		//按大小滚动
};


enum RMB_LOG_LEVEL
{
	RMB_LOG_FATAL = 0,
	RMB_LOG_ERROR,
	RMB_LOG_WARN,
	RMB_LOG_INFO,
	RMB_LOG_DEBUG,
	RMB_LOG_ALL
};


typedef struct StRmbLogPara
{
	int _logLevel;
	int _shiftType;
	char _path[256];
	int _maxFileSize;
	int _maxFileNum;
}StRmbLogPara;

typedef struct StRmbLog
{
	FILE *_pStatFile;
	FILE *_pLogFile;
	char _logStatFileName[300];
	int _fd;
	//int _fileOpen;						//表示文件是否打开
	char _logFileName[300];					//next shift, 当前日志文件需要rename的名字
	char _logBaseFileName[280];				//当前写的日志名字
	time_t _lastShiftTime;
	int _curFileNums;						//当前日志数
	int _curFileNo;							//当前第几个日志或者当天第几个日志，circle删除看这个

	
	int _curFileSize;						//当前日志大小

	
	int _toDeleteFileNo;		  			//for circle
	char _toDeleteFileName[300];  			//for daily
	time_t _toDeleteTime;	  				//删除哪天的日志
	StRmbLogPara _para;

	//pthread_spinlock_t logSpinLock = SPIN_LOCK_UNLOCKED;	 //自旋锁
	pthread_mutex_t rmbLogMutex;			//临界区
	struct timeval stLogTv;
	struct stat gRmbSb;

	//for file lock
	struct flock logFileLock;
	struct flock logStateLock;
}StRmbLog;

typedef struct StDayInfo
{
	unsigned int _curFileNo;				//当前天，下一个rename的名字
	unsigned int _deleteFileNo;				//当前天，马上要被delete的序号
	time_t _dayTime;						//代表当前天的时间
	unsigned int _curFileNum;				//当前天的日志个数

	char _baseFileName[280];			
	char _logFileName[300];
}StDayInfo;

int ReloadCfg(int logLevel, int shiftType, const char* path, int maxFileSize, int maxFileNum);
int GetCurFileNumFromStatFile();
int SetCurFileNumToStatFile();
int InitRmbLogFile(int logLevel, int shiftType, const char* path, int maxFileSize, int maxFileNum);
int OpenRmbLog();
int FindNextDeleteLog();
int LogRmb(int logLevel, const char *format, ...);
int ShiftRmbLog(FILE* file);
int GetCurStateFromRmbLogByCirCle();
int GetCurStateFromRmbLogByDaily();
int GetCurStateFromRmbLogByDaily_V2();
int GetCurStateFromRmbLogByDaily_V3();
int GetCurStateFromRmbLogByDaily_V4();
int GetDayInfo(StDayInfo* dayInfo, time_t *curTime);
int GetDayInfoWithBreak(StDayInfo* dayInfo, time_t* curTime);
int  GetRmbNowLongTime();

void _Log_Thread_func(void* args);
#endif
