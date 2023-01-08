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
import React, { FC, ReactNode } from 'react';
import {
  Flex, FlexProps, Link, Text,
} from '@chakra-ui/react';

interface MenuItemProps extends FlexProps {
  selected: boolean;
  active: boolean;
  href: string;
  children: string | number;
  setActiveName: (name: string) => void;
}

export const MenuItem = ({
  selected,
  active,
  href,
  children,
  setActiveName,
}: MenuItemProps) => (
  <Link
    position="relative"
    href={href}
    h="8"
    mt={1.5}
    mb={1.5}
    ml={3}
    mr={3}
    boxSizing="border-box"
    style={{ textDecoration: 'none' }}
    _focus={{ boxShadow: 'none' }}
    onMouseOver={() => setActiveName(children.toString())}
    onMouseOut={() => setActiveName('')}
  >
    <Flex
      position="absolute"
      zIndex={2}
      w="full"
      h="full"
      p="6"
      borderRadius="lg"
      align="center"
      role="group"
      cursor="pointer"
      fontSize="md"
      transition="color 0.15s, background-color 0.15s"
      color={selected || active ? '#2a62ad' : 'current'}
      fontWeight={selected ? 'bolder' : 'none'}
      bgColor={selected || active ? '#dce5fe' : 'none'}
      wordBreak="break-word"
      overflowWrap="normal"
    >
      {children}
    </Flex>
    {/* <Box
      position="absolute"
      zIndex={1}
      p={selected || active ? '4' : 0}
      borderRadius="lg"
      role="group"
      cursor="pointer"
      transition="width 0.3s, opacity 0.4s"
      bgGradient="linear(28deg,#2a4cad, #2a6bad, #28c9ff)"
      h="100%"
      w={selected || active ? '100%' : '0'}
      opacity={selected || active ? 1 : 0}
    /> */}
  </Link>
);

export const MenuGroupItem: FC<{ name: string; children: ReactNode }> = (
  props,
) => {
  const { name, children } = props;
  return (
    <Flex
      flexDirection="column"
      w="full"
      justifyContent="center"
      alignContent="center"
    >
      {name && (
        <Text
          mt={5}
          mb="2"
          pl="6"
          fontSize="xs"
          color="#a2b5d8"
          fontWeight="bold"
        >
          {name.toUpperCase()}
        </Text>
      )}
      {children}
    </Flex>
  );
};
