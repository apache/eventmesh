#include "rmb_vector.h"
#include <unistd.h>
#include <sys/time.h>
#include <sys/syscall.h>
#include <errno.h>
#include "rmb_msg.h"

void Init(Array *this)
{
    this->Input =_input;

	this->get_array_size = _get_size;
	this->return_index_value = _return_index_value;


    this->Constructor =_constructor;
    this->Destructor =_destructor;
    this->Constructor(this);
}
 
void _constructor(Array *this)
{
    this->size=0;
    this->max_size = MAX_SIZE_PER_TIME;
    this->Data=(DataType *)malloc(this->max_size*sizeof(DataType));
    memset(this->Data, 0x00, this->max_size*sizeof(DataType));
}
 
void _input(DataType data, Array *this)
{
    int i;
    DataType *ptr;
 
        
    ptr=(DataType *)malloc((this->max_size+MAX_SIZE_PER_TIME)*sizeof(DataType));
    memset(ptr, 0x00, (this->max_size+MAX_SIZE_PER_TIME)*sizeof(DataType));
    for(i=0;i<this->max_size;i++)
        ptr[i]=this->Data[i];
    free(this->Data);
    this->Data = ptr;

	snprintf(this->Data[this->max_size].unique_id, sizeof(data.unique_id), "%s", data.unique_id);
	snprintf(this->Data[this->max_size].biz_seq, sizeof(data.biz_seq), "%s", data.biz_seq);
	this->Data[this->max_size].flag = 1;
	this->Data[this->max_size].timeStamp = data.timeStamp;
	this->Data[this->max_size].timeout = data.timeout;
    this->max_size += MAX_SIZE_PER_TIME;
}
 

int _get_size(Array *this)
{
	assert(this != NULL);
	return this->max_size;
}

DataType _return_index_value(Array *this,int index)
{
	assert(this != NULL);
	return (this->Data[index]);
}
void _destructor(Array *this)
{
	assert(this != NULL);
    free(this->Data);
}
