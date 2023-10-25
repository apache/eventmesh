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

package org.apache.eventmesh.connector.spring.source.connector;

import org.apache.eventmesh.connector.spring.source.config.SpringSourceConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SpringSourceConnectorTest {

    @Spy
    private SpringSourceConnector connector;

    @Test
    public void testSpringSourceConnector() throws Exception {
        SpringSourceConfig sourceConfig = new SpringSourceConfig();
        connector.init(sourceConfig);
        connector.start();
        final int count = 5;
        final String message = "testMessage";
        writeMockedRecords(count, message);
        List<ConnectRecord> connectRecords = connector.poll();
        Assertions.assertEquals(count, connectRecords.size());
        for (int i = 0; i < connectRecords.size(); i++) {
            Object actualMessage = String.valueOf(connectRecords.get(i).getData());
            String expectedMessage = "testMessage" + i;
            Assertions.assertEquals(expectedMessage, actualMessage);
        }
    }

    private void writeMockedRecords(int count, String message) throws Exception {
        for (int i = 0; i < count; i++) {
            connector.send(message + i);
        }
    }
}
