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

#include <stdio.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include "curl/curl.h"
#include "rmb_http_client.h"
#include "rmb_define.h"

static pthread_mutex_t LIBCURL_LOCK = PTHREAD_MUTEX_INITIALIZER;
static int LIBCURL_INIT_FLAG = 0;

static void atexit_callback (void)
{
  if (0 != LIBCURL_INIT_FLAG)
  {
#ifdef RMB_HTTP_DEBUG
    printf ("[Exit]curl_global_cleanup\n");
#endif
    curl_global_cleanup ();
    LIBCURL_INIT_FLAG = 0;
  }
}

static void libcurl_init (void)
{
  pthread_mutex_lock (&LIBCURL_LOCK);
  if (0 == LIBCURL_INIT_FLAG)
  {
#ifdef RMB_HTTP_DEBUG
    printf ("[INIT]curl_global_init\n");
#endif
    curl_global_init (CURL_GLOBAL_ALL);
    atexit (atexit_callback);
    LIBCURL_INIT_FLAG = 1;
  }
  pthread_mutex_unlock (&LIBCURL_LOCK);
}

static int on_http_debug (CURL * handle, curl_infotype type, char *data,
                          size_t size, void *userptr)
{
  if (type == CURLINFO_TEXT)
  {
    //printf("[TEXT]%s\n", data);
  }
  else if (type == CURLINFO_HEADER_IN)
  {
    printf ("[HEADER_IN]%s\n", data);
  }
  else if (type == CURLINFO_HEADER_OUT)
  {
    printf ("[HEADER_OUT]%s\n", data);
  }
  else if (type == CURLINFO_DATA_IN)
  {
    printf ("[DATA_IN]%s\n", data);
  }
  else if (type == CURLINFO_DATA_OUT)
  {
    printf ("[DATA_OUT]%s\n", data);
  }
  return 0;
}

static size_t on_http_body (char *ptr, size_t size, size_t nmemb,
                            void *userdata)
{
  size_t len = size * nmemb;

  if (ptr == NULL || userdata == NULL)
  {
    printf ("%s ptr is null\n", __func__);
    return -1;
  }

  struct rmb_http_buffer *buf = (struct rmb_http_buffer *) userdata;

  buf->data =
    (char *) realloc (buf->data, sizeof (char) * (buf->len + len + 1));
  if (buf->data == NULL)
  {
    /* out of memory */
    printf ("not enough memory for realloc(buf->data)");
    return 0;
  }

  memcpy (&(buf->data[buf->len]), ptr, len);
  buf->len += len;
  buf->data[buf->len] = '\0';

  return len;
}

int rmb_http_easy_get (const char *url, void *buffer, long timeout)
{
  CURLcode res;
  CURL *curl = NULL;
  struct curl_slist *headers = NULL;

  libcurl_init ();
  curl = curl_easy_init ();
  if (NULL == curl)
  {
    LOGRMB (RMB_LOG_ERROR, "curl_easy_init failed");
    return -1;
  }

  struct rmb_http_buffer *response = (struct rmb_http_buffer *) buffer;

  headers = curl_slist_append (headers, "Connection: Keep-Alive");

#ifdef RMB_HTTP_DEBUG
  curl_easy_setopt (curl, CURLOPT_VERBOSE, 1);
  curl_easy_setopt (curl, CURLOPT_DEBUGFUNCTION, on_http_debug);
#endif
  curl_easy_setopt (curl, CURLOPT_HTTPHEADER, headers);
  curl_easy_setopt (curl, CURLOPT_URL, url);
  curl_easy_setopt (curl, CURLOPT_READFUNCTION, NULL);
  curl_easy_setopt (curl, CURLOPT_WRITEFUNCTION, on_http_body);
  curl_easy_setopt (curl, CURLOPT_WRITEDATA, (void *) response);
  curl_easy_setopt (curl, CURLOPT_NOSIGNAL, 1);
  curl_easy_setopt (curl, CURLOPT_CONNECTTIMEOUT_MS, timeout);
  curl_easy_setopt (curl, CURLOPT_TIMEOUT_MS, timeout);
  res = curl_easy_perform (curl);
  curl_easy_cleanup (curl);
  curl_slist_free_all (headers);

  if (res == CURLE_COULDNT_CONNECT)
  {
    return 1;
  }

  if (res != CURLE_OK)
  {
    LOGRMB (RMB_LOG_ERROR, "curl_easy_perform failed: %s\n",
            curl_easy_strerror (res));
    return -2;
  }

  return 0;
}

/*
int rmb_http_easy_post(char* url, char* post_data, size_t len, void* buffer, size_t size, size_t* used, long timeout)
{
    CURLcode res;
    CURL* curl = NULL;
    struct curl_slist *headers = NULL;
    struct http_buffer response;

    libcurl_init();
    curl = curl_easy_init();
    if (NULL == curl)
    {
        return -1;
    }

    response.buf = (char*)buffer;
    response.size = size;
    response.len = 0;
    headers = curl_slist_append(headers, "Connection: Keep-Alive");

#ifdef HTTP_DEBUG
    curl_easy_setopt(curl, CURLOPT_VERBOSE, 1);
    curl_easy_setopt(curl, CURLOPT_DEBUGFUNCTION, on_http_debug);
#endif
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_URL, url);

    curl_easy_setopt(curl, CURLOPT_POST, 1);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, post_data);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)len);

    curl_easy_setopt(curl, CURLOPT_READFUNCTION, NULL);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, on_http_body);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)&response);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, timeout);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, timeout);
    res = curl_easy_perform(curl);
    curl_easy_cleanup(curl);

    if (res == CURLE_COULDNT_CONNECT) {
        return 1;
    }
    if (0 != res) {
        return -1;
    }
    *used = response.len;
    if (response.len >= response.size) {
        return 2;
    }
    *(response.buf + response.len) = '\0';
    return 0;
}
*/
