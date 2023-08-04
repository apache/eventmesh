#ifndef __RMB_HTTP_CLIENT_H
#define __RMB_HTTP_CLIENT_H

#include "curl/curl.h"

#ifdef  __cplusplus
extern "C" {
#endif

struct rmb_http_buffer {
	size_t len;
    char*  data;
};

// timeout: ms
int rmb_http_easy_get(const char* url, void* buffer, long timeout);
//int rmb_http_easy_post(char* url, char* post_data, size_t len, void* buffer, size_t size, size_t* used, long timeout);

#ifdef  __cplusplus
}
#endif

#endif
