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

/* eslint-disable react/jsx-props-no-spreading */
import React, { ReactNode } from 'react';
import {
  IconButton,
  Box,
  CloseButton,
  Flex,
  Icon,
  useColorModeValue,
  Link,
  Drawer,
  DrawerContent,
  Text,
  useDisclosure,
  BoxProps,
  FlexProps,
} from '@chakra-ui/react';
import {
  FiList,
  FiGrid,
  FiServer,
  FiDatabase,
  FiMenu,
} from 'react-icons/fi';
import { IconType } from 'react-icons';

interface LinkItemProps {
  name: string;
  icon: IconType;
  href: string;
}

const LinkItems: Array<LinkItemProps> = [
  { name: 'Overview', icon: FiList, href: '/' },
  { name: 'Metrics', icon: FiMenu, href: '/metrics' },
  { name: 'Registry', icon: FiDatabase, href: '/registry' },
  { name: 'TCP Client', icon: FiServer, href: '/tcp' },
  { name: 'HTTP Client', icon: FiServer, href: '/http' },
  { name: 'gRPC Client', icon: FiServer, href: '/grpc' },
  { name: 'Topic', icon: FiGrid, href: '/topic' },
  { name: 'Event', icon: FiDatabase, href: '/event' },
];

interface NavItemProps extends FlexProps {
  icon: IconType;
  href: string;
  children: string | number;
}

const NavItem = ({
  icon, href, children, ...rest
}: NavItemProps) => (
  <Link
    href={href}
    style={{ textDecoration: 'none' }}
    _focus={{ boxShadow: 'none' }}
  >
    <Flex
      align="center"
      p="4"
      mx="4"
      borderRadius="lg"
      role="group"
      cursor="pointer"
      _hover={{
        bg: 'blue.500',
        color: 'white',
      }}
      {...rest}
    >
      {icon && (
      <Icon
        mr="4"
        fontSize="16"
        _groupHover={{
          color: 'white',
        }}
        as={icon}
      />
      )}
      {children}
    </Flex>
  </Link>
);

interface SidebarProps extends BoxProps {
  onClose: () => void;
}

const SidebarContent = ({ onClose, ...rest }: SidebarProps) => (
  <Box
    bg={useColorModeValue('white', 'gray.900')}
    borderRight="1px"
    borderRightColor={useColorModeValue('gray.200', 'gray.700')}
    w={{ base: 'full', md: 60 }}
    pos="fixed"
    h="full"
    {...rest}
  >
    <Flex h="20" alignItems="center" mx="8" justifyContent="space-between">
      <Text fontSize="2xl" fontWeight="bold">
        EventMesh
      </Text>
      <CloseButton display={{ base: 'flex', md: 'none' }} onClick={onClose} />
    </Flex>
    {LinkItems.map((link) => (
      <NavItem key={link.name} href={link.href} icon={link.icon}>
        {link.name}
      </NavItem>
    ))}
  </Box>
);

interface MobileProps extends FlexProps {
  onOpen: () => void;
}

const MobileNav = ({ onOpen }: MobileProps) => (
  <Flex
    ml={{ base: 0, md: 60 }}
    px={{ base: 4, md: 24 }}
    height="20"
    alignItems="center"
    bg={useColorModeValue('white', 'gray.900')}
    borderBottomWidth="1px"
    borderBottomColor={useColorModeValue('gray.200', 'gray.700')}
    justifyContent="flex-start"
    display={{ base: 'flex', md: 'none' }}
  >
    <IconButton
      variant="outline"
      onClick={onOpen}
      aria-label="open menu"
      icon={<FiMenu />}
    />

    <Text fontSize="2xl" ml="8" fontWeight="bold">
      EventMesh
    </Text>
  </Flex>
);

const Sidebar = ({ children }: { children: ReactNode }) => {
  const { isOpen, onOpen, onClose } = useDisclosure();
  return (
    <Box minH="100vh" bg={useColorModeValue('gray.100', 'gray.900')}>
      <SidebarContent
        onClose={() => onClose}
        display={{ base: 'none', md: 'block' }}
      />
      <Drawer
        autoFocus={false}
        isOpen={isOpen}
        placement="left"
        onClose={onClose}
        returnFocusOnClose={false}
        onOverlayClick={onClose}
        size="full"
      >
        <DrawerContent>
          <SidebarContent onClose={onClose} />
        </DrawerContent>
      </Drawer>

      <MobileNav onOpen={onOpen} />
      <Box ml={{ base: 0, md: 60 }} p="4">
        {children}
      </Box>
    </Box>
  );
};

export default Sidebar;
