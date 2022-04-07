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

package org.apache.eventmesh.common.protocol.http.body;

import static org.hamcrest.CoreMatchers.is;

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;

import org.junit.Assert;
import org.junit.Test;

public class BaseResponseBodyTest {

    @Test
    public void testToMap() {
        BaseResponseBody body = new BaseResponseBody();
        body.setRetCode(200);
        body.setRetMsg("SUCCESS");
        Assert.assertTrue(body.toMap().containsKey(ProtocolKey.RETCODE));
        Assert.assertTrue(body.toMap().containsKey(ProtocolKey.RETMSG));
        Assert.assertTrue(body.toMap().containsKey(ProtocolKey.RESTIME));
        Assert.assertThat(body.toMap().get(ProtocolKey.RETCODE), is(200));
        Assert.assertThat(body.toMap().get(ProtocolKey.RETMSG), is("SUCCESS"));
    }
}
