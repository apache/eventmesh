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

import React, {
  FC, useCallback, useEffect, useState,
} from 'react';
import axios from 'axios';
import {
  Button,
  Stack,
  Flex,
  Text,
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  TableContainer,
  TableCaption,
  Tag,
} from '@chakra-ui/react';
import moment from 'moment';
import { WorkflowInstanceType } from '../types';
import {
  WorkflowIntanceStatusMap,
  WorkflowIntanceStatusColorMap,
} from '../constant';

const ApiRoot = process.env.NEXT_PUBLIC_API_ROOT;

const Instances: FC<{ workflowId: string }> = ({ workflowId }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [instances, setInstances] = useState<WorkflowInstanceType[]>([]);
  const [total, setTotal] = useState(0);
  const [keywordFilter, setKeywordFilter] = useState('');
  const [pageIndex, setPageIndex] = useState(1);
  const pageSize = 10;
  const [refreshFlag, setRefreshFlag] = useState<number>(+new Date());

  const getWorkflows = useCallback(async () => {
    setIsLoading(true);
    try {
      const reqParams: {
        page: number;
        size: number;
        workflow_id?: string;
      } = {
        page: pageIndex,
        size: pageSize,
        workflow_id: workflowId,
      };

      const { data } = await axios.get<{
        total: number;
        workflow_instances: WorkflowInstanceType[];
      }>(`${ApiRoot}/workflow/instances`, {
        params: reqParams,
      });
      setInstances(data.workflow_instances);
      setTotal(data.total);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
    }
  }, [workflowId, pageIndex, pageSize, keywordFilter, refreshFlag]);

  useEffect(() => {
    const controller = new AbortController();
    getWorkflows();
    return () => {
      controller.abort();
    };
  }, [workflowId, pageIndex, pageSize, keywordFilter, refreshFlag]);

  return (
    <Stack>
      <Flex justifyContent="space-between" alignItems="center">
        <Button
          variant="ghost"
          colorScheme="blue"
          onClick={() => setRefreshFlag(+new Date())}
        >
          Refresh
        </Button>
        <Text>{`${total} instance${total > 1 ? 's' : ''} in total`}</Text>
      </Flex>
      <TableContainer>
        <Table variant="simple">
          <Thead>
            <Tr>
              <Th>Instance ID</Th>
              <Th>Status</Th>
              <Th>Updated at</Th>
              <Th>Created At</Th>
            </Tr>
          </Thead>
          <Tbody>
            {instances.map((workflow) => (
              <Tr key={workflow.workflow_instance_id}>
                <Td>{workflow.workflow_instance_id}</Td>
                <Td>
                  <Tag
                    size="sm"
                    colorScheme={WorkflowIntanceStatusColorMap.get(
                      workflow.workflow_status,
                    )}
                    variant="outline"
                  >
                    {WorkflowIntanceStatusMap.get(workflow.workflow_status)}
                  </Tag>
                </Td>
                <Td>
                  {moment(workflow.update_time).format('YYYY-mm-DD HH:mm:ss')}
                </Td>
                <Td>
                  {moment(workflow.create_time).format('YYYY-mm-DD HH:mm:ss')}
                </Td>
              </Tr>
            ))}
          </Tbody>

          {instances.length === 0 && (
            <TableCaption>
              <Text variant="xs" color="#909090">
                empty
              </Text>
            </TableCaption>
          )}
        </Table>
      </TableContainer>
    </Stack>
  );
};

export default Instances;
