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
  Button,
  Drawer,
  DrawerContent,
  DrawerCloseButton,
  DrawerOverlay,
  DrawerHeader,
  DrawerBody,
  Input,
  DrawerFooter,
  FormControl,
  FormLabel,
  FormHelperText,
  Box,
  Flex,
  Text,
} from '@chakra-ui/react';

import Editor from '@monaco-editor/react';
import { SchemaTypes } from './types';

const SchemaView: FC<{
  visible: boolean;
  mode: 'create' | 'edit';
  data?: SchemaTypes;
  onClose: () => void;
}> = ({
  mode, visible = false, data, onClose = () => {},
}) => {
  const editorRef = useRef(null);

  const handleEditorDidMount = (editor: any) => {
    // here is the editor instance
    // you can store it in `useRef` for further usage
    console.log('hhh');
    editorRef.current = editor;
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

        {mode === 'create' ? (
          <>
            <DrawerHeader>Create New Schema</DrawerHeader>
            <DrawerBody>
              <Flex flexDirection="column" h="full">
                <FormControl isRequired mb="3">
                  <FormLabel>Schema ID</FormLabel>
                  <Input placeholder="Please input" />
                  <FormHelperText>Normal letters only</FormHelperText>
                </FormControl>
                <FormControl mt="3" mb="3">
                  <FormLabel>Descriptions</FormLabel>
                  <Input placeholder="Optional" />
                  <FormHelperText>Max length is 1024</FormHelperText>
                </FormControl>
                <FormControl mt="3" mb="3">
                  <FormLabel>Shema Format</FormLabel>
                  <Input type="email" />
                  {/* <FormHelperText>We'll never share your email.</FormHelperText> */}
                </FormControl>
                <FormControl flexGrow={1}>
                  <Box height="100%">
                    <Editor
                      height="100%"
                      defaultLanguage="yaml"
                      defaultValue="# Your code goes here"
                      onMount={handleEditorDidMount}
                      theme="vs-dark"
                    />
                  </Box>
                </FormControl>
              </Flex>
            </DrawerBody>

            <DrawerFooter justifyContent="flex-start">
              <Button colorScheme="blue" mr={3}>
                Save
              </Button>
              <Button variant="outline" onClick={() => {}}>
                Cancel
              </Button>
            </DrawerFooter>
          </>
        ) : (
          <>
            <DrawerHeader>Schema Details</DrawerHeader>
            <DrawerBody>
              <Flex flexDirection="column" h="full">
                <FormControl mb="3">
                  <FormLabel opacity={0.3}>Schema ID</FormLabel>
                  <Text>{data?.schemaId}</Text>
                </FormControl>
                <FormControl mt="3" mb="3">
                  <FormLabel opacity={0.3}>Descriptions</FormLabel>
                  <Text>{data?.description}</Text>
                </FormControl>
                <FormControl mt="3" mb="3">
                  <FormLabel opacity={0.3}>Shema Format</FormLabel>
                  <Text>{data?.lastVersion}</Text>
                </FormControl>
                <FormControl flexGrow={1}>
                  <Box height="100%">
                    <Editor
                      height="100%"
                      defaultLanguage="yaml"
                      defaultValue="# Your code goes here"
                      onMount={handleEditorDidMount}
                      theme="vs-dark"
                    />
                  </Box>
                </FormControl>
              </Flex>
            </DrawerBody>
          </>
        )}
      </DrawerContent>
    </Drawer>
  );
};
SchemaView.defaultProps = {
  data: undefined,
};
export default SchemaView;
