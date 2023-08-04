#ifndef _TCP_SOCKET_FOR_WEMQ_H_
#define _TCP_SOCKET_FOR_WEMQ_H_
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int wemq_tcp_connect(const char *ip, uint16_t port, int timeout);
int wemq_tcp_send(int fd, void *msg, uint32_t totalLen, uint32_t headerLen, int iTimeOut);
int wemq_tcp_recv(int fd, void *msg, uint32_t *len, int iTimeout);

void wemq_getsockename(int fd, char* ip, uint32_t len, int* port);
void wemq_getpeername(int fd, char* ip, uint32_t len, int* port);

#ifdef __cplusplus
}
#endif

#endif
