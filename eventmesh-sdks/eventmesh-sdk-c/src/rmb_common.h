/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _RMB_COMMON_H_
#define _RMB_COMMON_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h> 
#include "string.h"

extern unsigned int g_uiSendMsgSeq;
extern unsigned int g_uiRecvMsgSeq;
//extern unsigned int g_iSendReq = 0;
#define MAX_SEND_MSG_SEQ 65535
extern unsigned int DEFAULT_WINDOW_SIZE;
#define CURRENT_WINDOW_SIZE (((g_uiRecvMsgSeq) <= (g_uiSendMsgSeq)) ? ((g_uiSendMsgSeq) - (g_uiRecvMsgSeq)) : (MAX_SEND_MSG_SEQ - (g_uiRecvMsgSeq) + (g_uiSendMsgSeq)))

static int cmpQueueStr ( const void *a , const void *b )
{
	return strcmp( ((StQueueName*)a)->cQueueName , ((StQueueName*)b)->cQueueName );
}

static int cmpQueueNodeStr ( const void *a , const void *b )
{
	return strcmp( ((StQueueNode*)a)->cQueueName , ((StQueueNode*)b)->cQueueName );
}


static int cmpServiceStatusStr(const void *a, const void *b)
{
// 	if (((StServiceStatus*)a)->ulGetTimes == 0)
// 	{
// 		return 1;
// 	}
// 	if (((StServiceStatus*)b)->ulGetTimes == 0)
// 	{
// 		return -1;
// 	}
// 	if (((StServiceStatus*)a)->ulGetTimes < ((StServiceStatus*)b)->ulGetTimes)
// 	{
// 		return 1;
// 	}
	int iRet = strcmp(((StServiceStatus*)a)->strServiceId, ((StServiceStatus*)b)->strServiceId);
	if (iRet != 0)
	{
		return iRet;
	}
	iRet = strcmp(((StServiceStatus*)a)->strScenarioId, ((StServiceStatus*)b)->strScenarioId);
	if (iRet != 0)
	{
		return iRet;
	}
	iRet = strcmp(((StServiceStatus*)a)->strTargetOrgId, ((StServiceStatus*)b)->strTargetOrgId);
	if (iRet != 0)
	{
		return iRet;
	}
	if (((StServiceStatus*)a)->cFlagForOrgId < ((StServiceStatus*)a)->cFlagForOrgId)
	{
		return -1;
	}
	else if( ((StServiceStatus*)a)->cFlagForOrgId > ((StServiceStatus*)a)->cFlagForOrgId )
	{
		return 1;
	}
	return 0;
}



static int cmpBroadNodeStr(const void *a, const void *b)
{
	int iRet = strcmp(((StBroadcastNode*)a)->strServiceId, ((StBroadcastNode*)b)->strServiceId);
	if (iRet != 0)
		return iRet;

	iRet = strcmp(((StBroadcastNode*)a)->strScenarioId, ((StBroadcastNode*)b)->strScenarioId);
	if (iRet != 0)
		return iRet;

//	iRet = strcmp(((StBroadcastNode*)a)->cConsumerSysId, ((StBroadcastNode*)b)->cConsumerSysId);
//	if (iRet != 0)
//		return iRet;

	return 0;
}

/*
typedef struct StQueueTree
{  
	char queueName[30];  
	struct StQueueTree *pLeft;  
	struct StQueueTree *pRight;     
}StQueueTree;	

static StQueueTree* InsertQueue(StQueueTree* pRoot, const char *cQueuName)
{  
	StQueueTree* pCurNode = pRoot;  
	StQueueTree* pTmp;   
	StQueueTree* pNewNode = (StQueueTree*)malloc(sizeof(StQueueTree));   
	strncpy(pNewNode->queueName, cQueuName, sizeof(pNewNode->queueName));  
	pNewNode->pLeft = NULL;  
	pNewNode->pRight = NULL;  

	if(pCurNode == NULL)
	{  
		return pNewNode;   
	}
	else
	{  
		while(pCurNode != NULL)
		{  
			pTmp = pCurNode;   
			if( strcmp(cQueuName, pTmp->queueName) > 0)
			{  
				pCurNode = pCurNode->pRight;   
			}
			else
			{  
				pCurNode = pCurNode->pLeft;   
			}   
		}   

		if(strcmp(cQueuName, pTmp->queueName) > 0)
		{  
			pTmp->pRight =  pNewNode;   
		}
		else
		{  
			pTmp->pLeft =  pNewNode;   
		}   
	}  
	return pRoot;   
}  


static StQueueTree* FindQueue(StQueueTree* pRoot, const char *cQueuName)
{  
	int iRet = 1;
	StQueueTree *pCurNode = pRoot;
	while (iRet != 0)
	{
		if (pCurNode == NULL)
		{
			return NULL;
		}
		iRet = strcmp(cQueuName, pCurNode->queueName);
		if( iRet > 0)
		{  
			pCurNode = pCurNode->pRight;
		}
		else if( iRet < 0)
		{  
			pCurNode = pCurNode->pLeft;   
		} 
		else
		{
			return pCurNode;
		}
	}
	return NULL;
}   

typedef struct StQueueName StQueueName;



static int rmb_partition(StQueueName* pQueueList,int low,int high)
{
	StQueueName tmpQueueName;
	strcpy(tmpQueueName.cQueueName, (pQueueList + low)->cQueueName);
	while(low < high)
	{
		if(low<high && strcmp((pQueueList+high)->cQueueName, tmpQueueName.cQueueName) >= 0) 
		{
			--high;
		}
		strcpy((pQueueList+low)->cQueueName, (pQueueList+high)->cQueueName);
		
		if(low<high && strcmp((pQueueList+low)->cQueueName, tmpQueueName.cQueueName) <= 0)
		{
			++low;
		}
		strcpy((pQueueList+high)->cQueueName, (pQueueList+low)->cQueueName);
	}
	strcpy((pQueueList + low)->cQueueName, tmpQueueName.cQueueName);
	return low;
}

static void rmb_quick_sort(StQueueName* pQueueList,int low,int high)
{
	if(low<high) 
	{
		int position = rmb_partition(pQueueList,low,high);
		rmb_quick_sort(pQueueList,low,position-1);
		rmb_quick_sort(pQueueList,position+1,high);
	}
}


static StQueueName* find_in_queue_list(StQueueName* pQueueList, int iQueueListSize , const char* cQueueName)
{
	if (iQueueListSize == 0)
	{
		return NULL;
	}
	int iRet = 0;
	int i = 0;
	int m = 0;
	int j = iQueueListSize -1;
	while(i < j)
	{
		m =  (j - i) / 2;
		iRet = strcmp( (pQueueList+m)->cQueueName, cQueueName);
		if ( iRet  == 0)
		{
			return (pQueueList + m);
		}
		else if (iRet < 0)
		{
			if (i == m)
			{
				return NULL;
			}
			i = m;
		}
		else if (iRet > 0)
		{
			if (j == m)
			{
				return NULL;
			}
			j = m;
		}
	}
	return NULL;
}
*/
#ifdef __cplusplus
}
#endif

#endif /* _RMB_COMMON_H_ */
