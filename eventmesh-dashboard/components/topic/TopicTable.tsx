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
  Modal,
  ModalBody,
  ModalCloseButton,
  ModalContent,
  ModalFooter,
  ModalHeader,
  ModalOverlay,
  useDisclosure,
} from '@chakra-ui/react';
import axios from 'axios';
import { useContext, useEffect, useState } from 'react';
import { AppContext } from '../../context/context';

interface Topic {
  name: string,
  messageCount: number,
}

interface TopicProps {
  name: string,
  messageCount: number,
}

interface CreateTopicRequest {
  name: string,
}

interface RemoveTopicRequest {
  name: string,
}

const CreateTopicModal = () => {
  const { state } = useContext(AppContext);

  const { isOpen, onOpen, onClose } = useDisclosure();
  const [topicName, setTopicName] = useState('');
  const handleTopicNameChange = (event: React.FormEvent<HTMLInputElement>) => {
    setTopicName(event.currentTarget.value);
  };

  const toast = useToast();
  const [loading, setLoading] = useState(false);
  const onCreateClick = async () => {
    try {
      setLoading(true);
      await axios.post<CreateTopicRequest>(`${state.endpoint}/topic`, {
        name: topicName,
      });
      onClose();
    } catch (error) {
      if (axios.isAxiosError(error)) {
        toast({
          title: 'Failed to create the topic',
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
    <>
      <Button
        colorScheme="blue"
        onClick={onOpen}
      >
        Create Topic
      </Button>
      <Modal isOpen={isOpen} onClose={onClose}>
        <ModalOverlay />
        <ModalContent>
          <ModalHeader>Create Topic</ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <Input
              placeholder="Topic Name"
              value={topicName}
              onChange={handleTopicNameChange}
            />
          </ModalBody>

          <ModalFooter>
            <Button
              mr={2}
              onClick={onClose}
            >
              Close
            </Button>
            <Button
              colorScheme="blue"
              onClick={onCreateClick}
              isLoading={loading}
              isDisabled={topicName.length === 0}
            >
              Create
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>
    </>
  );
};

const TopicRow = ({
  name,
  messageCount,
}: TopicProps) => {
  const { state } = useContext(AppContext);

  const toast = useToast();
  const [loading, setLoading] = useState(false);
  const onRemoveClick = async () => {
    try {
      setLoading(true);
      await axios.delete<RemoveTopicRequest>(`${state.endpoint}/topic`, {
        data: {
          name,
        },
      });
      setLoading(false);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        toast({
          title: 'Failed to remove the topic',
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
      <Td>{name}</Td>
      <Td>{messageCount}</Td>
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

const TopicTable = () => {
  const { state } = useContext(AppContext);

  const [searchInput, setSearchInput] = useState<string>('');
  const handleSearchInputChange = (event: React.FormEvent<HTMLInputElement>) => {
    setSearchInput(event.currentTarget.value);
  };

  const [topicList, setTopicList] = useState<Topic[]>([]);
  const toast = useToast();
  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<Topic[]>(`${state.endpoint}/topic`);
        setTopicList(data);
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast({
            title: 'Failed to fetch the list of topics',
            description: 'unable to connect to the EventMesh daemon',
            status: 'error',
            duration: 3000,
            isClosable: true,
          });
          setTopicList([]);
        }
      }
    };

    fetch();
  }, [topicList]);

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
          w="100%"
          placeholder="Search"
          value={searchInput}
          onChange={handleSearchInputChange}
        />
        <CreateTopicModal />
      </HStack>

      <TableContainer>
        <Table variant="simple">
          <Thead>
            <Tr>
              <Th>Topic Name</Th>
              <Th>Message Count</Th>
              <Th>Action</Th>
            </Tr>
          </Thead>
          <Tbody>
            {topicList.filter(({
              name,
            }) => {
              if (searchInput && !name.includes(searchInput)) {
                return false;
              }
              return true;
            }).map(({
              name,
              messageCount,
            }) => (
              <TopicRow
                name={name}
                messageCount={messageCount}
              />
            ))}
          </Tbody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default TopicTable;
