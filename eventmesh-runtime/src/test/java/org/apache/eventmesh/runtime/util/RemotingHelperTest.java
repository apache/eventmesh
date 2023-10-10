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

package org.apache.eventmesh.runtime.util;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.netty.channel.Channel;

public class RemotingHelperTest {

    @Test
    public void testExceptionSimpleDesc() {
        String result = RemotingHelper.exceptionSimpleDesc(new NullPointerException());
        Assertions.assertNotNull(result);
    }

    @Test
    public void testString2SocketAddress() {
        String addr = "127.0.0.1:11002";
        InetSocketAddress address = (InetSocketAddress) RemotingHelper.string2SocketAddress(addr);
        Assertions.assertNotNull(address);
        Assertions.assertEquals("127.0.0.1:11002", address.getHostString() + ":" + address.getPort());
    }

    @Test
    public void testParseChannelRemoteAddr() {
        SocketAddress address = new InetSocketAddress("localhost", 80);
        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.remoteAddress()).thenReturn(address);
        String addr = RemotingHelper.parseChannelRemoteAddr(channel);
        Assertions.assertEquals(addr, "127.0.0.1:80");
    }

    @Test
    public void testParseSocketAddressAddr() {
        InetSocketAddress address = new InetSocketAddress("localhost", 80);
        String addr = RemotingHelper.parseSocketAddressAddr(address);
        Assertions.assertEquals("127.0.0.1:80", addr);
    }
}
