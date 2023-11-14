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

#include "wemq_tcp.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/select.h>
#include <strings.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <errno.h>
#include <wemq_proto.h>
#include "rmb_define.h"

// return=0: timeout; return<0: error; return>0: success.
static int check_recv (int fd, int iSec, int iMs)
{
  fd_set rset;
  int ret;

  FD_ZERO (&rset);
  FD_SET (fd, &rset);

  struct timeval tv;
  tv.tv_sec = iSec;
  tv.tv_usec = iMs * 1000;

  do
  {
    ret = select (fd + 1, &rset, NULL, NULL, &tv);
  }
  while (ret < 0 && errno == EINTR);

  // 如果rset只有一个socket, 不需要去判断FD_ISSET
  if (ret > 0 && FD_ISSET (fd, &rset))
  {
    return 1;
  }

  return (ret == 0 ? 0 : -1);
}

// return=0: timeout; return<0: error; return>0: success.
static int check_send (int fd, int iSec, int iMs)
{
  fd_set wset;
  int ret;

  FD_ZERO (&wset);
  FD_SET (fd, &wset);

  struct timeval tv;
  tv.tv_sec = iSec;
  tv.tv_usec = iMs * 1000;

  do
  {
    ret = select (fd + 1, NULL, &wset, NULL, &tv);
  }
  while (ret < 0 && errno == EINTR);

  // 如果rset只有一个socket, 不需要去判断FD_ISSET
  if (ret > 0 && FD_ISSET (fd, &wset))
  {
    return 1;
  }

  return (ret == 0 ? 0 : -1);
}

//************************************
// Method:    wemq_tcp_connect
// FullName:  wemq_tcp_connect
// Access:    public 
// Returns:   int       if connect success return socket fd, else return -1
// Qualifier:
// Parameter: const char * ip
// Parameter: uint16_t port
// Parameter: int timeout：单位为ms
//************************************
int wemq_tcp_connect (const char *ip, uint16_t port, int timeout)
{
  int sockfd = -1;
  struct sockaddr_in servaddr;
  int flag = 0;
  int ret = 0;

  if ((sockfd = socket (AF_INET, SOCK_STREAM, 0)) < 0)
  {
    return -1;
  }
  if (timeout > 0)
  {
    flag = fcntl (sockfd, F_GETFL) | O_NONBLOCK;
    fcntl (sockfd, F_SETFL, flag);
  }

  bzero (&servaddr, sizeof (servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_port = htons (port);
  if (inet_pton (AF_INET, ip, &servaddr.sin_addr) <= 0)
  {
    close (sockfd);
    return -2;
  }

  do
  {
    ret =
      connect (sockfd, (struct sockaddr *) &servaddr,
               sizeof (struct sockaddr_in));
  }
  while (ret < 0 && errno == EINTR);
  if ((ret != 0) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS))
  {
    close (sockfd);
    return -3;
  }

  // 判断是否需要超时
  if (timeout > 0)
  {
    fd_set write_set;
    struct timeval tv;
    int sock_err = 0;
    int sock_err_len = sizeof (sock_err);

    FD_ZERO (&write_set);
    FD_SET (sockfd, &write_set);
    tv.tv_sec = timeout / 1000;
    tv.tv_usec = (timeout % 1000) * 1000;

    do
    {
      ret = select (FD_SETSIZE, NULL, &write_set, NULL, &tv);
    }
    while (ret < 0 && errno == EINTR);
    if (ret <= 0 || !FD_ISSET (sockfd, &write_set))
    {
      close (sockfd);
      return 0;
    }

    //getscokopt: check socket connect
    if (getsockopt
        (sockfd, SOL_SOCKET, SO_ERROR, (char *) &sock_err,
         (socklen_t *) & sock_err_len) != 0)
    {
      close (sockfd);
      return -4;
    }
    if (sock_err != 0)
    {
      close (sockfd);
      return -5;
    }

    //block
    flag &= ~O_NONBLOCK;
    fcntl (sockfd, F_SETFL, flag);
  }
  return sockfd;
}

//************************************
// Method:    wemq_tcp_send
// FullName:  wemq_tcp_send
// Access:    public 
// Returns:   int       0 for success, -1 for error
// Qualifier:
// Parameter: int fd    connected socket
// Parameter: char * msg        message buffer
// Parameter: uint32_t len      message length
//************************************
int wemq_tcp_send (int fd, void *msg, uint32_t totalLen, uint32_t headerLen,
                   int iTimeOut, SSL * ssl)
{
  //uint32_t uiHeadRemain = 4;
  //uint32_t uiHeadSend = 0;
  if (fd < 0 || msg == NULL)
  {
    return -1;
  }
  if (iTimeOut <= 0)
  {
    iTimeOut = 1000;
  }
  /*
     while (uiHeadRemain > 0)
     {
     iRet = check_send(fd, iTimeOut/1000, iTimeOut%1000);
     if (iRet == 0)
     {
     return 0;
     }
     else if (iRet < 0)
     {
     LOGRMB(RMB_LOG_ERROR, "check_send error, errno=%d\n", errno);
     return -2;
     }
     iRet = send(fd, "JSON", uiHeadRemain, 0);
     if (iRet < 0)
     {
     LOGRMB(RMB_LOG_ERROR, "send error, errno=%d\n", errno);
     return -2;
     }
     uiHeadSend += iRet;
     uiHeadRemain -= iRet;
     }
   */
  int iRet;
  uint32_t uiRemain = totalLen + 8;
  uint32_t uiSend = 0;
  char *msg_temp = (char *) malloc ((totalLen + 8) * sizeof (char));
  char *msg_new = msg_temp;
  ENCODE_DWSTR_MEMCPY (msg_temp, "WEMQ", 4);
  ENCODE_DWSTR_MEMCPY (msg_temp, "0000", 4);
  ENCODE_DWSTR_MEMCPY (msg_temp, msg, totalLen);
  // LOGRMB(RMB_LOG_DEBUG, "tcp data=%s\n", msg_temp);
  //LOGRMB(RMB_LOG_DEBUG, "tcp data2=%s\n", (char *)msg);
  while (uiRemain > 0)
  {
    iRet = check_send (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_send error, errno=%d\n", errno);
      return -2;
    }
    if (NULL != ssl)
    {
      iRet = SSL_write (ssl, msg_new + uiSend, uiRemain);
    }
    else
    {
      iRet = send (fd, msg_new + uiSend, uiRemain, 0);
    }
    if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "send error, errno=%d, iRet=%d\n", errno, iRet);
      return -2;
    }
    uiSend += iRet;
    uiRemain -= iRet;
  }
  free (msg_new);
  return uiSend;
}

int wemq_ssl_recv (int fd, void *msg, uint32_t * len, int iTimeOut, SSL * ssl)
{
  int iRet;
  uint32_t uiRemain, uiRecved;

  uiRemain = 4;
  uiRecved = 0;
  if (fd < 0 || msg == NULL || len == NULL)
  {
    return -1;
  }
  if (iTimeOut <= 0)
  {
    iTimeOut = 1000;
  }
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }
    iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }

  if (uiRecved != 4 || strcmp (msg, "WEMQ") != 0)
  {
    LOGRMB (RMB_LOG_ERROR, "recv msg header error,%u:%s", uiRecved,
            (char *) msg);
    return -3;
  }

  uiRemain = 4;
  uiRecved = 0;
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    if (iRecv > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
    if (0 == uiRemain)
    {
      break;
    }

    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }
    if (NULL != ssl)
    {
      iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    }
    else
    {
      iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    }
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }
  //LOGRMB(RMB_LOG_DEBUG, "Recv version = %s\n", (char *)msg);
  if (uiRecved != 4)
  {
    LOGRMB (RMB_LOG_ERROR, "recv msg version error,%u:%s", uiRecved,
            (char *) msg);
    return -3;
  }

  // read msg length
  uiRemain = 4;
  uiRecved = 0;
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    if (iRecv > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
    if (0 == uiRemain)
    {
      break;
    }
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }

    if (NULL != ssl)
    {
      iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    }
    else
    {
      iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    }
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }

  if (uiRecved != 4)
  {
    // can't read 4 bytes
    LOGRMB (RMB_LOG_ERROR, "recv less than 4, recv %d bytes\n", uiRecved);
    return -3;
  }

  *len = ntohl (*(uint32_t *) msg);

  uiRemain = *len - 4;
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    if (iRecv > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
    if (0 == uiRemain)
    {
      break;
    }
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }

    if (NULL != ssl)
    {
      iRecv = SSL_read (ssl, msg + uiRecved, uiRemain);
    }
    else
    {
      iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    }
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }

  if (uiRecved != *len)
  {
    // can't recv whole msg
    return -3;
  }

  return uiRecved;
}

//************************************
// Method:    wemq_tcp_recv
// FullName:  wemq_tcp_recv
// Access:    public 
// Returns:   int       0 for success, -1 for error
// Qualifier:
// Parameter: int fd    connected socket
// Parameter: void * msg        receive message buffer
// Parameter: uint32_t * len    receive message length
//************************************
int wemq_tcp_recv (int fd, void *msg, uint32_t * len, int iTimeOut, SSL * ssl)
{
  int iRet;
  uint32_t uiRemain, uiRecved;

  if (NULL != ssl)
  {
    return wemq_ssl_recv (fd, msg, len, iTimeOut, ssl);
  }

  uiRemain = 4;
  uiRecved = 0;
  if (fd < 0 || msg == NULL || len == NULL)
  {
    return -1;
  }
  if (iTimeOut <= 0)
  {
    iTimeOut = 1000;
  }
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }
    iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }

  if (uiRecved != 4 || strcmp (msg, "WEMQ") != 0)
  {
    LOGRMB (RMB_LOG_ERROR, "recv msg header error,%u:%s", uiRecved,
            (char *) msg);
    return -3;
  }

  uiRemain = 4;
  uiRecved = 0;
  if (fd < 0 || msg == NULL || len == NULL)
  {
    return -1;
  }
  if (iTimeOut <= 0)
  {
    iTimeOut = 1000;
  }
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }
    iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }
  //LOGRMB(RMB_LOG_DEBUG, "Recv version = %s\n", (char *)msg);
  if (uiRecved != 4)
  {
    LOGRMB (RMB_LOG_ERROR, "recv msg version error,%u:%s", uiRecved,
            (char *) msg);
    return -3;
  }

  // read msg length
  uiRemain = 4;
  uiRecved = 0;
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }

    iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }

  if (uiRecved != 4)
  {
    // can't read 4 bytes
    LOGRMB (RMB_LOG_ERROR, "recv less than 4, recv %d bytes\n", uiRecved);
    return -3;
  }

  *len = ntohl (*(uint32_t *) msg);

  uiRemain = *len - 4;
  while (uiRemain > 0)
  {
    int iRecv = 0;
    iRet = check_recv (fd, iTimeOut / 1000, iTimeOut % 1000);
    if (iRet == 0)
    {
      return 0;
    }
    else if (iRet < 0)
    {
      LOGRMB (RMB_LOG_ERROR, "check_recv error, error=%d\n", errno);
      return -2;
    }

    iRecv = recv (fd, msg + uiRecved, uiRemain, 0);
    if (iRecv < 0 && (errno != EAGAIN))
    {
      LOGRMB (RMB_LOG_ERROR, "Recv error, fd close() error=%d, iRecv=%d\n",
              errno, iRecv);
      return -2;
    }
    if (iRecv == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "Peer close connect, fd close() errno=%d\n",
              errno);
      return -2;
    }
    if (iRet > 0)
    {
      uiRecved += iRecv;
      uiRemain -= iRecv;
    }
  }

  if (uiRecved != *len)
  {
    // can't recv whole msg
    return -3;
  }

  return uiRecved;
}

int wemq_tcp_close (int fd, SSL * ssl)
{
  if (NULL != ssl)
  {
    SSL_shutdown (ssl);
    SSL_free (ssl);
  }
  if (fd >= 0)
  {
    close (fd);
  }

  return 0;
}

// 获取当前socket本地的IP和端口
void wemq_getsockename (int fd, char *ip, uint32_t len, int *port)
{
  struct sockaddr_in guest;
  socklen_t guest_len = sizeof (guest);

  if (fd <= 0 || port == NULL)
  {
    return;
  }
  if (getsockname (fd, (struct sockaddr *) &guest, (socklen_t *) & guest_len)
      != 0)
  {
    return;
  }
  *port = ntohs (guest.sin_port);
  if (ip != NULL && len > 0)
  {
    inet_ntop (AF_INET, &guest.sin_addr, ip, len);
  }
}

// 获取当前socket远端的IP和端口
void wemq_getpeername (int fd, char *ip, uint32_t len, int *port)
{
  struct sockaddr_in svr;
  socklen_t svr_len = sizeof (svr);

  if (fd <= 0 || port == NULL)
  {
    return;
  }
  if (getpeername (fd, (struct sockaddr *) &svr, (socklen_t *) & svr_len) !=
      0)
  {
    return;
  }
  *port = ntohs (svr.sin_port);
  if (ip != NULL && len > 0)
  {
    inet_ntop (AF_INET, &svr.sin_addr, ip, len);
  }
}
