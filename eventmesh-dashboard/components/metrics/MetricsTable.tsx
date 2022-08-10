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

interface EventMeshMetrics {
  maxHTTPTPS: number,
  avgHTTPTPS: number,
  maxHTTPCost: number,
  avgHTTPCost: number,
  avgHTTPBodyDecodeCost: number,
  httpDiscard: number,
  maxBatchSendMsgTPS: number,
  avgBatchSendMsgTPS: number,
  sendBatchMsgNumSum: number,
  sendBatchMsgFailNumSum: number,
  sendBatchMsgFailRate: number,
  sendBatchMsgDiscardNumSum: number,
  maxSendMsgTPS: number,
  avgSendMsgTPS: number,
  sendMsgNumSum: number,
  sendMsgFailNumSum: number,
  sendMsgFailRate: number,
  replyMsgNumSum: number,
  replyMsgFailNumSum: number,
  maxPushMsgTPS: number,
  avgPushMsgTPS: number,
  pushHTTPMsgNumSum: number,
  pushHTTPMsgFailNumSum: number,
  pushHTTPMsgFailRate: number,
  maxHTTPPushLatency : number,
  avgHTTPPushLatency : number,
  batchMsgQueueSize : number,
  sendMsgQueueSize : number,
  pushMsgQueueSize : number,
  retryHTTPQueueSize : number,
  avgBatchSendMsgCost : number,
  avgSendMsgCost : number,
  avgReplyMsgCost : number,

  // TCP Metrics
  retryTCPQueueSize: number,
  client2eventMeshTCPTPS : number,
  eventMesh2mqTCPTPS : number,
  mq2eventMeshTCPTPS : number,
  eventMesh2clientTCPTPS : number,
  allTCPTPS : number,
  allTCPConnections : number,
  subTopicTCPNum : number
}

const MetricsTable = () => {
  const { state } = useContext(AppContext);
  const [metrics, setMetrics] = useState<Partial<EventMeshMetrics>>({});

  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<EventMeshMetrics>(`${state.endpoint}/metrics`);
        setMetrics(data);
      } catch (error) {
        setMetrics({});
      }
    };

    fetch();
  }, []);

  type MetricRecord = Record<string, number | undefined>;
  const httpMetrics: MetricRecord = {
    'Max HTTP TPS': metrics.maxHTTPTPS,
    'Avg HTTP TPS': metrics.avgHTTPTPS,
    'Max HTTP Cost': metrics.maxHTTPCost,
    'Avg HTTP Cost': metrics.avgHTTPCost,
    'Avg HTTP Body Decode Cost': metrics.avgHTTPBodyDecodeCost,
    'HTTP Discard': metrics.httpDiscard,
  };
  const batchMetrics: MetricRecord = {
    'Max Batch Send Msg TPS': metrics.maxBatchSendMsgTPS,
    'Avg Batch Send Msg TPS': metrics.avgBatchSendMsgTPS,
    'Send Batch Msg Num Sum': metrics.sendBatchMsgNumSum,
    'Send Batch Msg Fail Num Sum': metrics.sendBatchMsgFailNumSum,
    'Send Batch Msg Fail Rate': metrics.sendBatchMsgFailRate,
    'Send Batch Msg Discard Num Sum': metrics.sendBatchMsgDiscardNumSum,
  };
  const sendMetrics: MetricRecord = {
    'Max Send Msg TPS': metrics.maxSendMsgTPS,
    'Avg Send Msg TPS': metrics.avgSendMsgTPS,
    'Send Msg Num Sum': metrics.sendMsgNumSum,
    'Send Msg Fail Num Sum': metrics.sendMsgFailNumSum,
    'Send Msg Fail Rate': metrics.sendMsgFailRate,
    'Reply Msg Num Sum': metrics.replyMsgNumSum,
    'Reply Msg Fail Num Sum': metrics.replyMsgFailNumSum,
  };
  const pushMetrics: MetricRecord = {
    'Max Push Msg TPS': metrics.maxPushMsgTPS,
    'Avg Push Msg TPS': metrics.avgPushMsgTPS,
    'Push HTTP Msg Num Sum': metrics.pushHTTPMsgNumSum,
    'Push HTTP Msg Fail Num Sum': metrics.pushHTTPMsgFailNumSum,
    'Push HTTP Msg Fail Rate': metrics.pushHTTPMsgFailRate,
    'Max HTTP Push Latency': metrics.maxHTTPPushLatency,
    'Avg HTTP Push Latency': metrics.avgHTTPPushLatency,
  };
  const tcpMetrics: MetricRecord = {
    'Retry TCP Queue Size': metrics.retryTCPQueueSize,
    'Client2eventMesh TCP TPS': metrics.client2eventMeshTCPTPS,
    'EventMesh2mq TCP TPS': metrics.eventMesh2mqTCPTPS,
    'MQ2eventMesh TCP TPS': metrics.mq2eventMeshTCPTPS,
    'EventMesh2client TCP TPS': metrics.eventMesh2clientTCPTPS,
    'All TCP TPS': metrics.allTCPTPS,
    'All TCP Connections': metrics.allTCPConnections,
    'Sub Topic TCP Num': metrics.subTopicTCPNum,
  };

  const convertConfigurationToTable = (
    metricRecord: Record<string, string | number | boolean | undefined>,
  ) => Object.entries(metricRecord).map(([key, value]) => {
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

  if (Object.keys(metrics).length === 0) {
    return false;
  }

  return (
    <>
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
          EventMesh HTTP Metrics
        </Text>

        <TableContainer mt="4">
          <Table variant="simple">
            <Thead>
              <Tr>
                <Th>Metric</Th>
                <Th>Value</Th>
              </Tr>
            </Thead>
            <Tbody>
              {convertConfigurationToTable(httpMetrics)}
            </Tbody>
          </Table>
        </TableContainer>

        <Text
          w="full"
          mt="4"
        >
          HTTP Batch Metrics
        </Text>

        <TableContainer mt="4">
          <Table variant="simple">
            <Thead>
              <Tr>
                <Th>Metric</Th>
                <Th>Value</Th>
              </Tr>
            </Thead>
            <Tbody>
              {convertConfigurationToTable(batchMetrics)}
            </Tbody>
          </Table>
        </TableContainer>

        <Text
          w="full"
          mt="4"
        >
          HTTP Send Metrics
        </Text>

        <TableContainer mt="4">
          <Table variant="simple">
            <Thead>
              <Tr>
                <Th>Metric</Th>
                <Th>Value</Th>
              </Tr>
            </Thead>
            <Tbody>
              {convertConfigurationToTable(sendMetrics)}
            </Tbody>
          </Table>
        </TableContainer>

        <Text
          w="full"
          mt="4"
        >
          HTTP Push Metrics
        </Text>

        <TableContainer mt="4">
          <Table variant="simple">
            <Thead>
              <Tr>
                <Th>Metric</Th>
                <Th>Value</Th>
              </Tr>
            </Thead>
            <Tbody>
              {convertConfigurationToTable(pushMetrics)}
            </Tbody>
          </Table>
        </TableContainer>
      </Box>
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
          mt="4"
        >
          TCP Metrics
        </Text>

        <TableContainer mt="4">
          <Table variant="simple">
            <Thead>
              <Tr>
                <Th>Metric</Th>
                <Th>Value</Th>
              </Tr>
            </Thead>
            <Tbody>
              {convertConfigurationToTable(tcpMetrics)}
            </Tbody>
          </Table>
        </TableContainer>

      </Box>
    </>
  );
};

export default MetricsTable;
