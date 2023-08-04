#ifndef __RMB_VECTOR_H_
#define __RMB_VECTOR_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include "rmb_define.h"

#define MAX_SIZE_PER_TIME 1024
 
void Init(Array *this);
void _constructor(Array *this);
void _destructor(Array *this);
void _input(DataType data,Array *this);
int _get_size(Array *this);
DataType _return_index_value(Array *this, int index);


#ifdef __cplusplus
}
#endif

#endif
