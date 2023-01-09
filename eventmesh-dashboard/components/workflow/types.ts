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
