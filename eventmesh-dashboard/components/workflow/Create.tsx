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

import React, { FC, useRef, useState } from 'react';

import {
  Drawer,
  DrawerContent,
  DrawerOverlay,
  DrawerHeader,
  DrawerBody,
  DrawerFooter,
  DrawerCloseButton,
  Button,
  useToast,
  Spinner,
} from '@chakra-ui/react';
import axios from 'axios';

import Editor, { Monaco } from '@monaco-editor/react';

const ApiRoot = process.env.NEXT_PUBLIC_WORKFLOW_API_ROOT;

const Create: FC<{
  visible: boolean;
  onClose: () => void;
  onSucceed: () => void;
}> = ({ visible = false, onClose = () => {}, onSucceed = () => {} }) => {
  const toast = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const editorRef = useRef<Monaco | null>(null);
  const defaultEditorValue = '# Your code goes here';

  const onSubmit = () => {
    setIsSubmitting(true);

    try {
      const value = editorRef.current.getValue();
      if (value === defaultEditorValue) {
        toast({
          title: 'Invalid definition',
          description: 'Please input your workflow definition properly',
          status: 'warning',
          position: 'top-right',
        });
        setIsSubmitting(false);
        return;
      }
      axios
        .post(
          `${ApiRoot}/workflow`,
          { workflow: { definition: value } },
          {
            headers: {
              'Content-Type': 'application/json',
            },
          },
        )
        .then(() => {
          toast({
            title: 'Succeeded',
            status: 'success',
            position: 'top-right',
          });
          onSucceed();
          setIsSubmitting(false);
        })
        .catch((error) => {
          toast({
            title: 'Failed',
            description: error.response.data,
            status: 'error',
            position: 'top-right',
          });
          setIsSubmitting(false);
        });
    } catch (error) {
      setIsSubmitting(false);
    }
  };

  const handleEditorDidMount = (editor: Monaco) => {
    editorRef.current = editor;
  };

  return (
    <Drawer
      isOpen={visible}
      size="xl"
      placement="right"
      closeOnEsc={false}
      onClose={() => onClose()}
    >
      <DrawerOverlay />
      <DrawerContent>
        <DrawerCloseButton />
        <DrawerHeader>Create New Workflow</DrawerHeader>
        <DrawerBody>
          <Editor
            height="1000px"
            defaultLanguage="yaml"
            defaultValue={defaultEditorValue}
            onMount={handleEditorDidMount}
            theme="vs-dark"
          />
        </DrawerBody>
        <DrawerFooter justifyContent="flex-start">
          <Button colorScheme="blue" mr={3} onClick={onSubmit}>
            {isSubmitting ? <Spinner colorScheme="white" size="sm" /> : 'Submit'}
          </Button>
          <Button variant="ghost" colorScheme="blue" onClick={onClose}>
            Cancel
          </Button>
        </DrawerFooter>
      </DrawerContent>
    </Drawer>
  );
};

export default React.memo(Create);
