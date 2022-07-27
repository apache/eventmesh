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
import { useEffect, useState } from 'react';

interface Client {
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

interface ClientProps {
  host: string,
  port: number,
  group: string,
  protocol: string,
  url: string,
}

interface RemoveClientRequest {
  host: string,
  port: number,
  protocol: string,
  url: string,
}

const ClientRow = ({
  host, port, group, protocol, url,
}: ClientProps) => {
  const toast = useToast();
  const [loading, setLoading] = useState(false);
  const onRemoveClick = async () => {
    try {
      setLoading(true);
      await axios.delete<RemoveClientRequest>('/client', {
        data: {
          host,
          port,
          protocol,
          url,
        },
      });
      setLoading(false);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        toast({
          title: 'Failed to remove the client',
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
      {protocol === 'TCP' && <Td>{`${host}:${port}`}</Td>}
      {(protocol === 'HTTP' || protocol === 'gRPC') && <Td>{url}</Td>}
      <Td>{group}</Td>
      <Td>{protocol}</Td>
      <Td>
        <HStack>
          <Button
            isDisabled
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

const ClientTable = () => {
  const [searchInput, setSearchInput] = useState<string>('');
  const handleSearchInputChange = (event: React.FormEvent<HTMLInputElement>) => {
    setSearchInput(event.currentTarget.value);
  };

  const [protocolFilter, setProtocolFilter] = useState<string>('');
  const handleProtocolSelectChange = (event: React.FormEvent<HTMLSelectElement>) => {
    setProtocolFilter(event.currentTarget.value);
  };

  const [groupSet, setGroupSet] = useState<Set<string>>(new Set());
  const [groupFilter, setGroupFilter] = useState<string>('');
  const handleGroupSelectChange = (event: React.FormEvent<HTMLSelectElement>) => {
    setGroupFilter(event.currentTarget.value);
  };

  const [clientList, setClientList] = useState<Client[]>([]);
  const toast = useToast();
  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<Client[]>('/client');
        setClientList(data);

        const nextGroupSet = new Set<string>();
        data.forEach(({ group }) => {
          nextGroupSet.add(group);
        });
        setGroupSet(nextGroupSet);
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast({
            title: 'Failed to fetch the list of clients',
            description: error.message,
            status: 'error',
            duration: 3000,
            isClosable: true,
          });
          setClientList([]);
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
          placeholder="Select Protocol"
          onChange={handleProtocolSelectChange}
        >
          <option value="TCP">TCP</option>
          <option value="HTTP">HTTP</option>
          <option value="gRPC">gRPC</option>
        </Select>
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
              <Th>Host or url</Th>
              <Th>Group</Th>
              <Th>Protocol</Th>
              <Th>Action</Th>
            </Tr>
          </Thead>
          <Tbody>
            {clientList && clientList.filter(({
              host, port, group, protocol,
            }) => {
              const address = `${host}:${port}`;
              if (searchInput && !address.includes(searchInput)) {
                return false;
              }
              if (groupFilter && groupFilter !== group) {
                return false;
              }
              if (protocolFilter && protocolFilter !== protocol) {
                return false;
              }
              return true;
            }).map(({
              host, port, group, protocol, url,
            }) => (
              <ClientRow
                host={host}
                port={port}
                group={group}
                protocol={protocol}
                url={url}
              />
            ))}
          </Tbody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ClientTable;
