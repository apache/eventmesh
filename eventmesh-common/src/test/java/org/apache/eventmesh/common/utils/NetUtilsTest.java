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

package org.apache.eventmesh.common.utils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NetUtilsTest {

    @Test
    public void testFormData2Dic() {
        String formData = "";
        Map<String, String> result = NetUtils.formData2Dic(formData);
        Assertions.assertTrue(result.isEmpty());

        formData = "item_id=10081&item_name=test item name";
        result = NetUtils.formData2Dic(formData);
        Assertions.assertEquals("10081", result.get("item_id"));
    }

    @Test
    public void testAddressToString() {
        List<InetSocketAddress> clients = new ArrayList<>();
        String result = NetUtils.addressToString(clients);
        Assertions.assertEquals("no session had been closed", result);

        InetSocketAddress localAddress = new InetSocketAddress(80);
        clients.add(localAddress);
        result = NetUtils.addressToString(clients);
        Assertions.assertEquals(localAddress + "|", result);
    }

}
