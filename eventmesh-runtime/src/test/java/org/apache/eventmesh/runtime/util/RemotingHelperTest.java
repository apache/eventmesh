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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.netty.channel.Channel;

public class RemotingHelperTest {

    @Test
    public void testExceptionSimpleDesc() {
        String result = RemotingHelper.exceptionSimpleDesc(new NullPointerException());
        Assert.assertNotNull(result);
    }

    @Test
    public void testString2SocketAddress() {
        String addr = "10.1.1.1:11002";
        SocketAddress address = RemotingHelper.string2SocketAddress(addr);
        Assert.assertNotNull(address);
    }

    @Test
    public void testParseChannelRemoteAddr() {
        SocketAddress address = new InetSocketAddress("127.0.0.1", 80);
        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.remoteAddress()).thenReturn(address);
        String addr = RemotingHelper.parseChannelRemoteAddr(channel);
        Assert.assertEquals(addr, "127.0.0.1:80");
    }

    @Test
    public void testParseSocketAddressAddr() {
        SocketAddress address = new InetSocketAddress("127.0.0.1", 80);
        String addr = RemotingHelper.parseSocketAddressAddr(address);
        Assert.assertEquals(addr, "127.0.0.1:80");
    }
}