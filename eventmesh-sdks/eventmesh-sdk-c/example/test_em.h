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

#ifndef RMB_C_LIB_RMB_TEST_TEST_RMB_CAPI_FOR_JEKINS_H_
#define RMB_C_LIB_RMB_TEST_TEST_RMB_CAPI_FOR_JEKINS_H_

#define LOG_PRINT(loglevel,fmt, args...) {log_print(loglevel, "[%s:%d(%s)]:     "fmt"", __FILE__, __LINE__, __FUNCTION__, ## args);}

//#define UNIX_DOMAIN "./unix.domain"
#define UNIX_DOMAIN "unix.domain"

typedef struct tMessage {
	int type;				//0:pub 1:sub
	pid_t pid;
	unsigned int process_num;
	unsigned int process_status;
	unsigned int has_send;
	unsigned int send_msg_num;
	unsigned int recv_msg_num;
}tMessage;

//////////////////////////////////////////////////////////////////////////
#endif /* RMB_C_LIB_RMB_TEST_TEST_RMB_CAPI_FOR_JEKINS_H_ */
