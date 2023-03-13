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

import React, { useState, useEffect, useCallback } from 'react';
import Head from 'next/head';
import type { NextPage } from 'next';

import {
  Divider,
  Button,
  Flex,
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  TableContainer,
  Box,
  Spinner,
  Text,
} from '@chakra-ui/react';
import { ChevronLeftIcon, ChevronRightIcon } from '@chakra-ui/icons';
import axios from 'axios';
import moment from 'moment';
import Details from '../components/eventCatalogs/Details';
import CreateCatalog from '../components/eventCatalogs/Create';
import { EventCatalogType } from '../components/eventCatalogs/types';
import { WorkflowStatusMap } from '../components/eventCatalogs/constant';

const ApiRoot = process.env.NEXT_PUBLIC_EVENTCATALOG_API_ROOT;

const EventCatalogs: NextPage = () => {
  const [isShowCreate, setIsShowCreate] = useState(false);
  const [curCatalog, setCurCatalog] = useState<EventCatalogType>();

  const [catalogs, setCatalogs] = useState<EventCatalogType[]>([]);
  const [total, setTotal] = useState(0);

  const pageSize = 10;
  const [isLoading, setIsLoading] = useState(true);
  const [pageIndex, setPageIndex] = useState(1);

  const [refreshFlag, setRefreshFlag] = useState<number>(+new Date());

  const getEventCatalogs = useCallback(async () => {
    setIsLoading(true);
    try {
      const { data } = await axios.get<{
        total: number;
        events: EventCatalogType[];
      }>(`${ApiRoot}/catalog`, {
        params: { page: pageIndex, size: pageSize },
      });
      setCatalogs(data.events);
      setTotal(data.total);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getEventCatalogs();
    return () => {
      controller.abort();
    };
  }, [pageIndex, pageSize, refreshFlag]);

  return (
    <>
      <Head>
        <title>Event Catalogs | Apache EventMesh Dashboard</title>
      </Head>
      <Box
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
            Create Catalog
          </Button>
          <Button
            size="md"
            colorScheme="blue"
            variant="ghost"
            onClick={() => setRefreshFlag(+new Date())}
          >
            Refresh
          </Button>
        </Flex>
        <Divider mt="15" mb="15" orientation="horizontal" />
        <TableContainer>
          <Table variant="simple">
            <Thead>
              <Tr>
                {/* <Th>Catalog ID</Th> */}
                <Th>Title</Th>
                <Th>File Name</Th>
                <Th>Version</Th>
                <Th>Status</Th>
                <Th>Created At</Th>
                <Th>Updated At</Th>
              </Tr>
            </Thead>
            <Tbody>
              {catalogs.map((catalog) => (
                <Tr key={catalog.id}>
                  {/* <Td>
                    <Button
                      colorScheme="blue"
                      variant="ghost"
                      onClick={() => setCurCatalog(catalog)}
                    >
                      {catalog.id}
                    </Button>
                  </Td> */}
                  <Td>
                    <Button
                      colorScheme="blue"
                      variant="ghost"
                      onClick={() => setCurCatalog(catalog)}
                    >
                      {catalog.title}
                    </Button>
                  </Td>
                  <Td>{catalog.file_name}</Td>
                  <Td>{catalog.version}</Td>
                  <Td>{WorkflowStatusMap.get(catalog.status)}</Td>
                  <Td>
                    {moment(catalog.create_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Td>
                  <Td>
                    {moment(catalog.update_time).format('YYYY-MM-DD HH:mm:ss')}
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
              {` catalog${total > 1 ? 's' : ''} in total, `}
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
      </Box>
      <Details
        visible={Boolean(curCatalog)}
        data={curCatalog}
        onClose={() => setCurCatalog(undefined)}
      />
      <CreateCatalog
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

export default EventCatalogs;
