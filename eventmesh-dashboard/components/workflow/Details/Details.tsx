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

import React, {
  FC, useEffect, useRef, useState,
} from 'react';

import {
  Drawer,
  DrawerContent,
  DrawerOverlay,
  DrawerHeader,
  DrawerBody,
  DrawerFooter,
  DrawerCloseButton,
  Box,
  FormLabel,
  Flex,
  Text,
  Button,
  Stack,
  Tabs,
  TabList,
  TabPanels,
  Tab,
  TabPanel,
  useToast,
  AlertDialog,
  AlertDialogOverlay,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogBody,
  AlertDialogFooter,
  Alert,
  Badge,
} from '@chakra-ui/react';

import { WarningIcon, InfoIcon } from '@chakra-ui/icons';
import moment from 'moment';

import Editor, { Monaco } from '@monaco-editor/react';
import axios from 'axios';
import { WorkflowType, WorkflowStatusEnum } from '../types';
// import { WorkflowStatusMap } from '../constant';
import Intances from './Instances';

const ApiRoot = process.env.NEXT_PUBLIC_WORKFLOW_API_ROOT;

const Details: FC<{
  visible: boolean;
  data?: WorkflowType | null;
  onClose: () => void;
  onSaved: () => void;
}> = ({
  visible = false, data, onClose = () => {}, onSaved = () => {},
}) => {
  const toast = useToast();
  const editorRef = useRef<Monaco | null>(null);
  const [isShowConfirm, setIsShowComfirm] = useState(false);
  const cancelRef = useRef(null);
  const handleEditorDidMount = (editor: Monaco) => {
    editorRef.current = editor;
    editor.setValue(data?.definition ?? '');
  };

  const onSubmit = () => {
    const value = editorRef.current.getValue();

    axios
      .post(
        `${ApiRoot}/workflow`,
        {
          workflow: {
            workflow_id: data?.workflow_id,
            definition: value,
          },
        },
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      )
      .then(() => {
        toast({
          title: 'Workflow saved',
          status: 'success',
          position: 'top-right',
        });
        setIsShowComfirm(false);
        onSaved();
        onClose();
      })
      .catch((error) => {
        setIsShowComfirm(false);
        toast({
          title: 'Failed to save',
          description: error.response.data,
          status: 'error',
          position: 'top-right',
        });
      });
  };

  const onConfirm = () => {
    const value = editorRef.current.getValue();
    if (data?.definition === value) {
      onClose();
      return;
    }
    setIsShowComfirm(true);
  };

  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.setValue(data?.definition ?? '');
    }
  }, [data, editorRef]);

  return (
    <>
      <AlertDialog
        leastDestructiveRef={cancelRef}
        isOpen={isShowConfirm}
        onClose={onClose}
      >
        <AlertDialogOverlay>
          <AlertDialogContent>
            <AlertDialogHeader
              fontSize="lg"
              fontWeight="bold"
              alignItems="center"
            >
              <WarningIcon boxSize="6" mr={2} color="orange" />
              <Text fontSize="xl" as="b">
                Confirm
              </Text>
            </AlertDialogHeader>
            <AlertDialogBody>
              Are you sure to save the changes to
              {' '}
              <Text as="b">{data?.workflow_id}</Text>
              ?
            </AlertDialogBody>
            <AlertDialogFooter>
              <Button ref={cancelRef} onClick={() => setIsShowComfirm(false)}>
                No
              </Button>
              <Button colorScheme="blue" onClick={onSubmit} ml={3}>
                Save
              </Button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialogOverlay>
      </AlertDialog>

      <Drawer
        isOpen={visible}
        size="xl"
        placement="right"
        onClose={() => onClose()}
      >
        <DrawerOverlay />
        <DrawerContent>
          <DrawerCloseButton />
          <DrawerHeader>
            {data?.workflow_id}
            {/* <Badge
              ml={2}
              colorScheme={
                data?.status === WorkflowStatusEnum.Normal ? 'blue' : 'red'
              }
            >
              {WorkflowStatusMap.get(data?.status ?? 0) ?? '-'}
            </Badge> */}
            <Badge ml={2}>
              Version
              {' '}
              {data?.version}
            </Badge>
          </DrawerHeader>
          <DrawerBody>
            <Stack mb="15px" direction="row" spacing={10}>
              <Flex flexDirection="column" h="full">
                <Box mb={2}>
                  <FormLabel opacity={0.5}>Workflow Name</FormLabel>
                  <Text>{data?.workflow_name}</Text>
                </Box>
                <Box mb={2}>
                  <FormLabel opacity={0.5}>Created At</FormLabel>
                  <Text>
                    {moment(data?.update_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Text>
                </Box>
                <Box>
                  <FormLabel opacity={0.5}>Updated At</FormLabel>
                  <Text>
                    {moment(data?.update_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Text>
                </Box>
              </Flex>

              <Flex flexDirection="column" h="full">
                <Box mb={2}>
                  <FormLabel opacity={0.5}>Total Instance</FormLabel>
                  <Text>{data?.total_instances}</Text>
                </Box>
                <Box mb={2}>
                  <FormLabel opacity={0.5}>Running</FormLabel>
                  <Text>{data?.total_running_instances}</Text>
                </Box>
                <Box>
                  <FormLabel opacity={0.5}>Failed</FormLabel>
                  <Text>{data?.total_failed_instances}</Text>
                </Box>
              </Flex>
            </Stack>

            <Tabs>
              <TabList>
                <Tab>Definition</Tab>
                <Tab>Instances</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <Alert status="info" mb={2}>
                    <InfoIcon color="#3182ce" mr={2} />
                    <Text fontSize="sm" color="#3182ce">
                      You can edit the workflow directly and click &quot;OK&quot; to save
                      it
                    </Text>
                  </Alert>
                  <Editor
                    height="1000px"
                    defaultLanguage="yaml"
                    defaultValue="# Your code goes here"
                    onMount={handleEditorDidMount}
                    theme="vs-dark"
                  />
                </TabPanel>
                <TabPanel>
                  <Intances workflowId={data?.workflow_id ?? ''} />
                </TabPanel>
              </TabPanels>
            </Tabs>
          </DrawerBody>
          <DrawerFooter justifyContent="flex-start">
            <Button colorScheme="blue" mr={3} onClick={onConfirm}>
              OK
            </Button>
            <Button
              colorScheme="blue"
              mr={3}
              variant="ghost"
              onClick={onClose}
            >
              Cancel
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </>
  );
};

Details.defaultProps = {
  data: null,
};
export default Details;
