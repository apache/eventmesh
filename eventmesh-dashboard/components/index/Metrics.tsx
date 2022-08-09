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
      sendMsgQueueSize  : number,
      pushMsgQueueSize : number,
      retryHTTPQueueSize : number,
      avgBatchSendMsgCost : number,
      avgSendMsgCost : number,
      avgReplyMsgCost : number,
  
    //TCP Metrics
      retryTCPQueueSize: number,
      client2eventMeshTCPTPS : number,
      eventMesh2mqTCPTPS : number,
      mq2eventMeshTCPTPS : number,
      eventMesh2clientTCPTPS : number,
      allTCPTPS : number,
      allTCPConnections : number,
      subTopicTCPNum : number
  }
  
  const Metrics = () => {
    const { state } = useContext(AppContext);
    const [configuration, setConfiguration] = useState<Partial<EventMeshMetrics>>({});
  
    useEffect(() => {
      const fetch = async () => {
        try {
          const { data } = await axios.get<EventMeshMetrics>(`${state.endpoint}/metrics`);
          setConfiguration(data);
        } catch (error) {
          setConfiguration({});
        }
      };
  
      fetch();
    }, []);
  
    type MetricRecord = Record<string, string | number | boolean | undefined>;
    const httpMetrics: MetricRecord = {
      'Max HTTP TPS': configuration.maxHTTPTPS,
      'Avg HTTP TPS': configuration.avgHTTPTPS,
      'Max HTTP Cost': configuration.maxHTTPCost,
      'Avg HTTP Cost': configuration.avgHTTPCost,
      'Avg HTTP Body Decode Cost': configuration.avgHTTPBodyDecodeCost,
      'HTTP Discard': configuration.httpDiscard,
    };
    const batchMetrics: MetricRecord = {
      'Max Batch Send Msg TPS': configuration.maxBatchSendMsgTPS,
      'Avg Batch Send Msg TPS': configuration.avgBatchSendMsgTPS,
      'Send Batch Msg Num Sum': configuration.sendBatchMsgNumSum,
      'Send Batch Msg Fail Num Sum': configuration.sendBatchMsgFailNumSum,
      'Send Batch Msg Fail Rate': configuration.sendBatchMsgFailRate,
      'Send Batch Msg Discard Num Sum': configuration.sendBatchMsgDiscardNumSum,
    };
    const sendMetrics: MetricRecord = {
      'Max Send Msg TPS': configuration.maxSendMsgTPS,
      'Avg Send Msg TPS': configuration.avgSendMsgTPS,
      'Send Msg Num Sum': configuration.sendMsgNumSum,
      'Send Msg Fail Num Sum': configuration.sendMsgFailNumSum,
      'Send Msg Fail Rate': configuration.sendMsgFailRate,
      'Reply Msg Num Sum': configuration.replyMsgNumSum,
      'Reply Msg Fail Num Sum': configuration.replyMsgFailNumSum,
    };
    const pushMetrics: MetricRecord = {
      'Max Push Msg TPS': configuration.maxPushMsgTPS,
      'Avg Push Msg TPS': configuration.avgPushMsgTPS,
      'Push HTTP Msg Num Sum': configuration.pushHTTPMsgNumSum,
      'Push HTTP Msg Fail Num Sum': configuration.pushHTTPMsgFailNumSum,
      'Push HTTP Msg Fail Rate': configuration.pushHTTPMsgFailRate,
      'Max HTTP Push Latency': configuration.maxHTTPPushLatency,
      'Avg HTTP Push Latency': configuration.avgHTTPPushLatency,
    };
    const tcpMetrics: MetricRecord = {
      'Retry TCP Queue Size': configuration.retryTCPQueueSize,
      'Client2eventMesh TCP TPS': configuration.client2eventMeshTCPTPS,
      'EventMesh2mq TCP TPS': configuration.eventMesh2mqTCPTPS,
      'MQ2eventMesh TCP TPS': configuration.mq2eventMeshTCPTPS,
      'EventMesh2client TCP TPS': configuration.eventMesh2clientTCPTPS,
      'All TCP TPS': configuration.allTCPTPS,
      'All TCP Connections': configuration.allTCPConnections,
      'Sub Topic TCP Num': configuration.subTopicTCPNum,
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
  
    if (Object.keys(configuration).length === 0) {
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
                  <Th>Configuration Field</Th>
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
                  <Th>Configuration Field</Th>
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
                  <Th>Configuration Field</Th>
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
  
  export default Metrics;