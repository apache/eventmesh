#include "rmb_udp.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <time.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <signal.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <string.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <arpa/inet.h>
#include <net/if.h>

int udp_get_socket(const char *host, const char *serv, socklen_t * addrlenp)
{
	int iUDPSocket;
	int iRet;

	struct addrinfo hints;
	struct addrinfo *res, *ressave;
	
	memset(&hints, 0,  sizeof(struct addrinfo));
	hints.ai_flags = AI_PASSIVE;
	hints.ai_family = AF_UNSPEC;
	hints.ai_socktype = SOCK_DGRAM;
	if((iRet = getaddrinfo(host, serv, &hints, &res)) != 0)
	{
		return -1;
	}
	ressave = res;

	do
	{
		iUDPSocket = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
		if(iUDPSocket < 0)
			continue;   /* error, try next one */

		if(bind(iUDPSocket, res->ai_addr, res->ai_addrlen) == 0)
			break;  /* success */

		close(iUDPSocket); /* bind error, close and try next one */
	} while((res = res->ai_next) != NULL);

	if(res == NULL) /* errno from final socket() or bind() */
		return -1;
	//err_sys("udp_server error for %s, %s", host, serv);

	if(addrlenp)
		*addrlenp = res->ai_addrlen;    /* return size of protocol address */

	freeaddrinfo(ressave);

	if(iUDPSocket < 0)
	{
		return -1;
		//err_sys("UDP_GetSocket error : %s", strerror(errno));
	}
	return iUDPSocket;
}



int udp_server(const char *pszHost, unsigned short usPort)
{
	int udpsock;
	struct hostent *hostinfo;
	struct in_addr *addp;
	struct sockaddr_in sockname;

	memset((char *) &sockname, 0, sizeof(sockname));
	if(pszHost == NULL)
	{
		hostinfo = NULL;
	}
	else if((hostinfo = gethostbyname(pszHost)) == NULL)
	{
		//err_msg("Cannot find %s - %s",pszHost,strerror(errno));
		return -1;
	}

	udpsock = socket(AF_INET, SOCK_DGRAM, 0);
	if(udpsock < 0)
	{
		//err_msg("Error opening socket - %s",strerror(errno));
		return -1;
	}
	//setsockopt(udpsock, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));
	if(hostinfo != NULL)
	{
		addp = (struct in_addr *) *(hostinfo->h_addr_list);
		sockname.sin_addr = *addp;
	}
	else
	{
		sockname.sin_addr.s_addr = INADDR_ANY;
	}
	sockname.sin_family = AF_INET;
	sockname.sin_port = htons(usPort);

	if((bind(udpsock, (struct sockaddr *) &sockname, sizeof(sockname))) == -1)
	{
		close(udpsock);
		//err_msg("Cannot bind port %i at %s -%s",usPort,pszHost,strerror(errno));
		return -1;
	}

	return (udpsock);
}

int check_socket(int iSocket, fd_set *pStReadFds, int iNfd)
{
	struct timeval stTimeVal;

	FD_ZERO(pStReadFds);
	FD_SET(iSocket, pStReadFds);
	stTimeVal.tv_sec = 0;
	stTimeVal.tv_usec = 5000;

	if (select(iNfd, pStReadFds, NULL, NULL, &stTimeVal) >0)
	{
		return 0;
	}
	return -1;
}

int check_socket_with_timeout(int iSocket, fd_set *pStReadFds, int iNfd, int sec, int usec)
{
	struct timeval stTimeVal;

	FD_ZERO(pStReadFds);
	FD_SET(iSocket, pStReadFds);
	stTimeVal.tv_sec = sec;
	stTimeVal.tv_usec = usec;

	if (select(iNfd, pStReadFds, NULL, NULL, &stTimeVal) >0)
	{
		return 0;
	}
	return -1;
}

int check_and_process_socket(int iSocket, fd_set *pStReadFds, char *cPkgBuf, const unsigned int uiMaxLen, unsigned int *pPkgLen)
{
	if (FD_ISSET(iSocket, pStReadFds))
	{
		struct sockaddr_in stFromAddr;
		socklen_t iAddrLength = sizeof(stFromAddr);
		*pPkgLen = 0;
		//LOGSYS(RMB_LOG_DEBUG, " sizeof(cPkgBuf)=%lu", sizeof(cPkgBuf));

		int iRet = recvfrom(iSocket, cPkgBuf, uiMaxLen,
			0, (struct sockaddr*)&stFromAddr, &iAddrLength);
		if (iRet > 0)
		{
			*pPkgLen = iRet;
		}
		return iRet;
	}
	return 0;
}

int tcp_nodelay(int iSockfd)
{
	int i;

	if((i = fcntl(iSockfd, F_GETFL, 0)) == -1)
		return (-1);
	else if(fcntl(iSockfd, F_SETFL, i | FNDELAY) == -1)
		return (-1);
	return 0;
}

int get_host_name(char* hostName, unsigned int uiHostNameLen)
{
	struct hostent *host;			//���������Ϣ
// 	while ()
// 	{
// 
// 	}
	
	if((host = gethostent()) == NULL)
	{
		LOGRMB(RMB_LOG_ERROR, " fail to get host's information");
		return -1;
	}
	strncpy(hostName, host->h_name, uiHostNameLen);
	
	while( (host = gethostent()) != NULL )  
	{  
	} 
	endhostent();
	//LOGSYS(LOG_ERROR, "%s hostName: %s,%s,uiHostName=%u" , __func__, hostName, host->h_name, uiHostNameLen);
	return 0;
}


int get_local_ip(char* addr, unsigned int uiAddrLen)
{
	int i=0;
	int sockfd;
	struct ifconf ifconf;
	char buf[512];
	struct ifreq *ifreq;
	char* ip;

	ifconf.ifc_len = 512;
	ifconf.ifc_buf = buf;

	if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
	{
		return -1;
	}
	ioctl(sockfd, SIOCGIFCONF, &ifconf);
	close(sockfd);

	ifreq = (struct ifreq*)buf;
	for (i=(ifconf.ifc_len/sizeof(struct ifreq)); i>0; i--)
	{
		ip = inet_ntoa(((struct sockaddr_in*)&(ifreq->ifr_addr))->sin_addr);

		if (strcmp(ip,"127.0.0.1")==0)
		{
			ifreq++;
			continue;
		}

		strncpy(addr,ip,uiAddrLen);
		//LOGSYS(LOG_ERROR, "%s HostIp: %s,%s,len=%u" , __func__, addr,ip, uiAddrLen);
		return 0;
	}

	return -1;
}

/**
 * 2.0.10版本增加,逻辑为:
 * 	通过获取环境变量networkInterface的值,来获取对应的ip
 * 		如果失败,则返回默认值
 * 	默认获取方式: bond1 > eth1 > eth0
 */
int get_local_ip_v2(char* addr, unsigned int uiAddrLen)
{
	char *val = NULL;

	val = getenv("networkInterface");

	int i=0;
	int sockfd;
	struct ifconf ifconf;
	char buf[512];
	struct ifreq *ifreq;
	char* ip;

	ifconf.ifc_len = 512;
	ifconf.ifc_buf = buf;

	if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
	{
		return -1;
	}
	ioctl(sockfd, SIOCGIFCONF, &ifconf);
	close(sockfd);

	//first getenv
	if (val != NULL) {
		ifreq = (struct ifreq*)buf;
		for (i=(ifconf.ifc_len/sizeof(struct ifreq)); i>0; i--)
		{
			if (strcmp(ifreq->ifr_name, val) != 0) {
				ifreq++;
				continue;
			}

			ip = inet_ntoa(((struct sockaddr_in*)&(ifreq->ifr_addr))->sin_addr);

			if (strcmp(ip,"127.0.0.1")==0)
			{
				ifreq++;
				continue;
			}

			strncpy(addr,ip,uiAddrLen);
			//LOGSYS(LOG_ERROR, "%s HostIp: %s,%s,len=%u" , __func__, addr,ip, uiAddrLen);
			return 0;
		}
	}

	//bond1
	{
		ifreq = (struct ifreq*)buf;
		for (i=(ifconf.ifc_len/sizeof(struct ifreq)); i>0; i--)
		{
			if (strcmp(ifreq->ifr_name, "bond1") != 0) {
				ifreq++;
				continue;
			}

			ip = inet_ntoa(((struct sockaddr_in*)&(ifreq->ifr_addr))->sin_addr);

			if (strcmp(ip,"127.0.0.1")==0)
			{
				ifreq++;
				continue;
			}

			strncpy(addr,ip,uiAddrLen);
			//LOGSYS(LOG_ERROR, "%s HostIp: %s,%s,len=%u" , __func__, addr,ip, uiAddrLen);
			return 0;
		}
	}

	//eth1
	{
		ifreq = (struct ifreq*)buf;
		for (i=(ifconf.ifc_len/sizeof(struct ifreq)); i>0; i--)
		{
			if (strcmp(ifreq->ifr_name, "eth1") != 0) {
				ifreq++;
				continue;
			}

			ip = inet_ntoa(((struct sockaddr_in*)&(ifreq->ifr_addr))->sin_addr);

			if (strcmp(ip,"127.0.0.1")==0)
			{
				ifreq++;
				continue;
			}

			strncpy(addr,ip,uiAddrLen);
			//LOGSYS(LOG_ERROR, "%s HostIp: %s,%s,len=%u" , __func__, addr,ip, uiAddrLen);
			return 0;
		}
	}

	ifreq = (struct ifreq*)buf;
	for (i=(ifconf.ifc_len/sizeof(struct ifreq)); i>0; i--)
	{
		ip = inet_ntoa(((struct sockaddr_in*)&(ifreq->ifr_addr))->sin_addr);

		if (strcmp(ip,"127.0.0.1")==0)
		{
			ifreq++;
			continue;
		}

		strncpy(addr,ip,uiAddrLen);
		//LOGSYS(LOG_ERROR, "%s HostIp: %s,%s,len=%u" , __func__, addr,ip, uiAddrLen);
		return 0;
	}

	return -1;
}
