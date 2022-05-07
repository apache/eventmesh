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

package org.apache.eventmesh.common.protocol.tcp.codec;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class CodecTest {

    @Test
    public void testCodec() throws Exception {
        Header header = new Header();
        header.setCmd(Command.HELLO_REQUEST);
        Package testP = new Package(header);
        testP.setBody(new Object());
        Codec.Encoder ce = new Codec.Encoder();
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
        ce.encode(null, testP, buf);
        Codec.Decoder cd = new Codec.Decoder();
        ArrayList<Object> result = new ArrayList<>();
        cd.decode(null, buf, result);
        Assert.assertNotNull(result.get(0));
        Assert.assertEquals(testP.getHeader(), ((Package) result.get(0)).getHeader());
    }

}
