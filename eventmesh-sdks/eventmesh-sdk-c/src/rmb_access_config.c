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
#include "curl/curl.h"
#include "common.h"
#include "rmb_define.h"
#include "rmb_http_client.h"

#define WEMQ_PROXY_MAX_SIZE        (512)
#define WEMQ_PROXY_BUFFER_SIZE     (262144)     // 256K
//#define WEMQ_PROXY_SERVER_MAX_TIME (3600)   // 1H
#define WEMQ_PROXY_SERVER_MAX_TIME (1 * 60 * 1000000)   // 1m

#define atomic_set(x, y)    __sync_lock_test_and_set((x), (y))

typedef struct StWemqProxy
{
  char host[100];
  char idc[30];
  unsigned int port;
  unsigned int weight;
  unsigned int index;

  long failed_time;             // 0: 没有失败; >0: 失败时间戳
  int flag;                     //用于初始连接时，该host是否已连接过
} StWemqProxy;

static struct StWemqProxy _wemq_proxy_list[WEMQ_PROXY_MAX_SIZE];
static int _wemq_proxy_list_num = 0;    // proxy个数
static int _wemq_proxy_list_used = -1;  // 当前使用proxy
static int _wemq_proxy_weight_tol = 0;  // 权重总和

static int _wemq_proxy_inited = 0;

static pthread_mutex_t __wemq_proxy_rmutex;

int rmb_get_wemq_proxy_list_num ()
{
  return _wemq_proxy_list_num;
}

int rmb_get_wemq_proxy_list_used ()
{
  return _wemq_proxy_list_used;
}

static void ltrim (char *s)
{
  char *p = s;
  int len = 0;

  if (s == NULL || *s == '\0')
  {
    return;
  }
  while (*p != '\0' && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r'))
  {
    p++;
    len++;
  }
  memmove (s, p, strlen (s) - len + 1);
}

static void rtrim (char *s)
{
  int i;

  if (s == NULL || *s == '\0')
  {
    return;
  }
  i = strlen (s) - 1;
  while ((s[i] == ' ' ||
          s[i] == '\t' || s[i] == '\n' || s[i] == '\r') && i >= 0)
  {
    s[i] = '\0';
    i--;
  }
}

static void trim (char *s)
{
  ltrim (s);
  rtrim (s);
}

static int _wemq_proxy_cmp (const void *val1, const void *val2)
{
  const StWemqProxy *proxy1 = (const StWemqProxy *) val1;
  const StWemqProxy *proxy2 = (const StWemqProxy *) val2;

  if (proxy1->weight > proxy2->weight)
    return 1;
  if (proxy1->weight < proxy2->weight)
    return -1;
  return 0;
}

/*
返回示例
{
"wemq-proxy-servers" : "10.255.1.144:10000#3;;127.0.0.1:36000#1;"
}
服务器列表格式
host:port#weight;
host:port#weight;host:port#weight;
*/
static int _wemq_proxy_load_item (StWemqProxy * pproxy, const char *pstr,
                                  size_t len)
{
  char weight[64] = "";
  char idc[32] = "";
  char port[64] = "";
  int index1 = -1;              // ':'位置
  int index2 = -1;              // '#'位置
  int index3 = -1;              // '|' 位置
  int begin = 0;
  int size = 0;
  int i = 0;

  if (pstr == NULL || pproxy == NULL || len == 0)
  {
    return -1;
  }

  for (i = 0; i < (int) len; i++)
  {
    if (pstr[i] == ':')
    {
      if (index1 != -1)
      {
        return -2;
      }
      index1 = i;
    }
    else if (pstr[i] == '#')
    {
      if (index2 != -1)
      {
        return -2;
      }
      index2 = i;
    }
    else if (pstr[i] == '|')
    {
      if (index3 != -1)
      {
        return -2;
      }
      index3 = i;
    }
  }

  // 确认格式是否正确
  if (index1 == -1 || index2 == -1 || index1 >= index2 || index2 >= index3)
  {
    return -2;
  }

  // IP
  if (index1 > 100 || index1 == 0)
  {
    return -3;
  }
  memcpy (pproxy->host, pstr, index1);
  pproxy->host[index1] = '\0';

  // port
  begin = index1 + 1;
  size = index2 - begin;
  if (size > 64 || size == 0)
  {
    return -3;
  }
  memcpy (port, &pstr[begin], size);
  port[size] = '\0';

  // weight
  begin = index2 + 1;
  size = index3 - begin;
  if (size > 64 || size == 0)
  {
    return -3;
  }
  memcpy (weight, &pstr[begin], size);
  weight[size] = '\0';

  // idc
  begin = index3 + 1;
  size = len - begin;
  if (size > 64 || size == 0)
  {
    return -3;
  }
  memcpy (pproxy->idc, &pstr[begin], size);
  pproxy->idc[size] = '\0';

  pproxy->port = atoi (port);
  pproxy->weight = atoi (weight);

  if (pproxy->port == 0 || pproxy->weight == 0)
  {
    return -4;
  }

  pproxy->flag = 0;
  return 0;
}

static int _wemq_proxy_load_by_http (StWemqProxy * pproxy, size_t size,
                                     const char *url, long timeout)
{

  int ret = 0;
  if (pproxy == NULL || url == NULL)
  {
    LOGRMB (RMB_LOG_ERROR, "pproxy or url is null");
    return -1;
  }

  char *servers = NULL;
  struct rmb_http_buffer req;
  req.len = 0;
  req.data = (char *) malloc (sizeof (char));
  if (req.data == NULL)
  {
    LOGRMB (RMB_LOG_ERROR, "malloc for req.data failed");
    return -1;
  }

  if ((ret = rmb_http_easy_get (url, (void *) &req, timeout)) != 0)
  {
    LOGRMB (RMB_LOG_ERROR, "rmb_http_easy_get error,iRet=%d", ret);
    ret = -2;
    goto _LOAD_ITEM_END;
  }

  servers = (char *) malloc (sizeof (char) * (req.len + 1));
  if (servers == NULL)
  {
    LOGRMB (RMB_LOG_ERROR, "malloc for servers failed");
    free (req.data);
    req.data = NULL;
    return -1;
  }

  LOGRMB (RMB_LOG_INFO, "_wemq_proxy_load_by_http:[resp:len=%u,%s]!",
          (unsigned int) req.len, req.data);
  if (req.len <= 2)
    return -1;
  // 解析json字符串

  WEMQJSON *json = json_tokener_parse (req.data);
  WEMQJSON *wemq_proxy_servers = NULL;
  if (json == NULL)
  {
    LOGRMB (RMB_LOG_ERROR,
            "_wemq_proxy_load_by_http: json_tokener_parse error!");
    ret = -3;
    goto _LOAD_ITEM_END;
  }

  char *config_param = "wemqAccessServer";
  if (strstr (url, "wemqAccessServer") == NULL)
    config_param = pRmbStConfig->strDepartMent;
  if (!json_object_object_get_ex (json, config_param, &wemq_proxy_servers))
  {
    LOGRMB (RMB_LOG_ERROR,
            "_wemq_proxy_load_by_http: json_object_object_get_ex error!");
    ret = -3;
    json_object_put (json);
    goto _LOAD_ITEM_END;
  }

  const char *pservers = json_object_get_string (wemq_proxy_servers);
  if (pservers != NULL)
  {
    snprintf (servers, WEMQ_PROXY_BUFFER_SIZE, "%s", pservers);
  }
  else
  {
    LOGRMB (RMB_LOG_ERROR,
            "_wemq_proxy_load_by_http: wemqAccessServer is null");
  }
  json_object_put (json);

  LOGRMB (RMB_LOG_INFO, "_wemq_proxy_load_by_http: [servers:%s]!", servers);

  json = json_tokener_parse (servers);
  wemq_proxy_servers = NULL;
  if (json == NULL)
  {
    LOGRMB (RMB_LOG_ERROR,
            "_wemq_proxy_load_by_http: json_tokener_parse error!");
    ret = -3;
    goto _LOAD_ITEM_END;
  }

  //如果当前子系统有特定的access，没有就默认
  if (!json_object_object_get_ex
      (json, pRmbStConfig->cConsumerSysId, &wemq_proxy_servers))
  {
    LOGRMB (RMB_LOG_INFO,
            "current subsystem does not have special access list");
    if (!json_object_object_get_ex
        (json, "wemqAccessServer", &wemq_proxy_servers))
    {
      LOGRMB (RMB_LOG_ERROR,
              "_wemq_proxy_load_by_http: json_object_object_get_ex error!");
      ret = -3;
      json_object_put (json);
      goto _LOAD_ITEM_END;
    }

  }

  pservers = json_object_get_string (wemq_proxy_servers);
  if (pservers != NULL)
  {
    snprintf (servers, WEMQ_PROXY_BUFFER_SIZE, "%s", pservers);
  }
  else
  {
    LOGRMB (RMB_LOG_ERROR,
            "_wemq_proxy_load_by_http: wemqAccessServer is null");
  }
  json_object_put (json);

  // 解析每个字段
  trim (servers);
  {
    char *item = NULL;
    char *saveptr = NULL;
    char *ptr = servers;
    int i = 0;

    while ((item = strtok_r (ptr, ";", &saveptr)) != NULL)
    {
      if (i >= (int) size)
      {
        LOGRMB (RMB_LOG_WARN,
                " [index: %d] [size: %zu] _wemq_proxy_load_by_http: servers too more!\n",
                i, size);
        break;
      }
      if (_wemq_proxy_load_item (&pproxy[i], item, strlen (item)) == 0)
      {
        i++;
      }
      else
      {
        LOGRMB (RMB_LOG_WARN,
                "[item: %s] _wemq_proxy_load_by_http:  _wemq_proxy_load_item error!\n",
                item);
      }
      ptr = NULL;
    }
    if (i == 0)
    {
      LOGRMB (RMB_LOG_ERROR, "_wemq_proxy_load_by_http: servers is 0!\n");
    }

    ret = i;
  }

_LOAD_ITEM_END:
  if (req.data != NULL)
  {
    free (req.data);
    req.data = NULL;
  }
  if (servers != NULL)
  {
    free (servers);
    servers = NULL;
  }
  return ret;
}

static int _wemq_proxy_save (StWemqProxy * pproxy, size_t size,
                             const char *path)
{
  FILE *fp = NULL;
  int i = 0;

  if ((fp = fopen (path, "w")) == NULL)
  {
    LOGRMB (RMB_LOG_ERROR, "[path: %s] _wemq_proxy_save: fopen error!\n",
            path);
    return -1;
  }
  for (i = 0; i < (int) size; i++)
  {
    fprintf (fp, "%s:%u#%u\n", pproxy[i].host, pproxy[i].port,
             pproxy[i].weight);
  }
  fclose (fp);
  return 0;
}

//int wemq_proxy_load_servers(char* url, long timeout, const char* path)
int wemq_proxy_load_servers (const char *url, long timeout)
{
  int ret = 0;
  int tol = 0;
  int i = 0;
  int j = 0;
  if (pRmbStConfig->iWemqUseHttpCfg != 1)
  {
    return 0;
  }

  if (_wemq_proxy_inited == 0)
  {
    _wemq_proxy_inited = 1;
    pthread_mutex_init (&__wemq_proxy_rmutex, NULL);
  }

  int tmp_wemq_proxy_list_num = 0;
  struct StWemqProxy tmp_wemq_proxy_list[WEMQ_PROXY_MAX_SIZE];

  memset (tmp_wemq_proxy_list, 0, sizeof (StWemqProxy) * WEMQ_PROXY_MAX_SIZE);

  //1. 通过HTTP请求配置中心
  ret =
    _wemq_proxy_load_by_http (tmp_wemq_proxy_list, WEMQ_PROXY_MAX_SIZE, url,
                              timeout);
  if (ret <= 0 && (strstr (url, "wemqAccessServer") == NULL))
  {
    LOGRMB (RMB_LOG_WARN, "get wemq proxy departMent ip list failed,url=%s",
            url);
    return -1;
  }
  if (ret <= 0)
  {
    LOGRMB (RMB_LOG_ERROR, "get wemq proxy ip list failed,url=%s", url);
    return -1;
  }
  tmp_wemq_proxy_list_num = ret;

  //2. 排序
  qsort (tmp_wemq_proxy_list, tmp_wemq_proxy_list_num, sizeof (StWemqProxy),
         _wemq_proxy_cmp);

  //3. 权重相加
  for (i = 0; i < tmp_wemq_proxy_list_num; i++)
  {
    tol += tmp_wemq_proxy_list[i].weight;
  }
  _wemq_proxy_weight_tol = tol;
  int equal_flag = 0;

  if (tmp_wemq_proxy_list_num == _wemq_proxy_list_num)
  {
    for (i = 0; i < _wemq_proxy_list_num; i++)
    {
      if (strcmp (_wemq_proxy_list[i].host, tmp_wemq_proxy_list[i].host) != 0
          || _wemq_proxy_list[i].port != tmp_wemq_proxy_list[i].port
          || _wemq_proxy_list[i].weight != tmp_wemq_proxy_list[i].weight)
      {
        equal_flag = 1;
        break;
      }
    }
    //配置中心和本地的配置相同
    if (equal_flag == 0)
      return 0;
  }
  //打印配置中心和本地的配置
  for (i = 0; i < _wemq_proxy_list_num; i++)
  {
    LOGRMB (RMB_LOG_DEBUG,
            "[local address %d: host,%s|port,%u|idc:%s|weight,%u|index,%u|failed_time,%ld|flag,%d",
            i, _wemq_proxy_list[i].host, _wemq_proxy_list[i].port,
            _wemq_proxy_list[i].idc, _wemq_proxy_list[i].weight,
            _wemq_proxy_list[i].index, _wemq_proxy_list[i].failed_time,
            _wemq_proxy_list[i].flag);
  }
  for (j = 0; j < tmp_wemq_proxy_list_num; j++)
  {
    LOGRMB (RMB_LOG_DEBUG,
            "[configcenter address %d: host,%s|port,%u|idc:%s|weight,%u|index,%u|failed_time,%ld|flag,%d",
            j, tmp_wemq_proxy_list[j].host, tmp_wemq_proxy_list[j].port,
            tmp_wemq_proxy_list[j].idc, tmp_wemq_proxy_list[j].weight,
            tmp_wemq_proxy_list[j].index, tmp_wemq_proxy_list[j].failed_time,
            tmp_wemq_proxy_list[j].flag);
  }

  pthread_mutex_lock (&__wemq_proxy_rmutex);
  //比较缓存数据和配置中心获取的数据,保留缓存数据中的flag和failedtime
  for (i = 0; i < _wemq_proxy_list_num; i++)
  {
    if (_wemq_proxy_list[i].failed_time == 0 && _wemq_proxy_list[i].flag == 0)
      continue;
    for (j = 0; j < tmp_wemq_proxy_list_num; j++)
    {
      if (strcmp (_wemq_proxy_list[i].host, tmp_wemq_proxy_list[j].host) == 0
          && _wemq_proxy_list[i].port == tmp_wemq_proxy_list[j].port)
      {
        tmp_wemq_proxy_list[j].failed_time = _wemq_proxy_list[i].failed_time;
        tmp_wemq_proxy_list[j].flag = _wemq_proxy_list[i].flag;
      }
    }
  }
  //更新缓存数据
  memset (_wemq_proxy_list, 0, sizeof (StWemqProxy) * WEMQ_PROXY_MAX_SIZE);
  memcpy (_wemq_proxy_list, tmp_wemq_proxy_list,
          sizeof (StWemqProxy) * tmp_wemq_proxy_list_num);
  _wemq_proxy_list_num = tmp_wemq_proxy_list_num;
  _wemq_proxy_list_used = -1;
  pthread_mutex_unlock (&__wemq_proxy_rmutex);

  return 0;
}

int wemq_proxy_get_server (char *host, size_t size, unsigned int *port)
{
  int now_index = -1;
  int tmp = -1;
  int wemq_proxy_usable_server_list[_wemq_proxy_list_num];
  long now_time = 0;
  struct timeval tv;

  if (pRmbStConfig->iWemqUseHttpCfg != 1)
  {
    *port = pRmbStConfig->cWemqProxyPort;
    snprintf (host, size, "%s", pRmbStConfig->cWemqProxyIp);
    return 1;
  }

  pthread_mutex_lock (&__wemq_proxy_rmutex);
  if (_wemq_proxy_list_num <= 0)
  {
    pthread_mutex_unlock (&__wemq_proxy_rmutex);
    LOGRMB (RMB_LOG_ERROR, "get proxy list number is:%d",
            _wemq_proxy_list_num);
    *port = 0;
    memset (host, 0x00, sizeof (char) * size);
    return 2;
  }

  //从可以连接的ip中选取
  int wemq_proxy_usable_sever_weight = 0;
  int wemq_proxy_usable_count = 0;
  wemq_proxy_get_usable_server_list (wemq_proxy_usable_server_list,
                                     &wemq_proxy_usable_sever_weight,
                                     &wemq_proxy_usable_count);
  //LOGRMB(RMB_LOG_DEBUG, "final count: %d | final weight: %d", wemq_proxy_usable_count, wemq_proxy_usable_sever_weight);
  if (wemq_proxy_usable_count == 0)
  {
    pthread_mutex_unlock (&__wemq_proxy_rmutex);
    LOGRMB (RMB_LOG_ERROR, "local proxy list all in black list");
    *port = 0;
    memset (host, 0x00, sizeof (char) * size);
    return 2;
  }
  else if (wemq_proxy_usable_count == 1)
  {
    now_index = wemq_proxy_usable_server_list[0];
  }
  else
  {

    //use weight
    gettimeofday (&tv, NULL);
    now_time = tv.tv_sec * 1000000 + tv.tv_usec;
    srand ((unsigned int) now_time);
    int random_index = rand () % wemq_proxy_usable_sever_weight;
    int i = 0;
    int tmp_weight = 0;
    int tmp_index = 0;
    for (; i < wemq_proxy_usable_count; i++)
    {
      tmp_index = wemq_proxy_usable_server_list[i];
      tmp_weight += _wemq_proxy_list[tmp_index].weight;
      if (random_index < tmp_weight)
      {
        //LOGRMB(RMB_LOG_DEBUG, "choose host: %s | failed_time:%ld", _wemq_proxy_list[tmp_index].host, _wemq_proxy_list[tmp_index].failed_time);
        now_index = tmp_index;
        break;
      }
    }
  }
  gettimeofday (&tv, NULL);
  now_time = tv.tv_sec * 1000000 + tv.tv_usec;
  if ((_wemq_proxy_list[now_index].failed_time +
       WEMQ_PROXY_SERVER_MAX_TIME) <= now_time)
  {
    atomic_set (&_wemq_proxy_list[now_index].failed_time, 0);
  }

  // } while (tmp != now_index);

  _wemq_proxy_list_used = now_index;
  if (_wemq_proxy_list[now_index].flag == 0)
  {
    _wemq_proxy_list[now_index].flag = 1;
  }

  // 如果没有找到，由之前的使用默认IP调整为打印error日志，并等待黑名单解禁
  if (_wemq_proxy_list[now_index].failed_time != 0 &&
      (_wemq_proxy_list[now_index].failed_time + WEMQ_PROXY_SERVER_MAX_TIME) >
      now_time)
  {
    pthread_mutex_unlock (&__wemq_proxy_rmutex);
    LOGRMB (RMB_LOG_ERROR, "local proxy list all in black list");
    //*port = pRmbStConfig->cWemqProxyPort;
    //snprintf(host, size, "%s", pRmbStConfig->cWemqProxyIp);
    *port = 0;
    memset (host, 0x00, sizeof (char) * size);
    return 2;
  }

  *port = (int) _wemq_proxy_list[now_index].port;
  snprintf (host, size, "%s", _wemq_proxy_list[now_index].host);
  pthread_mutex_unlock (&__wemq_proxy_rmutex);

  return 0;
}

void wemq_proxy_goodbye (const char *host, unsigned int port)
{
  if (pRmbStConfig->iWemqUseHttpCfg != 1)
  {
    return;
  }

  struct timeval tv;
  gettimeofday (&tv, NULL);
//    long now_time = time(NULL);
  long now_time = tv.tv_sec * 1000000 + tv.tv_usec;

  LOGRMB (RMB_LOG_INFO,
          "[used: host,%s|port,%u] [wemq_proxy_list_used:%d] wemq_proxy_goodbye!\n",
          host, port, _wemq_proxy_list_used);
  pthread_mutex_lock (&__wemq_proxy_rmutex);

  int i = 0;
  for (i = 0; i < _wemq_proxy_list_num; i++)
  {
    if ((strcmp (host, _wemq_proxy_list[i].host) == 0)
        && (port == _wemq_proxy_list[i].port))
    {
//              LOGRMB(RMB_LOG_WARN, "[used: host:%s|port:%d] wemq_proxy_goodbye inconformity!", host, port);
      atomic_set (&_wemq_proxy_list[i].failed_time, now_time);
    }
  }
  pthread_mutex_unlock (&__wemq_proxy_rmutex);
}

void wemq_proxy_to_black_list (const char *host, unsigned int port)
{
  if (pRmbStConfig->iWemqUseHttpCfg != 1)
  {
    return;
  }

  struct timeval tv;
  gettimeofday (&tv, NULL);
  long now_time = tv.tv_sec * 1000000 + tv.tv_usec;

  LOGRMB (RMB_LOG_INFO, "[host:%s|port:%u] add to black list!\n", host, port);
  pthread_mutex_lock (&__wemq_proxy_rmutex);
  int i = 0;
  for (i = 0; i < _wemq_proxy_list_num; i++)
  {
    if ((strcmp (host, _wemq_proxy_list[i].host) == 0)
        && (port == _wemq_proxy_list[i].port))
    {
      atomic_set (&_wemq_proxy_list[i].failed_time, now_time);
    }
  }
  pthread_mutex_unlock (&__wemq_proxy_rmutex);
}

void wemq_proxy_get_usable_server_list (int *wemq_proxy_usable_server_list,
                                        int *wemq_proxy_usable_sever_weight,
                                        int *wemq_proxy_usable_count)
{
  if (pRmbStConfig->iWemqUseHttpCfg != 1)
  {
    return;
  }
  int i = 0;
  int j = 0;
  int loop = 0;
  int local_idc_usable_flag = 0;
  int wemq_proxy_usable_server_list_backup[_wemq_proxy_list_num];
  memset (wemq_proxy_usable_server_list_backup, 0x00,
          sizeof (int) * _wemq_proxy_list_num);
  int wemq_proxy_usable_count_backup = 0;
  int wemq_proxy_usable_sever_weight_backup = 0;
  bool usableLocalIdc = false;

  struct timeval tv;
  gettimeofday (&tv, NULL);
  long now_time = tv.tv_sec * 1000000 + tv.tv_usec;

  for (; i < _wemq_proxy_list_num; i++)
  {
    char isBlack =
      ((_wemq_proxy_list[i].failed_time + WEMQ_PROXY_SERVER_MAX_TIME) <=
       now_time) ? 'n' : 'y';
    //LOGRMB(RMB_LOG_DEBUG, "[host:%s, port:%d, black:%c]", _wemq_proxy_list[i].host, _wemq_proxy_list[i].port, isBlack);
    if (strcmp (_wemq_proxy_list[i].idc, pRmbStConfig->cRegion) == 0)
    {

      if (_wemq_proxy_list[i].failed_time == 0
          || (_wemq_proxy_list[i].failed_time + WEMQ_PROXY_SERVER_MAX_TIME) <=
          now_time)
      {
        wemq_proxy_usable_server_list[j] = i;
        j++;
        *wemq_proxy_usable_count += 1;
        *wemq_proxy_usable_sever_weight += (int) _wemq_proxy_list[i].weight;
        usableLocalIdc = true;
        //LOGRMB(RMB_LOG_DEBUG, "[host:%s | port:%u | weight:%d | index: %d] is usable!\n", _wemq_proxy_list[i].host, _wemq_proxy_list[i].port, _wemq_proxy_list[i].weight, i);
        //LOGRMB(RMB_LOG_DEBUG, "j: %d | count: %d | weight: %d", j, *wemq_proxy_usable_count, *wemq_proxy_usable_sever_weight);
      }
    }
    else
    {
      if (_wemq_proxy_list[i].failed_time == 0
          || (_wemq_proxy_list[i].failed_time + WEMQ_PROXY_SERVER_MAX_TIME) <=
          now_time)
      {
        wemq_proxy_usable_server_list_backup[loop] = i;
        loop++;
        wemq_proxy_usable_count_backup += 1;
        wemq_proxy_usable_sever_weight_backup +=
          (int) _wemq_proxy_list[i].weight;
        //LOGRMB(RMB_LOG_DEBUG, "[host:%s | port:%u | weight:%d | index: %d] is usable!\n", _wemq_proxy_list[i].host, _wemq_proxy_list[i].port, _wemq_proxy_list[i].weight, i);
        //LOGRMB(RMB_LOG_DEBUG, "j: %d | count: %d | weight: %d", j, *wemq_proxy_usable_count, *wemq_proxy_usable_sever_weight);
      }
    }
  }
  /*
     for(i = 0; i < *wemq_proxy_usable_count; i++){
     LOGRMB(RMB_LOG_DEBUG, "[local usable index: %d]\n", wemq_proxy_usable_server_list[i]);
     }
     for(i = 0; i < wemq_proxy_usable_count_backup; i++){
     LOGRMB(RMB_LOG_DEBUG, "[backup usable index: %d]\n", wemq_proxy_usable_server_list_backup[i]);
     } */

  if (!usableLocalIdc)
  {
    memset (wemq_proxy_usable_server_list, 0,
            sizeof (int) * _wemq_proxy_list_num);
    memcpy (wemq_proxy_usable_server_list,
            wemq_proxy_usable_server_list_backup,
            sizeof (int) * wemq_proxy_usable_count_backup);
    *wemq_proxy_usable_count = wemq_proxy_usable_count_backup;
    *wemq_proxy_usable_sever_weight = wemq_proxy_usable_sever_weight_backup;
  }
  /*
     for(i = 0; i < *wemq_proxy_usable_count; i++){
     LOGRMB(RMB_LOG_DEBUG, "[final usable index: %d]\n", wemq_proxy_usable_server_list[i]);
     }
   */
}

/**
 * 判断所有的ip是否均连接过
 */
int wemq_proxy_ip_is_connected ()
{
  if (pRmbStConfig->iWemqUseHttpCfg != 1)
  {
    return 1;
  }

  int iRet = 0;
  int i = 0;
  pthread_mutex_lock (&__wemq_proxy_rmutex);
  for (i = 0; i < _wemq_proxy_list_num; i++)
  {
    if (_wemq_proxy_list[i].flag == 0)
      break;
  }
  pthread_mutex_unlock (&__wemq_proxy_rmutex);

  if (i >= _wemq_proxy_list_num)
  {
    iRet = 1;
  }

  return iRet;
}

/**
 * 分隔IP列表
 */
void split_str (char *ips, char ipArray[][50], int *len)
{
  //    printf("%s\n", ips);
  //char (*ipArray)[50] = (char(*)[50])malloc(sizeof(char) * 15 * 50);
  int count = 0;
  char *ip = NULL;
  ip = strtok (ips, ";");
  if (ip == NULL)
  {
    *len = 0;
    return;
  }
  strcpy (ipArray[count], ip);
  count++;
  while (ip = strtok (NULL, ";"))
  {
    strcpy (ipArray[count], ip);
    count++;
  }
  *len = count;
}
