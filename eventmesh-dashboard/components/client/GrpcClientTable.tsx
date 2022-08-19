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
  Button,
} from '@chakra-ui/react';
import axios from 'axios';
import { useContext, useEffect, useState } from 'react';
import { AppContext } from '../../context/context';

interface GrpcClient {
  env: string,
  subsystem: string,
  url: string,
  pid: number,
  host: string,
  port: number,
  version: string,
  idc: string,
  group: string,
  purpose: string,
  protocol: string,
}

interface GrpcClientProps {
  url: string,
  group: string,
}

interface RemoveGrpcClientRequest {
  url: string,
}

const GrpcClientRow = ({
  url, group,
}: GrpcClientProps) => {
  const { state } = useContext(AppContext);

  const toast = useToast();
  const [loading, setLoading] = useState(false);
  const onRemoveClick = async () => {
    try {
      setLoading(true);
      await axios.delete<RemoveGrpcClientRequest>(`${state.endpoint}/client/grpc`, {
        data: {
          url,
        },
      });
      setLoading(false);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        toast({
          title: 'Failed to remove the gRPC Client',
          description: error.message,
          status: 'error',
          duration: 3000,
          isClosable: true,
        });
      }
    }
  };

  return (
    <Tr>
      <Td>{url}</Td>
      <Td>{group}</Td>
      <Td>
        <HStack>
          <Button
            colorScheme="red"
            isLoading={loading}
            onClick={onRemoveClick}
          >
            Remove
          </Button>
        </HStack>
      </Td>
    </Tr>
  );
};

const GrpcClientTable = () => {
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

  const [GrpcClientList, setGrpcClientList] = useState<GrpcClient[]>([]);
  const toast = useToast();
  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<GrpcClient[]>(`${state.endpoint}/client/grpc`);
        setGrpcClientList(data);

        const nextGroupSet = new Set<string>();
        data.forEach(({ group }) => {
          nextGroupSet.add(group);
        });
        setGroupSet(nextGroupSet);
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast({
            title: 'Failed to fetch the list of gRPC Clients',
            description: 'unable to connect to the EventMesh daemon',
            status: 'error',
            duration: 3000,
            isClosable: true,
          });
          setGrpcClientList([]);
        }
      }
    };

    fetch();
  }, [GrpcClientList]);

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
              <Th>URL</Th>
              <Th>Group</Th>
              <Th>Action</Th>
            </Tr>
          </Thead>
          <Tbody>
            {GrpcClientList && GrpcClientList.filter(({
              url, group,
            }) => {
              if (searchInput && !url.includes(searchInput)) {
                return false;
              }
              if (groupFilter && groupFilter !== group) {
                return false;
              }
              return true;
            }).map(({
              url, group,
            }) => (
              <GrpcClientRow
                url={url}
                group={group}
              />
            ))}
          </Tbody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default GrpcClientTable;
