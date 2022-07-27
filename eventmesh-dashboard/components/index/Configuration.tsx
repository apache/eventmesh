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
  Text,
  Table,
  TableContainer,
  Tbody,
  Th,
  Thead,
  Tr,
  Td,
  Box,
} from '@chakra-ui/react';
import axios from 'axios';
import React, { useContext, useEffect, useState } from 'react';
import { AppContext } from '../../context/context';

interface EventMeshConfiguration {
  sysID: string,
  namesrvAddr: string,
  eventMeshEnv: string,
  eventMeshIDC: string,
  eventMeshCluster: string,
  eventMeshServerIp: string,
  eventMeshName: string,
  eventMeshWebhookOrigin: string,
  eventMeshServerSecurityEnable: boolean,
  eventMeshServerRegistryEnable: boolean,
}

const Configuration = () => {
  const { state } = useContext(AppContext);
  const [configuration, setConfiguration] = useState<Partial<EventMeshConfiguration>>({});

  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<EventMeshConfiguration>(`${state.endpoint}/configuration`);
        setConfiguration(data);
      } catch (error) {
        setConfiguration({});
      }
    };

    fetch();
  }, []);

  const commonConfiguration: Record<string, string | number | boolean | undefined> = {
    'System ID': configuration.sysID,
    'NameServer Address': configuration.namesrvAddr,
    'EventMesh Environment': configuration.eventMeshEnv,
    'EventMesh IDC': configuration.eventMeshIDC,
    'EventMesh Cluster': configuration.eventMeshCluster,
    'EventMesh Server IP': configuration.eventMeshServerIp,
    'EventMEsh Name': configuration.eventMeshName,
    'EventMesh Webhook Origin': configuration.eventMeshWebhookOrigin,
    'EventMesh Server Security Enable': configuration.eventMeshServerSecurityEnable,
    'EventMesh Server Registry Enable': configuration.eventMeshServerRegistryEnable,
  };

  const convertConfigurationToTable = (
    configurationRecord: Record<string, string | number | boolean | undefined>,
  ) => Object.entries(configurationRecord).map(([key, value]) => {
    if (value === undefined) {
      return (
        <Tr>
          <Td>{key}</Td>
          <Td>Undefined</Td>
        </Tr>
      );
    }

    return (
      <Tr>
        <Td>{key}</Td>
        <Td>{value.toString()}</Td>
      </Tr>
    );
  });

  return (
    <Box
      maxW="full"
      bg="white"
      borderWidth="1px"
      borderRadius="md"
      overflow="hidden"
      p="4"
      mt="4"
    >
      <Text
        w="full"
      >
        EventMesh Configuration
      </Text>

      <TableContainer mt="4">
        <Table variant="simple">
          <Thead>
            <Tr>
              <Th>Configuration Field</Th>
              <Th>Value</Th>
            </Tr>
          </Thead>
          <Tbody>
            {convertConfigurationToTable(commonConfiguration)}
          </Tbody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default Configuration;
