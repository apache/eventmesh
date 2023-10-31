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

#ifndef _WEMQ_TOPIC_LIST_H_
#define _WEMQ_TOPIC_LIST_H_
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define WEMQ_TOPIC_MAX_LEN	100

typedef struct StWemqTopicProp
{
  char cServiceId[32];
  char cScenario[32];
  char cTopic[WEMQ_TOPIC_MAX_LEN];
  int flag;                     //flag=0, serviceid flag=1, topic
  struct StWemqTopicProp *next;
} StWemqTopicProp;

typedef struct StWemqTopicList
{
  StWemqTopicProp *next;
  StWemqTopicProp *tail;
} StWemqTopicList;

typedef int (*WEMQ_DEC_FUNC) (StWemqTopicProp * pArg);

void wemq_topic_list_init (StWemqTopicList * ptTopicList);
int32_t wemq_topic_list_delete (StWemqTopicList * pt);
int32_t wemq_topic_list_add_node (StWemqTopicList * pt,
                                  StWemqTopicProp * ptpp);
int32_t wemq_topic_list_find_node (StWemqTopicList * pt,
                                   StWemqTopicProp * ptpp,
                                   StWemqTopicProp ** pos);
int32_t wemq_topic_list_del_node (StWemqTopicList * pt,
                                  StWemqTopicProp * ptpp);
int32_t wemq_topic_list_is_empty (StWemqTopicList * pt);
int32_t wemq_topic_list_clear (StWemqTopicList * ptTopicList);
#endif
