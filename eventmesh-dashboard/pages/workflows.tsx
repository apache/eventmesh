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
  useCallback, useEffect, useState, useRef,
} from 'react';
import Head from 'next/head';
import type { NextPage } from 'next';
import moment from 'moment';
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  WarningTwoIcon,
} from '@chakra-ui/icons';

import {
  Divider,
  Button,
  Flex,
  Input,
  Stack,
  // Select,
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  TableContainer,
  Text,
  AlertDialog,
  AlertDialogOverlay,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogBody,
  AlertDialogFooter,
  useToast,
  Box,
  Spinner,
} from '@chakra-ui/react';
import axios from 'axios';
import Details from '../components/workflow/Details';
import Create from '../components/workflow/Create';
import { WorkflowType } from '../components/workflow/types';
// import { WorkflowStatusMap } from '../components/workflow/constant';

const ApiRoot = process.env.NEXT_PUBLIC_WORKFLOW_API_ROOT;

const Workflows: NextPage = () => {
  const toast = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [workflows, setWorkflows] = useState<WorkflowType[]>([]);
  const [total, setTotal] = useState(0);
  const [keywordFilter, setKeywordFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('any');
  const [pageIndex, setPageIndex] = useState(1);
  const pageSize = 10;
  const [refreshFlag, setRefreshFlag] = useState<number>(+new Date());
  const [isShowCreate, setIsShowCreate] = useState(false);
  const [isShowDetails, setIsShowDetails] = useState(false);
  const [isShowCancelConfirm, setIsShowCancelComfirm] = useState(false);
  const cancelRef = useRef(null);

  const [selectedWorkflow, setSelectedWorkflow] = useState<WorkflowType | null>(
    null,
  );

  const onDelete = () => {
    axios
      .delete(`${ApiRoot}/workflow/${selectedWorkflow?.workflow_id}`)
      .then(() => {
        toast({
          title: 'Workflow has been deleted',
          description: (
            <Box>
              <Text>{`Workflow ID: ${selectedWorkflow?.workflow_id}`}</Text>
              <Text>{`Workflow Name: ${selectedWorkflow?.workflow_name}`}</Text>
            </Box>
          ),
          status: 'success',
          position: 'top-right',
        });
        setIsShowCancelComfirm(false);
        setSelectedWorkflow(null);
        setRefreshFlag(+new Date());
      })
      .catch((error) => {
        setIsShowCancelComfirm(false);
        toast({
          title: 'Failed to delete',
          description: error.response.data,
          status: 'error',
          position: 'top-right',
        });
      });
  };

  const getWorkflows = useCallback(async () => {
    setIsLoading(true);
    try {
      const reqParams: {
        page: number;
        size: number;
        workflow_id?: string;
        status?: string;
      } = {
        page: pageIndex,
        size: pageSize,
      };
      // if (statusFilter) {
      //   reqParams.status = statusFilter;
      // }
      if (keywordFilter) {
        reqParams.workflow_id = keywordFilter;
      }
      const { data } = await axios.get<{
        total: number;
        workflows: WorkflowType[];
      }>(`${ApiRoot}/workflow`, {
        params: reqParams,
      });
      setWorkflows(data.workflows);
      setTotal(data.total);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
    }
  }, [pageIndex, pageSize, keywordFilter, statusFilter, refreshFlag]);

  useEffect(() => {
    const controller = new AbortController();
    getWorkflows();
    return () => {
      controller.abort();
    };
  }, [pageIndex, pageSize, keywordFilter, statusFilter, refreshFlag]);

  return (
    <>
      <Head>
        <title>Workflows | Apache EventMesh Dashboard</title>
      </Head>
      <Flex
        w="full"
        h="full"
        bg="white"
        flexDirection="column"
        borderWidth="1px"
        borderRadius="md"
        overflow="hidden"
        p="6"
      >
        <Flex w="full" justifyContent="space-between" mt="2" mb="2">
          <Button
            size="md"
            backgroundColor="#2a62ad"
            color="white"
            _hover={{ bg: '#dce5fe', color: '#2a62ad' }}
            onClick={() => setIsShowCreate(true)}
          >
            Create Workflow
          </Button>
          <Stack direction="row" spacing="2">
            <Input
              size="md"
              placeholder="Workflow ID"
              value={keywordFilter}
              onChange={(evt) => setKeywordFilter(evt.target.value)}
            />
            {/* <Select
              size="md"
              placeholder="Status"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              <option value="1">Running</option>
              <option value="-1">Deleted</option>
            </Select> */}
            <Box>
              <Button
                colorScheme="blue"
                variant="ghost"
                onClick={() => setRefreshFlag(+new Date())}
              >
                Refresh
              </Button>
            </Box>
          </Stack>
        </Flex>
        <Divider mt="15" mb="15" orientation="horizontal" />
        <TableContainer>
          <Table variant="simple">
            <Thead>
              <Tr>
                <Th>Workflow ID</Th>
                <Th>Workflow Name</Th>
                {/* <Th>Status</Th> */}
                <Th isNumeric>Total Instance</Th>
                <Th isNumeric>Running</Th>
                <Th isNumeric>Failed</Th>
                <Th>Updated at</Th>
                <Th>Created At</Th>
                <Th>Actions</Th>
              </Tr>
            </Thead>
            <Tbody>
              {workflows.map((workflow) => (
                <Tr key={workflow.workflow_id}>
                  <Td>
                    <Button
                      size="sm"
                      colorScheme="blue"
                      variant="ghost"
                      onClick={() => {
                        setIsShowDetails(true);
                        setSelectedWorkflow(workflow);
                      }}
                    >
                      {workflow.workflow_id}
                    </Button>
                  </Td>
                  <Td>{workflow.workflow_name}</Td>

                  {/* <Td>{WorkflowStatusMap.get(workflow.status)}</Td> */}
                  <Td isNumeric>{workflow.total_instances}</Td>
                  <Td isNumeric>{workflow.total_running_instances}</Td>
                  <Td isNumeric>{workflow.total_failed_instances}</Td>
                  <Td>
                    {moment(workflow.update_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Td>
                  <Td>
                    {moment(workflow.create_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Td>
                  <Td>
                    <Button
                      size="sm"
                      colorScheme="blue"
                      variant="ghost"
                      onClick={() => {
                        setSelectedWorkflow(workflow);
                        setIsShowCancelComfirm(true);
                      }}
                    >
                      Delete
                    </Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableContainer>
        <Flex mt={4} alignItems="center">
          {isLoading ? (
            <Spinner colorScheme="blue" size="sm" />
          ) : (
            <Text fontSize="sm" color="#909090">
              {total}
              {` workflow${total > 1 ? 's' : ''} in total, `}
              {`page ${pageIndex} of ${Math.ceil(total / pageSize)}`}
            </Text>
          )}
          <Flex flex={1} justifyContent="flex-end" align="center">
            <Button
              mr={2}
              size="sm"
              leftIcon={<ChevronLeftIcon />}
              colorScheme="blue"
              variant="outline"
              disabled={pageIndex < 2}
              onClick={() => setPageIndex(pageIndex - 1)}
            >
              Prev
            </Button>
            <Button
              size="sm"
              rightIcon={<ChevronRightIcon />}
              colorScheme="blue"
              variant="outline"
              disabled={pageIndex >= Math.ceil(total / pageSize)}
              onClick={() => setPageIndex(pageIndex + 1)}
            >
              Next
            </Button>
          </Flex>
        </Flex>
      </Flex>

      <AlertDialog
        leastDestructiveRef={cancelRef}
        isOpen={isShowCancelConfirm}
        onClose={() => setIsShowCancelComfirm(false)}
      >
        <AlertDialogOverlay>
          <AlertDialogContent>
            <AlertDialogHeader fontSize="lg" fontWeight="bold">
              <Flex alignItems="center">
                <WarningTwoIcon mr={2} boxSize={6} color="orange" />
                <Text fontSize="xl" as="b">
                  Confirm
                </Text>
              </Flex>
            </AlertDialogHeader>

            <AlertDialogBody>
              Are you sure to delete
              {' '}
              <Text fontSize="sm" as="b">
                {selectedWorkflow?.workflow_name}
              </Text>
              ?
              <Box />
            </AlertDialogBody>

            <AlertDialogFooter>
              <Button
                ref={cancelRef}
                onClick={() => {
                  setIsShowCancelComfirm(false);
                  setSelectedWorkflow(null);
                }}
              >
                No
              </Button>
              <Button colorScheme="blue" onClick={() => onDelete()} ml={3}>
                Delete
              </Button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialogOverlay>
      </AlertDialog>

      <Details
        visible={isShowDetails}
        data={selectedWorkflow}
        onSaved={() => {
          setIsShowDetails(false);
          setRefreshFlag(+new Date());
        }}
        onClose={() => {
          setIsShowDetails(false);
          setSelectedWorkflow(null);
        }}
      />

      <Create
        visible={isShowCreate}
        onSucceed={() => {
          setIsShowCreate(false);
          setPageIndex(1);
          setRefreshFlag(+new Date());
        }}
        onClose={() => setIsShowCreate(false)}
      />
    </>
  );
};

export default Workflows;
