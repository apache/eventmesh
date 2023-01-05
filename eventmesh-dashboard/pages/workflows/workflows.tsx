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

import React from 'react';
import Head from 'next/head';
import type { NextPage } from 'next';
import { useRouter } from 'next/router';

import {
  Divider,
  Button,
  Flex,
  Input,
  Stack,
  Select,
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  TableContainer,
} from '@chakra-ui/react';

const Workflows: NextPage = () => {
  const router = useRouter();

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
            onClick={() => router.push('/workflows/create')}
          >
            Create Wokrflow
          </Button>
          <Stack direction="row" spacing="2">
            <Input size="md" placeholder="Workflow name" />
            <Select size="md" placeholder="Status">
              <option value="option1">Option 1</option>
              <option value="option2">Option 2</option>
              <option value="option3">Option 3</option>
              <option value="option3">Option 3</option>
            </Select>
            <Button size="md" w="60" colorScheme="blue" variant="outline">
              Refresh
            </Button>
          </Stack>
        </Flex>
        <Divider mt="15" mb="15" orientation="horizontal" />
        <TableContainer>
          <Table variant="simple" size="sm">
            <Thead>
              <Tr>
                <Th>Name</Th>
                <Th>Created At</Th>
                <Th>Status</Th>
                <Th>Logs</Th>
                <Th isNumeric>Total Instance</Th>
                <Th isNumeric>Running</Th>
                <Th isNumeric>Failed</Th>
                <Th isNumeric>Timeout</Th>
                <Th isNumeric>Aborted</Th>
                <Th>Actions</Th>
              </Tr>
            </Thead>
            <Tbody>
              <Tr>
                <Td>
                  <Button size="sm" colorScheme="blue" variant="ghost">
                    HelloWorld
                  </Button>
                </Td>
                <Td>2022-12-1 12:10:12</Td>
                <Td>Active</Td>
                <Td>On</Td>
                <Td isNumeric>3</Td>
                <Td isNumeric>1</Td>
                <Td isNumeric>1</Td>
                <Td isNumeric>0</Td>
                <Td isNumeric>1</Td>
                <Td>
                  <Button size="sm" colorScheme="blue" variant="ghost">
                    Execute
                  </Button>
                  <Button size="sm" colorScheme="blue" variant="ghost">
                    Edit
                  </Button>
                  <Button size="sm" colorScheme="blue" variant="ghost">
                    Delete
                  </Button>
                </Td>
              </Tr>
            </Tbody>
          </Table>
        </TableContainer>
      </Flex>
    </>
  );
};

export default Workflows;
