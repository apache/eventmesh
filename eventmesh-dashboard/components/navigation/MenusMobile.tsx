import {
  IconButton,
  Flex,
  useColorModeValue,
  Text,
  FlexProps,
} from '@chakra-ui/react';
import { FiMenu } from 'react-icons/fi';

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

export default MobileNav;
