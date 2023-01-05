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

import React, { FC, ReactNode, useState } from 'react';
import {
  Box,
  BoxProps,
  Button,
  Flex,
  useColorModeValue,
  Image,
} from '@chakra-ui/react';
import { IconType } from 'react-icons';
import { ArrowBackIcon } from '@chakra-ui/icons';

import { useRouter } from 'next/router';

import {
  FiList, FiGrid, FiServer, FiDatabase, FiMenu,
} from 'react-icons/fi';
import LogoImg from '../../static/images/logo.png';
import { MenuItem, MenuGroupItem } from './MenuItem';

const Menus: Array<{
  group?: string;
  name: string;
  icon: IconType;
  href: string;
  subPath?:string[]
}> = [
  { name: 'Overview', icon: FiList, href: '/' },
  { name: 'Metrics', icon: FiMenu, href: '/metrics' },
  { name: 'Registry', icon: FiDatabase, href: '/registry' },
  { name: 'Topic', icon: FiGrid, href: '/topic' },
  { name: 'Event', icon: FiDatabase, href: '/event' },
  {
    group: 'Workflow',
    name: 'Workflows',
    icon: FiServer,
    href: '/workflows',
    subPath: ['/workflows/create'],
  },
  {
    group: 'Workflow',
    name: 'Event Catalogs',
    icon: FiServer,
    href: '/eventCatalogs',
  },
  {
    group: 'Clients',
    name: 'TCP',
    icon: FiServer,
    href: '/tcp',
  },
  {
    group: 'Clients',
    name: 'HTTP',
    icon: FiServer,
    href: '/http',
  },
  {
    group: 'Clients',
    name: 'gRPC',
    icon: FiServer,
    href: '/grpc',
  },
];

interface MenuProps extends BoxProps {
  onClose: () => void;
}
interface IGroupItem {
  name?: string;
  children: ReactNode[];
}

const NavMenu: FC<MenuProps> = ({ display = {}, onClose }) => {
  const router = useRouter();
  const [curMenu, setCurMenu] = useState('');
  const curRoute = router.pathname;

  const MenuByGroup = Menus.reduce<{
    [groupName: string]: IGroupItem;
  }>(
    (groupItems, item) => {
      const {
        group, name, href, subPath,
      } = item;
      const menuItem = (
        <MenuItem
          key={`menu_item_${name}`}
          selected={curRoute === href || (subPath?.includes(curRoute) ?? false)}
          active={curMenu === group}
          href={href}
          setActiveName={(selectedName:string) => setCurMenu(selectedName)}
        >
          {name}
        </MenuItem>
      );

      if (!group) {
        groupItems.topMenu.children.push(menuItem);
        return groupItems;
      }

      if (!groupItems[group]) {
        groupItems[group] = { name: group, children: [] };
      }
      groupItems[group].children.push(menuItem);

      return groupItems;
    },
    { topMenu: { children: [] } },
  );

  return (
    <Box
      display={display}
      pos="fixed"
      w={{ base: 'full', md: 60 }}
      borderRight="1px"
      borderRightColor={useColorModeValue('gray.200', 'gray.700')}
      h="full"
      bg={useColorModeValue('white', 'gray.900')}
      boxShadow="base"
    >
      <Flex
        mt={{ base: 5, md: 10 }}
        mb={{ base: 5, md: 10 }}
        alignItems={{ base: 'space-between', md: 'center' }}
        justifyContent={{ base: 'space-between', md: 'center' }}
        w={{ base: 'full' }}
      >
        <Image
          display={{ base: 'none', md: 'block' }}
          w={100}
          src={LogoImg.src}
          alt="Dan Abramov"
        />
        <Button
          display={{ base: 'block', md: 'none' }}
          w={{ base: 'full' }}
          size="lg"
          textAlign="left"
          onClick={onClose}
        >
          <ArrowBackIcon mr={2} />
          Back
        </Button>
      </Flex>

      <Flex flexDirection="column" alignItems="center">
        {Object.entries(MenuByGroup).map((groupItem) => (
          <MenuGroupItem key={`group_item_${groupItem[1].name}`} name={groupItem[1].name ?? ''}>
            {groupItem[1].children}
          </MenuGroupItem>
        ))}
      </Flex>
    </Box>
  );
};

export default NavMenu;
