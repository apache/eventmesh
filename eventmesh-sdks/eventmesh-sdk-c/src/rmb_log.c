// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "rmb_log.h"
#include <stdio.h>
#include "string.h"
#include <pthread.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/time.h>
#include <time.h>
#include "rmb_define.h"
#include "string.h"
#include "rmb_access_config.h"
#include <stdlib.h>

StRmbLog *pStRmbLog = NULL;

struct tm currRmb;
struct tm lastRmb;
static char StatLogLevel[6][10] =
  { "FATAL", "ERROR", "WARN ", "INFO", "DEBUG", "ALL" };

int GetRmbNowLongTime ()
{
  struct timeval nowRmbTimeVal;
  gettimeofday (&nowRmbTimeVal, NULL);
  pRmbStConfig->ulNowTtime =
    (unsigned long) nowRmbTimeVal.tv_sec * (unsigned long) 1000UL +
    (unsigned long) nowRmbTimeVal.tv_usec / 1000UL;
  return 0;
}

char *RmbGetDateTimeStr (const time_t * mytime)
{
  static char s[50];
  currRmb = *localtime (mytime);
  if (currRmb.tm_year > 50)
    snprintf (s, sizeof (s), "%04d-%02d-%02d %02d:%02d:%02d",
              currRmb.tm_year + 1900, currRmb.tm_mon + 1, currRmb.tm_mday,
              currRmb.tm_hour, currRmb.tm_min, currRmb.tm_sec);
  else
    snprintf (s, sizeof (s), "%04d-%02d-%02d %02d:%02d:%02d",
              currRmb.tm_year + 2000, currRmb.tm_mon + 1, currRmb.tm_mday,
              currRmb.tm_hour, currRmb.tm_min, currRmb.tm_sec);
  return s;
}

char *RmbGetCurDateTimeStr ()
{
  time_t mytime = time (NULL);
  return RmbGetDateTimeStr (&mytime);
}

char *RmbGetShortDateStr (const time_t * mytime)
{
  static char s[50];
  currRmb = *localtime (mytime);
  if (currRmb.tm_year > 50)
    snprintf (s, sizeof (s), "%04d%02d%02d", currRmb.tm_year + 1900,
              currRmb.tm_mon + 1, currRmb.tm_mday);
  else
    snprintf (s, sizeof (s), "%04d%02d%02d", currRmb.tm_year + 2000,
              currRmb.tm_mon + 1, currRmb.tm_mday);

  return s;
}

char *RmbGetCurShortDateStr ()
{
  time_t mytime = time (NULL);
  return RmbGetShortDateStr (&mytime);
}

int RmbIsOtherDay (time_t * newTime, time_t * oldTime)
{
  lastRmb = *localtime (oldTime);
  currRmb = *localtime (newTime);
  if (currRmb.tm_mday != lastRmb.tm_mday)
  {
    return 1;
  }
  return 0;
}

int GetCurFileNum (FILE * fl)
{
  if (fl != NULL)
  {
    fscanf (fl, "%d %d %lu", &pStRmbLog->_curFileNo, &pStRmbLog->_curFileNums,
            &pStRmbLog->_lastShiftTime);
  }
  return 0;
}

int SetCurFileNum (FILE * fl)
{
  if (fl != NULL)
  {
    ftruncate (fileno (fl), 0);
    rewind (fl);
    fprintf (fl, "%d %d %lu", pStRmbLog->_curFileNo, pStRmbLog->_curFileNums,
             pStRmbLog->_lastShiftTime);
  }
  return 0;
}

int GetCurFileNumFromStatFile ()
{
  FILE *fp = fopen (pStRmbLog->_logStatFileName, "r");
  if (fp == NULL)
  {
    return 0;
  }
  fscanf (fp, "%d %d %lu", &pStRmbLog->_curFileNo, &pStRmbLog->_curFileNums,
          &pStRmbLog->_lastShiftTime);
  fclose (fp);
  return 0;
}

int SetCurFileNumToStatFile ()
{
  struct flock lock;
  FILE *fp = fopen (pStRmbLog->_logStatFileName, "w");
  if (fp == NULL)
  {
    return 0;
  }
  int fd = fileno (fp);
  lock.l_type = F_WRLCK;
  lock.l_start = 0;
  lock.l_whence = SEEK_SET;
  lock.l_len = 0;
  lock.l_pid = getpid ();
  if (-1 != fcntl (fd, F_SETLKW, &lock))
  {
//              printf("pid=%d set FileNo=%d.Num=%d", getpid(), pStRmbLog->_curFileNo, pStRmbLog->_curFileNums);
    fprintf (fp, "%d %d", pStRmbLog->_curFileNo, pStRmbLog->_curFileNums);
    lock.l_type = F_UNLCK;
    lock.l_whence = SEEK_SET;
    lock.l_start = 0;
    lock.l_len = 0;
    fcntl (fd, F_SETLKW, &lock);
  }
  fclose (fp);
  return 0;
}

int ReloadCfg (int logLevel, int shiftType, const char *path, int maxFileSize,
               int maxFileNum)
{
  int iRet = 0;
  pStRmbLog->_para._logLevel = logLevel;
  pStRmbLog->_para._shiftType = shiftType;
  if (shiftType == 6)
  {
    pStRmbLog->_para._shiftType = (int) RMB_LOG_TYPE_CYCLE;
  }
  strncpy (pStRmbLog->_para._path, path, sizeof (pStRmbLog->_para._path) - 1);
  snprintf (pStRmbLog->_logStatFileName,
            sizeof (pStRmbLog->_logStatFileName) - 1, "%s.stat", path);
  pStRmbLog->_para._maxFileSize = maxFileSize;
  pStRmbLog->_para._maxFileNum = maxFileNum;
  pStRmbLog->_lastShiftTime = time (NULL);
  if (strlen (pStRmbLog->_para._path) == 0
      || pStRmbLog->_para._shiftType == RMB_LOG_TYPE_NORMAL)
  {
    pStRmbLog->_pLogFile = stdout;
    pStRmbLog->_curFileNums = 1;
    pStRmbLog->_para._shiftType = 0;
    pStRmbLog->_lastShiftTime = 0;
    return 0;
  }

  if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_CYCLE)
  {
    iRet = GetCurStateFromRmbLogByCirCle ();
    if (iRet != 0)
    {
      return -1;
    }
  }
  else if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_DAILY
           || pStRmbLog->_para._shiftType == RMB_LOG_TYPE_DAILY_AND_CYCLE)
  {
    pStRmbLog->_para._shiftType = RMB_LOG_TYPE_DAILY;
    iRet = GetCurStateFromRmbLogByDaily_V4 ();
    if (iRet != 0)
    {
      return -1;
    }
  }
  else
  {
    pStRmbLog->_para._shiftType = RMB_LOG_TYPE_CYCLE;
    iRet = GetCurStateFromRmbLogByCirCle ();
    if (iRet != 0)
    {
      return -1;
    }
  }
  return 0;
}

int InitRmbLogFile (int logLevel, int shiftType, const char *path,
                    int maxFileSize, int maxFileNum)
{
  int iRet = 0;
  memset (&pRmbStConfig->gRmbLog, 0, sizeof (StRmbLog));
  pStRmbLog = &pRmbStConfig->gRmbLog;
  pStRmbLog->_para._logLevel = logLevel;

  pStRmbLog->_para._shiftType = shiftType;
  if (shiftType == 6)
  {
    pStRmbLog->_para._shiftType = (int) RMB_LOG_TYPE_CYCLE;
  }
  strncpy (pStRmbLog->_para._path, path, sizeof (pStRmbLog->_para._path) - 1);
  snprintf (pStRmbLog->_logStatFileName,
            sizeof (pStRmbLog->_logStatFileName) - 1, "%s.stat", path);
  pStRmbLog->_para._maxFileSize = maxFileSize;
  pStRmbLog->_para._maxFileNum = maxFileNum;
  pStRmbLog->_lastShiftTime = time (NULL);

  //spin_lock_init(&logSpinLock);
  pthread_mutex_init (&pStRmbLog->rmbLogMutex, NULL);
  //printf("log init!!");
  //if len(path) = 0, use stdout

  if (strlen (pStRmbLog->_para._path) == 0
      || pStRmbLog->_para._shiftType == RMB_LOG_TYPE_NORMAL)
  {
    pStRmbLog->_pLogFile = stdout;
    pStRmbLog->_curFileNums = 1;
    pStRmbLog->_para._shiftType = 0;
    pStRmbLog->_lastShiftTime = 0;
    return 0;
  }

  if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_CYCLE)
  {
    iRet = GetCurStateFromRmbLogByCirCle ();
    if (iRet != 0)
    {
      return -1;
    }
  }
  else if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_DAILY
           || pStRmbLog->_para._shiftType == RMB_LOG_TYPE_DAILY_AND_CYCLE)
  {
    pStRmbLog->_para._shiftType = RMB_LOG_TYPE_DAILY;
    iRet = GetCurStateFromRmbLogByDaily_V4 ();
    if (iRet != 0)
    {
      return -1;
    }
  }
  else
  {
    pStRmbLog->_para._shiftType = RMB_LOG_TYPE_CYCLE;
    iRet = GetCurStateFromRmbLogByCirCle ();
    if (iRet != 0)
    {
      return -1;
    }
  }
  return 0;
}

int CloseRmbLog ()
{
//      if(pStRmbLog->_pLogFile == NULL)
//      {
//              return 0;
//      }
//      fclose(pStRmbLog->_pLogFile);
//      pStRmbLog->_pLogFile = NULL;
//      return 0;
  close (pStRmbLog->_fd);
  pStRmbLog->_fd = -1;
  return 0;
}

int OpenRmbLog ()
{
//      if (pStRmbLog->_pLogFile != NULL)
//      {
//              return 0;
//      }
//      printf("open file=%s", pStRmbLog->_logBaseFileName);
//      if ((pStRmbLog->_pLogFile = fopen(pStRmbLog->_logBaseFileName, "a+")) == NULL)
//      {
//              pStRmbLog->_pLogFile = stdout;
//              printf("openLog failed!fileName=%s, now select stdout!", pStRmbLog->_logBaseFileName);
//              return -1;
//      }
//      printf("open file=%s succ", pStRmbLog->_logBaseFileName);
  pStRmbLog->_fd =
    open (pStRmbLog->_logBaseFileName, O_RDWR | O_CREAT | O_APPEND, 0666);
  if (pStRmbLog->_fd == -1)
  {
    printf ("openLog failed!fileName=%s, now select stdout!",
            pStRmbLog->_logBaseFileName);
    pStRmbLog->_fd = 1;
    return 0;
  }
  return 0;
}

int OpenStateLog ()
{
  pStRmbLog->_pStatFile = fopen (pStRmbLog->_logStatFileName, "a+");
  if (pStRmbLog->_pStatFile == NULL)
  {
    return 0;
  }
  return fileno (pStRmbLog->_pStatFile);
}

int CloseStateLog ()
{
  if (pStRmbLog->_pStatFile != NULL)
  {
    fclose (pStRmbLog->_pStatFile);
  }
  pStRmbLog->_pStatFile = NULL;
  return 0;
}

int FindNextDeleteLog (StDayInfo * dayInfo)
{
  //update next delete file name and time
  int i = 0;
  //找当天的
  for (i = 1; i < pStRmbLog->_para._maxFileNum; i++)
  {
    pStRmbLog->_toDeleteFileNo =
      (pStRmbLog->_toDeleteFileNo + 1) % pStRmbLog->_para._maxFileNum;
    if (pStRmbLog->_toDeleteFileNo == 0)
    {
      pStRmbLog->_toDeleteFileNo = 1;
    }

    snprintf (pStRmbLog->_toDeleteFileName,
              sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s_%s.%d",
              pStRmbLog->_para._path,
              RmbGetShortDateStr ((time_t *) & pStRmbLog->_toDeleteTime),
              pStRmbLog->_toDeleteFileNo);
//              printf("check fileName=%s is exist or not!", pStRmbLog->_toDeleteFileName);
    struct stat sb;
    if (stat (pStRmbLog->_toDeleteFileName, &sb) != 0)
    {
//                      printf("i=%u,logFile=%s not exist!", i, pStRmbLog->_toDeleteFileName);
      break;
    }
//              printf("Next deleteNo=%u,deleteFileName=%s", pStRmbLog->_toDeleteFileNo, pStRmbLog->_toDeleteFileName);
    return 0;
  }

  int count = (time (NULL) - pStRmbLog->_toDeleteTime) / 60 * 60 * 24 + 1;
  for (i = 1; i < count; i++)
  {
    pStRmbLog->_toDeleteTime = pStRmbLog->_toDeleteTime + 60 * 60 * 24;
    GetDayInfo (dayInfo, &pStRmbLog->_toDeleteTime);
    if (dayInfo->_curFileNum != 0)
    {
      pStRmbLog->_toDeleteFileNo = dayInfo->_deleteFileNo;
      snprintf (pStRmbLog->_toDeleteFileName,
                sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s_%s.%d",
                pStRmbLog->_para._path,
                RmbGetShortDateStr ((time_t *) & pStRmbLog->_toDeleteTime),
                pStRmbLog->_toDeleteFileNo);
      break;
    }
  }

  return 0;
}

int ShiftRmbLog (FILE * file)
{
  if (strlen (pStRmbLog->_para._path) == 0
      || pStRmbLog->_para._shiftType == RMB_LOG_TYPE_NORMAL)
  {
    return 0;
  }

  if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_CYCLE)
  {

    if (stat (pStRmbLog->_logBaseFileName, &pStRmbLog->gRmbSb) < 0)
    {
      pStRmbLog->_curFileSize = 0;
    }
    else
    {
      pStRmbLog->_curFileSize = (int) pStRmbLog->gRmbSb.st_size;
    }

    if (pStRmbLog->_curFileSize < pStRmbLog->_para._maxFileSize)
    {
      return 0;
    }

    OpenRmbLog ();
    //file lock
    pStRmbLog->logFileLock.l_type = F_WRLCK;
    pStRmbLog->logFileLock.l_start = 0;
    pStRmbLog->logFileLock.l_whence = SEEK_SET;
    pStRmbLog->logFileLock.l_len = 0;
    pStRmbLog->logFileLock.l_pid = getpid ();
    if (-1 != fcntl (pStRmbLog->_fd, F_SETLKW, &pStRmbLog->logFileLock))
    {
      int fd = OpenStateLog ();
      pStRmbLog->logStateLock.l_type = F_WRLCK;
      pStRmbLog->logStateLock.l_start = 0;
      pStRmbLog->logStateLock.l_whence = SEEK_SET;
      pStRmbLog->logStateLock.l_len = 0;
      pStRmbLog->logStateLock.l_pid = getpid ();
      if (-1 != fcntl (fd, F_SETLKW, &pStRmbLog->logStateLock))
      {
        //GetCurFileNum(pStRmbLog->_pStatFile);
        GetCurFileNum (pStRmbLog->_pStatFile);
//                              printf("pid=%d,getCurNo=%d,CurNums=%d", getpid(), pStRmbLog->_curFileNo, pStRmbLog->_curFileNums);
        snprintf (pStRmbLog->_logFileName,
                  sizeof (pStRmbLog->_logFileName) - 1, "%s.%d",
                  pStRmbLog->_logBaseFileName, pStRmbLog->_curFileNo);

        if (stat (pStRmbLog->_logBaseFileName, &pStRmbLog->gRmbSb) != -1
            && (int) pStRmbLog->gRmbSb.st_size >=
            pStRmbLog->_para._maxFileSize)
        {
          pStRmbLog->_curFileNums += 1;
          if (pStRmbLog->_curFileNums > pStRmbLog->_para._maxFileNum)
          {
            if (access (pStRmbLog->_logFileName, F_OK) == 0)
            {
//                                                      printf("pid=%d remove file=%s,curNo=%d, curNum=%d", getpid(), pStRmbLog->_logFileName, pStRmbLog->_curFileNo, pStRmbLog->_curFileNums);
              remove (pStRmbLog->_logFileName);
            }
            pStRmbLog->_curFileNums -= 1;
          }

          if (access (pStRmbLog->_logBaseFileName, F_OK) == 0)
          {
//                                              printf("pid=%d rename %s-->%s, curNo=%d,curNum=%d", getpid(), pStRmbLog->_logBaseFileName, pStRmbLog->_logFileName, pStRmbLog->_curFileNo, pStRmbLog->_curFileNums);
            rename (pStRmbLog->_logBaseFileName, pStRmbLog->_logFileName);
          }
          else
          {
//                                              printf("pid=%d baseFileName=%s not exist", getpid(), pStRmbLog->_logBaseFileName);
          }
          pStRmbLog->_curFileNo =
            (pStRmbLog->_curFileNo + 1) % pStRmbLog->_para._maxFileNum;
//                                      if (pStRmbLog->_curFileNo == 0)
//                                      {
//                                              pStRmbLog->_curFileNo = 1;
//                                      }
          SetCurFileNum (pStRmbLog->_pStatFile);
          //snprintf(pStRmbLog->_logFileName, sizeof(pStRmbLog->_logFileName) - 1, "%s.%d", pStRmbLog->_logBaseFileName, pStRmbLog->_curFileNo);
        }

//                              printf("pid=%d setFileNum.curNo=%d curNum=%d", getpid(), pStRmbLog->_curFileNo, pStRmbLog->_curFileNums);
        pStRmbLog->logStateLock.l_type = F_UNLCK;
        pStRmbLog->logStateLock.l_whence = SEEK_SET;
        pStRmbLog->logStateLock.l_start = 0;
        pStRmbLog->logStateLock.l_len = 0;
        fcntl (fd, F_SETLKW, &pStRmbLog->logStateLock);
      }
      CloseStateLog ();

//                      printf("shift over!pid=%d, curNo=%d,curNum=%d, logFileName=%s", getpid(),  pStRmbLog->_curFileNo, pStRmbLog->_curFileNums,  pStRmbLog->_logFileName);
      //strncpy(pStRmbLog->_toDeleteFileName, pStRmbLog->_logFileName, sizeof(pStRmbLog->_toDeleteFileName) - 1);

      pStRmbLog->logFileLock.l_type = F_UNLCK;
      pStRmbLog->logFileLock.l_whence = SEEK_SET;
      pStRmbLog->logFileLock.l_start = 0;
      pStRmbLog->logFileLock.l_len = 0;
      fcntl (pStRmbLog->_fd, F_SETLKW, &pStRmbLog->logFileLock);
    }
    CloseRmbLog ();
    return 0;
  }
  else if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_DAILY)
  {
    time_t curTime = time (NULL);
    //日期变更发送滚动
    if (RmbIsOtherDay (&curTime, &pStRmbLog->_lastShiftTime))
    {
      OpenRmbLog ();
      //file lock
      pStRmbLog->logFileLock.l_type = F_WRLCK;
      pStRmbLog->logFileLock.l_start = 0;
      pStRmbLog->logFileLock.l_whence = SEEK_SET;
      pStRmbLog->logFileLock.l_len = 0;
      pStRmbLog->logFileLock.l_pid = getpid ();
      if (-1 != fcntl (pStRmbLog->_fd, F_SETLKW, &pStRmbLog->logFileLock))
      {
        int fd = OpenStateLog ();
        pStRmbLog->logStateLock.l_type = F_WRLCK;
        pStRmbLog->logStateLock.l_start = 0;
        pStRmbLog->logStateLock.l_whence = SEEK_SET;
        pStRmbLog->logStateLock.l_len = 0;
        pStRmbLog->logStateLock.l_pid = getpid ();
        if (-1 != fcntl (fd, F_SETLKW, &pStRmbLog->logStateLock))
        {
          GetCurFileNum (pStRmbLog->_pStatFile);
          snprintf (pStRmbLog->_logFileName,
                    sizeof (pStRmbLog->_logFileName) - 1, "%s.%d",
                    pStRmbLog->_logBaseFileName, pStRmbLog->_curFileNo);

          if (RmbIsOtherDay (&curTime, &pStRmbLog->_lastShiftTime))
          {
            pStRmbLog->_curFileNums += 1;
            if (pStRmbLog->_curFileNums > pStRmbLog->_para._maxFileNum)
            {
              if (access (pStRmbLog->_logFileName, F_OK) == 0)
              {
                remove (pStRmbLog->_logFileName);
              }
              pStRmbLog->_curFileNums -= 1;
            }
            if (access (pStRmbLog->_logBaseFileName, F_OK) == 0)
            {
              rename (pStRmbLog->_logBaseFileName, pStRmbLog->_logFileName);
            }

            snprintf (pStRmbLog->_logBaseFileName,
                      sizeof (pStRmbLog->_logBaseFileName), "%s_%s.log",
                      pStRmbLog->_para._path, RmbGetShortDateStr (&curTime));
            snprintf (pStRmbLog->_logFileName,
                      sizeof (pStRmbLog->_logFileName), "%s.0",
                      pStRmbLog->_logBaseFileName);
            pStRmbLog->_curFileNo = 0;
            pStRmbLog->_curFileNums = 0;
            pStRmbLog->_lastShiftTime = curTime;
            SetCurFileNum (pStRmbLog->_pStatFile);
          }
          else
          {
            snprintf (pStRmbLog->_logBaseFileName,
                      sizeof (pStRmbLog->_logBaseFileName), "%s_%s.log",
                      pStRmbLog->_para._path, RmbGetShortDateStr (&curTime));
          }
          pStRmbLog->logStateLock.l_type = F_UNLCK;
          pStRmbLog->logStateLock.l_whence = SEEK_SET;
          pStRmbLog->logStateLock.l_start = 0;
          pStRmbLog->logStateLock.l_len = 0;
          fcntl (fd, F_SETLKW, &pStRmbLog->logStateLock);
        }
        CloseStateLog ();

        pStRmbLog->logFileLock.l_type = F_UNLCK;
        pStRmbLog->logFileLock.l_whence = SEEK_SET;
        pStRmbLog->logFileLock.l_start = 0;
        pStRmbLog->logFileLock.l_len = 0;
        fcntl (pStRmbLog->_fd, F_SETLKW, &pStRmbLog->logFileLock);
      }
      CloseRmbLog ();
      return 0;
    }

    if (stat (pStRmbLog->_logBaseFileName, &pStRmbLog->gRmbSb) < 0)
    {
      pStRmbLog->_curFileSize = 0;
    }
    else
    {
      pStRmbLog->_curFileSize = (int) pStRmbLog->gRmbSb.st_size;
    }

    if (pStRmbLog->_curFileSize < pStRmbLog->_para._maxFileSize)
    {
      return 0;
    }

    OpenRmbLog ();
    //file lock
    pStRmbLog->logFileLock.l_type = F_WRLCK;
    pStRmbLog->logFileLock.l_start = 0;
    pStRmbLog->logFileLock.l_whence = SEEK_SET;
    pStRmbLog->logFileLock.l_len = 0;
    pStRmbLog->logFileLock.l_pid = getpid ();
    if (-1 != fcntl (pStRmbLog->_fd, F_SETLKW, &pStRmbLog->logFileLock))
    {
      int fd = OpenStateLog ();
      pStRmbLog->logStateLock.l_type = F_WRLCK;
      pStRmbLog->logStateLock.l_start = 0;
      pStRmbLog->logStateLock.l_whence = SEEK_SET;
      pStRmbLog->logStateLock.l_len = 0;
      pStRmbLog->logStateLock.l_pid = getpid ();
      if (-1 != fcntl (fd, F_SETLKW, &pStRmbLog->logStateLock))
      {
        GetCurFileNum (pStRmbLog->_pStatFile);
        snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName),
                  "%s.%d", pStRmbLog->_logBaseFileName,
                  pStRmbLog->_curFileNo);

        if (stat (pStRmbLog->_logBaseFileName, &pStRmbLog->gRmbSb) != -1
            && (int) pStRmbLog->gRmbSb.st_size >=
            pStRmbLog->_para._maxFileSize)
        {
          pStRmbLog->_curFileNums += 1;
          if (pStRmbLog->_curFileNums > pStRmbLog->_para._maxFileNum)
          {
            if (access (pStRmbLog->_logFileName, F_OK) == 0)
            {
              remove (pStRmbLog->_logFileName);
            }
            pStRmbLog->_curFileNums -= 1;
          }
          //CloseZrdLog();
          if (access (pStRmbLog->_logBaseFileName, F_OK) == 0)
          {
            rename (pStRmbLog->_logBaseFileName, pStRmbLog->_logFileName);
          }

          pStRmbLog->_curFileNo =
            (pStRmbLog->_curFileNo + 1) % pStRmbLog->_para._maxFileNum;
//                                      if (pStRmbLog->_curFileNo == 0)
//                                      {
//                                              pStRmbLog->_curFileNo = 1;
//                                      }
          SetCurFileNum (pStRmbLog->_pStatFile);

        }

        pStRmbLog->logStateLock.l_type = F_UNLCK;
        pStRmbLog->logStateLock.l_whence = SEEK_SET;
        pStRmbLog->logStateLock.l_start = 0;
        pStRmbLog->logStateLock.l_len = 0;
        fcntl (fd, F_SETLKW, &pStRmbLog->logStateLock);
      }
      CloseStateLog ();

      pStRmbLog->logFileLock.l_type = F_UNLCK;
      pStRmbLog->logFileLock.l_whence = SEEK_SET;
      pStRmbLog->logFileLock.l_start = 0;
      pStRmbLog->logFileLock.l_len = 0;
      fcntl (pStRmbLog->_fd, F_SETLKW, &pStRmbLog->logFileLock);
    }
    CloseRmbLog ();
    return 0;
  }
  else if (pStRmbLog->_para._shiftType == RMB_LOG_TYPE_DAILY_AND_CYCLE)
  {
    time_t curTime = time (NULL);
    //日期变更发送滚动
    if (RmbIsOtherDay (&curTime, &pStRmbLog->_lastShiftTime))
    {
      pStRmbLog->_curFileNums += 1;
      //假如个数限制，则删除老的
      if (pStRmbLog->_curFileNums > pStRmbLog->_para._maxFileNum)
      {
        remove (pStRmbLog->_toDeleteFileName);
        pStRmbLog->_curFileNums -= 1;
        StDayInfo dayInfo;
        FindNextDeleteLog (&dayInfo);
      }
      //GetCurStateFromRmbLogByDaily_V3(); //保证没有出错
      //rename
      //CloseRmbLog();
      rename (pStRmbLog->_logBaseFileName, pStRmbLog->_logFileName);
      snprintf (pStRmbLog->_logBaseFileName,
                sizeof (pStRmbLog->_logBaseFileName), "%s_%s.log",
                pStRmbLog->_para._path, RmbGetShortDateStr (&curTime));
      snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName),
                "%s_1.log", pStRmbLog->_logBaseFileName);
      pStRmbLog->_curFileNo = 1;
      //OpenRmbLog();
      return 0;
    }

    if (stat (pStRmbLog->_logBaseFileName, &pStRmbLog->gRmbSb) < 0)
    {
      pStRmbLog->_curFileSize = 0;
    }
    else
    {
      pStRmbLog->_curFileSize = (int) pStRmbLog->gRmbSb.st_size;
    }

    if (pStRmbLog->_curFileSize < pStRmbLog->_para._maxFileSize)
    {
      return 0;
    }
    //文件大小促发滚动
    pStRmbLog->_curFileNums += 1;
    if (pStRmbLog->_curFileNums > pStRmbLog->_para._maxFileNum)
    {
      remove (pStRmbLog->_toDeleteFileName);
      pStRmbLog->_curFileNums -= 1;
      StDayInfo dayInfo;
      FindNextDeleteLog (&dayInfo);
    }
    //rename
    //CloseRmbLog();
    rename (pStRmbLog->_logBaseFileName, pStRmbLog->_logFileName);
    pStRmbLog->_curFileNo += 1;
    snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName),
              "%s_%d.log", pStRmbLog->_logBaseFileName,
              pStRmbLog->_curFileNo);
    //OpenRmbLog();
    return 0;
  }
  return 0;
}

int LogRmb (int logLevel, const char *format, ...)
{
//      printf("logLevel=%u,sysLevel=%u,file=%p,baseFile=%s", 
//                              logLevel, pStRmbLog->_para._logLevel, pStRmbLog->_pLogFile, pStRmbLog->_logBaseFileName);
//      va_list ap1;
//      va_start(ap1, format);
//      printf(format, ap1);
//      va_end(ap1);
//      return 0;
//      return ShiftRmbLog(pStRmbLog->_pLogFile);

  if (logLevel > pStRmbLog->_para._logLevel)
  {
    return 0;
  }

  //pthread_spin_lock(&logSpinLock);
  pthread_mutex_lock (&pStRmbLog->rmbLogMutex);
  ShiftRmbLog (pStRmbLog->_pLogFile);
  va_list ap;
  if ((pStRmbLog->_pLogFile =
       fopen (pStRmbLog->_logBaseFileName, "a+")) == NULL)
  {
    pStRmbLog->_pLogFile = stdout;
    //printf("openLog failed!fileName=%s, now select stdout!", pStRmbLog->_logBaseFileName);
  }
  va_start (ap, format);
  gettimeofday (&pStRmbLog->stLogTv, NULL);
  fprintf (pStRmbLog->_pLogFile, "[%s][%s %03d][%d][%lu]",
           StatLogLevel[logLevel],
           RmbGetDateTimeStr ((const time_t *) &(pStRmbLog->stLogTv.tv_sec)),
           (int) ((pStRmbLog->stLogTv.tv_usec) / 1000), pRmbStConfig->uiPid,
           pthread_self ());
  vfprintf (pStRmbLog->_pLogFile, format, ap);
  va_end (ap);
  fprintf (pStRmbLog->_pLogFile, "\n");
  if (pStRmbLog->_pLogFile != stdout)
  {
    fclose (pStRmbLog->_pLogFile);
  }

  //pthread_spin_unlock(&logSpinLock);
  pthread_mutex_unlock (&pStRmbLog->rmbLogMutex);
  return 0;
}

//统计现在有几个日志，当前日志马上要被rename成什么，是不是需要促发删除
int GetCurStateFromRmbLogByCirCle ()
{
  pStRmbLog->_curFileNums = 1;
  //unsigned int minModifyTime = 0;
  //int i = 0;
  snprintf (pStRmbLog->_logBaseFileName, sizeof (pStRmbLog->_logBaseFileName),
            "%s.log", pStRmbLog->_para._path);
  //strncpy(pStRmbLog->_logFileName, pStRmbLog->_logBaseFileName, sizeof(pStRmbLog->_logFileName) - 1);
  pStRmbLog->_curFileNo = 0;
  /*
     for (i = 1; i < pStRmbLog->_para._maxFileNum ; i++)
     {
     snprintf(pStRmbLog->_logFileName, sizeof(pStRmbLog->_logFileName) - 1, "%s.%d", pStRmbLog->_logBaseFileName, i);
     printf("log file:%s", pStRmbLog->_logFileName);

     struct stat sb;
     if (stat (pStRmbLog->_logFileName, &sb) != 0)
     {
     //�ļ�������, ʹ�øñ��
     pStRmbLog->_curFileNo = i;
     printf("logFile=%s not exist!", pStRmbLog->_logFileName);
     break;  
     }
     pStRmbLog->_curFileNums++;

     printf("fileName=%s,modifyTime=%lu", pStRmbLog->_logFileName, sb.st_mtime);
     //ѭ����־, ��Ҫ������޸�ʱ���������
     if (minModifyTime == 0 || sb.st_mtime < minModifyTime)
     {
     pStRmbLog->_toDeleteFileNo = i;
     minModifyTime = sb.st_mtime;
     }
     //           if (sb.st_mtime > maxModifyTime)
     //           {
     //                   pStRmbLog->_curFileNo = i;
     //                   maxModifyTime = sb.st_mtime;
     //           }
     }
   */
  pStRmbLog->_curFileNums = 0;
  GetCurFileNumFromStatFile ();
//      if (pStRmbLog->_curFileNo == 0)
//      {
//              pStRmbLog->_curFileNo = pStRmbLog->_toDeleteFileNo;
//      }
  //SetCurFileNumToStatFile();
  snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName) - 1,
            "%s.%d", pStRmbLog->_logBaseFileName, pStRmbLog->_curFileNo);
  snprintf (pStRmbLog->_toDeleteFileName,
            sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s.%d",
            pStRmbLog->_logBaseFileName, pStRmbLog->_toDeleteFileNo);
  //printf("Circle Log: curFileNo=%u", pStRmbLog->_curFileNo);
  return 0;
}

//统计现在有几个日志，当前日志马上要被rename成什么，是不是需要促发删除(按编号大小)
int GetCurStateFromRmbLogByDaily ()
{
  pStRmbLog->_curFileNums = 1;
  int i = 0;
  snprintf (pStRmbLog->_logBaseFileName, sizeof (pStRmbLog->_logBaseFileName),
            "%s_%s.log", pStRmbLog->_para._path, RmbGetCurShortDateStr ());
  //strncpy(pStRmbLog->_logFileName, pStRmbLog->_logBaseFileName, sizeof(pStRmbLog->_logFileName) - 1);
  //int todayFileNo = 0;
  unsigned int curTime = time (NULL);
  //当天
  for (i = 1; i < pStRmbLog->_para._maxFileNum - 1; i++)
  {
    snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName) - 1,
              "%s.%d", pStRmbLog->_logBaseFileName, i);
//              printf("GetCurStateFromRmbLogByDaily log file:%s", pStRmbLog->_logFileName);

    struct stat sb;
    if (stat (pStRmbLog->_logFileName, &sb) != 0)
    {
      //file not exist, use it
      pStRmbLog->_curFileNo = i;
      //pStRmbLog->_curFileNums = i;
//                      printf("GetCurStateFromRmbLogByDaily logFile=%s not exist!", pStRmbLog->_logFileName);
      break;
    }
    pStRmbLog->_toDeleteTime = curTime;
    pStRmbLog->_curFileNums++;
  }

  //unsigned int minModifyTime = 0;
  char baseFileName[300] = { 0 };
  int j = 0;
  int finishFlag = 0;
  int deleteNo = 0;
  //往前找
  for (i = 1; !finishFlag && i < pStRmbLog->_para._maxFileNum; i++)
  {
    pStRmbLog->_toDeleteFileNo = deleteNo;
    pStRmbLog->_toDeleteTime = curTime - 60 * 60 * 24 * i;
    snprintf (baseFileName, sizeof (baseFileName), "%s_%s.log",
              pStRmbLog->_para._path,
              RmbGetShortDateStr ((time_t *) & pStRmbLog->_toDeleteTime));
//              printf("%d_baseName=%s", i , baseFileName);
    int iNotNullFlag = 0;
    for (j = 1; j < pStRmbLog->_para._maxFileNum; j++)
    {
      snprintf (pStRmbLog->_toDeleteFileName,
                sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s.%d",
                baseFileName, i);
//                      printf("check fileName=%s is exist or not!", pStRmbLog->_toDeleteFileName);
      struct stat sb;
      if (stat (pStRmbLog->_toDeleteFileName, &sb) != 0)
      {
        if (iNotNullFlag == 1)
        {
//                                      printf("GetCurStateFromRmbLogByDaily logFile=%s not exist!", pStRmbLog->_toDeleteFileName);
          break;
        }
        if (j == pStRmbLog->_para._maxFileNum - 1)
        {
          if (iNotNullFlag == 0)
          {
            finishFlag = 1;
//                                              printf("GetCurStateFromRmbLogByDaily logFile=%s has't exist", pStRmbLog->_toDeleteFileName);
            break;
          }
        }

        continue;
      }
      if (deleteNo == 0)
      {
        deleteNo = j;
      }
      iNotNullFlag = 1;
      pStRmbLog->_curFileNums++;
    }
  }
  snprintf (pStRmbLog->_toDeleteFileName,
            sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s.1", baseFileName);
//      printf("Status:curFileNums=%u,mayBeDeleteFileName=%s", pStRmbLog->_curFileNums, pStRmbLog->_toDeleteFileName);
  return 0;
}

int GetDayInfo (StDayInfo * dayInfo, time_t * curTime)
{
  int i = 0;
  time_t minModifyTime = 0;
  dayInfo->_deleteFileNo = 1;
  dayInfo->_curFileNo = 0;
  dayInfo->_curFileNum = 0;

  snprintf (dayInfo->_baseFileName, sizeof (dayInfo->_baseFileName),
            "%s_%s.log", pStRmbLog->_para._path,
            RmbGetShortDateStr (curTime));

  //当天
  for (i = 1; i < pStRmbLog->_para._maxFileNum; i++)
  {
    snprintf (dayInfo->_logFileName, sizeof (dayInfo->_logFileName) - 1,
              "%s.%d", dayInfo->_baseFileName, i);
//              printf("GetDayInfo log file:%s", dayInfo->_logFileName);

    struct stat sb;
    if (stat (dayInfo->_logFileName, &sb) != 0)
    {
      //file not exist, use it
      if (dayInfo->_curFileNo == 0)
      {
        dayInfo->_curFileNo = i;
      }
//                      printf("GetDayInfo logFile=%s not exist!", dayInfo->_logFileName);
      continue;
      //break;        //之所以不用break, 因为存在消除该日期下其他日志的可能
    }
    dayInfo->_curFileNum++;
    //循环日志，需要按最后修改时间排序
    if (minModifyTime == 0 || sb.st_mtime < minModifyTime)
    {
      dayInfo->_deleteFileNo = i;
      minModifyTime = sb.st_mtime;
    }
  }
  return 0;
}

int GetDayInfoWithBreak (StDayInfo * dayInfo, time_t * curTime)
{
  int i = 0;
  time_t minModifyTime = 0;
  dayInfo->_deleteFileNo = 1;
  dayInfo->_curFileNo = 0;
  dayInfo->_curFileNum = 0;
  snprintf (dayInfo->_baseFileName, sizeof (dayInfo->_baseFileName),
            "%s_%s.log", pStRmbLog->_para._path,
            RmbGetShortDateStr (curTime));

  //当天
  for (i = 1; i < pStRmbLog->_para._maxFileNum; i++)
  {
    snprintf (dayInfo->_logFileName, sizeof (dayInfo->_logFileName) - 1,
              "%s.%d", dayInfo->_baseFileName, i);
//              printf("GetDayInfoWithBreak log file:%s", dayInfo->_logFileName);

    struct stat sb;
    if (stat (dayInfo->_logFileName, &sb) != 0)
    {
      //file not exist, use it
      if (dayInfo->_curFileNo == 0)
      {
        dayInfo->_curFileNo = i;
      }
//                      printf("GetDayInfoWithBreak logFile=%s not exist!", dayInfo->_logFileName);
      break;
    }
    dayInfo->_curFileNum++;
    //循环日志，需要按最后修改时间排序
    if (minModifyTime == 0 || sb.st_mtime < minModifyTime)
    {
      dayInfo->_deleteFileNo = i;
      minModifyTime = sb.st_mtime;
    }
  }
  return 0;
}

//统计现在有几个日志，当前日志马上要被rename成什么，是不是需要促发删除(同一个日期，按修改日期滚动)
int GetCurStateFromRmbLogByDaily_V3 ()
{
  pStRmbLog->_curFileNums = 1;
  int i = 0;
  //当前天
  time_t curTime = time (NULL);
  StDayInfo dayInfo;
  GetDayInfo (&dayInfo, &curTime);
  if (dayInfo._curFileNo == 0)
  {
    pStRmbLog->_curFileNo = dayInfo._deleteFileNo;
  }
  else
  {
    pStRmbLog->_curFileNo = dayInfo._curFileNo;
  }
  pStRmbLog->_curFileNums += dayInfo._curFileNum;
  pStRmbLog->_toDeleteFileNo = dayInfo._deleteFileNo;
  pStRmbLog->_toDeleteTime = curTime;
  snprintf (pStRmbLog->_logBaseFileName, sizeof (pStRmbLog->_logBaseFileName),
            "%s_%s.log", pStRmbLog->_para._path,
            RmbGetShortDateStr (&curTime));
  snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName) - 1,
            "%s.%d", pStRmbLog->_logBaseFileName, pStRmbLog->_curFileNo);

  //往前数
  for (i = 1; i < pStRmbLog->_para._maxFileNum; i++)
  {
    curTime = curTime - 60 * 60 * 24;
    GetDayInfo (&dayInfo, &curTime);
    if (dayInfo._curFileNum != 0)
    {
      pStRmbLog->_curFileNums += dayInfo._curFileNum;
      pStRmbLog->_toDeleteFileNo = dayInfo._deleteFileNo;
      pStRmbLog->_toDeleteTime = curTime;
    }
    else
    {
      break;
    }

  }

  snprintf (pStRmbLog->_toDeleteFileName,
            sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s_%s.%d",
            pStRmbLog->_para._path,
            RmbGetShortDateStr (&pStRmbLog->_toDeleteTime),
            pStRmbLog->_toDeleteFileNo);
//      printf("GetCurStateFromRmbLogByDaily_V3 Status:curFileNums=%u,mayBeDeleteFileName=%s", pStRmbLog->_curFileNums, pStRmbLog->_toDeleteFileName);
  return 0;
}

//统计现在有几个日志，当前日志马上要被rename成什么，是不是需要促发删除(同一个日期，按修改日期滚动)
int GetCurStateFromRmbLogByDaily_V2 ()
{
  pStRmbLog->_curFileNums = 1;
  int i = 0;
  snprintf (pStRmbLog->_logBaseFileName, sizeof (pStRmbLog->_logBaseFileName),
            "%s_%s.log", pStRmbLog->_para._path, RmbGetCurShortDateStr ());
  //strncpy(pStRmbLog->_logFileName, pStRmbLog->_logBaseFileName, sizeof(pStRmbLog->_logFileName) - 1);
  //int todayFileNo = 0;
  unsigned int curTime = time (NULL);
  //unsigned int maxModifyTime = 0;
  unsigned int minModifyTime = 0;

  pStRmbLog->_toDeleteFileNo = 1;
  //当天
  for (i = 1; i < pStRmbLog->_para._maxFileNum; i++)
  {
    snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName) - 1,
              "%s.%d", pStRmbLog->_logBaseFileName, i);
//              printf("GetCurStateFromRmbLogByDaily log file:%s", pStRmbLog->_logFileName);

    struct stat sb;
    if (stat (pStRmbLog->_logFileName, &sb) != 0)
    {
      //file not exist, use it
      pStRmbLog->_curFileNo = i;

//                      printf("GetCurStateFromRmbLogByDaily logFile=%s not exist!", pStRmbLog->_logFileName);
      break;
    }
    pStRmbLog->_toDeleteTime = curTime;
    pStRmbLog->_curFileNums++;

    //循环日志，需要按最后修改时间排序
    if (minModifyTime == 0 || sb.st_mtime < minModifyTime)
    {
      pStRmbLog->_toDeleteFileNo = i;
      minModifyTime = sb.st_mtime;
    }
  }

  char baseFileName[300] = { 0 };
  int j = 0;
  int finishFlag = 0;
  int deleteNo = 0;
  //往前
  for (i = 1; !finishFlag && i < pStRmbLog->_para._maxFileNum; i++)
  {
    pStRmbLog->_toDeleteTime = curTime - 60 * 60 * 24 * i;
    time_t uiDeleteTime = pStRmbLog->_toDeleteTime;
    snprintf (baseFileName, sizeof (baseFileName), "%s_%s.log",
              pStRmbLog->_para._path, RmbGetShortDateStr (&uiDeleteTime));
//              printf("%d_baseName=%s", i , baseFileName);
    int iNotNullFlag = 0;
    deleteNo = 0;
    //针对每一天查找
    for (j = 1; j < pStRmbLog->_para._maxFileNum; j++)
    {
      snprintf (pStRmbLog->_toDeleteFileName,
                sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s.%d",
                baseFileName, j);
//                      printf("check fileName=%s is exist or not!", pStRmbLog->_toDeleteFileName);
      struct stat sb;
      if (stat (pStRmbLog->_toDeleteFileName, &sb) != 0)
      {
        if (iNotNullFlag == 1)
        {
//                                      printf("GetCurStateFromRmbLogByDaily logFile=%s not exist!", pStRmbLog->_toDeleteFileName);
          break;
        }
        if (j == pStRmbLog->_para._maxFileNum - 1)
        {
          if (iNotNullFlag == 0)
          {
            finishFlag = 1;
            pStRmbLog->_toDeleteTime =
              pStRmbLog->_toDeleteTime + 60 * 60 * 24;
//                                              printf("GetCurStateFromRmbLogByDaily logFile=%s has't exist", pStRmbLog->_toDeleteFileName);
            break;
          }
        }

        continue;
      }
      if (deleteNo == 0)
      {
        deleteNo = j;
        pStRmbLog->_toDeleteFileNo = deleteNo;  //记录当前最应该删除的
      }
      iNotNullFlag = 1;         //表示前面不是空的，没有类似000111的情况
      pStRmbLog->_curFileNums++;
    }

  }
  time_t uiDeleteTime = pStRmbLog->_toDeleteTime;
  snprintf (baseFileName, sizeof (baseFileName), "%s_%s.log",
            pStRmbLog->_para._path, RmbGetShortDateStr (&uiDeleteTime));
  snprintf (pStRmbLog->_toDeleteFileName,
            sizeof (pStRmbLog->_toDeleteFileName) - 1, "%s.%d", baseFileName,
            pStRmbLog->_toDeleteFileNo);
//      printf("Status:curFileNums=%u,mayBeDeleteFileName=%s", pStRmbLog->_curFileNums, pStRmbLog->_toDeleteFileName);
  return 0;
}

//只在当天滚
int GetCurStateFromRmbLogByDaily_V4 ()
{
  pStRmbLog->_curFileNums = 0;
  //当前天
  /*
     time_t curTime = time(NULL);
     StDayInfo dayInfo;
     GetDayInfoWithBreak(&dayInfo, &curTime);
     if (dayInfo._curFileNo == 0)
     {
     pStRmbLog->_curFileNo = dayInfo._deleteFileNo;
     }
     else
     {
     pStRmbLog->_curFileNo = dayInfo._curFileNo;
     }
     pStRmbLog->_curFileNums = dayInfo._curFileNum;
     pStRmbLog->_toDeleteFileNo = dayInfo._curFileNo;
     pStRmbLog->_toDeleteTime = curTime;
   */
  pStRmbLog->_curFileNo = 0;
  time_t curTime = time (NULL);
  pStRmbLog->_lastShiftTime = curTime;
  GetCurFileNumFromStatFile ();
  pStRmbLog->_toDeleteFileNo = pStRmbLog->_curFileNo;

  snprintf (pStRmbLog->_logBaseFileName, sizeof (pStRmbLog->_logBaseFileName),
            "%s_%s.log", pStRmbLog->_para._path,
            RmbGetShortDateStr (&curTime));
  snprintf (pStRmbLog->_logFileName, sizeof (pStRmbLog->_logFileName) - 1,
            "%s.%d", pStRmbLog->_logBaseFileName, pStRmbLog->_curFileNo);
  strncpy (pStRmbLog->_toDeleteFileName, pStRmbLog->_logFileName,
           sizeof (pStRmbLog->_toDeleteFileName) - 1);
  return 0;
}

void _Log_Thread_func (void *args)
{
  struct timeval nowTimeVal;
  gettimeofday (&nowTimeVal, NULL);
  unsigned long ulNowTime =
    (unsigned long) nowTimeVal.tv_sec * (unsigned long) 1000UL +
    (unsigned long) nowTimeVal.tv_usec / 1000UL;
  unsigned long lastPrintTime = 0;

  struct timeval tv_now;
  unsigned long ulLastGetConfigTime = 0;
  unsigned long ulLastGetAccessTime = 0;

  // char url[512];
  // snprintf(url, sizeof(url), "http://%s:%d/%s/%s/%s/%s", pRmbStConfig->cConfigIp, pRmbStConfig->iConfigPort, RMB_REQ_SINGLE, RMB_WHITE_LIST_SND, pRmbStConfig->cConsumerSysId, pRmbStConfig->cRegion);

  while (1)
  {
    int i = 0;
    if ((pRmbStConfig->iWemqUseHttpCfg == 1))
    {
      gettimeofday (&tv_now, NULL);
      if (tv_now.tv_sec >
          (ulLastGetAccessTime + pRmbStConfig->getAccessIpPeriod))
      {
        ulLastGetAccessTime = tv_now.tv_sec;
        //int iRet = wemq_proxy_load_servers(cAccessIpUrl, pRmbStConfig->iConfigTimeout, pRmbStConfig->cWemqSavePath);
        char cAccessIpUrl[512];
        char addrArr[15][50] = { 0 };
        int lenAddrs = 0;
        char tmpAddrs[512] = { 0 };
        strcpy (tmpAddrs, pRmbStConfig->ConfigAddr);
        LOGRMB (RMB_LOG_DEBUG,
                "config center ip str is:%s,try get access ip from the %d config center in list,lists:",
                tmpAddrs, pRmbStConfig->configIpPosInArr + 1);
        split_str (tmpAddrs, addrArr, &lenAddrs);
        int j = 0;
        for (j = 0; j < lenAddrs; ++j)
        {
          LOGRMB (RMB_LOG_DEBUG, "%s", addrArr[j]);
        }
        if (lenAddrs == 0)
        {
          LOGRMB (RMB_LOG_ERROR,
                  "config center addr list len is 0,please check if configCenterAddrMulti is empty in conf file");
          return -1;
        }

        int iRet = 0;
        int i = 0;
        for (i = 0; i < lenAddrs; ++i)
        {
          memset (&cAccessIpUrl, 0x00, sizeof (cAccessIpUrl));
          snprintf (cAccessIpUrl, sizeof (cAccessIpUrl), "%s/%s",
                    addrArr[(i + pRmbStConfig->configIpPosInArr) % lenAddrs],
                    WEMQ_ACCESS_SERVER);
          LOGRMB (RMB_LOG_DEBUG, "try to get access addr from %s",
                  cAccessIpUrl);
          iRet =
            wemq_proxy_load_servers (cAccessIpUrl,
                                     pRmbStConfig->iConfigTimeout);

          if (iRet != 0)
          {
            LOGRMB (RMB_LOG_WARN,
                    "get access ip from %s failed,try next config center ip",
                    cAccessIpUrl);
            continue;
          }
          else
          {
            LOGRMB (RMB_LOG_INFO,
                    "get all access ip list result=%d from url:%s", iRet,
                    cAccessIpUrl);
            pRmbStConfig->configIpPosInArr =
              pRmbStConfig->configIpPosInArr + i;
            break;
          }
        }

        // free addrArr
      }
    }

    sleep (1);
  }
}
