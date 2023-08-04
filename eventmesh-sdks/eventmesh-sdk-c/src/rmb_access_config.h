
#ifndef RMB_ACCESS_CONFIG_H_
#define RMB_ACCESS_CONFIG_H_

#ifdef __cplusplus
extern "C" {
#endif

int rmb_get_wemq_proxy_list_num();

int rmb_get_wemq_proxy_list_used();

//int wemq_proxy_load_servers(char* url, long timeout, const char* path);
int wemq_proxy_load_servers(const char* url, long timeout);

int wemq_proxy_get_server(char* host, size_t size, unsigned int* port);

void wemq_proxy_goodbye(const char* host, unsigned int port);

void wemq_proxy_to_black_list(const char* host, unsigned int port);

int wemq_proxy_ip_is_connected();

void split_str(char *ips, char ipArray[][50], int *len);

#ifdef __cplusplus
}
#endif

#endif /* RMB_ACCESS_CONFIG_H_ */
