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

import React, { FC, useRef } from 'react';

import {
  Drawer,
  DrawerContent,
  DrawerCloseButton,
  DrawerOverlay,
  DrawerHeader,
  DrawerBody,
  FormLabel,
  Box,
  Flex,
  Text,
  Stack,
  Badge,
} from '@chakra-ui/react';
import moment from 'moment';
import Editor, { Monaco } from '@monaco-editor/react';
import { EventCatalogType } from './types';
// import { WorkflowStatusMap } from './constant';

const Details: FC<{
  visible: boolean;
  data?: EventCatalogType | undefined;
  onClose: () => void;
}> = ({ visible = false, data, onClose = () => {} }) => {
  const editorRef = useRef(null);
  const handleEditorDidMount = (editor: Monaco) => {
    editorRef.current = editor;
    editor.setValue(data?.definition ?? '');
  };

  return (
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
          {data?.title}
          <Badge ml={2}>
            Version
            {' '}
            {data?.version}
          </Badge>
        </DrawerHeader>
        <DrawerBody>
          <Flex flexDirection="column" h="full">
            <Stack direction="row">
              <Flex width="240px" flexDirection="column">
                <Box mb="1">
                  <FormLabel opacity={0.5}>Catalog ID</FormLabel>
                  <Text>{data?.id}</Text>
                </Box>
                <Box mt="1" mb="3">
                  <FormLabel opacity={0.5}>Title</FormLabel>
                  <Text>{data?.title}</Text>
                </Box>
                <Box mt="1" mb="3">
                  <FormLabel opacity={0.5}>File Name</FormLabel>
                  <Text>{data?.file_name}</Text>
                </Box>
              </Flex>
              <Flex flexDirection="column">
                <Box mb="1">
                  <FormLabel opacity={0.5}>Created At</FormLabel>
                  <Text>
                    {moment(data?.create_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Text>
                </Box>
                <Box mt="1" mb="3">
                  <FormLabel opacity={0.5}>Updated At</FormLabel>
                  <Text>
                    {moment(data?.update_time).format('YYYY-MM-DD HH:mm:ss')}
                  </Text>
                </Box>
              </Flex>
            </Stack>
            <Box flex={1}>
              <Editor
                height="100%"
                defaultLanguage="yaml"
                defaultValue="# Your code goes here"
                onMount={handleEditorDidMount}
                theme="vs-dark"
                options={{ readOnly: true }}
              />
            </Box>
          </Flex>
        </DrawerBody>
      </DrawerContent>
    </Drawer>
  );
};
Details.defaultProps = {
  data: undefined,
};
export default Details;
