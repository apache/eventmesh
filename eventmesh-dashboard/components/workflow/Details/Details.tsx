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
<<<<<<< HEAD
<<<<<<< HEAD
=======
  Tag,
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
>>>>>>> 6c6dc141 ([Dashboard] Hide status in details)
  useToast,
  AlertDialog,
  AlertDialogOverlay,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogBody,
  AlertDialogFooter,
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
  Alert,
  Badge,
} from '@chakra-ui/react';

import { WarningIcon, InfoIcon } from '@chakra-ui/icons';
<<<<<<< HEAD
=======
} from '@chakra-ui/react';

>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
import moment from 'moment';

import Editor, { Monaco } from '@monaco-editor/react';
import axios from 'axios';
import { WorkflowType, WorkflowStatusEnum } from '../types';
<<<<<<< HEAD
<<<<<<< HEAD
// import { WorkflowStatusMap } from '../constant';
import Intances from './Instances';

const ApiRoot = process.env.NEXT_PUBLIC_WORKFLOW_API_ROOT;
=======
import { WorkflowStatusMap } from '../constant';
import Intances from './Instances';

const ApiRoot = process.env.NEXT_PUBLIC_API_ROOT;
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
// import { WorkflowStatusMap } from '../constant';
import Intances from './Instances';

const ApiRoot = process.env.NEXT_PUBLIC_WORKFLOW_API_ROOT;
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)

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
<<<<<<< HEAD
=======
            id: data?.id,
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
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
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
            <AlertDialogHeader
              fontSize="lg"
              fontWeight="bold"
              alignItems="center"
            >
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
              <WarningIcon boxSize="6" mr={2} color="orange" />
              <Text fontSize="xl" as="b">
                Confirm
              </Text>
<<<<<<< HEAD
            </AlertDialogHeader>
            <AlertDialogBody>
              Are you sure to save the changes to
              {' '}
              <Text as="b">{data?.workflow_id}</Text>
              ?
            </AlertDialogBody>
=======
            <AlertDialogHeader fontSize="lg" fontWeight="bold">
              Saved Workflow
            </AlertDialogHeader>

            <AlertDialogBody>Are you sure?</AlertDialogBody>

>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
              <WarningIcon boxSize="8" mr={2} color="orange" />
              Confirm
=======
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
            </AlertDialogHeader>
            <AlertDialogBody>
              Are you sure to save the changes to
              {' '}
              <Text as="b">{data?.workflow_id}</Text>
              ?
            </AlertDialogBody>
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
            <AlertDialogFooter>
              <Button ref={cancelRef} onClick={() => setIsShowComfirm(false)}>
                No
              </Button>
              <Button colorScheme="blue" onClick={onSubmit} ml={3}>
<<<<<<< HEAD
<<<<<<< HEAD
                Save
=======
                Yes
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
                Save
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
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
<<<<<<< HEAD
<<<<<<< HEAD
          <DrawerHeader>
            {data?.workflow_id}
            {/* <Badge
=======
          <DrawerHeader>
            {data?.workflow_id}
<<<<<<< HEAD
            <Badge
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
=======
            {/* <Badge
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
              ml={2}
              colorScheme={
                data?.status === WorkflowStatusEnum.Normal ? 'blue' : 'red'
              }
            >
              {WorkflowStatusMap.get(data?.status ?? 0) ?? '-'}
<<<<<<< HEAD
<<<<<<< HEAD
            </Badge> */}
=======
            </Badge>
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
=======
            </Badge> */}
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
            <Badge ml={2}>
              Version
              {' '}
              {data?.version}
            </Badge>
          </DrawerHeader>
<<<<<<< HEAD
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
=======
          <DrawerHeader>Workflow Details</DrawerHeader>
=======
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
          <DrawerBody>
            <Stack mb="15px" direction="row" spacing={10}>
              <Flex flexDirection="column" h="full">
                <Box mb={2}>
                  <FormLabel opacity={0.5}>Workflow Name</FormLabel>
                  <Text>{data?.workflow_name}</Text>
                </Box>
<<<<<<< HEAD
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
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
                  </Text>
                </Box>
              </Flex>

              <Flex flexDirection="column" h="full">
<<<<<<< HEAD
                <Box mb={2}>
<<<<<<< HEAD
                  <FormLabel opacity={0.5}>Total Instance</FormLabel>
                  <Text>{data?.total_instances}</Text>
                </Box>
                <Box mb={2}>
                  <FormLabel opacity={0.5}>Running</FormLabel>
                  <Text>{data?.total_running_instances}</Text>
                </Box>
                <Box>
                  <FormLabel opacity={0.5}>Failed</FormLabel>
=======
                <Box mb={5}>
                  <FormLabel opacity={0.3}>Version</FormLabel>
                  <Text>{data?.version}</Text>
                </Box>

                <Box mb={5}>
=======
                <Box mb={2}>
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
                  <FormLabel opacity={0.3}>Created At</FormLabel>
=======
                  <FormLabel opacity={0.5}>Created At</FormLabel>
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
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
<<<<<<< HEAD
                  <FormLabel opacity={0.3}>Failed</FormLabel>
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
                  <FormLabel opacity={0.5}>Failed</FormLabel>
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
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
<<<<<<< HEAD
<<<<<<< HEAD
                  <Alert status="info" mb={2}>
                    <InfoIcon color="#3182ce" mr={2} />
                    <Text fontSize="sm" color="#3182ce">
                      You can edit the workflow directly and click &quot;OK&quot; to save
                      it
                    </Text>
                  </Alert>
=======
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
                  <Alert status="info" mb={2}>
                    <InfoIcon color="#3182ce" mr={2} />
                    <Text fontSize="sm" color="#3182ce">
                      You can edit the workflow directly and click &quot;OK&quot; to save
                      it
                    </Text>
                  </Alert>
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
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
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
            <Button
              colorScheme="blue"
              mr={3}
              variant="ghost"
<<<<<<< HEAD
<<<<<<< HEAD
              onClick={onClose}
            >
              Cancel
            </Button>
=======
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
=======
              onClick={onConfirm}
=======
              onClick={onClose}
>>>>>>> 91196cde ([Dashboard] Completed EventCatalogs feature; Minor adjustment to Workflow)
            >
              Cancel
            </Button>
>>>>>>> bbd9da7f ([Dashboard] Update pagination in workflow and instance list)
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
