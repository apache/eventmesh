#include <stdio.h>
#include "wemq_topic_list.h"


static int print_wemq_topic(StWemqTopicProp* pArg)
{
	if (pArg == NULL) {
		printf("pArg is null\n");
		return -1;
	}

	if (pArg->flag == 0) {
		printf("Topic:\n\tflag=%d\n\tserviceid=%s\n\tscenario=%s\n", pArg->flag, pArg->cServiceId, pArg->cScenario);
	} else if (pArg->flag == 1) {
		printf("Topic:\n\tflag=%d\n\ttopic=%s\n", pArg->flag, pArg->cTopic);
	} else {
		printf("unknown flag:%d\n", pArg->flag);
	}

	return 0;
}

static int wemq_topic_list_iter(StWemqTopicList *ptTopicList, WEMQ_DEC_FUNC func)
{
	StWemqTopicProp* tmp = ptTopicList->next;
	while (tmp != NULL) {
		func(tmp);
		tmp = tmp->next;
	}
	return 1;
}

inline int32_t wemq_topic_list_is_empty(StWemqTopicList* ptTopicList)
{
	return (ptTopicList->next == NULL) ? 1 : 0;
}

void wemq_topic_list_init(StWemqTopicList* ptTopicList)
{
	if (!wemq_topic_list_is_empty(ptTopicList)) {
		wemq_topic_list_clear(ptTopicList);
	}
	ptTopicList->next = NULL;
	ptTopicList->tail = NULL;
}

int wemq_topic_list_clear(StWemqTopicList* ptTopicList)
{
	if (ptTopicList == NULL) {
		return 1;
	}

	StWemqTopicProp* ptTemp1 = ptTopicList->next;
	StWemqTopicProp* ptTemp2 = ptTopicList->next;
	while (ptTemp1 != NULL) {
		ptTemp1 = ptTemp1->next;
		ptTemp2->next = NULL;
		free(ptTemp2);
		ptTemp2 = ptTemp1;	
	}

	return 1;
}

int32_t wemq_topic_list_add_node(StWemqTopicList* ptTopicList, StWemqTopicProp* ptTopicProp)
{
	if (ptTopicProp == NULL) {
		return -1;
	}

	if (ptTopicList == NULL) {
		return -1;
	}

	if (wemq_topic_list_find_node(ptTopicList, ptTopicProp, NULL) != 1) {
		if (ptTopicList->tail == NULL) {
			StWemqTopicProp* tmp = (StWemqTopicProp *)malloc(sizeof(StWemqTopicProp));
			if (tmp == NULL) {
				printf("malloc for StTopicProp failed!\n");
				return -2;
			}
			memset(tmp, 0, sizeof(StWemqTopicProp));
			if (ptTopicProp->flag == 0) {
				strncpy(tmp->cServiceId, ptTopicProp->cServiceId, strlen(ptTopicProp->cServiceId));
				strncpy(tmp->cScenario, ptTopicProp->cScenario, strlen(ptTopicProp->cScenario));
			} else {
				strncpy(tmp->cTopic, ptTopicProp->cTopic, strlen(ptTopicProp->cTopic));
			}
			tmp->flag = ptTopicProp->flag;
			tmp->next = NULL;
			ptTopicList->next = tmp;
			ptTopicList->tail = tmp;
		} else {
			StWemqTopicProp* tmp = (StWemqTopicProp *)malloc(sizeof(StWemqTopicProp));
			if (tmp == NULL) {
				printf("malloc for StTopicProp failed!\n");
				return -2;
			}
			memset(tmp, 0, sizeof(StWemqTopicProp));
			if (ptTopicProp->flag == 0) {
				strncpy(tmp->cServiceId, ptTopicProp->cServiceId, strlen(ptTopicProp->cServiceId));
				strncpy(tmp->cScenario, ptTopicProp->cScenario, strlen(ptTopicProp->cScenario));
			} else {
				strncpy(tmp->cTopic, ptTopicProp->cTopic, strlen(ptTopicProp->cTopic));
			}
			tmp->flag = ptTopicProp->flag;
			tmp->next = NULL;
			ptTopicList->tail->next = tmp;
			ptTopicList->tail = tmp;
		}
		return 1;
	} else {
		return 2;
	}
}

int32_t wemq_topic_list_find_node(StWemqTopicList* ptTopicList, StWemqTopicProp* ptTopicProp, StWemqTopicProp** pos)
{
	if (ptTopicList == NULL || ptTopicProp == NULL) {
		return -1;
	}

	StWemqTopicProp* tmp = ptTopicList->next;
	while (tmp != NULL) {
		if (tmp->flag == 0) {
			if ((strncmp(tmp->cServiceId, ptTopicProp->cServiceId, strlen(tmp->cServiceId)) == 0) &&
					(strncmp(tmp->cScenario, ptTopicProp->cScenario, strlen(tmp->cScenario)) == 0)) {
				if (pos != NULL)
					*pos = tmp;
				return 1;
			}
		} else {
			if (strncmp(tmp->cTopic, ptTopicProp->cTopic, strlen(tmp->cTopic)) == 0) {
				if (pos != NULL)
					*pos = tmp;

				return 1;
			}
		}

		tmp = tmp->next;
	}
	return -1;
}

int32_t wemq_topic_list_del_node(StWemqTopicList* ptTopicList, StWemqTopicProp* ptTopicProp)
{
	StWemqTopicProp* pos = NULL;
	if (wemq_topic_list_find_node(ptTopicList, ptTopicProp, &pos) != 1) {
		return -1;
	}

	StWemqTopicProp* tmp1 = pos;
	StWemqTopicProp* tmp2 = ptTopicList->next;
	if (tmp2 == tmp1) {
		ptTopicList->next = tmp1->next;
		free(tmp1);
		return 1;
	}

	while ((tmp2->next != tmp1) && (tmp2->next != NULL)) {
		tmp2 = tmp2->next;
	}
	tmp2->next = tmp1->next;
	free(tmp1);
	return 1;
}
