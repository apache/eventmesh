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
  Select,
  VStack,
  Textarea,
} from '@chakra-ui/react';
import axios from 'axios';
import { useContext, useEffect, useState } from 'react';
import { CloudEvent } from 'cloudevents';
import { AppContext } from '../../context/context';

interface Topic {
  name: string,
  messageCount: number,
}

interface EventProps {
  event: CloudEvent<string>,
}

interface CreateEventRequest {
  event: CloudEvent<string>,
}

const CreateEventModal = () => {
  const { state } = useContext(AppContext);

  const { isOpen, onOpen, onClose } = useDisclosure();

  const [id, setId] = useState('');
  const handleIdChange = (event: React.FormEvent<HTMLInputElement>) => {
    setId(event.currentTarget.value);
  };

  const [source, setSource] = useState('');
  const handleSourceChange = (event: React.FormEvent<HTMLInputElement>) => {
    setSource(event.currentTarget.value);
  };

  const [subject, setSubject] = useState('');
  const handleSubjectChange = (event: React.FormEvent<HTMLInputElement>) => {
    setSubject(event.currentTarget.value);
  };

  const [type, setType] = useState('');
  const handleTypeChange = (event: React.FormEvent<HTMLInputElement>) => {
    setType(event.currentTarget.value);
  };

  const [data, setData] = useState('');
  const handleDataChange = (event: React.FormEvent<HTMLInputElement>) => {
    setData(event.currentTarget.value);
  };

  const toast = useToast();
  const [loading, setLoading] = useState(false);
  const onCreateClick = async () => {
    try {
      setLoading(true);
      await axios.post<CreateEventRequest>(`${state.endpoint}/event`, new CloudEvent({
        source,
        subject,
        type,
        data,
        specversion: '1.0',
      }));
      onClose();
    } catch (error) {
      if (axios.isAxiosError(error)) {
        toast({
          title: 'Failed to publish the event',
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
        w="25%"
        colorScheme="blue"
        onClick={onOpen}
      >
        Create Event
      </Button>
      <Modal isOpen={isOpen} onClose={onClose}>
        <ModalOverlay />
        <ModalContent>
          <ModalHeader>Create Event</ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <VStack>
              <Input
                placeholder="Event ID"
                value={id}
                onChange={handleIdChange}
              />
              <Input
                placeholder="Event Source"
                value={source}
                onChange={handleSourceChange}
              />
              <Input
                placeholder="Event Subject"
                value={subject}
                onChange={handleSubjectChange}
              />
              <Input
                placeholder="Event Type"
                value={type}
                onChange={handleTypeChange}
              />
              <Input
                placeholder="Event Data"
                value={data}
                onChange={handleDataChange}
              />
            </VStack>
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
              isDisabled={
                id.length === 0 || subject.length === 0 || source.length === 0 || type.length === 0
              }
            >
              Create
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>
    </>
  );
};

const EventRow = ({
  event,
}: EventProps) => {
  const { isOpen, onOpen, onClose } = useDisclosure();
  const eventDataBase64 = event.data_base64 || '';
  const eventData = Buffer.from(eventDataBase64, 'base64').toString('utf-8');

  return (
    <>
      <Modal isOpen={isOpen} onClose={onClose}>
        <ModalOverlay />
        <ModalContent>
          <ModalHeader>Event Data</ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <Box>
              <Textarea isDisabled value={eventData} />
            </Box>
          </ModalBody>

          <ModalFooter>
            <Button
              mr={2}
              onClick={onClose}
            >
              Close
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>

      <Tr>
        <Td>{event.id}</Td>
        <Td>{event.subject}</Td>
        <Td>{new Date(Number(event.reqc2eventmeshtimestamp)).toLocaleString()}</Td>
        <Td>
          <HStack>
            <Button
              colorScheme="blue"
              onClick={onOpen}
            >
              View Data
            </Button>
          </HStack>

        </Td>
      </Tr>
    </>
  );
};

const EventTable = () => {
  const { state } = useContext(AppContext);

  const [searchInput, setSearchInput] = useState<string>('');
  const handleSearchInputChange = (event: React.FormEvent<HTMLInputElement>) => {
    setSearchInput(event.currentTarget.value);
  };

  const [eventList, setEventList] = useState<CloudEvent<string>[]>([]);
  const [topicList, setTopicList] = useState<Topic[]>([]);
  const [topic, setTopic] = useState<Topic>({
    name: '',
    messageCount: 0,
  });
  const handleTopicChange = (event: React.FormEvent<HTMLSelectElement>) => {
    setTopic({
      name: event.currentTarget.value,
      messageCount: 0,
    });
  };

  const toast = useToast();

  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await axios.get<Topic[]>(`${state.endpoint}/topic`);
        setTopicList(data);
        if (data.length !== 0) {
          setTopic(data[0]);
        }
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast({
            title: 'Failed to fetch the list of events',
            description: 'unable to connect to the EventMesh daemon',
            status: 'error',
            duration: 3000,
            isClosable: true,
          });
          setEventList([]);
        }
      }
    };

    fetch();
  }, []);

  useEffect(() => {
    const fetch = async () => {
      try {
        if (topic.name !== '') {
          const eventResponse = await axios.get<string[]>(`${state.endpoint}/event`, {
            params: {
              topicName: topic.name,
              offset: 0,
              length: 15,
            },
          });
          setEventList(eventResponse.data.map((rawEvent) => JSON.parse(rawEvent)));
        }
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast({
            title: 'Failed to fetch the list of events',
            description: 'unable to connect to the EventMesh daemon',
            status: 'error',
            duration: 3000,
            isClosable: true,
          });
          setEventList([]);
        }
      }
    };

    fetch();
  }, [topic]);

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
        <Select
          w="100%"
          onChange={handleTopicChange}
        >
          {topicList.map(({ name }) => (
            <option value={name} key={name} selected={topic.name === name}>{name}</option>
          ))}
        </Select>
        <CreateEventModal />
      </HStack>

      <TableContainer>
        <Table variant="simple">
          <Thead>
            <Tr>
              <Th>Event Id</Th>
              <Th>Event Subject</Th>
              <Th>Event Time</Th>
              <Th>Action</Th>
            </Tr>
          </Thead>
          <Tbody>
            {eventList.filter(() => true).map((event) => (
              <EventRow
                key={event.id}
                event={event}
              />
            ))}
          </Tbody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default EventTable;
