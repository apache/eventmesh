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

import {
  HStack,
  Select,
  Input,
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  TableContainer,
  useToast,
  Box,
} from '@chakra-ui/react';
import axios from 'axios';
import { useContext, useEffect, useState } from 'react';
import { AppContext } from '../../context/context';

interface EventMeshInstance {
  eventMeshClusterName: string,
  eventMeshName: string,
  endpoint: string,
  lastUpdateTimestamp: number,
  metadata: string
}

const EventMeshInstanceRow = ({
  eventMeshClusterName, eventMeshName, endpoint, lastUpdateTimestamp, metadata,
}: EventMeshInstance) => (
  <Tr>
    <Td>{eventMeshClusterName}</Td>
    <Td>{eventMeshName}</Td>
    <Td>{endpoint}</Td>
    <Td>{lastUpdateTimestamp}</Td>
    <Td>{metadata}</Td>
  </Tr>
);

const RegistryTable = () => {
  const { state } = useContext(AppContext);

  const [searchInput, setSearchInput] = useState<string>('');
  const handleSearchInputChange = (event: React.FormEvent<HTMLInputElement>) => {
    setSearchInput(event.currentTarget.value);
  };

  const [groupSet, setGroupSet] = useState<Set<string>>(new Set());
  const [groupFilter, setGroupFilter] = useState<string>('');
  const handleGroupSelectChange = (event: React.FormEvent<HTMLSelectElement>) => {
    setGroupFilter(event.currentTarget.value);
  };

  const [EventMeshInstanceList, setEventMeshInstanceList] = useState<EventMeshInstance[]>([]);
  const toast = useToast();
  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<EventMeshInstance[]>(`${state.endpoint}/registry`);
        setEventMeshInstanceList(data);

        const nextGroupSet = new Set<string>();
        data.forEach(({ eventMeshClusterName }) => {
          nextGroupSet.add(eventMeshClusterName);
        });
        setGroupSet(nextGroupSet);
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast({
            title: 'Failed to fetch registry list',
            description: 'unable to connect to the EventMesh daemon',
            status: 'error',
            duration: 3000,
            isClosable: true,
          });
          setEventMeshInstanceList([]);
        }
      }
    };

    fetch();
  }, []);

  return (
    <Box
      maxW="full"
      bg="white"
      borderWidth="1px"
      borderRadius="md"
      overflow="hidden"
      p="4"
    >
      <HStack
        spacing="2"
      >
        <Input
          w="200%"
          placeholder="Search"
          value={searchInput}
          onChange={handleSearchInputChange}
        />
        <Select
          placeholder="Select Group"
          onChange={handleGroupSelectChange}
        >
          {Array.from(groupSet).map((group) => (
            <option value={group} key={group}>{group}</option>
          ))}
        </Select>
      </HStack>

      <TableContainer>
        <Table variant="simple">
          <Thead>
            <Tr>
              <Th>Event Mesh Cluster Name</Th>
              <Th>Event Mesh Name</Th>
              <Th>Endpoint</Th>
              <Th>Last Update Timestamp</Th>
              <Th>Metadata</Th>
            </Tr>
          </Thead>
          <Tbody>
            {EventMeshInstanceList && EventMeshInstanceList.filter(({
              eventMeshClusterName, eventMeshName,
            }) => {
              if (searchInput && !eventMeshName.includes(searchInput)) {
                return false;
              }
              if (groupFilter && groupFilter !== eventMeshClusterName) {
                return false;
              }
              return true;
            }).map(({
              eventMeshClusterName, eventMeshName, endpoint, lastUpdateTimestamp, metadata,
            }) => (
              <EventMeshInstanceRow
                eventMeshClusterName={eventMeshClusterName}
                eventMeshName={eventMeshName}
                endpoint={endpoint}
                lastUpdateTimestamp={lastUpdateTimestamp}
                metadata={metadata}
              />
            ))}
          </Tbody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default RegistryTable;
