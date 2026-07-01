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

#include <malloc.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include "rmb_errno.h"

char *p_err_msg[RMB_MAX_ERR_NUMS] = { 0 };

struct rmb_err_msg rmb_err_msg_array[] = {
  {RMB_ERROR_ARGV_NULL, "argv is null"},
  {RMB_ERROR_ARGV_LEN_ERROR, "argv len error"},
  {RMB_ERROR_MALLOC_FAIL, "malloc fail"},
  {RMB_ERROR_INIT_CONTEXT_FAIL, "init context fail"},
  {RMB_ERROR_MSG_MISSING_PART, "msg missing necessary part"},
  {RMB_ERROR_RR_INTERFACE_CAN_NOT_SEND_EVENT_MSG,
   "rr interface can't send event msg"},
  {RMB_ERROR_EVENT_INTERFACE_CAN_NOT_SEND_RR_MSG,
   "event msg can't send rr msg"},
  {RMB_ERROR_NOW_CAN_NOT_SEND_MSG, "now can't send msg"},
  {RMB_ERROR_QUEUE_FULL, "queue is full"},
  {RMB_ERROR_GSL_SERVICE_ID_NULL, "gsl service id null"},
  {RMB_ERROR_GSL_SERVICE_ID_ERROR, "gsl service id error"},
  {RMB_ERROR_GSL_SVR_ERROR, "gsl svr return error"},
  {RMB_ERROR_REQ_GSL_ERROR, "req gsl error"},
  {RMB_ERROR_MSG_UUID_FAIL, "msg generate uuid fail"},
  {RMB_ERROR_MSG_SET_SYSTEMHEADER_FAIL, "msg set systemheadfer fail"},
  {RMB_ERROR_SEND_GET_SESSION_FAIL, "send get session fail"},
  {RMB_ERROR_SEND_EVENT_MSG_FAIL, "send event msg fail"},
  {RMB_ERROR_SEND_RR_MSG_FAIL, "send rr msg fail"},
  {RMB_ERROR_SEND_RR_MSG_TIMEOUT, "send rr msg timeout"},
  {RMB_ERROR_REPLY_TO_NULL, "reply to is null"},
  {RMB_ERROR_REPLY_FAIL, "reply msg fail"},
  {RMB_ERROR_SESSION_CONNECT_FAIL, "session connect fail"},
  {RMB_ERROR_SESSION_RECONNECT_FAIL, "session reconnect fail"},
  {RMB_ERROR_SESSION_DESTORY_FAIL, "session destory fail"},
  {RMB_ERROR_SESSION_NUMS_LIMIT, "session nums limit"},
  {RMB_ERROR_LISTEN_QUEUE_NOT_EXIST, "listen queue not exist"},
  {RMB_ERROR_LISTEN_QUEUE_FAIL, "listen queue fail"},
  {RMB_ERROR_FLOW_DESTORY_FAIL, "flow destory fail"},
  {RMB_ERROR_LISTEN_TOPIC_FAIL, "listen topic fail"},
  {RMB_ERROR_INIT_MQ_FAIL, "init mq fail"},
  {RMB_ERROR_INIT_FIFO_FAIL, "init fifo fail"},
  {RMB_ERROR_INIT_UDP_FAIL, "init udp fail"},
  {RMB_ERROR_INIT_EPOLL_FAIL, "init epoll fail"},
  {RMB_ERROR_MQ_NUMS_LIMIT, "mq nums limit"},
  {RMB_ERROR_ENQUEUE_MQ_FAIL, "enqueue mq fail"},
  {RMB_ERROR_DEQUEUE_MQ_FAIL, "dequeue mq fail"},
  {RMB_ERROR_MSG_2_BUF_FAIL, "shift msg to buf fail"},
  {RMB_ERROR_BUF_2_MSG_FAIL, "shift buf to msg fail"},
  {RMB_ERROR_MSG_SET_CONTENT_TOO_LARGE, "msg set content too large"},
  {RMB_ERROR_MSG_SET_APPHEADER_TOO_LARGE, "msg set appheader too large"},
  {RMB_ERROR_MSG_TTL_0, "message ttl can't be 0"},
  {RMB_ERROR_RCV_MSG_GET_CONTENT_FAIL, "receive msg has no content"},
  {RMB_ERROR_RCV_MSG_GET_BINARY_FAIL, "receive msg has no binary"},
  {RMB_ERROR_RCV_MSG_CONTENT_TOO_LARGE, "receive msg content too large"},
  {RMB_ERROR_RCV_MSG_APPHEADER_TOO_LARGE,
   "receive message appheader too large"},
  {RMB_ERROR_MANAGE_MSG_PKG_ERROR, "manage msg format error"},
  {RMB_ERROR_MANAGE_MSG_PKG_CHECK_FAIL, "manage msg check fail"},
  {RMB_ERROR_CONTEXT_CREATE_FAIL, "context create fail"},
  {RMB_ERROR_FIFO_PARA_ERROR, "fifo para error"},
  {RMB_ERROR_SHM_PARA_ERROR, "shm para error"},
  {RMB_ERROR_SEND_FIFO_NOTIFY_ERROR, "send notify fail"},
  {RMB_ERROR_FLOW_NUMS_LIMIT, "flow nums limit"},
  {RMB_ERROR_MSG_GET_SYSTEMHEADER_ERROR, "get system header error"},
  {RMB_ERROR_RMB_MSG_2_SOLACE_MSG_ERROR, "copy rmb msg 2 solace msg error"},
  {RMB_ERROR_SOLACE_MSG_2_RMB_MSG_ERROR, "copy solace msg 2 rmb msg error"},
  {RMB_ERROR_NO_AVAILABLE_SESSION, "no available session"},
  {RMB_ERROR_NO_BROADCAST_SESSION, "no broadcast session"},
  {RMB_ERROR_NO_AVAILABLE_CONTEXT, "no available context"},
  {RMB_ERROR_SET_FLOW_PROPETY, "set flow property fail"},
  {RMB_ERROR_ACK_MSG_FAIL, "ack msg fail"},
  {RMB_ERROR_LOGIC_NOTIFY_INIT, "notify for logic init error"},
  {RMB_ERROR_SESSION_ADD_FAIL, "session add fail"},
  {RMB_ERROR_WORKER_NUMS_LIMIT, "worker nums limit"},
  {RMB_ERROR_BUF_NOT_ENOUGH, "buf not enough"},
  {RMB_ERROR_STOP_FLOW_ERROR, "stop flow receive error"},
  {RMB_ERROR_DEQUEUE_MQ_EMPTY, "dequeue empty"},
  {RMB_ERROR_WORKER_WINDOW_FULL, "worker send window full"},
  {RMB_ERROR_WORKER_PUT_FIFO_ERROR, "worker put msg into fifo error"},
  {RMB_ERROR_START_CALL_FAIL, "send log for start call error"},
  {RMB_ERROR_END_CALL_FAIL, "send log for end call error"},
  {RMB_ERROR_ENTRY_FAIL, "send log for entry error"},
  {RMB_ERROR_EXIT_FAIL, "send log for exit error"},
  {RMB_ERROR_BASE_BEGIN, NULL}
};

static pthread_key_t rmb_user_key;
static pthread_once_t rmb_user_key_once = PTHREAD_ONCE_INIT;

static void rmb_make_key ()
{
  (void) pthread_key_create (&rmb_user_key, NULL);
}

int *rmb_error ()
{
  int *ptr;

  (void) pthread_once (&rmb_user_key_once, rmb_make_key);
  if ((ptr = pthread_getspecific (rmb_user_key)) == NULL)
  {
    ptr = malloc (sizeof (int));
    memset (ptr, 0, sizeof (int));
    (void) pthread_setspecific (rmb_user_key, ptr);
  }
  return ptr;
}

void init_error ()
{
  int i = 0;
  for (; rmb_err_msg_array[i].err_msg != NULL && i < RMB_MAX_ERR_NUMS; i++)
  {
    p_err_msg[rmb_err_msg_array[i].err_no - RMB_ERROR_BASE_BEGIN] =
      rmb_err_msg_array[i].err_msg;
  }
  rmb_errno = 0;
}

const char *get_rmb_last_error ()
{
  if (rmb_errno == 0)
  {
    return "ok";
  }
  if (rmb_errno <= RMB_ERROR_BASE_BEGIN
      && rmb_errno >= RMB_ERROR_BASE_BEGIN + RMB_MAX_ERR_NUMS)
  {
    return "unknown errorno";
  }
  return ((p_err_msg[rmb_errno - RMB_ERROR_BASE_BEGIN] ==
           NULL) ? "unknown errorno" : p_err_msg[rmb_errno -
                                                 RMB_ERROR_BASE_BEGIN]);
}

void rmb_reset_error ()
{
  rmb_errno = 0;
}
