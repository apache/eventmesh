import { ColorModeScript } from '@chakra-ui/react';
import NextDocument, {
  Html, Head, Main, NextScript,
} from 'next/document';

export default class Document extends NextDocument {
  render() {
    return (
      <Html lang="en">
        <Head />
        <body>
          <ColorModeScript />
          <Main />
          <NextScript />
        </body>
      </Html>
    );
  }
}
