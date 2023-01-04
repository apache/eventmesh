import React, { useState } from 'react';
import Head from 'next/head';
import type { NextPage } from 'next';
// import { useRouter } from 'next/router';

import {
  Divider,
  Button,
  Flex,
  Table,
  Thead,
  Tbody,
  Tfoot,
  Tr,
  Th,
  Td,
  TableCaption,
  TableContainer,
  Box,
} from '@chakra-ui/react';
import SchemaView from './SchemaView';
import { SchemaTypes } from './types';

const schemaData: SchemaTypes[] = [
  {
    schemaId: 'schema 1',
    lastVersion: '3',
    description: 'new schema 1',
  },
  {
    schemaId: 'schema 2',
    lastVersion: '3',
    description: 'new schema 2',
  },
];

const EventCatalogs: NextPage = () => {
  // const router = useRouter();
  const [isShowCreate, setIsShowCreate] = useState(false);
  const [detailMode, setDetailMode] = useState<'create' | 'edit'>('create');
  const [curSchema, setCurSchema] = useState<SchemaTypes>();

  return (
    <>
      <Head>
        <title>Event Catalogs | Apache EventMesh Dashboard</title>
      </Head>
      <Box
        w="full"
        h="full"
        bg="white"
        flexDirection="column"
        borderWidth="1px"
        borderRadius="md"
        overflow="hidden"
        p="6"
      >
        <Flex w="full" justifyContent="space-between" mt="2" mb="2">
          <Button
            size="md"
            backgroundColor="#2a62ad"
            color="white"
            _hover={{ bg: '#dce5fe', color: '#2a62ad' }}
            onClick={() => setIsShowCreate(true)}
          >
            Create Shecma
          </Button>
        </Flex>
        <Divider mt="15" mb="15" orientation="horizontal" />
        <TableContainer>
          <Table variant="simple" size="lg">
            <Thead>
              <Tr>
                <Th>Shema ID</Th>
                <Th>Lastest Version</Th>
                <Th>Description</Th>
                <Th align="center">Action</Th>
              </Tr>
            </Thead>
            <Tbody>
              {schemaData.map((schema) => (
                <Tr key={schema.schemaId}>
                  <Td>{schema.schemaId}</Td>
                  <Td>{schema.lastVersion}</Td>
                  <Td>{schema.description}</Td>
                  <Th>
                    <Button
                      colorScheme="blue"
                      variant="ghost"
                      onClick={() => {
                        setIsShowCreate(true);
                        setDetailMode('edit');
                        setCurSchema(schema);
                      }}
                    >
                      Details
                    </Button>
                  </Th>
                </Tr>
              ))}
            </Tbody>
            <Tfoot>
              <Tr>
                {/* <Th>Shema ID</Th>
                <Th>Lastest Version</Th>
                <Th>Description</Th>
                <Th>Action</Th> */}
              </Tr>
            </Tfoot>
          </Table>
        </TableContainer>
      </Box>
      <SchemaView
        mode={detailMode}
        data={curSchema}
        visible={isShowCreate}
        onClose={() => setIsShowCreate(false)}
      />
    </>
  );
};

export default EventCatalogs;
