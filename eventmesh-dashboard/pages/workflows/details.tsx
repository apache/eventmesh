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

import React, { useRef } from 'react';
import Head from 'next/head';
import type { NextPage } from 'next';

import {
  Stack,
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  Grid,
  GridItem,
  FormControl,
  FormLabel,
  Input,
  Button,
  Textarea,
} from '@chakra-ui/react';

import Editor from '@monaco-editor/react';
import { useRouter } from 'next/router';

const Workflows: NextPage = () => {
  const router = useRouter();
  const editorRef = useRef(null);

  const handleEditorDidMount = (editor: any) => {
    // here is the editor instance
    // you can store it in `useRef` for further usage
    console.log('hhh');
    editorRef.current = editor;
  };

  return (
    <>
      <Head>
        <title>Create Workflow | Apache EventMesh Dashboard</title>
      </Head>
      <Breadcrumb mb={2}>
        <BreadcrumbItem>
          <BreadcrumbLink href="/workflows">Workflows</BreadcrumbLink>
        </BreadcrumbItem>

        <BreadcrumbItem>
          <BreadcrumbLink>Create</BreadcrumbLink>
        </BreadcrumbItem>
      </Breadcrumb>
      <Grid
        w="full"
        h="calc(100vh - 56px)"
        bg="white"
        borderWidth="1px"
        borderRadius="md"
        p="6"
        templateColumns="320px 1fr"
        gap={6}
      >
        <GridItem h="full" display="flex">
          <Stack w="full" direction="column">
            <FormControl isRequired>
              <FormLabel>Workflow name</FormLabel>
              <Input placeholder="Please input" />
            </FormControl>

            <FormControl mt={5}>
              <FormLabel>Description</FormLabel>
              <Textarea minH={240} placeholder="Optional" />
            </FormControl>
            <Stack
              direction="row"
              spacing={2}
              h="full"
              pb="2"
              alignItems="flex-end"
            >
              <Button colorScheme="blue">Save</Button>
              <Button variant="outline">Cancel</Button>
            </Stack>
          </Stack>
        </GridItem>
        <GridItem h="full" bgColor="blackAlpha.50">
          <Editor
            height="100%"
            defaultLanguage="yaml"
            defaultValue="# Compose your workflow here"
            onMount={handleEditorDidMount}
            theme="vs-dark"
          />
        </GridItem>
      </Grid>
    </>
  );
};
export default Workflows;
