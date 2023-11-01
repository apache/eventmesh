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

#ifndef _UDP_SOCKET_FOR_RMB_H_
#define _UDP_SOCKET_FOR_RMB_H_

#ifdef __cplusplus
extern "C"
{
#endif

#include <sys/socket.h>
#include <netinet/in_systm.h>   /* required for ip.h */
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <net/if.h>
#include <time.h>
#include <sys/time.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>

////////////////////////
#include "rmb_define.h"
////////////////////////

  typedef struct sockaddr_in StSockAddr;

  int udp_get_socket (const char *host, const char *serv,
                      socklen_t * addrlenp);

  int udp_server (const char *pszHost, unsigned short usPort);

  int check_socket (int iSocket, fd_set * stReadFds, int iNFd);

  int check_socket_with_timeout (int iSocket, fd_set * pStReadFds, int iNfd,
                                 int sec, int usec);

  int check_and_process_socket (int iSocket, fd_set * stReadFds,
                                char *cPkgBuf, const unsigned int uiMaxLen,
                                unsigned int *pPkgLen);

  int tcp_nodelay (int iSockfd);

  int get_host_name (char *hostName, unsigned int uiHostNameLen);

  int get_local_ip (char *addr, unsigned int uiAddrLen);

  int get_local_ip_v2 (char *addr, unsigned int uiAddrLen);

#ifdef __cplusplus
}
#endif

#endif
