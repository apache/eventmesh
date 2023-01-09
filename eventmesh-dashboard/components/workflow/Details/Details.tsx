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
  Tag,
  useToast,
  AlertDialog,
  AlertDialogOverlay,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogBody,
  AlertDialogFooter,
} from '@chakra-ui/react';

import moment from 'moment';

import Editor, { Monaco } from '@monaco-editor/react';
import axios from 'axios';
import { WorkflowType, WorkflowStatusEnum } from '../types';
import { WorkflowStatusMap } from '../constant';
import Intances from './Instances';

const ApiRoot = process.env.NEXT_PUBLIC_API_ROOT;

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
            id: data?.id,
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
            <AlertDialogHeader fontSize="lg" fontWeight="bold">
              Saved Workflow
            </AlertDialogHeader>

            <AlertDialogBody>Are you sure?</AlertDialogBody>

            <AlertDialogFooter>
              <Button ref={cancelRef} onClick={() => setIsShowComfirm(false)}>
                No
              </Button>
              <Button colorScheme="blue" onClick={onSubmit} ml={3}>
                Yes
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
          <DrawerHeader>Workflow Details</DrawerHeader>
          <DrawerBody>
            <Stack mb="30px" direction="row" spacing={10}>
              <Flex flexDirection="column" h="full">
                <Box mb={5}>
                  <FormLabel opacity={0.3}>Workflow ID</FormLabel>
                  <Text>{data?.workflow_id}</Text>
                </Box>
                <Box mb={5}>
                  <FormLabel opacity={0.3}>Workflow Name</FormLabel>
                  <Text>{data?.workflow_name}</Text>
                </Box>
                <Box>
                  <FormLabel opacity={0.3}>Status</FormLabel>

                  <Text>
                    <Tag
                      size="lg"
                      variant="outline"
                      colorScheme={
                        data?.status === WorkflowStatusEnum.Normal
                          ? 'blue'
                          : 'red'
                      }
                    >
                      {WorkflowStatusMap.get(data?.status ?? 0) ?? '-'}
                    </Tag>
                  </Text>
                </Box>
              </Flex>

              <Flex flexDirection="column" h="full">
                <Box mb={5}>
                  <FormLabel opacity={0.3}>Version</FormLabel>
                  <Text>{data?.version}</Text>
                </Box>

                <Box mb={5}>
                  <FormLabel opacity={0.3}>Created At</FormLabel>
                  <Text>
                    {moment(data?.update_time).format('YYYY-mm-DD HH:mm:ss')}
                  </Text>
                </Box>
                <Box>
                  <FormLabel opacity={0.3}>Updated At</FormLabel>
                  <Text>
                    {moment(data?.update_time).format('YYYY-mm-DD HH:mm:ss')}
                  </Text>
                </Box>
              </Flex>
              <Flex flexDirection="column" h="full">
                <Box mb={5}>
                  <FormLabel opacity={0.3}>Total Instance</FormLabel>
                  <Text>{data?.total_instances}</Text>
                </Box>
                <Box mb={5}>
                  <FormLabel opacity={0.3}>Running</FormLabel>
                  <Text>{data?.total_running_instances}</Text>
                </Box>
                <Box>
                  <FormLabel opacity={0.3}>Failed</FormLabel>
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
