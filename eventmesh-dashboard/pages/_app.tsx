/* eslint-disable react/jsx-props-no-spreading */
import '@fontsource/inter';
import { ChakraProvider, extendTheme } from '@chakra-ui/react';
import type { AppProps } from 'next/app';
import Sidebar from '../components/Sidebar';

const theme = extendTheme({
  initialColorMode: 'light',
  useSystemColorMode: true,
  fonts: {
    heading: 'Inter, sans-serif',
    body: 'Inter, sans-serif',
  },
});

const Application = ({ Component, pageProps }: AppProps) => (
  <ChakraProvider theme={theme}>
    <Sidebar>
      <Component {...pageProps} />
    </Sidebar>
  </ChakraProvider>
);

export default Application;
