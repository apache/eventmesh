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

#ifndef RMB_CFG_H_
#define RMB_CFG_H_

#include "rmb_define.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define CFG_STRING	(int)1
#define CFG_INT		(int)2
#define CFG_LONG	(int)3
#define CFG_DOUBLE	(int)4
#define CFG_LINE	(int)5
#define CFG_SHORT   (int)6

#define US      0x1f

#define MAX_CONFIG_LINE_LEN 1023

  void RMB_TLib_Cfg_GetConfig (char *sConfigFilePath, ...);

#define  Rmb_TLib_Cfg_GetConfig(sConfigFilePath,fmt,args...)  RMB_TLib_Cfg_GetConfig(sConfigFilePath,fmt,## args)

/**
 * Function: rmb_load_config
 * Description: rmb load configure
 * Return:
 * 		0: success
 * 		-1: failed
 */
  int rmb_load_config (const char *configPath);

  const char *rmb_get_host_ip ();

  void rmb_get_config_python (RmbPythonConfig * config);

  const char *getRmbLastError ();

#ifdef __cplusplus
}
#endif

#endif                          /* RMB_CFG_H_ */
