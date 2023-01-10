/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

export type WorkflowType = {
  create_time: string;
  definition: string;
  id: number;
  status: number;
  total_failed_instances: number;
  total_instances: number;
  total_running_instances: number;
  update_time: string;
  version: string;
  workflow_id: string;
  workflow_name: string;
};

export type WorkflowInstanceType = {
  create_time: string,
  id: number,
  update_time: string,
  workflow_id : string,
  workflow_instance_id : string,
  workflow_status : number
};

export enum WorkflowStatusEnum {
  'Normal' = 1,
  'Deleted' = -1,
}

export enum WorkflowInstanceStatusEnum {
  Sleep = 1,
  Wait = 2,
  Process = 3,
  Succeed = 4,
  Fail = 5,
}
