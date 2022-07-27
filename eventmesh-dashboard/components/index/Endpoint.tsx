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
  Input,
  VStack,
  Button,
  Text,
  useToast,
} from '@chakra-ui/react';
import axios from 'axios';
import React, { useEffect, useState } from 'react';

const Endpoint = () => {
  const toast = useToast();
  const [endpointInput, setEndpointInput] = useState('http://localhost:10106');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const endpoint = localStorage.getItem('endpoint');
    if (endpoint === null) {
      return;
    }
    setEndpointInput(endpoint);
    axios.defaults.baseURL = endpoint;
  }, []);

  const handleEndpointInputChange = (event: React.FormEvent<HTMLInputElement>) => {
    setEndpointInput(event.currentTarget.value);
  };

  const handleSaveButtonClick = async () => {
    try {
      setLoading(true);
      await axios.get(`${endpointInput}/client`);
      axios.defaults.baseURL = endpointInput;
      localStorage.setItem('endpoint', endpointInput);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        toast({
          title: `Failed to connect to ${endpointInput}`,
          description: error.message,
          status: 'error',
          duration: 3000,
          isClosable: true,
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <VStack
      maxW="full"
      bg="white"
      borderWidth="1px"
      borderRadius="md"
      overflow="hidden"
      p="4"
    >
      <Text
        w="full"
      >
        EventMesh Admin Endpoint
      </Text>
      <HStack
        w="full"
      >
        <Input
          placeholder="Apache EventMesh Backend Endpoint"
          value={endpointInput}
          onChange={handleEndpointInputChange}
        />
        <Button
          colorScheme="blue"
          isLoading={loading}
          onClick={handleSaveButtonClick}
        >
          Save
        </Button>
      </HStack>
    </VStack>
  );
};

export default Endpoint;
