/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.admin.server.web.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class EncryptUtil {
    public EncryptUtil() {
    }

    private static byte[] hexStringToBytes(String hexString) {
        if (hexString != null && !hexString.equals("")) {
            hexString = hexString.toUpperCase();
            int length = hexString.length() / 2;
            char[] hexChars = hexString.toCharArray();
            byte[] d = new byte[length];

            for (int i = 0; i < length; ++i) {
                int pos = i * 2;
                d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
            }

            return d;
        } else {
            return null;
        }
    }

    public static String byteToHexString(byte[] b) {
        String a = "";

        for (int i = 0; i < b.length; ++i) {
            String hex = Integer.toHexString(b[i] & 255);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }

            a = a + hex;
        }

        return a;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    private static String readFileContent(String filePath) {
        File file = new File(filePath);
        BufferedReader reader = null;
        StringBuffer key = new StringBuffer();

        try {
            IOException e;
            try {
                reader = new BufferedReader(new FileReader(file));
                e = null;

                String tempString;
                while ((tempString = reader.readLine()) != null) {
                    if (!tempString.startsWith("--")) {
                        key.append(tempString);
                    }
                }

                reader.close();
            } catch (IOException ioException) {
                e = ioException;
                e.printStackTrace();
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }

        }

        return key.toString();
    }

    public static String decrypt(String sysPubKeyFile, String appPrivKeyFile, String encStr) throws Exception {
        String pubKeyBase64 = readFileContent(sysPubKeyFile);
        String privKeyBase64 = readFileContent(appPrivKeyFile);
        byte[] encBin = hexStringToBytes(encStr);
        byte[] pubDecBin = RSAUtils.decryptByPublicKeyBlock(encBin, pubKeyBase64);
        byte[] privDecBin = RSAUtils.decryptByPrivateKeyBlock(pubDecBin, privKeyBase64);
        return new String(privDecBin);
    }

    public static String decrypt(ParamType pubKeyType, String sysPubKey, ParamType privKeyType, String appPrivKey, ParamType passwdType,
                                 String passwd) throws Exception {
        String pubKeyBase64 = pubKeyType == ParamType.FILE ? readFileContent(sysPubKey) : sysPubKey;
        String privKeyBase64 = privKeyType == ParamType.FILE ? readFileContent(appPrivKey) : appPrivKey;
        String passwdContent = passwdType == ParamType.FILE ? readFileContent(passwd) : passwd;
        byte[] encBin = hexStringToBytes(passwdContent);
        byte[] pubDecBin = RSAUtils.decryptByPublicKeyBlock(encBin, pubKeyBase64);
        byte[] privDecBin = RSAUtils.decryptByPrivateKeyBlock(pubDecBin, privKeyBase64);
        return new String(privDecBin);
    }

    public static String encrypt(String appPubKeyFile, String sysPrivKeyFile, String passwd) throws Exception {
        String pubKeyBase64 = readFileContent(appPubKeyFile);
        String privKeyBase64 = readFileContent(sysPrivKeyFile);
        byte[] pubEncBin = RSAUtils.encryptByPublicKeyBlock(passwd.getBytes(), pubKeyBase64);
        byte[] privEncBin = RSAUtils.encryptByPrivateKeyBlock(pubEncBin, privKeyBase64);
        return byteToHexString(privEncBin);
    }

    public static String encrypt(ParamType pubKeyType, String appPubKey, ParamType privKeyType, String sysPrivKey, String passwd) throws Exception {
        String pubKeyBase64 = pubKeyType == ParamType.FILE ? readFileContent(appPubKey) : appPubKey;
        String privKeyBase64 = privKeyType == ParamType.FILE ? readFileContent(sysPrivKey) : sysPrivKey;
        byte[] pubEncBin = RSAUtils.encryptByPublicKeyBlock(passwd.getBytes(), pubKeyBase64);
        byte[] privEncBin = RSAUtils.encryptByPrivateKeyBlock(pubEncBin, privKeyBase64);
        return byteToHexString(privEncBin);
    }
}
