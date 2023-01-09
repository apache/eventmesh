import { WorkflowStatusEnum, WorkflowInstanceStatusEnum } from './types';

export const WorkflowStatusMap = new Map([
  [WorkflowStatusEnum.Normal, 'Normal'],
  [WorkflowStatusEnum.Deleted, 'Deleted'],
]);

export const WorkflowIntanceStatusMap = new Map([
  [WorkflowInstanceStatusEnum.Sleep, 'Sleeping'],
  [WorkflowInstanceStatusEnum.Wait, 'Waiting'],
  [WorkflowInstanceStatusEnum.Process, 'Processing'],
  [WorkflowInstanceStatusEnum.Succeed, 'Succeeded'],
  [WorkflowInstanceStatusEnum.Fail, 'Failed'],
]);

export const WorkflowIntanceStatusColorMap = new Map([
  [WorkflowInstanceStatusEnum.Sleep, 'gray'],
  [WorkflowInstanceStatusEnum.Wait, 'orange'],
  [WorkflowInstanceStatusEnum.Process, 'blue'],
  [WorkflowInstanceStatusEnum.Succeed, 'green'],
  [WorkflowInstanceStatusEnum.Fail, 'red'],
]);
