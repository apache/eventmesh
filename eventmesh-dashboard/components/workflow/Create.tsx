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
<<<<<<< HEAD
  Spinner,
=======
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
} from '@chakra-ui/react';
import axios from 'axios';

import Editor, { Monaco } from '@monaco-editor/react';

<<<<<<< HEAD
const ApiRoot = process.env.NEXT_PUBLIC_WORKFLOW_API_ROOT;
=======
const ApiRoot = process.env.NEXT_PUBLIC_API_ROOT;
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)

const Create: FC<{
  visible: boolean;
  onClose: () => void;
  onSucceed: () => void;
}> = ({ visible = false, onClose = () => {}, onSucceed = () => {} }) => {
  const toast = useToast();
<<<<<<< HEAD
  const [isSubmitting, setIsSubmitting] = useState(false);
=======
  const [isLoading, setIsLoading] = useState(false);
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
  const editorRef = useRef<Monaco | null>(null);
  const defaultEditorValue = '# Your code goes here';

  const onSubmit = () => {
<<<<<<< HEAD
    setIsSubmitting(true);
=======
    setIsLoading(true);
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)

    try {
      const value = editorRef.current.getValue();
      if (value === defaultEditorValue) {
        toast({
          title: 'Invalid definition',
          description: 'Please input your workflow definition properly',
          status: 'warning',
          position: 'top-right',
        });
<<<<<<< HEAD
        setIsSubmitting(false);
=======
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
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
<<<<<<< HEAD
          setIsSubmitting(false);
=======
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
        })
        .catch((error) => {
          toast({
            title: 'Failed',
            description: error.response.data,
            status: 'error',
            position: 'top-right',
          });
<<<<<<< HEAD
          setIsSubmitting(false);
        });
    } catch (error) {
      setIsSubmitting(false);
=======
        });
    } catch (error) {
      setIsLoading(false);
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
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
<<<<<<< HEAD
=======
          {/* <Box mb={5}>
            <FormControl isRequired isInvalid={nameError}>
              <FormLabel>Workflow name</FormLabel>
              <Input
                placeholder="Please input"
                value={workflowName}
                onChange={(evt) => setWorkflowName(evt.target.value.trim())}
              />
              {!nameError ? (
                <FormHelperText>Max length 1024</FormHelperText>
              ) : (
                <FormErrorMessage>
                  Workflow is required, max length is 1024.
                </FormErrorMessage>
              )}
            </FormControl>
            <FormControl mt={2}>
              <FormLabel>Description</FormLabel>
              <Textarea
                minH={120}
                placeholder="Optional"
                value={description}
                onChange={(evt) => setDescription(evt.target.value)}
              />
            </FormControl>
          </Box> */}

>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
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
<<<<<<< HEAD
            {isSubmitting ? <Spinner colorScheme="white" size="sm" /> : 'Submit'}
=======
            Submit
>>>>>>> 5185581f ([Dashboard] Complete workflow all functions)
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
